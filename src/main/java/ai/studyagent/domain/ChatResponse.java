package ai.studyagent.domain;

import java.util.List;

public class ChatResponse {
    private final String answerText;
    private final List<DocumentChunk> sourceChunks;
    private final List<WebSnippet> webCitations;
    private final boolean usedWebFallback;

    public ChatResponse(String answerText, List<DocumentChunk> sourceChunks, List<WebSnippet> webCitations, boolean usedWebFallback) {
        this.answerText = answerText;
        this.sourceChunks = sourceChunks;
        this.webCitations = webCitations;
        this.usedWebFallback = usedWebFallback;
    }

    public String getAnswerText() { return answerText; }
    public List<DocumentChunk> getSourceChunks() { return sourceChunks; }
    public List<WebSnippet> getWebCitations() { return webCitations; }
    public boolean isUsedWebFallback() { return usedWebFallback; }
}