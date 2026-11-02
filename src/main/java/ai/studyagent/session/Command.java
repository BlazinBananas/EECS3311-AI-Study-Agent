package ai.studyagent.session;

public interface Command {
    boolean execute();
    void undo();
}