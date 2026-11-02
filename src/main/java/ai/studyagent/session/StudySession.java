package ai.studyagent.session;

import ai.studyagent.domain.Quiz;
import ai.studyagent.domain.QuizQuestion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StudySession {
    private final Quiz quiz;
    private int currentQuestionIndex = 0;
    private int currentScore = 0;
    private boolean completed = false;
    private final Map<Integer, Integer> userAnswersMap = new HashMap<>();

    public StudySession(Quiz quiz) {
        this.quiz = quiz;
    }

    public Quiz getQuiz() { return quiz; }
    public int getCurrentQuestionIndex() { return currentQuestionIndex; }
    public int getCurrentScore() { return currentScore; }
    public boolean isCompleted() { return completed; }
    public int getTotalQuestions() { return quiz.getQuestions().size(); }

    public QuizQuestion getCurrentQuestion() {
        List<QuizQuestion> questions = quiz.getQuestions();
        if (currentQuestionIndex >= 0 && currentQuestionIndex < questions.size()) {
            return questions.get(currentQuestionIndex);
        }
        return null;
    }

    public boolean applyAnswer(QuizQuestion question, int chosenOptionIndex) {
        boolean isCorrect = question.verifyAnswer(chosenOptionIndex);
        userAnswersMap.put(currentQuestionIndex, chosenOptionIndex);
        if (isCorrect) {
            currentScore++;
        }
        currentQuestionIndex++;
        if (currentQuestionIndex >= quiz.getQuestions().size()) {
            completed = true;
        }
        return isCorrect;
    }

    public void restoreState(int previousQuestionIndex, int previousScore) {
        this.currentQuestionIndex = previousQuestionIndex;
        this.currentScore = previousScore;
        this.completed = false;
        this.userAnswersMap.remove(previousQuestionIndex);
    }
}