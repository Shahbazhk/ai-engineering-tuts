package dev.aieng.p11;

import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;

/**
 * MCP tools over Liferay DXP content. The gateway hides the Headless Delivery calls (Phase 9) and always acts
 * with the identity of the MCP client's user - the server never escalates privileges.
 */
public final class LiferayTools {

    /** What the tools need from Liferay. Implemented with RestClient + the user's OAuth2 token in production. */
    public interface ContentGateway {
        record Hit(long id, String title, String url, String snippet) {
        }

        List<Hit> search(String siteKey, String query, int limit);

        String content(long id); // throws SecurityException if the user may not read it
    }

    private LiferayTools() {
    }

    // region tools
    public static List<McpServer.Tool> tools(ContentGateway liferay) {
        return List.of(
                new McpServer.Tool("search_content",
                        "Search published web content on a Liferay site. Returns titles, URLs and snippets.",
                        schema(Map.of(
                                "site", Map.of("type", "string", "description", "Site key, e.g. 'intranet'"),
                                "query", Map.of("type", "string", "description", "Search terms"),
                                "limit", Map.of("type", "integer", "minimum", 1, "maximum", 20)),
                                List.of("site", "query")),
                        args -> {
                            int limit = Math.clamp(args.path("limit").asInt(5), 1, 20);
                            StringBuilder sb = new StringBuilder();
                            for (ContentGateway.Hit h : liferay.search(required(args, "site"), required(args, "query"), limit)) {
                                sb.append("[").append(h.id()).append("] ").append(h.title()).append(" (").append(h.url())
                                        .append(")\n").append(h.snippet()).append("\n\n");
                            }
                            return sb.isEmpty() ? "No results." : sb.toString().strip();
                        }),
                new McpServer.Tool("get_content",
                        "Get the full text of one web content item by id (from search_content results).",
                        schema(Map.of("id", Map.of("type", "integer")), List.of("id")),
                        args -> liferay.content(args.path("id").asLong()))
        );
    }
    // endregion

    static McpServer.Tool echoTool() {
        return new McpServer.Tool("echo", "Echo the given text.", schema(Map.of("text", Map.of("type", "string")), List.of("text")),
                args -> required(args, "text"));
    }

    static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "object", "properties", properties, "required", required, "additionalProperties", false);
    }

    static String required(JsonNode args, String name) {
        String v = args.path(name).asString("");
        if (v.isBlank()) {
            throw new IllegalArgumentException("missing argument '" + name + "'");
        }
        return v;
    }
}
