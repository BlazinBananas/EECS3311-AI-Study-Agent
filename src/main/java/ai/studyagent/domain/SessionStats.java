package ai.studyagent.domain;

import java.time.LocalDateTime;

public class SessionStats {
    private final String subjectId;
    private final int correctCount;
    private final int totalQuestions;
    private final LocalDateTime completedAt;

    public SessionStats(String subjectId, int correctCount, int totalQuestions) {
        this.subjectId = subjectId;
        this.correctCount = correctCount;
        this.totalQuestions = totalQuestions;
        this.completedAt = LocalDateTime.now();
    }

    public String getSubjectId() { return subjectId; }
    public int getCorrectCount() { return correctCount; }
    public int getTotalQuestions() { return totalQuestions; }
    public LocalDateTime getCompletedAt() { return completedAt; }

    public double getAccuracyPercentage() {
        if (totalQuestions == 0) return 0.0;
        return (correctCount * 100.0) / totalQuestions;
    }
}