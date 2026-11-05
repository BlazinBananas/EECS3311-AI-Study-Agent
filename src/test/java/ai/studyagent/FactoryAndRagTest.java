package ai.studyagent;

import ai.studyagent.controller.StudyAgentFacade;
import ai.studyagent.domain.*;
import ai.studyagent.factory.FlashcardFactory;
import ai.studyagent.factory.QuizFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FactoryAndRagTest {
    private FlashcardFactory flashcardFactory;
    private QuizFactory quizFactory;
    private StudyAgentFacade facade;

    @BeforeEach
    public void setup() {
        flashcardFactory = new FlashcardFactory();
        quizFactory = new QuizFactory();
        facade = new StudyAgentFacade();
    }

    @Test
    public void testFlashcardFactoryParsesValidJsonAndRejectsMalformedJson() {
        String validJson = "[{\"term\":\"Facade\",\"definition\":\"Unified interface to a subsystem.\"}]";
        List<Flashcard> deck = flashcardFactory.createDeck("EECS3311", validJson);

        assertEquals(1, deck.size());
        assertEquals("Facade", deck.get(0).getTerm());
        assertThrows(IllegalArgumentException.class, () -> flashcardFactory.createDeck("EECS3311", "{bad_json"));
    }

    @Test
    public void testQuizFactoryCreatesQuizAggregateAndRejectsEmptyQuestions() {
        String validQuizJson = "{\"difficulty\":\"Advanced\",\"questions\":[{\"prompt\":\"Q1?\",\"options\":[\"A\",\"B\"],\"correctAnswerIndex\":1}]}";
        StudyMaterial material = quizFactory.createMaterial("EECS3311", validQuizJson);

        assertTrue(material instanceof Quiz);
        Quiz quiz = (Quiz) material;
        assertEquals("Advanced", quiz.getDifficulty());
        assertTrue(quiz.getQuestions().get(0).verifyAnswer(1));

        String emptyQuizJson = "{\"difficulty\":\"Easy\",\"questions\":[]}";
        assertThrows(IllegalArgumentException.class, () -> quizFactory.createMaterial("EECS3311", emptyQuizJson));
    }

    @Test
    public void testLocalDirectoryIngestionAndGroundedGeneration() throws Exception {
        IngestionResult result = facade.ingestFolder("EECS3311", "sample-course-notes");
        assertFalse(result.getImportedFileNames().isEmpty());
        assertTrue(result.getTotalChunksCreated() > 0);

        List<Flashcard> cards = facade.requestFlashcards("EECS3311", 2);
        assertFalse(cards.isEmpty());

        Quiz quiz = facade.requestQuiz("EECS3311", "Intermediate", 2);
        assertNotNull(quiz);
        assertFalse(quiz.getQuestions().isEmpty());
    }

    @Test
    public void testIntelligentGradingRejectsBlankSubmissionAndGradesValidCode() throws Exception {
        CodingProblem problem = facade.generateCodingProblem("Java Strategy Pattern");
        assertNotNull(problem);

        // Deterministic guard branch: blank solution must throw IllegalArgumentException before calling LLM
        assertThrows(IllegalArgumentException.class, () -> facade.gradeSubmission(problem, "   "));

        // Valid solution branch
        GradeEvaluation eval = facade.gradeSubmission(problem, "public class StrategyImpl implements LLMStrategy {}");
        assertTrue(eval.getScore() >= 80);
        assertFalse(eval.getHints().isEmpty());
    }
}