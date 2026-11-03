package ai.studyagent.domain;

public class CodingProblem {
    private final String problemTitle;
    private final String description;
    private final String starterCode;
    private final String constraints;

    public CodingProblem(String problemTitle, String description, String starterCode, String constraints) {
        this.problemTitle = problemTitle;
        this.description = description;
        this.starterCode = starterCode;
        this.constraints = constraints;
    }

    public String getProblemTitle() { return problemTitle; }
    public String getDescription() { return description; }
    public String getStarterCode() { return starterCode; }
    public String getConstraints() { return constraints; }
}