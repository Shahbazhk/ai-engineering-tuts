package dev.aieng.p15;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Heuristic prompt-injection signals for UNTRUSTED content (retrieved documents, emails, web pages, tool output).
 * It raises a risk score - it does not and cannot prove safety: attackers paraphrase, translate and encode.
 * Use it to route risky content to a stricter path (no tools, human review), never as the only defence.
 */
public final class InjectionDetector {

    public record Assessment(int score, List<String> signals) {
        public boolean suspicious() {
            return score >= 3;
        }
    }

    private static final List<Pattern> PHRASES = List.of(
            Pattern.compile("ignore (all |any )?(previous|prior|above) (instructions|rules)"),
            Pattern.compile("disregard (the |your )?(system|previous) (prompt|instructions)"),
            Pattern.compile("you are now (a|an|in) "),
            Pattern.compile("(reveal|print|show) (your|the) (system prompt|instructions)"),
            Pattern.compile("do not tell the user"),
            Pattern.compile("(send|forward|email) .* to [\\w.+-]+@"));
    private static final Pattern ROLE_MARKERS = Pattern.compile("(?m)^\\s*(system|assistant)\\s*:|<\\|?(im_start|system)\\|?>");
    private static final Pattern MARKDOWN_IMAGE_URL = Pattern.compile("!\\[[^]]*]\\(https?://[^)]*[?&][^)]*\\)");
    private static final Pattern LONG_BASE64 = Pattern.compile("[A-Za-z0-9+/]{120,}={0,2}");
    private static final Pattern INVISIBLE = Pattern.compile("[\\u200B-\\u200F\\u2060-\\u2064\\uFEFF\\u202A-\\u202E\\x{E0000}-\\x{E007F}]");

    private InjectionDetector() {
    }

    // region assess
    public static Assessment assess(String untrusted) {
        List<String> signals = new ArrayList<>();
        int score = 0;
        if (INVISIBLE.matcher(untrusted).find()) {
            signals.add("invisible or bidi control characters");
            score += 2;
        }
        String text = Normalizer.normalize(INVISIBLE.matcher(untrusted).replaceAll(""), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        for (Pattern p : PHRASES) {
            if (p.matcher(text).find()) {
                signals.add("instruction phrase: " + p.pattern());
                score += 3;
            }
        }
        if (ROLE_MARKERS.matcher(text).find()) {
            signals.add("fake role / chat-template markers");
            score += 2;
        }
        if (MARKDOWN_IMAGE_URL.matcher(untrusted).find()) {
            signals.add("markdown image with query string (exfiltration channel)");
            score += 3;
        }
        if (LONG_BASE64.matcher(untrusted).find()) {
            signals.add("long encoded blob");
            score += 1;
        }
        return new Assessment(score, signals);
    }
    // endregion

    /** Removes characters humans cannot see but models read - a cheap, always-on sanitising step. */
    public static String stripInvisible(String text) {
        return INVISIBLE.matcher(text).replaceAll("");
    }
}
