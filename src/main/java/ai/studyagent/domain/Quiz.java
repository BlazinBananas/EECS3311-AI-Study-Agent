package ai.studyagent.domain;

import java.util.List;

public class Quiz implements StudyMaterial {
    private String id;
    private String subjectId;
    private String difficulty;
    private List<QuizQuestion> questions;

    public Quiz(String id, String subjectId, String difficulty, List<QuizQuestion> questions) {
        this.id = id;
        this.subjectId = subjectId;
        this.difficulty = difficulty;
        this.questions = questions;
    }

    @Override
    public String getId() { return id; }

    @Override
    public String getSubjectId() { return subjectId; }

    public String getDifficulty() { return difficulty; }

    public List<QuizQuestion> getQuestions() { return questions; }

    @Override
    public String getSummary() {
        return "Quiz (" + difficulty + ") - " + questions.size() + " questions";
    }
}