package ai.studyagent.session;

import ai.studyagent.domain.Quiz;
import ai.studyagent.domain.QuizQuestion;
import ai.studyagent.domain.SessionStats;
import ai.studyagent.observer.ProgressObserver;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class SessionManager {
    private StudySession activeSession;
    private final Stack<Command> commandHistory = new Stack<>();
    private final List<ProgressObserver> observers = new ArrayList<>();

    public StudySession loadSession(Quiz quiz) {
        this.activeSession = new StudySession(quiz);
        this.commandHistory.clear();
        return this.activeSession;
    }

    public boolean executeCommand(Command cmd) {
        boolean result = cmd.execute();
        commandHistory.push(cmd);

        if (activeSession != null && activeSession.isCompleted()) {
            SessionStats stats = new SessionStats(
                    activeSession.getQuiz().getSubjectId(),
                    activeSession.getCurrentScore(),
                    activeSession.getTotalQuestions()
            );
            notifyObservers(stats);
        }
        return result;
    }

    public boolean undoLastAction() {
        if (commandHistory.isEmpty()) {
            return false;
        }
        Command lastCmd = commandHistory.pop();
        lastCmd.undo();
        return true;
    }

    public StudySession getActiveSession() {
        return activeSession;
    }

    public QuizQuestion displayNextQuestion() {
        if (activeSession == null) return null;
        return activeSession.getCurrentQuestion();
    }

    public void attachObserver(ProgressObserver observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void detachObserver(ProgressObserver observer) {
        observers.remove(observer);
    }

    public void notifyObservers(SessionStats stats) {
        for (ProgressObserver observer : observers) {
            observer.update(stats);
        }
    }
}