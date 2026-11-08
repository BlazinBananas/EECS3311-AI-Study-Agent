package ai.studyagent.domain;

public class Flashcard implements StudyMaterial {
    private String id;
    private String subjectId;
    private String term;
    private String definition;

    public Flashcard(String id, String subjectId, String term, String definition) {
        this.id = id;
        this.subjectId = subjectId;
        this.term = term;
        this.definition = definition;
    }

    @Override
    public String getId() { return id; }

    @Override
    public String getSubjectId() { return subjectId; }

    public String getTerm() { return term; }
    public void setTerm(String term) { this.term = term; }

    public String getDefinition() { return definition; }
    public void setDefinition(String definition) { this.definition = definition; }

    @Override
    public String getSummary() {
        return "Flashcard [" + term + ": " + definition + "]";
    }
}