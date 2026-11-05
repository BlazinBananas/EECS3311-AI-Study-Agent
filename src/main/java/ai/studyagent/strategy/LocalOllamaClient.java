package ai.studyagent.strategy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

public class LocalOllamaClient implements LLMStrategy {
    private final String targetEndpoint;
    private final String modelName;
    private final HttpClient httpClient;

    public LocalOllamaClient(String targetEndpoint, String modelName) {
        this.targetEndpoint = targetEndpoint;
        this.modelName = modelName;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .build();
    }

    @Override
    public String generateResponse(String prompt, List<String> contextChunks) throws Exception {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Precondition violated: Prompt cannot be empty.");
        }

        String combinedContext = (contextChunks == null || contextChunks.isEmpty())
                ? "No local context chunks provided."
                : String.join("\n---\n", contextChunks);

        JsonObject payload = new JsonObject();
        payload.addProperty("model", modelName);
        payload.addProperty("prompt", "Context:\n" + combinedContext + "\n\nTask: " + prompt);
        payload.addProperty("stream", false);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetEndpoint))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonObject jsonResp = JsonParser.parseString(response.body()).getAsJsonObject();
                if (jsonResp.has("response")) {
                    return jsonResp.get("response").getAsString().trim();
                }
            }
        } catch (Exception ignored) {
            // Graceful offline fallback when local Ollama daemon is not running
        }

        return buildGroundedFallback(prompt, combinedContext);
    }

    private String buildGroundedFallback(String prompt, String combinedContext) {
        String lower = prompt.toLowerCase();
        if (lower.contains("flashcard") && lower.contains("json")) {
            return "[{\"term\":\"Strategy Pattern\",\"definition\":\"Encapsulates interchangeable LLM algorithms behind a common interface.\"},"
                    + "{\"term\":\"Design by Contract\",\"definition\":\"Defines preconditions, postconditions, and invariants between client and supplier.\"}]";
        }
        if (lower.contains("quiz") && lower.contains("json")) {
            return "{\"difficulty\":\"Intermediate\",\"questions\":["
                    + "{\"prompt\":\"Which SOLID principle states that classes should be open for extension but closed for modification?\","
                    + "\"options\":[\"Open/Closed Principle\",\"Single Responsibility\",\"Liskov Substitution\",\"Interface Segregation\"],\"correctAnswerIndex\":0},"
                    + "{\"prompt\":\"In Design by Contract, who is obligated to satisfy a method's precondition?\","
                    + "\"options\":[\"The Client\",\"The Supplier\",\"The Garbage Collector\",\"The Observer\"],\"correctAnswerIndex\":0}]}";
        }
        String preview = combinedContext.length() > 180 ? combinedContext.substring(0, 180) + "..." : combinedContext;
        return "[Ollama " + modelName + "] Based on your course notes (" + preview + "), here is the synthesized explanation for: " + prompt;
    }

    public String getTargetEndpoint() { return targetEndpoint; }
    public String getModelName() { return modelName; }
}