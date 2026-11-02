package ai.studyagent.session;

import ai.studyagent.domain.QuizQuestion;

public class SubmitAnswerCommand implements Command {
    private final StudySession session;
    private final QuizQuestion question;
    private final int submittedChoiceIndex;

    // Snapshot of pre-execution state for deterministic undo
    private int previousScore;
    private int previousQuestionIndex;
    private boolean wasCorrect;

    public SubmitAnswerCommand(StudySession session, QuizQuestion question, int submittedChoiceIndex) {
        this.session = session;
        this.question = question;
        this.submittedChoiceIndex = submittedChoiceIndex;
    }

    @Override
    public boolean execute() {
        this.previousScore = session.getCurrentScore();
        this.previousQuestionIndex = session.getCurrentQuestionIndex();
        this.wasCorrect = session.applyAnswer(question, submittedChoiceIndex);
        return this.wasCorrect;
    }

    @Override
    public void undo() {
        session.restoreState(previousQuestionIndex, previousScore);
    }

    public boolean wasCorrect() {
        return wasCorrect;
    }
}