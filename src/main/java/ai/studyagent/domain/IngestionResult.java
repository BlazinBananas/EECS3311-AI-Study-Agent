package ai.studyagent.domain;

import java.util.List;

public class IngestionResult {
    private List<String> importedFileNames;
    private List<String> skippedFiles;
    private int totalChunksCreated;

    public IngestionResult(List<String> importedFileNames, List<String> skippedFiles, int totalChunksCreated) {
        this.importedFileNames = importedFileNames;
        this.skippedFiles = skippedFiles;
        this.totalChunksCreated = totalChunksCreated;
    }

    public List<String> getImportedFileNames() { return importedFileNames; }
    public List<String> getSkippedFiles() { return skippedFiles; }
    public int getTotalChunksCreated() { return totalChunksCreated; }
}