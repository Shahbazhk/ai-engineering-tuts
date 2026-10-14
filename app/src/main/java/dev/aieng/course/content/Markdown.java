package dev.aieng.course.content;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.web.util.HtmlUtils;

/**
 * CommonMark rendering plus two course-specific concerns: LaTeX math is protected from Markdown
 * emphasis parsing, and the document is split into heading sections for search.
 */
final class Markdown {

    // Code spans are matched first so "$" inside `code` is left alone.
    private static final Pattern MATH = Pattern.compile(
            "(`+[^`]*`+)|\\$\\$(.+?)\\$\\$|(?<![\\\\$\\w])\\$(?![\\s$])([^$\\n]+?)(?<!\\s)\\$(?![\\d$\\w])",
            Pattern.DOTALL);
    private static final Pattern HEADING_ID = Pattern.compile("<h[23] id=\"([^\"]+)\"");

    private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create(), HeadingAnchorExtension.create());
    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder().extensions(EXTENSIONS).build();

    record Rendered(String html, List<Course.Section> sections) {
    }

    private Markdown() {
    }

    static Rendered render(String lessonId, String markdown) {
        List<String> math = new ArrayList<>();
        Node doc = PARSER.parse(protectMath(markdown, math));
        String html = RENDERER.render(doc);
        for (int i = 0; i < math.size(); i++) {
            html = html.replace(placeholder(i), math.get(i));
        }
        return new Rendered(html, sections(lessonId, doc, html));
    }

    /** Replaces math outside fenced code with placeholders so CommonMark cannot mangle it. */
    static String protectMath(String md, List<String> math) {
        StringBuilder out = new StringBuilder();
        StringBuilder prose = new StringBuilder();
        boolean inFence = false;
        for (String line : md.split("\n", -1)) {
            if (line.stripLeading().startsWith("```")) {
                if (!inFence) {
                    out.append(replaceMath(prose.toString(), math));
                    prose.setLength(0);
                }
                inFence = !inFence;
                out.append(line).append('\n');
            } else if (inFence) {
                out.append(line).append('\n');
            } else {
                prose.append(line).append('\n');
            }
        }
        out.append(replaceMath(prose.toString(), math));
        return out.toString();
    }

    private static String replaceMath(String prose, List<String> math) {
        Matcher m = MATH.matcher(prose);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String replacement;
            if (m.group(1) != null) {
                replacement = m.group(1);
            } else {
                boolean display = m.group(2) != null;
                String tex = HtmlUtils.htmlEscape(display ? m.group(2) : m.group(3));
                math.add(display ? "\\[" + tex + "\\]" : "\\(" + tex + "\\)");
                replacement = placeholder(math.size() - 1);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String placeholder(int i) {
        return "MATHPH" + i + "X";
    }

    /** Splits top-level nodes into sections at h2/h3; anchors match the ids HeadingAnchorExtension wrote. */
    private static List<Course.Section> sections(String lessonId, Node doc, String html) {
        List<String> anchors = new ArrayList<>();
        Matcher ids = HEADING_ID.matcher(html);
        while (ids.find()) {
            anchors.add(ids.group(1));
        }
        List<Course.Section> sections = new ArrayList<>();
        String heading = "Introduction";
        String anchor = "";
        StringBuilder text = new StringBuilder();
        int h = 0;
        for (Node node = doc.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof Heading hd && (hd.getLevel() == 2 || hd.getLevel() == 3)) {
                add(sections, lessonId, anchor, heading, text);
                heading = plain(hd);
                anchor = h < anchors.size() ? anchors.get(h) : "";
                h++;
                text.setLength(0);
            } else {
                text.append(plain(node)).append('\n');
            }
        }
        add(sections, lessonId, anchor, heading, text);
        return sections;
    }

    private static void add(List<Course.Section> sections, String lessonId, String anchor, String heading, StringBuilder text) {
        if (!text.toString().isBlank()) {
            sections.add(new Course.Section(lessonId, anchor, heading, text.toString().strip()));
        }
    }

    private static String plain(Node node) {
        StringBuilder sb = new StringBuilder();
        node.accept(new AbstractVisitor() {
            @Override
            public void visit(Text text) {
                sb.append(text.getLiteral()).append(' ');
            }

            @Override
            public void visit(Code code) {
                sb.append(code.getLiteral()).append(' ');
            }

            @Override
            public void visit(FencedCodeBlock block) {
                sb.append(block.getLiteral()).append('\n');
            }
        });
        return sb.toString().replaceAll("MATHPH\\d+X", " ").strip();
    }
}
