package ai.studyagent.session;

import ai.studyagent.domain.QuizQuestion;

public class SubmitAnswerCommand implements Command {
    private /*@ spec_public non_null @*/ final StudySession session;
    private /*@ spec_public non_null @*/ final QuizQuestion question;
    private /*@ spec_public @*/ final int submittedChoiceIndex;

    // Snapshot of pre-execution state for deterministic undo
    private /*@ spec_public @*/ int previousScore;
    private /*@ spec_public @*/ int previousQuestionIndex;
    private /*@ spec_public @*/ boolean wasCorrect;

    /*@ requires session != null && question != null;
      @ requires submittedChoiceIndex >= 0 && submittedChoiceIndex < question.getOptions().size();
      @*/
    public SubmitAnswerCommand(StudySession session, QuizQuestion question, int submittedChoiceIndex) {
        if (session == null || question == null) {
            throw new IllegalArgumentException("Precondition violated: Session and Question must be non-null.");
        }
        this.session = session;
        this.question = question;
        this.submittedChoiceIndex = submittedChoiceIndex;
    }

    /*@ also
      @ assignable previousScore, previousQuestionIndex, wasCorrect;
      @ ensures previousScore == \old(session.getCurrentScore());
      @ ensures previousQuestionIndex == \old(session.getCurrentQuestionIndex());
      @ ensures session.getCurrentQuestionIndex() == \old(session.getCurrentQuestionIndex()) + 1;
      @*/
    @Override
    public boolean execute() {
        this.previousScore = session.getCurrentScore();
        this.previousQuestionIndex = session.getCurrentQuestionIndex();
        this.wasCorrect = session.applyAnswer(question, submittedChoiceIndex);
        return this.wasCorrect;
    }

    /*@ also
      @ ensures session.getCurrentQuestionIndex() == previousQuestionIndex;
      @ ensures session.getCurrentScore() == previousScore;
      @ ensures !session.isCompleted();
      @*/
    @Override
    public void undo() {
        session.restoreState(previousQuestionIndex, previousScore);
    }

    public /*@ pure @*/ boolean wasCorrect() {
        return wasCorrect;
    }
}