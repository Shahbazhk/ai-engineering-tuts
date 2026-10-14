package dev.aieng.p15;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pseudonymises personal data before text is sent to a model, and restores it in the answer for the same user.
 * Pattern + checksum detection (Luhn for card numbers, mod-97 for IBANs) cuts false positives. Regexes cannot find
 * names or addresses - combine with an NER-based detector in production.
 */
public final class PiiRedactor {

    public record Redacted(String text, Map<String, String> vault) {
        /** Puts the original values back into model output, for the user who supplied them. */
        public String restore(String modelOutput) {
            String out = modelOutput;
            for (Map.Entry<String, String> e : vault.entrySet()) {
                out = out.replace(e.getKey(), e.getValue());
            }
            return out;
        }
    }

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+");
    private static final Pattern CARD = Pattern.compile("\\b(?:\\d[ -]?){13,19}\\b");
    private static final Pattern IBAN = Pattern.compile("\\b[A-Z]{2}\\d{2}(?:[ ]?[A-Z0-9]{4}){2,7}(?:[ ]?[A-Z0-9]{1,3})?\\b");
    // International (+49 170 1234567) or national with a separator (0170-1234567). Bare digit runs such as order
    // numbers are deliberately NOT matched - precision matters, or redaction destroys useful data.
    private static final Pattern PHONE = Pattern.compile(
            "(?<!\\w)(?:\\+\\d{1,3}[ -]?(?:\\(?\\d{1,4}\\)?[ -]?){1,4}\\d{3,8}|0\\d{2,4}[ /-]\\d{4,8})(?!\\w)");

    private PiiRedactor() {
    }

    // region redact
    public static Redacted redact(String text) {
        Map<String, String> vault = new LinkedHashMap<>();
        String out = replace(text, EMAIL, "EMAIL", vault, v -> true);
        out = replace(out, IBAN, "IBAN", vault, PiiRedactor::validIban);
        out = replace(out, CARD, "CARD", vault, PiiRedactor::luhn);
        out = replace(out, PHONE, "PHONE", vault, v -> v.replaceAll("\\D", "").length() >= 8);
        return new Redacted(out, vault);
    }

    private static String replace(String text, Pattern p, String type, Map<String, String> vault,
                                  java.util.function.Predicate<String> validator) {
        Matcher m = p.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String value = m.group();
            String token = value;
            if (validator.test(value)) {
                token = vault.entrySet().stream().filter(e -> e.getValue().equals(value)).map(Map.Entry::getKey)
                        .findFirst().orElse("[" + type + "_" + (vault.size() + 1) + "]");
                vault.put(token, value); // same value -> same token, so the model can still reason about identity
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(token));
        }
        m.appendTail(sb);
        return sb.toString();
    }
    // endregion

    // region checksums
    /** Luhn checksum used by payment card numbers: rejects most random digit strings (order numbers, timestamps). */
    static boolean luhn(String candidate) {
        String digits = candidate.replaceAll("\\D", "");
        if (digits.length() < 13 || digits.length() > 19) {
            return false;
        }
        int sum = 0;
        boolean dbl = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (dbl) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            dbl = !dbl;
        }
        return sum % 10 == 0;
    }

    /** ISO 13616 IBAN check: move the first four characters to the end, letters to numbers, mod 97 must equal 1. */
    static boolean validIban(String candidate) {
        String iban = candidate.replace(" ", "");
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        StringBuilder numeric = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            numeric.append(Character.isLetter(c) ? String.valueOf(c - 'A' + 10) : String.valueOf(c));
        }
        return new BigInteger(numeric.toString()).mod(BigInteger.valueOf(97)).intValue() == 1;
    }
    // endregion
}
