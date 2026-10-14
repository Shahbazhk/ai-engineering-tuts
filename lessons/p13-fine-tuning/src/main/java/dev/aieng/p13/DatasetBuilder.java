package dev.aieng.p13;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

import tools.jackson.databind.json.JsonMapper;

/**
 * Builds a supervised fine-tuning dataset in the chat "messages" JSONL format that Hugging Face TRL, OpenAI,
 * and most fine-tuning services accept. Most fine-tuning failures are data failures: duplicates, leaked PII,
 * inconsistent style, examples that contradict each other - so the pipeline cleans before it writes.
 */
public final class DatasetBuilder {

    public record Example(String instruction, String response) {
    }

    public record Report(List<String> trainJsonl, List<String> validationJsonl, int duplicates, int rejected) {
    }

    record Message(String role, String content) {
    }

    record Conversation(List<Message> messages) {
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+");
    private static final Pattern IBAN = Pattern.compile("\\b[A-Z]{2}\\d{2}[A-Z0-9]{11,30}\\b");

    private DatasetBuilder() {
    }

    // region build
    public static Report build(String systemPrompt, List<Example> raw, double validationFraction, long seed) {
        Set<String> seen = new HashSet<>();
        List<Example> clean = new ArrayList<>();
        int duplicates = 0;
        int rejected = 0;
        for (Example e : raw) {
            String key = sha(normalize(e.instruction()));
            if (!seen.add(key)) {
                duplicates++;               // near-identical prompts over-weight one behaviour
                continue;
            }
            if (!acceptable(e)) {
                rejected++;
                continue;
            }
            clean.add(e);
        }
        Collections.shuffle(clean, new Random(seed));
        int v = (int) Math.round(clean.size() * validationFraction);
        return new Report(toJsonl(systemPrompt, clean.subList(v, clean.size())), toJsonl(systemPrompt, clean.subList(0, v)),
                duplicates, rejected);
    }

    /** Quality gates: non-trivial, not too long, and no obvious personal data in either side. */
    static boolean acceptable(Example e) {
        String both = e.instruction() + "\n" + e.response();
        return e.instruction().strip().length() >= 10
                && e.response().strip().length() >= 2
                && both.length() <= 8_000
                && !EMAIL.matcher(both).find()
                && !IBAN.matcher(both).find();
    }

    /** {"messages":[{"role":"system",...},{"role":"user",...},{"role":"assistant",...}]} - one line per example. */
    static List<String> toJsonl(String system, List<Example> examples) {
        // Records serialise their components in declaration order, so every line has a stable shape.
        return examples.stream().map(e -> JSON.writeValueAsString(new Conversation(List.of(
                new Message("system", system),
                new Message("user", e.instruction()),
                new Message("assistant", e.response()))))).toList();
    }
    // endregion

    static String normalize(String s) {
        return s.toLowerCase().replaceAll("\\s+", " ").replaceAll("[^\\p{L}\\p{N} ]", "").strip();
    }

    static String sha(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
