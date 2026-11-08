package ai.studyagent.domain;

import java.util.ArrayList;
import java.util.List;

public class QuizQuestion {
    private String prompt;
    private List<String> options;
    private int correctAnswerIndex;

    public QuizQuestion(String prompt, List<String> options, int correctAnswerIndex) {
        this.prompt = prompt;
        this.options = new ArrayList<>(options);
        this.correctAnswerIndex = correctAnswerIndex;
    }

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }

    public List<String> getOptions() { return options; }
    public void setOptions(List<String> options) { this.options = new ArrayList<>(options); }

    public int getCorrectAnswerIndex() { return correctAnswerIndex; }
    public void setCorrectAnswerIndex(int correctAnswerIndex) { this.correctAnswerIndex = correctAnswerIndex; }

    public boolean verifyAnswer(int chosenIndex) {
        return chosenIndex == correctAnswerIndex;
    }
}