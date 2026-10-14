package dev.aieng.p00;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/** Runs against a fake Ollama built on the JDK's own HTTP server, so it needs no network or model. */
class RawOllamaClientTest {

    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private String baseUrl;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastBody.set(body);
            String reply = body.contains("\"stream\":true")
                    ? "{\"message\":{\"content\":\"Hel\"},\"done\":false}\n{\"message\":{\"content\":\"lo\"},\"done\":false}\n"
                      + "{\"message\":{\"content\":\"\"},\"done\":true}\n"
                    : "{\"message\":{\"role\":\"assistant\",\"content\":\"A vector.\"},\"prompt_eval_count\":12,"
                      + "\"eval_count\":3,\"total_duration\":5000000}";
            byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.createContext("/api/tags", exchange -> {
            byte[] bytes = "{\"models\":[{\"name\":\"llama3.2:latest\"},{\"name\":\"nomic-embed-text:latest\"}]}".getBytes();
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void blockingChatParsesContentAndUsage() {
        var reply = new RawOllamaClient(baseUrl, "llama3.2")
                .chat(List.of(new RawOllamaClient.Message("user", "hi")), 0.0);
        assertEquals("A vector.", reply.content());
        assertEquals(12, reply.promptTokens());
        assertEquals(3, reply.completionTokens());
        assertEquals(5, reply.totalMillis());
        assertTrue(lastBody.get().contains("\"model\":\"llama3.2\""));
        assertTrue(lastBody.get().contains("\"temperature\":0.0"));
    }

    @Test
    void streamingDeliversTokensInOrder() {
        List<String> tokens = new ArrayList<>();
        String full = new RawOllamaClient(baseUrl, "llama3.2")
                .stream(List.of(new RawOllamaClient.Message("user", "hi")), tokens::add);
        assertEquals("Hello", full);
        assertEquals(List.of("Hel", "lo", ""), tokens);
    }

    @Test
    void environmentCheckFindsFakeOllama() {
        var check = EnvironmentCheck.ollama(baseUrl);
        assertTrue(check.ok());
        assertTrue(check.detail().contains("nomic-embed-text"));
        assertFalse(EnvironmentCheck.ollama("http://127.0.0.1:1").ok());
    }
}
