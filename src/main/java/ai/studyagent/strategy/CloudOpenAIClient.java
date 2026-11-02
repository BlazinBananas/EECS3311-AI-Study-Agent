package ai.studyagent.strategy;

import java.util.List;

public class CloudOpenAIClient implements LLMStrategy {
    private String apiKey;
    private String modelName;

    public CloudOpenAIClient(String apiKey, String modelName) {
        this.apiKey = apiKey;
        this.modelName = modelName;
    }

    @Override
    public String generateResponse(String prompt, List<String> contextChunks) throws Exception {
        // Implementation for HTTPS POST request to OpenAI /v1/chat/completions
        System.out.println("[OpenAI Cloud] Generating response using " + modelName);
        return "Simulated Cloud OpenAI response based on context chunks.";
    }
}