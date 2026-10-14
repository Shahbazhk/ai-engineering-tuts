package dev.aieng.p00;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Prints what the course needs and whether this machine has it.
 * Run: ./mvnw -q -pl lessons/p00-setup exec:java -Dexec.mainClass=dev.aieng.p00.EnvironmentCheck
 * (or just run main() from the IDE).
 */
public final class EnvironmentCheck {

    public record Check(String name, boolean ok, String detail) {
    }

    private EnvironmentCheck() {
    }

    public static void main(String[] args) {
        String ollama = args.length > 0 ? args[0] : "http://localhost:11434";
        for (Check c : run(ollama)) {
            System.out.printf("%s %-22s %s%n", c.ok() ? "[ok]  " : "[miss]", c.name(), c.detail());
        }
    }

    public static List<Check> run(String ollamaBaseUrl) {
        List<Check> checks = new ArrayList<>();
        int feature = Runtime.version().feature();
        checks.add(new Check("Java 21+", feature >= 21, "running " + Runtime.version()));
        checks.add(new Check("Virtual threads", feature >= 21, "Thread.ofVirtual() " + (feature >= 21 ? "available" : "missing")));
        Runtime rt = Runtime.getRuntime();
        long maxHeapMb = rt.maxMemory() / (1024 * 1024);
        checks.add(new Check("Heap >= 2 GB", maxHeapMb >= 2048, maxHeapMb + " MB max heap (use -Xmx4g for Phase 3/5 training)"));
        checks.add(new Check("CPU cores", rt.availableProcessors() >= 4, rt.availableProcessors() + " cores"));
        checks.add(ollama(ollamaBaseUrl));
        return checks;
    }

    static Check ollama(String baseUrl) {
        try (HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(3)).GET().build(), HttpResponse.BodyHandlers.ofString());
            boolean ok = res.statusCode() == 200;
            return new Check("Ollama", ok, ok ? "reachable at " + baseUrl + " (models: " + modelNames(res.body()) + ")"
                    : "HTTP " + res.statusCode());
        } catch (Exception e) {
            return new Check("Ollama", false, "not reachable at " + baseUrl + " - run `docker compose up ollama` or install Ollama");
        }
    }

    /** Pulls "name" values out of the /api/tags JSON without a JSON library - good enough for a status line. */
    static String modelNames(String json) {
        List<String> names = new ArrayList<>();
        int i = 0;
        while ((i = json.indexOf("\"name\":\"", i)) >= 0) {
            int start = i + 8;
            int end = json.indexOf('"', start);
            names.add(json.substring(start, end));
            i = end;
        }
        return names.isEmpty() ? "none pulled yet" : String.join(", ", names);
    }
}
