package dev.aieng.p07;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.chat.prompt.PromptTemplate;

/**
 * Prompts as versioned, reviewable artefacts: stored under {@code prompts/<name>/v<N>.st} on the classpath,
 * selected by version (pinned in configuration), rendered with variables, and identified by a content hash that
 * is logged with every model call. This is what lets you answer "which prompt produced this answer?".
 */
public final class PromptRegistry {

    public record Rendered(String name, int version, String sha256, String text) {
    }

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    // region render
    public Rendered render(String name, int version, Map<String, Object> variables) {
        String path = "prompts/" + name + "/v" + version + ".st";
        String template = cache.computeIfAbsent(path, PromptRegistry::load);
        String text = PromptTemplate.builder().template(template).build().render(variables).replace("\r\n", "\n");
        return new Rendered(name, version, sha256(template), text);
    }
    // endregion

    private static String load(String path) {
        try (InputStream in = PromptRegistry.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("unknown prompt " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n").strip();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)))
                    .substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
