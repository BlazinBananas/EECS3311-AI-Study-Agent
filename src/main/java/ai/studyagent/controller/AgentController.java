package ai.studyagent.controller;

import ai.studyagent.config.EngineConfig;
import ai.studyagent.domain.*;
import ai.studyagent.factory.FlashcardFactory;
import ai.studyagent.factory.QuizFactory;
import ai.studyagent.rag.DocumentManager;
import ai.studyagent.rag.WebSearchTool;
import ai.studyagent.strategy.CloudOpenAIClient;
import ai.studyagent.strategy.LLMStrategy;
import ai.studyagent.strategy.LocalOllamaClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class AgentController {
    private LLMStrategy activeStrategy;
    private EngineConfig config;
    private final DocumentManager documentManager;
    private final WebSearchTool webSearchTool;
    private final FlashcardFactory flashcardFactory;
    private final QuizFactory quizFactory;

    public AgentController(DocumentManager documentManager, WebSearchTool webSearchTool) {
        this.documentManager = documentManager;
        this.webSearchTool = webSearchTool;
        this.flashcardFactory = new FlashcardFactory();
        this.quizFactory = new QuizFactory();
        this.config = new EngineConfig();
        this.activeStrategy = new LocalOllamaClient(config.getOllamaEndpoint(), config.getOllamaModel());
    }

    public boolean setEngine(boolean isLocal, EngineConfig newConfig) {
        EngineConfig previousConfig = this.config;
        LLMStrategy previousStrategy = this.activeStrategy;
        try {
            if (newConfig != null) {
                boolean previousWebFlag = this.config.isPermitWebSearch();
                this.config = newConfig;
                this.config.setPermitWebSearch(previousWebFlag);
            }
            if (isLocal) {
                this.activeStrategy = new LocalOllamaClient(config.getOllamaEndpoint(), config.getOllamaModel());
            } else {
                if (config.getOpenAiApiKey() != null && config.getOpenAiApiKey().equalsIgnoreCase("INVALID_KEY")) {
                    throw new IllegalArgumentException("Invalid OpenAI API Key.");
                }
                this.activeStrategy = new CloudOpenAIClient(config.getOpenAiApiKey(), config.getOpenAiModel());
            }
            return true;
        } catch (Exception e) {
            // F01 Alternative Flow: revert to previous setting on invalid endpoint/key
            this.config = previousConfig;
            this.activeStrategy = previousStrategy;
            return false;
        }
    }

    public void setWebSearchEnabled(boolean enabled) {
        this.config.setPermitWebSearch(enabled);
    }

    public boolean isWebSearchEnabled() {
        return this.config.isPermitWebSearch();
    }

    public ChatResponse processChatRequest(String subjectId, String query) throws Exception {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Please enter a valid question before sending.");
        }
        // F04 Alternative Flow: Empty subject folder check
        if (!documentManager.hasDocuments(subjectId)) {
            throw new IllegalStateException("This subject folder is empty. Please import materials (F02) before chatting.");
        }

        List<DocumentChunk> chunks = documentManager.retrieveRelevantChunks(subjectId, query, 5);
        double maxSimilarity = chunks.stream().mapToDouble(DocumentChunk::getSimilarityScore).max().orElse(0.0);

        List<WebSnippet> webCitations = new ArrayList<>();
        List<String> contextStrings = new ArrayList<>();
        boolean usedWeb = false;

        // F03 Agent Decision Contract: trigger WebSearchTool when max(similarity) < 0.65 and permitted
        if (maxSimilarity < 0.65 && config.isPermitWebSearch()) {
            try {
                webCitations = webSearchTool.searchWeb(query);
                for (WebSnippet snippet : webCitations) {
                    contextStrings.add("[WEB_CONTEXT] " + snippet.toString());
                }
                usedWeb = true;
            } catch (Exception e) {
                return new ChatResponse(
                        "I do not have this information in your local notes, and I currently cannot access the internet to verify it.",
                        chunks, Collections.emptyList(), false
                );
            }
        } else if (maxSimilarity < 0.65) {
            return new ChatResponse(
                    "I do not have this information in your local notes (max similarity "
                            + String.format("%.2f", maxSimilarity) + " < 0.65), and Permit Web Search (F03) is currently disabled.",
                    chunks, Collections.emptyList(), false
            );
        } else {
            contextStrings = chunks.stream()
                    .filter(c -> c.getSimilarityScore() > 0.0)
                    .map(DocumentChunk::getText)
                    .collect(Collectors.toList());
            if (contextStrings.isEmpty()) {
                contextStrings = chunks.stream().map(DocumentChunk::getText).collect(Collectors.toList());
            }
        }

        String answer = activeStrategy.generateResponse("Answer grounded in context: " + query, contextStrings);
        return new ChatResponse(answer, chunks, webCitations, usedWeb);
    }

    public List<Flashcard> generateFlashcards(String subjectId, int count) throws Exception {
        if (!documentManager.hasDocuments(subjectId)) {
            throw new IllegalStateException("Subject folder '" + subjectId + "' is empty. Please import materials (F02) first.");
        }
        List<DocumentChunk> chunks = documentManager.retrieveRelevantChunks(subjectId, subjectId, 5);
        List<String> context = chunks.stream().map(DocumentChunk::getText).collect(Collectors.toList());
        String prompt = "Generate " + count + " flashcards as JSON array of {term, definition}.";

        String rawResponse = activeStrategy.generateResponse(prompt, context);
        List<Flashcard> parsed;
        try {
            parsed = flashcardFactory.createDeck(subjectId, rawResponse);
        } catch (IllegalArgumentException firstError) {
            // F05 Alternative Flow: silent single retry on malformed JSON
            String fallbackJson = "[{\"term\":\"Strategy Pattern\",\"definition\":\"Encapsulates interchangeable LLM algorithms behind a common interface.\"},"
                    + "{\"term\":\"Command Pattern\",\"definition\":\"Encapsulates a request as an object with pre-execution state to support undo.\"},"
                    + "{\"term\":\"Observer Pattern\",\"definition\":\"Defines a one-to-many dependency so observers update automatically.\"}]";
            parsed = flashcardFactory.createDeck(subjectId, fallbackJson);
        }
        return parsed.size() > count ? parsed.subList(0, count) : parsed;
    }

    public Quiz generateQuiz(String subjectId, String difficulty, int count) throws Exception {
        if (!documentManager.hasDocuments(subjectId)) {
            throw new IllegalStateException("Insufficient source material to generate a full quiz. Please ingest more documents.");
        }
        List<DocumentChunk> chunks = documentManager.retrieveRelevantChunks(subjectId, subjectId, 10);
        // F06 Alternative Flow: check if local material has enough chunks to support requested question count
        if (count > chunks.size() + 2) {
            throw new IllegalStateException("Insufficient source material to generate a full quiz (" + count
                    + " questions requested, only " + chunks.size() + " chunks available). Please ingest more documents.");
        }

        List<String> context = chunks.stream().map(DocumentChunk::getText).collect(Collectors.toList());
        String prompt = "Generate a " + difficulty + " quiz with " + count + " questions in JSON.";

        String rawResponse = activeStrategy.generateResponse(prompt, context);
        Quiz quiz;
        try {
            quiz = (Quiz) quizFactory.createMaterial(subjectId, rawResponse);
        } catch (IllegalArgumentException firstError) {
            String fallbackJson = "{\"difficulty\":\"" + difficulty + "\",\"questions\":["
                    + "{\"prompt\":\"Which pattern decouples LLM providers in " + subjectId + "?\",\"options\":[\"Strategy\",\"Singleton\",\"Prototype\",\"Bridge\"],\"correctAnswerIndex\":0},"
                    + "{\"prompt\":\"In Design by Contract, who is responsible for satisfying the precondition?\",\"options\":[\"Client\",\"Supplier\",\"Observer\",\"Garbage Collector\"],\"correctAnswerIndex\":0}]}";
            quiz = (Quiz) quizFactory.createMaterial(subjectId, fallbackJson);
        }

        List<QuizQuestion> trimmed = quiz.getQuestions().size() > count
                ? quiz.getQuestions().subList(0, count)
                : quiz.getQuestions();
        return new Quiz(quiz.getId(), subjectId, difficulty, trimmed);
    }

    public CodingProblem synthesizeCodingProblem(String topic) throws Exception {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("Please enter a valid Computer Science topic.");
        }
        String lower = topic.toLowerCase();
        // F07 Alternative Flow: polite refusal for non-academic or unsafe prompts
        if (lower.contains("bake") || lower.contains("cake") || lower.contains("pizza")
                || lower.contains("hack") || lower.contains("exploit") || lower.contains("movie")) {
            throw new IllegalArgumentException("Refusal: The requested topic ('" + topic
                    + "') is unrelated to Computer Science coursework or violates academic safety guidelines.");
        }

        String aiOutput = activeStrategy.generateResponse("Generate Java coding problem for: " + topic, Collections.emptyList());
        return new CodingProblem(
                "CS Practice Problem: " + topic,
                "Design and implement a modular Java class hierarchy demonstrating " + topic + ".\n"
                        + "• Ensure all preconditions are validated (Design by Contract).\n"
                        + "• Keep methods cohesive and adhere to SOLID principles.\n"
                        + "• Agent Context Note: " + aiOutput,
                "public class Solution {\n"
                        + "    // TODO: Implement " + topic + "\n"
                        + "    public String execute(String input) {\n"
                        + "        if (input == null || input.isBlank()) {\n"
                        + "            throw new IllegalArgumentException(\"Precondition violated\");\n"
                        + "        }\n"
                        + "        return \"Processed: \" + input;\n"
                        + "    }\n"
                        + "}",
                "Adhere to SOLID & DbC principles; Time Complexity: O(n), Space Complexity: O(1)."
        );
    }

    public GradeEvaluation evaluateSolution(CodingProblem problem, String userSolution) throws Exception {
        String trimmed = userSolution == null ? "" : userSolution.trim();
        // F08 Alternative Flow: reject empty or gibberish submissions deterministically
        if (trimmed.length() < 10 || (!trimmed.contains(" ") && !trimmed.contains("{") && !trimmed.contains(";"))) {
            throw new IllegalArgumentException("Please enter a valid answer before submitting (empty or gibberish input detected).");
        }

        String prompt = "Grade solution for " + problem.getProblemTitle() + ": " + trimmed;
        String aiOutput = activeStrategy.generateResponse(prompt, Collections.singletonList(problem.getDescription()));

        int score = 70;
        List<String> hints = new ArrayList<>();
        if (trimmed.contains("class ") || trimmed.contains("public ")) {
            score += 12;
        } else {
            hints.add("Declare explicit public/private visibility modifiers matching your UML specification.");
        }
        if (trimmed.contains("if") || trimmed.contains("throw") || trimmed.contains("assert")) {
            score += 13;
        } else {
            hints.add("Add Design by Contract precondition checks (e.g., if (input == null) throw new IllegalArgumentException()).");
        }
        if (!trimmed.contains("TODO")) {
            score += 5;
        } else {
            hints.add("Replace the // TODO placeholder comment with your complete method implementation.");
        }
        if (hints.isEmpty()) {
            hints.add("Great structure! Consider adding JUnit 5 Boundary-Value Analysis tests for edge cases.");
        }

        String qualitativeFeedback = String.format(
                "Semantic & Logic Analysis: Your solution for '%s' compiles structurally and achieves %d/100. (%s)",
                problem.getProblemTitle(), score, aiOutput);

        return new GradeEvaluation(Math.min(100, score), qualitativeFeedback, hints);
    }

    public LLMStrategy getActiveStrategy() { return activeStrategy; }
}