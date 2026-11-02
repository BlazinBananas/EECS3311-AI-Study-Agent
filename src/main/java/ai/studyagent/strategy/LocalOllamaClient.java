package ai.studyagent.strategy;

import java.util.List;

public class LocalOllamaClient implements LLMStrategy {
    private String targetEndpoint;
    private String modelName;

    public LocalOllamaClient(String targetEndpoint, String modelName) {
        this.targetEndpoint = targetEndpoint;
        this.modelName = modelName;
    }

    @Override
    public String generateResponse(String prompt, List<String> contextChunks) throws Exception {
        // Implementation for HTTP POST request to local Ollama API
        System.out.println("[Ollama Local] Generating response using " + modelName + " at " + targetEndpoint);
        return "Simulated Local Ollama response based on local context chunks.";
    }
}