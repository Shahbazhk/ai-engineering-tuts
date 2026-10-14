package dev.aieng.p11;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;

/**
 * The same Liferay tools in Spring AI's annotation model. Put this class in a Spring Boot app with
 * spring-ai-starter-mcp-server-webmvc, register it as a bean, and the starter publishes the methods as MCP tools
 * over Streamable HTTP - JSON schemas are generated from the method signatures.
 */
public class LiferayMcpTools {

    private final LiferayTools.ContentGateway liferay;

    public LiferayMcpTools(LiferayTools.ContentGateway liferay) {
        this.liferay = liferay;
    }

    // region annotated
    @McpTool(name = "search_content",
            description = "Search published web content on a Liferay site. Returns titles, URLs and snippets.")
    public List<LiferayTools.ContentGateway.Hit> searchContent(
            @McpToolParam(description = "Site key, e.g. 'intranet'", required = true) String site,
            @McpToolParam(description = "Search terms", required = true) String query,
            @McpToolParam(description = "Maximum results, 1-20", required = false) Integer limit) {
        return liferay.search(site, query, Math.clamp(limit == null ? 5 : limit, 1, 20));
    }

    @McpTool(name = "get_content", description = "Get the full text of one web content item by id.")
    public String getContent(@McpToolParam(description = "Content id from search_content", required = true) long id) {
        return liferay.content(id);
    }
    // endregion
}
