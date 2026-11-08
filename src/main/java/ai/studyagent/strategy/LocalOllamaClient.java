package ai.studyagent.strategy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class LocalOllamaClient implements LLMStrategy {
    private final String targetEndpoint;
    private final String modelName;
    private final HttpClient httpClient;

    public LocalOllamaClient(String targetEndpoint, String modelName) {
        if (targetEndpoint == null || !targetEndpoint.startsWith("http")) {
            throw new IllegalArgumentException("Invalid local endpoint URL: " + targetEndpoint);
        }
        this.targetEndpoint = targetEndpoint.trim();
        this.modelName = (modelName == null || modelName.isBlank()) ? "qwen3-8b" : modelName.trim();
        // Force HTTP_1_1 so LM Studio and local llama.cpp servers never reject HTTP/2 upgrade headers
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public String generateResponse(String prompt, List<String> contextChunks) throws Exception {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Precondition violated: Prompt cannot be empty.");
        }

        String combinedContext = (contextChunks == null || contextChunks.isEmpty())
                ? "No additional context provided."
                : String.join("\n\n", contextChunks);

        String fullPrompt = "Context Information:\n" + combinedContext + "\n\nInstruction: " + prompt;

        try {
            // 1. If endpoint is configured for LM Studio (:1234 or /v1/chat/completions), call LM Studio directly
            if (targetEndpoint.contains("1234") || targetEndpoint.contains("/v1/chat/completions")) {
                return callLMStudioServer(targetEndpoint, fullPrompt);
            }
            // 2. Otherwise try the configured Ollama endpoint first; if Ollama is off, auto-check LM Studio on :1234
            try {
                return callOllamaNativeServer(fullPrompt);
            } catch (Exception ollamaEx) {
                return callLMStudioServer("http://127.0.0.1:1234/v1/chat/completions", fullPrompt);
            }
        } catch (Exception connectionEx) {
            return buildOfflineFallback(prompt, contextChunks, connectionEx.getMessage());
        }
    }

    private String callLMStudioServer(String endpointUrl, String fullPrompt) throws Exception {
        // Auto-resolve the active model ID from LM Studio if available
        String activeModelId = resolveActiveLMStudioModel(this.modelName);

        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", "You are a helpful, accurate Computer Science study assistant. Follow formatting instructions strictly.");

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", fullPrompt);

        JsonArray messages = new JsonArray();
        messages.add(systemMsg);
        messages.add(userMsg);

        JsonObject payload = new JsonObject();
        payload.addProperty("model", activeModelId);
        payload.add("messages", messages);
        payload.addProperty("temperature", 0.3);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpointUrl))
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            String content = root.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString().trim();
            return stripMarkdownCodeFences(content);
        }
        throw new IllegalStateException("LM Studio HTTP " + response.statusCode() + ": " + response.body());
    }

    private String resolveActiveLMStudioModel(String fallbackModel) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:1234/v1/models"))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofSeconds(2))
                    .GET().build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                if (root.has("data") && !root.getAsJsonArray("data").isEmpty()) {
                    for (JsonElement el : root.getAsJsonArray("data")) {
                        String id = el.getAsJsonObject().get("id").getAsString();
                        if (id.toLowerCase().contains("qwen")) {
                            return id;
                        }
                    }
                    return root.getAsJsonArray("data").get(0).getAsJsonObject().get("id").getAsString();
                }
            }
        } catch (Exception ignored) {}
        return fallbackModel;
    }

    private String callOllamaNativeServer(String fullPrompt) throws Exception {
        JsonObject payload = new JsonObject();
        payload.addProperty("model", modelName);
        payload.addProperty("prompt", fullPrompt);
        payload.addProperty("stream", false);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetEndpoint))
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            JsonObject jsonResp = JsonParser.parseString(response.body()).getAsJsonObject();
            if (jsonResp.has("response")) {
                return stripMarkdownCodeFences(jsonResp.get("response").getAsString().trim());
            }
        }
        throw new IllegalStateException("Ollama HTTP " + response.statusCode());
    }

    public static List<String> detectInstalledLocalModels() {
        List<String> models = new ArrayList<>();
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        // 1. Check LM Studio first on 127.0.0.1:1234
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:1234/v1/models"))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofSeconds(2))
                    .GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                if (root.has("data")) {
                    for (JsonElement el : root.getAsJsonArray("data")) {
                        models.add(el.getAsJsonObject().get("id").getAsString());
                    }
                }
            }
        } catch (Exception ignored) {}

        // 2. Check Ollama on 127.0.0.1:11434
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:11434/api/tags"))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofSeconds(2))
                    .GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                if (root.has("models")) {
                    for (JsonElement el : root.getAsJsonArray("models")) {
                        models.add(el.getAsJsonObject().get("name").getAsString());
                    }
                }
            }
        } catch (Exception ignored) {}

        return models;
    }

    public static String stripMarkdownCodeFences(String text) {
        if (text == null) return "";
        String cleaned = text.trim();
        // Strip <think>...</think> reasoning blocks emitted by Qwen3 / DeepSeek models
        cleaned = cleaned.replaceAll("(?s)<think>.*?</think>", "").trim();
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline != -1) {
                cleaned = cleaned.substring(firstNewline + 1);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
        }
        return cleaned.trim();
    }

    private String buildOfflineFallback(String prompt, List<String> contextChunks, String errorReason) {
        String lower = prompt.toLowerCase();
        if (lower.contains("flashcard") && lower.contains("json")) {
            return "["
                    + "{\"term\":\"Single Responsibility Principle (SRP)\",\"definition\":\"Every class should have only one responsibility and one reason to change.\"},"
                    + "{\"term\":\"Liskov Substitution Principle (LSP)\",\"definition\":\"Subtypes must be substitutable for their base types without altering program correctness.\"},"
                    + "{\"term\":\"Strategy Pattern\",\"definition\":\"Encapsulates interchangeable algorithms behind a common interface.\"},"
                    + "{\"term\":\"Command Pattern\",\"definition\":\"Encapsulates a request as an object, supporting undo via pre-execution state snapshots.\"}"
                    + "]";
        }
        if (lower.contains("quiz") && lower.contains("json")) {
            return "{\"difficulty\":\"Intermediate\",\"questions\":["
                    + "{\"prompt\":\"Which SOLID principle states that software entities should be open for extension but closed for modification?\","
                    + "\"options\":[\"Open/Closed Principle (OCP)\",\"Single Responsibility Principle (SRP)\",\"Interface Segregation Principle (ISP)\",\"Liskov Substitution Principle (LSP)\"],\"correctAnswerIndex\":0},"
                    + "{\"prompt\":\"In Design by Contract (DbC), who is obligated to satisfy a method's precondition?\","
                    + "\"options\":[\"The Client (Caller)\",\"The Supplier (Callee)\",\"The Observer\",\"The Garbage Collector\"],\"correctAnswerIndex\":0}"
                    + "]}";
        }
        if (contextChunks != null && !contextChunks.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("[Offline Fallback — Local LLM server not reachable (").append(errorReason)
              .append("). Start LM Studio Server on port 1234 for live inference.]\n\n");
            sb.append("Retrieved Context Summary:\n");
            for (String chunk : contextChunks) {
                sb.append("• ").append(chunk).append("\n\n");
            }
            return sb.toString().trim();
        }
        return "[Offline Fallback — Start LM Studio Server on port 1234] Processed task: " + prompt;
    }

    public String getTargetEndpoint() { return targetEndpoint; }
    public String getModelName() { return modelName; }
}