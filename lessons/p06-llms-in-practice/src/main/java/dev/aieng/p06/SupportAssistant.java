package dev.aieng.p06;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.PromptTemplate;

/**
 * A Spring AI service the way you would write it in a Boot app: one ChatClient built once with defaults
 * (system prompt, options, advisors), then used per request. In a Boot app the ChatModel is injected by the
 * provider starter (Ollama, OpenAI, ...) - nothing in this class names a provider.
 */
public class SupportAssistant {

    // region client
    static final String SYSTEM = """
            You are the support assistant for {product}.
            Answer in at most {maxSentences} sentences. If you are not sure, say so and suggest contacting support.
            Never invent product features.""";

    private final ChatClient chat;

    public SupportAssistant(ChatModel model, String product) {
        this.chat = ChatClient.builder(model)
                .defaultSystem(s -> s.text(SYSTEM).params(Map.of("product", product, "maxSentences", 3)))
                .defaultOptions(ChatOptions.builder().temperature(0.2).maxTokens(400))
                .defaultAdvisors(new SimpleLoggerAdvisor()) // logs request/response at DEBUG: an interceptor
                .build();
    }

    public String answer(String question) {
        return chat.prompt().user(question).call().content();
    }
    // endregion

    // region template
    /** PromptTemplate renders {placeholders}; the same engine (StringTemplate) backs defaultSystem(...).params(...). */
    public static String renderTicketPrompt(String customer, String ticket) {
        return PromptTemplate.builder()
                .template("Customer {customer} wrote:\n<ticket>\n{ticket}\n</ticket>\nSummarise the problem in one line.")
                .build()
                .render(Map.of("customer", customer, "ticket", ticket));
    }
    // endregion
}
