package dev.aieng.p11;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

class McpTest {

    final LiferayTools.ContentGateway fakeLiferay = new LiferayTools.ContentGateway() {
        @Override
        public List<Hit> search(String siteKey, String query, int limit) {
            return siteKey.equals("intranet") && query.contains("vpn")
                    ? List.of(new Hit(101, "VPN setup", "/w/vpn-setup", "Install the client..."))
                    : List.of();
        }

        @Override
        public String content(long id) {
            if (id == 102) {
                throw new SecurityException("not allowed to read content 102");
            }
            return "VPN setup\nInstall the client. Proxy port 8443.";
        }
    };

    McpServer server() {
        return new McpServer("liferay-mcp", "0.1.0", LiferayTools.tools(fakeLiferay));
    }

    @Test
    void lifecycleListAndCallOverJsonRpc() {
        McpServer server = server();
        McpClient client = new McpClient(line -> server.handle(line).orElseThrow(), line -> assertTrue(server.handle(line).isEmpty()));

        assertEquals("liferay-mcp", client.initialize("course-test"));
        List<McpClient.ToolInfo> tools = client.listTools();
        assertEquals(List.of("search_content", "get_content"), tools.stream().map(McpClient.ToolInfo::name).toList());
        assertEquals("object", tools.getFirst().inputSchema().path("type").asString());
        assertEquals("site", tools.getFirst().inputSchema().path("required").get(0).asString());

        var args = McpClient.args().put("site", "intranet").put("query", "vpn proxy");
        McpClient.CallResult hits = client.call("search_content", args);
        assertFalse(hits.isError());
        assertTrue(hits.text().startsWith("[101] VPN setup (/w/vpn-setup)"), hits.text());
        assertTrue(client.call("get_content", McpClient.args().put("id", 101)).text().contains("8443"));
    }

    @Test
    void toolFailuresAreResultsAndProtocolFailuresAreErrors() {
        McpServer server = server();
        McpClient client = new McpClient(line -> server.handle(line).orElseThrow(), server::handle);
        client.initialize("t");

        McpClient.CallResult denied = client.call("get_content", McpClient.args().put("id", 102));
        assertTrue(denied.isError(), "permission problems come back as isError results the model can explain");
        assertTrue(denied.text().contains("not allowed"));

        McpClient.CallResult missing = client.call("search_content", McpClient.args().put("site", "intranet"));
        assertTrue(missing.isError());
        assertTrue(missing.text().contains("missing argument 'query'"));

        var e = assertThrows(IllegalStateException.class, () -> client.call("drop_tables", McpClient.args()));
        assertTrue(e.getMessage().contains("-32602"));
    }

    @Test
    void serverEnforcesLifecycleAndJsonRpcRules() {
        McpServer server = server();
        assertTrue(server.handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}").orElseThrow().contains("-32002"),
                "tools are unavailable before initialisation completes");
        assertTrue(server.handle("not json").orElseThrow().contains("-32700"));
        assertTrue(server.handle("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"resources/list\"}").orElseThrow().contains("-32601"));
        assertTrue(server.handle("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}").isEmpty(), "no reply to notifications");
        String init = server.handle("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"1999-01-01\"}}").orElseThrow();
        assertTrue(init.contains("\"protocolVersion\":\"" + McpServer.PROTOCOL_VERSION + "\""), "server offers its own version");
    }

    @Test
    void stdioTransportReadsAndWritesOneMessagePerLine() {
        String input = """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}}
                {"jsonrpc":"2.0","method":"notifications/initialized"}
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"echo","arguments":{"text":"hello"}}}
                """;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new McpServer("demo", "1", List.of(LiferayTools.echoTool()))
                .serve(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), new PrintStream(out, true, StandardCharsets.UTF_8));
        List<String> lines = out.toString(StandardCharsets.UTF_8).lines().toList();
        assertEquals(2, lines.size(), "two requests, one notification -> two responses");
        assertTrue(lines.get(1).contains("\"text\":\"hello\""), lines.get(1));
    }
}
