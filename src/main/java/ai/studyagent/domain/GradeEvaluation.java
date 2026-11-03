package ai.studyagent.domain;

import java.util.List;

public class GradeEvaluation {
    private final int score;
    private final String feedback;
    private final List<String> hints;

    public GradeEvaluation(int score, String feedback, List<String> hints) {
        this.score = score;
        this.feedback = feedback;
        this.hints = hints;
    }

    public int getScore() { return score; }
    public String getFeedback() { return feedback; }
    public List<String> getHints() { return hints; }
}