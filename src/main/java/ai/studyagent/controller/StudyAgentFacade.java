package ai.studyagent.controller;

import ai.studyagent.config.EngineConfig;
import ai.studyagent.domain.ChatResponse;
import ai.studyagent.domain.CodingProblem;
import ai.studyagent.domain.DocumentChunk;
import ai.studyagent.domain.Flashcard;
import ai.studyagent.domain.GradeEvaluation;
import ai.studyagent.domain.IngestionResult;
import ai.studyagent.domain.Quiz;
import ai.studyagent.domain.QuizQuestion;
import ai.studyagent.observer.AnalyticsDashboard;
import ai.studyagent.rag.DocumentManager;
import ai.studyagent.rag.WebSearchTool;
import ai.studyagent.session.SessionManager;
import ai.studyagent.session.StudySession;
import ai.studyagent.session.SubmitAnswerCommand;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class StudyAgentFacade {
    private final DocumentManager documentManager;
    private final WebSearchTool webSearchTool;
    private final AgentController agentController;
    private final SessionManager sessionManager;
    private final AnalyticsDashboard analyticsDashboard;

    public StudyAgentFacade() {
        this.documentManager = new DocumentManager();
        this.webSearchTool = new WebSearchTool();
        this.agentController = new AgentController(documentManager, webSearchTool);
        this.sessionManager = new SessionManager();
        this.analyticsDashboard = new AnalyticsDashboard();
        this.sessionManager.attachObserver(this.analyticsDashboard);
    }

    public boolean setEngineMode(boolean isLocal, EngineConfig config) {
        return agentController.setEngine(isLocal, config);
    }

    public EngineConfig getEngineConfig() {
        return agentController.getConfig();
    }

    public void setWebSearchPermitted(boolean permitted) {
        agentController.setWebSearchEnabled(permitted);
    }

    public boolean isWebSearchPermitted() {
        return agentController.isWebSearchEnabled();
    }

    public IngestionResult ingestFolder(String subjectId, String path) {
        return documentManager.ingestFolder(subjectId, path);
    }

    public List<String> getSubjectSources(String subjectId) {
        return documentManager.getSubjectSourceFiles(subjectId);
    }

    public Map<String, List<DocumentChunk>> getAllIngestedChunks() {
        return documentManager.getSubjectStore();
    }

    public void restoreIngestedChunks(String subjectId, List<DocumentChunk> chunks) {
        documentManager.restoreSubjectChunks(subjectId, chunks);
    }

    public ChatResponse askQuestion(String subjectId, String query) throws Exception {
        return agentController.processChatRequest(subjectId, query, null);
    }

    public ChatResponse askQuestion(String subjectId, String query, Set<String> selectedSources) throws Exception {
        return agentController.processChatRequest(subjectId, query, selectedSources);
    }

    public List<Flashcard> requestFlashcards(String subjectId, int count) throws Exception {
        return agentController.generateFlashcards(subjectId, count, "", null);
    }

    public List<Flashcard> requestFlashcards(String subjectId, int count, String customTopic,
            Set<String> selectedSources) throws Exception {
        return agentController.generateFlashcards(subjectId, count, customTopic, selectedSources);
    }

    public Quiz requestQuiz(String subjectId, String difficulty, int count) throws Exception {
        return agentController.generateQuiz(subjectId, difficulty, count, "", null);
    }

    public Quiz requestQuiz(String subjectId, String difficulty, int count, String customTopic,
            Set<String> selectedSources) throws Exception {
        return agentController.generateQuiz(subjectId, difficulty, count, customTopic, selectedSources);
    }

    public CodingProblem generateCodingProblem(String topic) throws Exception {
        return agentController.synthesizeCodingProblem(topic);
    }

    public GradeEvaluation gradeSubmission(CodingProblem problem, String userSolution) throws Exception {
        if (userSolution == null || userSolution.isBlank()) {
            throw new IllegalArgumentException("Please enter a valid answer before submitting");
        }
        return agentController.evaluateSolution(problem, userSolution);
    }

    public StudySession startStudySession(Quiz quiz) {
        return sessionManager.loadSession(quiz);
    }

    public StudySession getActiveStudySession() {
        return sessionManager.getActiveSession();
    }

    public boolean submitQuizAnswer(int choiceIndex) {
        StudySession session = sessionManager.getActiveSession();
        if (session == null || session.isCompleted())
            return false;
        QuizQuestion currentQuestion = session.getCurrentQuestion();
        SubmitAnswerCommand cmd = new SubmitAnswerCommand(session, currentQuestion, choiceIndex);
        return sessionManager.executeCommand(cmd);
    }

    public boolean undoLastSessionAnswer() {
        return sessionManager.undoLastAction();
    }

    public String viewAnalytics() {
        return analyticsDashboard.renderCharts();
    }

    public void deleteSubjectData(String subjectId) {
        documentManager.deleteSubjectData(subjectId);
    }
}