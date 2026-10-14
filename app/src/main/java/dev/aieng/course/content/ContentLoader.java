package dev.aieng.course.content;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import dev.aieng.course.content.Course.GlossaryEntry;
import dev.aieng.course.content.Course.InterviewQuestion;
import dev.aieng.course.content.Course.Lesson;
import dev.aieng.course.content.Course.Phase;
import dev.aieng.course.content.Course.QuizQuestion;

/**
 * Reads the course from disk. Layout:
 * <pre>
 * content/glossary.yml
 * content/pNN-slug/phase.yml          title, summary, hours
 * content/pNN-slug/MM-slug.md         YAML front matter + Markdown body
 * </pre>
 * A body line {@code <!-- code: lessons/.../Foo.java#region -->} is replaced with that file (or region) so
 * lesson text always shows the code that the build compiles and tests. Any broken reference fails fast.
 */
public final class ContentLoader {

    private static final Pattern INCLUDE = Pattern.compile("(?m)^<!--\\s*code:\\s*([^#\\s]+)(?:#(\\S+))?\\s*-->\\s*$");
    private static final Pattern REGION_MARKER = Pattern.compile("^\\s*(//|#)\\s*(region|endregion)\\b.*$");

    private final Path root;

    public ContentLoader(Path root) {
        this.root = root;
    }

    public Course load() {
        Path contentDir = root.resolve("content");
        List<Phase> phases = new ArrayList<>();
        Map<String, Lesson> lessons = new LinkedHashMap<>();
        for (Path dir : list(contentDir, p -> Files.isDirectory(p) && p.getFileName().toString().matches("p\\d\\d-.+"))) {
            Phase phase = loadPhase(dir);
            phases.add(phase);
            phase.lessons().forEach(l -> lessons.put(l.id(), l));
        }
        validatePrereqs(lessons);
        return new Course(List.copyOf(phases), Map.copyOf(lessons), loadGlossary(contentDir.resolve("glossary.yml"), lessons));
    }

    private Phase loadPhase(Path dir) {
        String id = dir.getFileName().toString();
        Map<String, Object> meta = yaml(read(dir.resolve("phase.yml")));
        List<Lesson> lessons = new ArrayList<>();
        for (Path file : list(dir, p -> p.getFileName().toString().matches("\\d\\d-.+\\.md"))) {
            lessons.add(loadLesson(id, file));
        }
        return new Phase(id, Integer.parseInt(id.substring(1, 3)), required(meta, "title", dir), str(meta.get("summary")),
                str(meta.get("hours")), List.copyOf(lessons));
    }

    Lesson loadLesson(String phaseId, Path file) {
        String raw = read(file).replace("\r\n", "\n");
        if (!raw.startsWith("---\n")) {
            throw new IllegalStateException(file + ": missing YAML front matter");
        }
        int end = raw.indexOf("\n---\n", 4);
        if (end < 0) {
            throw new IllegalStateException(file + ": unterminated front matter");
        }
        Map<String, Object> meta = yaml(raw.substring(4, end));
        String slug = file.getFileName().toString().replaceFirst("\\.md$", "");
        String id = phaseId + "/" + slug;
        String body = expandIncludes(raw.substring(end + 5), file);
        Markdown.Rendered rendered = Markdown.render(id, body);
        return new Lesson(id, phaseId, slug, Integer.parseInt(slug.substring(0, 2)), required(meta, "title", file),
                str(meta.get("summary")), meta.get("minutes") instanceof Number n ? n.intValue() : 30,
                strings(meta.get("prereqs")), strings(meta.get("tags")), rendered.html(), rendered.sections(),
                quiz(meta.get("quiz"), file), interview(meta.get("interview")));
    }

    private String expandIncludes(String body, Path file) {
        Matcher m = INCLUDE.matcher(body);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String path = m.group(1);
            Path source = root.resolve(path).normalize();
            if (!source.startsWith(root.normalize()) || !Files.isRegularFile(source)) {
                throw new IllegalStateException(file + ": include not found: " + path);
            }
            String code = region(read(source).replace("\r\n", "\n"), m.group(2), file + " -> " + path);
            String fileName = source.getFileName().toString();
            String lang = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1) : "text";
            String block = "<div class=\"code-file\">" + path + (m.group(2) == null ? "" : " &middot; " + m.group(2))
                    + "</div>\n\n```" + lang + "\n" + code.strip() + "\n```\n";
            m.appendReplacement(sb, Matcher.quoteReplacement(block));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Returns the whole file or the lines between "// region name" and the matching "// endregion". */
    static String region(String code, String name, String where) {
        List<String> out = new ArrayList<>();
        boolean inside = name == null;
        boolean found = name == null;
        int depth = 0;
        for (String line : code.split("\n", -1)) {
            Matcher marker = REGION_MARKER.matcher(line);
            if (marker.matches()) {
                boolean open = marker.group(2).equals("region");
                if (name != null && open && !inside && line.trim().endsWith(" " + name)) {
                    inside = true;
                    found = true;
                    depth = 0;
                } else if (name != null && inside) {
                    depth += open ? 1 : -1;
                    if (depth < 0) {
                        inside = false;
                    }
                }
                continue;
            }
            if (inside) {
                out.add(line);
            }
        }
        if (!found) {
            throw new IllegalStateException(where + ": region not found: " + name);
        }
        return dedent(out);
    }

    private static String dedent(List<String> lines) {
        int indent = lines.stream().filter(l -> !l.isBlank())
                .mapToInt(l -> l.length() - l.stripLeading().length()).min().orElse(0);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line.isBlank() ? "" : line.substring(indent)).append('\n');
        }
        return sb.toString();
    }

    private static List<QuizQuestion> quiz(Object raw, Path file) {
        List<QuizQuestion> out = new ArrayList<>();
        for (Map<String, Object> q : maps(raw)) {
            List<String> options = strings(q.get("options"));
            int answer = ((Number) q.get("answer")).intValue();
            if (options.size() < 2 || answer < 0 || answer >= options.size()) {
                throw new IllegalStateException(file + ": bad quiz question: " + q.get("q"));
            }
            out.add(new QuizQuestion(str(q.get("q")), options, answer, str(q.get("why"))));
        }
        return List.copyOf(out);
    }

    private static List<InterviewQuestion> interview(Object raw) {
        return maps(raw).stream().map(q -> new InterviewQuestion(str(q.get("q")), str(q.get("a")),
                q.get("level") == null ? "senior" : str(q.get("level")))).toList();
    }

    private List<GlossaryEntry> loadGlossary(Path file, Map<String, Lesson> lessons) {
        if (!Files.exists(file)) {
            return List.of();
        }
        List<GlossaryEntry> out = new ArrayList<>();
        for (Map<String, Object> e : maps(new Yaml(new SafeConstructor(new LoaderOptions())).load(read(file)))) {
            String lesson = str(e.get("lesson"));
            if (!lesson.isEmpty() && !lessons.containsKey(lesson)) {
                throw new IllegalStateException("glossary.yml: unknown lesson " + lesson + " for " + e.get("term"));
            }
            out.add(new GlossaryEntry(str(e.get("term")), str(e.get("definition")), lesson));
        }
        out.sort((a, b) -> a.term().compareToIgnoreCase(b.term()));
        return List.copyOf(out);
    }

    private static void validatePrereqs(Map<String, Lesson> lessons) {
        for (Lesson l : lessons.values()) {
            for (String p : l.prereqs()) {
                if (!lessons.containsKey(p)) {
                    throw new IllegalStateException(l.id() + ": unknown prereq " + p);
                }
            }
        }
    }

    // --- small helpers -------------------------------------------------------------------------------

    private static Map<String, Object> yaml(String text) {
        Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(text);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = loaded instanceof Map<?, ?> ? (Map<String, Object>) loaded : Map.of();
        return map;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(Object raw) {
        return raw instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private static List<String> strings(Object raw) {
        return raw instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).strip();
    }

    private static String required(Map<String, Object> meta, String key, Path where) {
        String v = str(meta.get(key));
        if (v.isEmpty()) {
            throw new IllegalStateException(where + ": missing '" + key + "'");
        }
        return v;
    }

    private static List<Path> list(Path dir, java.util.function.Predicate<Path> filter) {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(filter).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
