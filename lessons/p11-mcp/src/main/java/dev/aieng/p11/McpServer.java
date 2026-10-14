package dev.aieng.p11;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A minimal Model Context Protocol server: JSON-RPC 2.0 messages, one per line, over stdin/stdout (the "stdio"
 * transport). Implements the lifecycle (initialize / initialized), tools/list and tools/call. Real servers use the
 * official MCP Java SDK; this shows exactly what goes over the wire.
 */
public final class McpServer {

    public static final String PROTOCOL_VERSION = "2025-06-18";

    /** A tool: JSON-schema for its input, and a handler that returns text (or throws for a tool-level error). */
    public record Tool(String name, String description, Map<String, Object> inputSchema,
                       Function<JsonNode, String> handler) {
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String name;
    private final String version;
    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private boolean initialized;

    public McpServer(String name, String version, List<Tool> tools) {
        this.name = name;
        this.version = version;
        tools.forEach(t -> this.tools.put(t.name(), t));
    }

    // region dispatch
    /** Handles one JSON-RPC message. Notifications (no "id") never get a response. */
    public Optional<String> handle(String line) {
        JsonNode msg;
        try {
            msg = JSON.readTree(line);
        } catch (RuntimeException e) {
            return Optional.of(error(null, -32700, "Parse error"));
        }
        JsonNode id = msg.get("id");
        String method = msg.path("method").asString("");
        if (id == null) {
            if (method.equals("notifications/initialized")) {
                initialized = true;
            }
            return Optional.empty();
        }
        return Optional.of(switch (method) {
            case "initialize" -> result(id, initializeResult(msg.path("params")));
            case "ping" -> result(id, JSON.createObjectNode());
            case "tools/list" -> initialized ? result(id, toolsList()) : error(id, -32002, "Server not initialized");
            case "tools/call" -> initialized ? callTool(id, msg.path("params")) : error(id, -32002, "Server not initialized");
            default -> error(id, -32601, "Method not found: " + method);
        });
    }
    // endregion

    private ObjectNode initializeResult(JsonNode params) {
        ObjectNode r = JSON.createObjectNode();
        // Version negotiation: echo the client's version if we support it, otherwise offer ours.
        String requested = params.path("protocolVersion").asString(PROTOCOL_VERSION);
        r.put("protocolVersion", requested.equals(PROTOCOL_VERSION) ? requested : PROTOCOL_VERSION);
        r.putObject("capabilities").putObject("tools").put("listChanged", false);
        r.putObject("serverInfo").put("name", name).put("version", version);
        return r;
    }

    // region tools
    private ObjectNode toolsList() {
        ObjectNode r = JSON.createObjectNode();
        ArrayNode list = r.putArray("tools");
        for (Tool t : tools.values()) {
            ObjectNode node = list.addObject();
            node.put("name", t.name());
            node.put("description", t.description());
            node.set("inputSchema", JSON.valueToTree(t.inputSchema()));
        }
        return r;
    }

    private String callTool(JsonNode id, JsonNode params) {
        Tool tool = tools.get(params.path("name").asString(""));
        if (tool == null) {
            return error(id, -32602, "Unknown tool: " + params.path("name").asString(""));
        }
        ObjectNode r = JSON.createObjectNode();
        ArrayNode content = r.putArray("content");
        try {
            content.addObject().put("type", "text").put("text", tool.handler().apply(params.path("arguments")));
            r.put("isError", false);
        } catch (RuntimeException e) {
            // Tool failures are RESULTS with isError=true (the model can read and react to them), not protocol errors.
            content.addObject().put("type", "text").put("text", "Error: " + e.getMessage());
            r.put("isError", true);
        }
        return result(id, r);
    }
    // endregion

    private static String result(JsonNode id, JsonNode result) {
        ObjectNode r = JSON.createObjectNode();
        r.put("jsonrpc", "2.0");
        r.set("id", id);
        r.set("result", result);
        return JSON.writeValueAsString(r);
    }

    private static String error(JsonNode id, int code, String message) {
        ObjectNode r = JSON.createObjectNode();
        r.put("jsonrpc", "2.0");
        r.set("id", id);
        r.putObject("error").put("code", code).put("message", message);
        return JSON.writeValueAsString(r);
    }

    // region stdio
    /** The stdio transport: read lines from the client, write responses to stdout. Logs must go to stderr! */
    public void serve(InputStream in, PrintStream out) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    handle(line).ifPresent(response -> {
                        out.println(response);
                        out.flush();
                    });
                }
            }
        } catch (java.io.IOException e) {
            System.err.println("MCP server stopped: " + e.getMessage());
        }
    }
    // endregion

    public static void main(String[] args) {
        new McpServer("course-demo", "1.0.0", List.of(LiferayTools.echoTool())).serve(System.in, System.out);
    }
}
