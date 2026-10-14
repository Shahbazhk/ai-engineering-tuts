package dev.aieng.p06;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * Structured output: ask for a Java type, get a Java object. Spring AI appends JSON-schema format instructions
 * derived from the record to the prompt and parses the reply with Jackson. The model can still return invalid
 * or wrong data, so the result is validated like any untrusted input.
 */
public class TicketTriage {

    // region types
    public enum Category { BILLING, TECHNICAL, ACCOUNT, OTHER }

    public enum Priority { LOW, MEDIUM, HIGH, URGENT }

    public record Triage(Category category, Priority priority, String summary, List<String> affectedProducts) {
    }
    // endregion

    private final ChatClient chat;

    public TicketTriage(ChatModel model) {
        this.chat = ChatClient.builder(model)
                .defaultSystem("You triage support tickets for an enterprise portal product.")
                .defaultOptions(ChatOptions.builder().temperature(0.0)) // extraction: no creativity wanted
                .build();
    }

    // region triage
    public Triage triage(String ticket) {
        Triage t = chat.prompt()
                .user(u -> u.text("Triage this ticket:\n<ticket>\n{ticket}\n</ticket>").param("ticket", ticket))
                .call()
                .entity(Triage.class);
        return validate(t);
    }

    /** Business validation the schema cannot express. Throwing lets the caller retry or route to a human. */
    static Triage validate(Triage t) {
        if (t == null || t.category() == null || t.priority() == null) {
            throw new IllegalStateException("model returned an incomplete triage: " + t);
        }
        if (t.summary() == null || t.summary().isBlank() || t.summary().length() > 200) {
            throw new IllegalStateException("summary missing or too long");
        }
        return t;
    }
    // endregion
}
