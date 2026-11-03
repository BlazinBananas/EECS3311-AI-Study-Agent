package ai.studyagent.config;

public class EngineConfig {
    private String ollamaEndpoint = "http://localhost:11434/api/generate";
    private String ollamaModel = "llama3.1:8b-instruct-q4_K_M";
    private String openAiApiKey = "";
    private String openAiModel = "gpt-4o-mini";
    private boolean permitWebSearch = true;

    public EngineConfig() {}

    public EngineConfig(String ollamaEndpoint, String ollamaModel, String openAiApiKey, String openAiModel, boolean permitWebSearch) {
        this.ollamaEndpoint = ollamaEndpoint;
        this.ollamaModel = ollamaModel;
        this.openAiApiKey = openAiApiKey;
        this.openAiModel = openAiModel;
        this.permitWebSearch = permitWebSearch;
    }

    public String getOllamaEndpoint() { return ollamaEndpoint; }
    public String getOllamaModel() { return ollamaModel; }
    public String getOpenAiApiKey() { return openAiApiKey; }
    public String getOpenAiModel() { return openAiModel; }
    public boolean isPermitWebSearch() { return permitWebSearch; }
    public void setPermitWebSearch(boolean permitWebSearch) { this.permitWebSearch = permitWebSearch; }
}