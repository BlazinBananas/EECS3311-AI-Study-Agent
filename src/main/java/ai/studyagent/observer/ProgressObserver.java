package ai.studyagent.observer;

import ai.studyagent.domain.SessionStats;

public interface ProgressObserver {
    void update(SessionStats stats);
}