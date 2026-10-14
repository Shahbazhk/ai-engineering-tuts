package dev.aieng.p00;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * An LLM call with nothing but the JDK HTTP client and Jackson. Every framework in this course
 * (Spring AI, LangChain4j) ultimately does exactly this: serialise messages, POST, parse the reply.
 */
public final class RawOllamaClient {

    public record Message(String role, String content) {
    }

    public record Reply(String content, int promptTokens, int completionTokens, long totalMillis) {
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final URI chatUri;
    private final String model;

    public RawOllamaClient(String baseUrl, String model) {
        this.chatUri = URI.create(baseUrl + "/api/chat");
        this.model = model;
    }

    // region blocking
    /** One request, one complete answer. */
    public Reply chat(List<Message> messages, double temperature) {
        String body = JSON.writeValueAsString(new Request(model, messages, false, new Options(temperature)));
        HttpRequest request = HttpRequest.newBuilder(chatUri)
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Ollama returned HTTP " + response.statusCode() + ": " + response.body());
            }
            JsonNode json = JSON.readTree(response.body());
            return new Reply(json.path("message").path("content").asString(),
                    json.path("prompt_eval_count").asInt(), json.path("eval_count").asInt(),
                    json.path("total_duration").asLong() / 1_000_000);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
    // endregion

    // region streaming
    /**
     * Streaming: Ollama sends newline-delimited JSON, one object per generated chunk, ending with "done": true.
     * Each chunk is handed to {@code onToken} as soon as it arrives - this is what makes chat UIs feel fast.
     */
    public String stream(List<Message> messages, Consumer<String> onToken) {
        String body = JSON.writeValueAsString(new Request(model, messages, true, new Options(0.7)));
        HttpRequest request = HttpRequest.newBuilder(chatUri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        StringBuilder full = new StringBuilder();
        try {
            HttpResponse<java.io.InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (BufferedReader lines = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = lines.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    JsonNode chunk = JSON.readTree(line);
                    String token = chunk.path("message").path("content").asString();
                    full.append(token);
                    onToken.accept(token);
                    if (chunk.path("done").asBoolean()) {
                        break;
                    }
                }
            }
            return full.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
    // endregion

    record Options(double temperature) {
    }

    record Request(String model, List<Message> messages, boolean stream, Options options) {
    }

    public static void main(String[] args) {
        RawOllamaClient client = new RawOllamaClient("http://localhost:11434", "llama3.2");
        List<Message> messages = List.of(
                new Message("system", "You are a concise assistant for senior Java engineers."),
                new Message("user", "In two sentences: what does an embedding model return?"));
        client.stream(messages, System.out::print);
        System.out.println();
    }
}
