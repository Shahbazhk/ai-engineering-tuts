package dev.aieng.p06;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Tool calling: the model cannot touch your systems, but it can ASK you to run a method. Spring AI turns
 * {@code @Tool} methods into JSON-schema tool definitions, sends them with the prompt, executes the method the
 * model asks for, sends the result back, and loops until the model produces a final answer.
 */
public class OrderTools {

    // region tools
    public record Order(String id, String status, String eta) {
    }

    private final Map<String, Order> orders = Map.of(
            "A-1001", new Order("A-1001", "SHIPPED", "2026-10-16"),
            "A-1002", new Order("A-1002", "PROCESSING", "2026-10-20"));

    /** Every call is recorded so tests (and audit logs) can see what the model did. */
    final List<String> calls = new ArrayList<>();

    @Tool(description = "Look up the status and estimated delivery date of a customer order by its order id")
    public Order orderStatus(@ToolParam(description = "Order id, format A-1234") String orderId) {
        calls.add("orderStatus(" + orderId + ")");
        Order o = orders.get(orderId);
        if (o == null) {
            // Errors are returned as data the model can explain - not thrown into the void.
            return new Order(orderId, "NOT_FOUND", "unknown");
        }
        return o;
    }
    // endregion

    // region usage
    /**
     * The tool loop is itself an advisor (an interceptor around the model call): when the response contains tool
     * calls, ToolCallingAdvisor executes them, appends the results to the conversation and calls the model again.
     * Registering it explicitly keeps the loop visible and works the same with any ChatClient.
     */
    public static String ask(ChatModel model, OrderTools tools, String question) {
        return ChatClient.create(model)
                .prompt()
                .system("You help customers with orders. Use tools for facts; never guess order data.")
                .user(question)
                .tools(tools)
                .advisors(ToolCallingAdvisor.builder().build())
                .call()
                .content();
    }
    // endregion

    public List<String> calls() {
        return List.copyOf(calls);
    }
}
