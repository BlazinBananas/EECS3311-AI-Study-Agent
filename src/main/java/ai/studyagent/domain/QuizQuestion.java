package ai.studyagent.domain;

import java.util.List;

public class QuizQuestion {
    private String prompt;
    private List<String> options;
    private int correctAnswerIndex;

    public QuizQuestion(String prompt, List<String> options, int correctAnswerIndex) {
        this.prompt = prompt;
        this.options = options;
        this.correctAnswerIndex = correctAnswerIndex;
    }

    public String getPrompt() { return prompt; }
    public List<String> getOptions() { return options; }
    public int getCorrectAnswerIndex() { return correctAnswerIndex; }

    public boolean verifyAnswer(int chosenIndex) {
        return chosenIndex == correctAnswerIndex;
    }
}