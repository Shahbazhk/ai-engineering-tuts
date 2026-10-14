package dev.aieng.p11;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The client side of MCP, as an AI host (IDE, chat app, your Spring service) runs it: initialize, discover tools,
 * call them. The transport is a function from request line to response line - stdio pipes, HTTP or in-memory.
 */
public final class McpClient {

    public record ToolInfo(String name, String description, JsonNode inputSchema) {
    }

    public record CallResult(String text, boolean isError) {
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Function<String, String> transport;
    private final java.util.function.Consumer<String> notify;
    private int nextId = 1;

    public McpClient(Function<String, String> transport, java.util.function.Consumer<String> notify) {
        this.transport = transport;
        this.notify = notify;
    }

    // region lifecycle
    public String initialize(String clientName) {
        ObjectNode params = JSON.createObjectNode();
        params.put("protocolVersion", McpServer.PROTOCOL_VERSION);
        params.putObject("capabilities");
        params.putObject("clientInfo").put("name", clientName).put("version", "1.0.0");
        JsonNode result = request("initialize", params);
        notify.accept("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
        return result.path("serverInfo").path("name").asString();
    }

    public List<ToolInfo> listTools() {
        List<ToolInfo> out = new ArrayList<>();
        for (JsonNode t : request("tools/list", JSON.createObjectNode()).path("tools")) {
            out.add(new ToolInfo(t.path("name").asString(), t.path("description").asString(), t.path("inputSchema")));
        }
        return out;
    }

    public CallResult call(String tool, ObjectNode arguments) {
        ObjectNode params = JSON.createObjectNode();
        params.put("name", tool);
        params.set("arguments", arguments);
        JsonNode result = request("tools/call", params);
        StringBuilder text = new StringBuilder();
        result.path("content").forEach(c -> text.append(c.path("text").asString()));
        return new CallResult(text.toString(), result.path("isError").asBoolean());
    }
    // endregion

    public static ObjectNode args() {
        return JSON.createObjectNode();
    }

    private JsonNode request(String method, ObjectNode params) {
        ObjectNode req = JSON.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("id", nextId++);
        req.put("method", method);
        req.set("params", params);
        JsonNode response = JSON.readTree(transport.apply(JSON.writeValueAsString(req)));
        if (response.has("error")) {
            throw new IllegalStateException("MCP error " + response.path("error").path("code").asInt() + ": "
                    + response.path("error").path("message").asString());
        }
        return response.path("result");
    }
}
