package dev.aieng.p15;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Tamper-evident audit trail for AI decisions: each record carries the hash of the previous one, so editing or
 * deleting any record breaks the chain. Stores hashes of prompts and outputs, not the content itself, so the log
 * proves what happened without becoming a second copy of personal data. Supports EU AI Act record-keeping for
 * high-risk systems and ordinary incident investigation alike.
 */
public final class AuditLog {

    public record Entry(long seq, Instant at, String actor, String system, String model, String promptVersion,
                        String inputHash, String outputHash, String decision, String previousHash, String hash) {
    }

    private final List<Entry> entries = new ArrayList<>();

    // region append
    public synchronized Entry append(Instant at, String actor, String system, String model, String promptVersion,
                                     String input, String output, String decision) {
        String previous = entries.isEmpty() ? "GENESIS" : entries.getLast().hash();
        long seq = entries.size();
        String inputHash = sha256(input);
        String outputHash = sha256(output);
        String hash = sha256(String.join("|", String.valueOf(seq), at.toString(), actor, system, model, promptVersion,
                inputHash, outputHash, decision, previous));
        Entry e = new Entry(seq, at, actor, system, model, promptVersion, inputHash, outputHash, decision, previous, hash);
        entries.add(e);
        return e;
    }

    /** Recomputes every hash; returns the sequence number of the first broken record, or -1 if intact. */
    public synchronized long verify() {
        String previous = "GENESIS";
        for (Entry e : entries) {
            String expected = sha256(String.join("|", String.valueOf(e.seq()), e.at().toString(), e.actor(), e.system(),
                    e.model(), e.promptVersion(), e.inputHash(), e.outputHash(), e.decision(), previous));
            if (!e.previousHash().equals(previous) || !e.hash().equals(expected)) {
                return e.seq();
            }
            previous = e.hash();
        }
        return -1;
    }
    // endregion

    /** Test hook simulating an attacker editing storage directly. */
    synchronized void tamper(int index, String newDecision) {
        Entry e = entries.get(index);
        entries.set(index, new Entry(e.seq(), e.at(), e.actor(), e.system(), e.model(), e.promptVersion(), e.inputHash(),
                e.outputHash(), newDecision, e.previousHash(), e.hash()));
    }

    public static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
