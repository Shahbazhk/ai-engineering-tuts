package dev.aieng.p15;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks model output before it reaches the user or a downstream system: system-prompt leakage (via a canary
 * token), personal data, links to non-approved domains (a classic exfiltration channel when the UI renders
 * Markdown images), and out-of-range citations. Returns violations instead of throwing so callers choose:
 * block, redact, regenerate or escalate.
 */
public final class OutputGuard {

    public record Verdict(boolean allowed, List<String> violations) {
    }

    private static final Pattern LINK = Pattern.compile("\\]\\((https?://[^)\\s]+)\\)|https?://[^\\s)\"'>]+");
    private static final Pattern CITATION = Pattern.compile("\\[(\\d+)]");

    private final String canary;
    private final Set<String> allowedDomains;

    public OutputGuard(String canary, Set<String> allowedDomains) {
        this.canary = canary;
        this.allowedDomains = allowedDomains;
    }

    // region check
    public Verdict check(String output, int sourceCount) {
        List<String> violations = new ArrayList<>();
        if (output.contains(canary)) {
            violations.add("system prompt leaked (canary found)");
        }
        if (!PiiRedactor.redact(output).vault().isEmpty()) {
            violations.add("personal data in output");
        }
        Matcher links = LINK.matcher(output);
        while (links.find()) {
            String url = links.group(1) != null ? links.group(1) : links.group();
            String host = URI.create(url.replaceAll("[.,;]+$", "")).getHost();
            if (host == null || allowedDomains.stream().noneMatch(d -> host.equals(d) || host.endsWith("." + d))) {
                violations.add("link to non-approved domain: " + host);
            }
        }
        Matcher cites = CITATION.matcher(output);
        while (cites.find()) {
            int n = Integer.parseInt(cites.group(1));
            if (n < 1 || n > sourceCount) {
                violations.add("citation [" + n + "] does not match any source");
            }
        }
        return new Verdict(violations.isEmpty(), violations);
    }
    // endregion
}
