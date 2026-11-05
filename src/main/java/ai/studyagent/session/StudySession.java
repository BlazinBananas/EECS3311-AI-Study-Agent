package ai.studyagent.session;

import ai.studyagent.domain.Quiz;
import ai.studyagent.domain.QuizQuestion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StudySession {
    private /*@ spec_public non_null @*/ final Quiz quiz;
    private /*@ spec_public @*/ int currentQuestionIndex = 0;
    private /*@ spec_public @*/ int currentScore = 0;
    private /*@ spec_public @*/ boolean completed = false;
    private final Map<Integer, Integer> userAnswersMap = new HashMap<>();

    /*@ public invariant currentScore >= 0 && currentScore <= currentQuestionIndex;
      @ public invariant currentQuestionIndex >= 0 && currentQuestionIndex <= quiz.getQuestions().size();
      @*/

    /*@ requires quiz != null && quiz.getQuestions() != null && !quiz.getQuestions().isEmpty();
      @ ensures this.quiz == quiz && currentQuestionIndex == 0 && currentScore == 0 && !completed;
      @*/
    public StudySession(Quiz quiz) {
        if (quiz == null || quiz.getQuestions() == null || quiz.getQuestions().isEmpty()) {
            throw new IllegalArgumentException("Precondition violated: Quiz must be non-null and contain questions.");
        }
        this.quiz = quiz;
        assertInvariants();
    }

    public /*@ pure @*/ Quiz getQuiz() { return quiz; }
    public /*@ pure @*/ int getCurrentQuestionIndex() { return currentQuestionIndex; }
    public /*@ pure @*/ int getCurrentScore() { return currentScore; }
    public /*@ pure @*/ boolean isCompleted() { return completed; }
    public /*@ pure @*/ int getTotalQuestions() { return quiz.getQuestions().size(); }

    public /*@ pure @*/ QuizQuestion getCurrentQuestion() {
        List<QuizQuestion> questions = quiz.getQuestions();
        if (currentQuestionIndex >= 0 && currentQuestionIndex < questions.size()) {
            return questions.get(currentQuestionIndex);
        }
        return null;
    }

    /*@ requires !completed;
      @ requires question != null;
      @ requires chosenOptionIndex >= 0 && chosenOptionIndex < question.getOptions().size();
      @ assignable currentQuestionIndex, currentScore, completed;
      @ ensures currentQuestionIndex == \old(currentQuestionIndex) + 1;
      @ ensures \result ==> (currentScore == \old(currentScore) + 1);
      @ ensures !\result ==> (currentScore == \old(currentScore));
      @*/
    public boolean applyAnswer(QuizQuestion question, int chosenOptionIndex) {
        // Negated preconditions encoded as exceptions (DbC Lecture 14)
        if (completed) {
            throw new IllegalStateException("Precondition violated: Session is already completed.");
        }
        if (question == null) {
            throw new IllegalArgumentException("Precondition violated: Question cannot be null.");
        }
        if (chosenOptionIndex < 0 || chosenOptionIndex >= question.getOptions().size()) {
            throw new IllegalArgumentException("Precondition violated: Option index out of bounds.");
        }

        // Capture pre-execution state for postcondition verification
        int oldIndex = this.currentQuestionIndex;
        int oldScore = this.currentScore;

        boolean isCorrect = question.verifyAnswer(chosenOptionIndex);
        userAnswersMap.put(currentQuestionIndex, chosenOptionIndex);
        if (isCorrect) {
            currentScore++;
        }
        currentQuestionIndex++;
        if (currentQuestionIndex >= quiz.getQuestions().size()) {
            completed = true;
        }

        // Postconditions and Class Invariants encoded as assertions
        assert this.currentQuestionIndex == oldIndex + 1 : "Postcondition failed: Question index did not increment by 1";
        assert (isCorrect && this.currentScore == oldScore + 1) || (!isCorrect && this.currentScore == oldScore)
                : "Postcondition failed: Score update mismatch";
        assertInvariants();

        return isCorrect;
    }

    /*@ requires previousQuestionIndex >= 0 && previousQuestionIndex < quiz.getQuestions().size();
      @ requires previousScore >= 0 && previousScore <= previousQuestionIndex;
      @ assignable currentQuestionIndex, currentScore, completed;
      @ ensures currentQuestionIndex == previousQuestionIndex;
      @ ensures currentScore == previousScore;
      @ ensures !completed;
      @*/
    public void restoreState(int previousQuestionIndex, int previousScore) {
        if (previousQuestionIndex < 0 || previousQuestionIndex >= quiz.getQuestions().size()) {
            throw new IllegalArgumentException("Precondition violated: Invalid previous question index.");
        }
        if (previousScore < 0 || previousScore > previousQuestionIndex) {
            throw new IllegalArgumentException("Precondition violated: Invalid previous score.");
        }

        this.currentQuestionIndex = previousQuestionIndex;
        this.currentScore = previousScore;
        this.completed = false;
        this.userAnswersMap.remove(previousQuestionIndex);

        // Postconditions and Invariants
        assert this.currentQuestionIndex == previousQuestionIndex : "Postcondition failed: Question index not restored";
        assert this.currentScore == previousScore : "Postcondition failed: Score not restored";
        assert !this.completed : "Postcondition failed: Session should not be marked completed after undo";
        assertInvariants();
    }

    private void assertInvariants() {
        assert currentScore >= 0 && currentScore <= currentQuestionIndex : "Invariant violated: Score out of valid range";
        assert currentQuestionIndex >= 0 && currentQuestionIndex <= quiz.getQuestions().size() : "Invariant violated: Question index out of bounds";
    }
}