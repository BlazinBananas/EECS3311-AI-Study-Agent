package ai.studyagent;

import ai.studyagent.domain.DocumentChunk;
import ai.studyagent.domain.Quiz;
import ai.studyagent.domain.QuizQuestion;
import ai.studyagent.observer.AnalyticsDashboard;
import ai.studyagent.session.SessionManager;
import ai.studyagent.session.StudySession;
import ai.studyagent.session.SubmitAnswerCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SessionAndContractTest {
    private Quiz sampleQuiz;
    private SessionManager sessionManager;
    private AnalyticsDashboard dashboard;

    @BeforeEach
    public void setup() {
        QuizQuestion q1 = new QuizQuestion(
                "Which SOLID principle promotes high cohesion?",
                List.of("Single Responsibility", "Open/Closed", "Liskov Substitution", "Dependency Inversion"),
                0
        );
        QuizQuestion q2 = new QuizQuestion(
                "Which pattern encapsulates a request as an object to support undo?",
                List.of("Observer", "Command", "Facade", "Adapter"),
                1
        );
        sampleQuiz = new Quiz("quiz-1", "EECS3311", "Intermediate", List.of(q1, q2));
        sessionManager = new SessionManager();
        dashboard = new AnalyticsDashboard();
        sessionManager.attachObserver(dashboard);
    }

    @Test
    public void testStudySessionPreconditionRejectsNullOrEmptyQuiz() {
        assertThrows(IllegalArgumentException.class, () -> new StudySession(null));
        assertThrows(IllegalArgumentException.class,
                () -> new StudySession(new Quiz("q-empty", "EECS3311", "Easy", List.of())));
    }

    @Test
    public void testApplyAnswerPreconditionRejectsOutOfBoundsOption() {
        StudySession session = sessionManager.loadSession(sampleQuiz);
        QuizQuestion current = session.getCurrentQuestion();
        assertThrows(IllegalArgumentException.class, () -> session.applyAnswer(current, -1));
        assertThrows(IllegalArgumentException.class, () -> session.applyAnswer(current, 4));
    }

    @Test
    public void testCommandExecuteAndUndoRestoresPreExecutionState() {
        StudySession session = sessionManager.loadSession(sampleQuiz);
        QuizQuestion q1 = session.getCurrentQuestion();

        SubmitAnswerCommand cmd1 = new SubmitAnswerCommand(session, q1, 0);
        boolean correct = sessionManager.executeCommand(cmd1);

        assertTrue(correct);
        assertEquals(1, session.getCurrentQuestionIndex());
        assertEquals(1, session.getCurrentScore());
        assertFalse(session.isCompleted());

        boolean undone = sessionManager.undoLastAction();
        assertTrue(undone);
        assertEquals(0, session.getCurrentQuestionIndex());
        assertEquals(0, session.getCurrentScore());
        assertFalse(session.isCompleted());
    }

    @Test
    public void testUndoOnEmptyCommandStackReturnsFalse() {
        sessionManager.loadSession(sampleQuiz);
        assertFalse(sessionManager.undoLastAction());
    }

    @Test
    public void testObserverNotifiedOnlyWhenSessionCompletes() {
        StudySession session = sessionManager.loadSession(sampleQuiz);

        // Answer Question 1 (Correct) - session not yet complete
        sessionManager.executeCommand(new SubmitAnswerCommand(session, session.getCurrentQuestion(), 0));
        assertTrue(dashboard.getSubjectHistory().isEmpty());

        // Answer Question 2 (Incorrect) - session completes and triggers Observer update
        sessionManager.executeCommand(new SubmitAnswerCommand(session, session.getCurrentQuestion(), 0));
        assertTrue(session.isCompleted());
        assertEquals(1, dashboard.getSubjectHistory().get("EECS3311").size());
        assertTrue(dashboard.renderCharts().contains("50.0%"));
    }

    @Test
    public void testBoundaryValueAnalysisOnSimilarityThreshold() {
        // Boundary-Value Analysis around threshold 0.65: below (0.64), on (0.65), above (0.66)
        DocumentChunk belowBoundary = new DocumentChunk("notes.txt", "EECS3311", "text", 0.64);
        DocumentChunk onBoundary = new DocumentChunk("notes.txt", "EECS3311", "text", 0.65);
        DocumentChunk aboveBoundary = new DocumentChunk("notes.txt", "EECS3311", "text", 0.66);

        assertTrue(belowBoundary.getSimilarityScore() < 0.65);
        assertFalse(onBoundary.getSimilarityScore() < 0.65);
        assertFalse(aboveBoundary.getSimilarityScore() < 0.65);
    }
}