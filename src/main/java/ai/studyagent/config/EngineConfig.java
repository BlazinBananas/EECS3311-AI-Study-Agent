package ai.studyagent.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.nio.file.Files;

public class EngineConfig {
    private String ollamaEndpoint = "http://localhost:11434/api/generate";
    private String ollamaModel = "llama3.1:8b-instruct-q4_K_M";
    private String openAiApiKey = "";
    private String openAiModel = "gpt-4o-mini";
    private boolean permitWebSearch = true;

    public EngineConfig() {
        loadFromLocalConfig("config.local.json");
    }

    public EngineConfig(String ollamaEndpoint, String ollamaModel, String openAiApiKey, String openAiModel, boolean permitWebSearch) {
        this.ollamaEndpoint = ollamaEndpoint;
        this.ollamaModel = ollamaModel;
        this.openAiApiKey = openAiApiKey;
        this.openAiModel = openAiModel;
        this.permitWebSearch = permitWebSearch;
    }

    public final void loadFromLocalConfig(String filePath) {
        File configFile = new File(filePath);
        if (!configFile.exists() || !configFile.isFile()) {
            return;
        }
        try {
            String rawJson = Files.readString(configFile.toPath());
            JsonObject obj = JsonParser.parseString(rawJson).getAsJsonObject();
            if (obj.has("ollama_endpoint")) {
                this.ollamaEndpoint = obj.get("ollama_endpoint").getAsString();
            }
            if (obj.has("ollama_model")) {
                this.ollamaModel = obj.get("ollama_model").getAsString();
            }
            if (obj.has("openai_api_key")) {
                this.openAiApiKey = obj.get("openai_api_key").getAsString();
            }
            if (obj.has("openai_model")) {
                this.openAiModel = obj.get("openai_model").getAsString();
            }
        } catch (Exception ignored) {
            // Retain safe default settings if local file is unreadable
        }
    }

    public String getOllamaEndpoint() { return ollamaEndpoint; }
    public String getOllamaModel() { return ollamaModel; }
    public String getOpenAiApiKey() { return openAiApiKey; }
    public String getOpenAiModel() { return openAiModel; }
    public boolean isPermitWebSearch() { return permitWebSearch; }
    public void setPermitWebSearch(boolean permitWebSearch) { this.permitWebSearch = permitWebSearch; }
}