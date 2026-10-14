package dev.aieng.p09;

import java.util.ArrayList;
import java.util.List;

/**
 * Chunking strategies. A chunk is the unit that gets embedded, retrieved and pasted into the prompt, so its size
 * and boundaries decide what the model can see: too large and retrieval gets vague and expensive; too small and a
 * chunk loses the context that made it meaningful.
 */
public final class Chunker {

    public record Chunk(String text, String headingPath, int index) {
    }

    private Chunker() {
    }

    // region fixed
    /** Fixed-size windows of {@code size} words with {@code overlap} words repeated between neighbours. */
    public static List<String> fixedSize(String text, int size, int overlap) {
        if (overlap >= size) {
            throw new IllegalArgumentException("overlap must be smaller than size");
        }
        String[] words = text.trim().split("\\s+");
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < words.length; start += size - overlap) {
            int end = Math.min(words.length, start + size);
            chunks.add(String.join(" ", java.util.Arrays.copyOfRange(words, start, end)));
            if (end == words.length) {
                break;
            }
        }
        return chunks;
    }
    // endregion

    // region recursive
    /**
     * Recursive splitting: try the coarsest separator first (paragraphs), and only split a piece further (lines,
     * sentences, words) if it is still too long. Keeps natural boundaries whenever possible.
     */
    public static List<String> recursive(String text, int maxChars) {
        return recursive(text.strip(), maxChars, new String[] {"\n\n", "\n", ". ", " "}, 0);
    }

    private static List<String> recursive(String text, int maxChars, String[] separators, int level) {
        if (text.length() <= maxChars || level == separators.length) {
            return text.isBlank() ? List.of() : List.of(text.strip());
        }
        String sep = separators[level];
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String piece : text.split(java.util.regex.Pattern.quote(sep))) {
            String candidate = current.isEmpty() ? piece : current + sep + piece;
            if (candidate.length() <= maxChars) {
                current.setLength(0);
                current.append(candidate);
            } else {
                if (!current.isEmpty()) {
                    out.add(current.toString().strip());
                }
                if (piece.length() > maxChars) {
                    out.addAll(recursive(piece, maxChars, separators, level + 1));
                    current.setLength(0);
                } else {
                    current.setLength(0);
                    current.append(piece);
                }
            }
        }
        if (!current.isEmpty() && !current.toString().isBlank()) {
            out.add(current.toString().strip());
        }
        return out;
    }
    // endregion

    // region headings
    /**
     * Structure-aware chunking for Markdown/HTML-like content: split at headings and keep the heading path
     * ("Install > Windows > Proxy") with every chunk. Prepending that path to the chunk text before embedding
     * ("contextual chunk headers") makes a chunk like "Set the port to 8443." retrievable for "VPN proxy port".
     */
    public static List<Chunk> byHeadings(String markdown, int maxChars) {
        List<Chunk> chunks = new ArrayList<>();
        String[] path = new String[7];
        StringBuilder body = new StringBuilder();
        for (String line : markdown.split("\n", -1)) {
            int level = headingLevel(line);
            if (level > 0) {
                flush(chunks, body, path, maxChars);
                path[level] = line.substring(level).strip();
                for (int deeper = level + 1; deeper < path.length; deeper++) {
                    path[deeper] = null;
                }
            } else {
                body.append(line).append('\n');
            }
        }
        flush(chunks, body, path, maxChars);
        return chunks;
    }

    public static String withHeader(String documentTitle, Chunk chunk) {
        return documentTitle + (chunk.headingPath().isEmpty() ? "" : " > " + chunk.headingPath()) + "\n" + chunk.text();
    }
    // endregion

    private static void flush(List<Chunk> chunks, StringBuilder body, String[] path, int maxChars) {
        if (body.toString().isBlank()) {
            body.setLength(0);
            return;
        }
        List<String> parts = new ArrayList<>();
        for (String p : path) {
            if (p != null) {
                parts.add(p);
            }
        }
        String headingPath = String.join(" > ", parts);
        for (String piece : recursive(body.toString(), maxChars)) {
            chunks.add(new Chunk(piece, headingPath, chunks.size()));
        }
        body.setLength(0);
    }

    private static int headingLevel(String line) {
        int n = 0;
        while (n < line.length() && n < 6 && line.charAt(n) == '#') {
            n++;
        }
        return n > 0 && n < line.length() && line.charAt(n) == ' ' ? n : 0;
    }
}
