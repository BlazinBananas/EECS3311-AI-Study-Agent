package ai.studyagent.strategy;

import java.util.List;

public interface LLMStrategy {
    String generateResponse(String prompt, List<String> contextChunks) throws Exception;
}