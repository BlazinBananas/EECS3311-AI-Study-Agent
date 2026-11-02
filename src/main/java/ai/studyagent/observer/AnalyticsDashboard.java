package ai.studyagent.observer;

import ai.studyagent.domain.SessionStats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnalyticsDashboard implements ProgressObserver {
    private final Map<String, List<SessionStats>> subjectHistory = new HashMap<>();

    @Override
    public void update(SessionStats stats) {
        subjectHistory.putIfAbsent(stats.getSubjectId(), new ArrayList<>());
        subjectHistory.get(stats.getSubjectId()).add(stats);
        renderCharts();
    }

    public String renderCharts() {
        if (subjectHistory.isEmpty()) {
            return "No data available yet. Start a study session!";
        }
        StringBuilder sb = new StringBuilder("=== Progress Analytics Dashboard ===\n");
        for (Map.Entry<String, List<SessionStats>> entry : subjectHistory.entrySet()) {
            String subject = entry.getKey();
            List<SessionStats> sessions = entry.getValue();
            int totalCorrect = 0;
            int totalQuestions = 0;
            for (SessionStats s : sessions) {
                totalCorrect += s.getCorrectCount();
                totalQuestions += s.getTotalQuestions();
            }
            double mastery = totalQuestions == 0 ? 0.0 : (totalCorrect * 100.0) / totalQuestions;
            sb.append(String.format("Subject: %s | Sessions: %d | Mastery: %.1f%% (%d/%d)\n",
                    subject, sessions.size(), mastery, totalCorrect, totalQuestions));
        }
        return sb.toString();
    }

    public Map<String, List<SessionStats>> getSubjectHistory() {
        return subjectHistory;
    }
}