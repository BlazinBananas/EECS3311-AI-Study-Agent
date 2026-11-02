package ai.studyagent.domain;

public class DocumentChunk {
    private String sourceFileName;
    private String subjectId;
    private String text;
    private double similarityScore;

    public DocumentChunk(String sourceFileName, String subjectId, String text, double similarityScore) {
        this.sourceFileName = sourceFileName;
        this.subjectId = subjectId;
        this.text = text;
        this.similarityScore = similarityScore;
    }

    public String getSourceFileName() { return sourceFileName; }
    public String getSubjectId() { return subjectId; }
    public String getText() { return text; }
    public double getSimilarityScore() { return similarityScore; }

    public void setSimilarityScore(double similarityScore) {
        this.similarityScore = similarityScore;
    }
}