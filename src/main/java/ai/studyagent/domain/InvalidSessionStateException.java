package ai.studyagent.domain;

public class InvalidSessionStateException extends Exception {
    public InvalidSessionStateException(String message) {
        super(message);
    }
}