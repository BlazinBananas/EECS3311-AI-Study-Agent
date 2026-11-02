package ai.studyagent.rag;

import ai.studyagent.domain.DocumentChunk;
import ai.studyagent.domain.IngestionResult;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DocumentManager {
    private final Map<String, List<DocumentChunk>> subjectStore = new HashMap<>();

    public IngestionResult ingestFolder(String subjectId, String directoryPath) {
        List<String> imported = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        int chunksAdded = 0;

        File dir = new File(directoryPath);
        if (!dir.exists() || !dir.isDirectory()) {
            skipped.add(directoryPath + " (Invalid directory)");
            return new IngestionResult(imported, skipped, 0);
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return new IngestionResult(imported, skipped, 0);
        }

        subjectStore.putIfAbsent(subjectId, new ArrayList<>());

        for (File file : files) {
            if (file.isDirectory()) continue;
            String name = file.getName().toLowerCase();
            try {
                String rawText;
                if (name.endsWith(".txt")) {
                    rawText = Files.readString(file.toPath());
                } else if (name.endsWith(".pdf")) {
                    try (PDDocument pdf = PDDocument.load(file)) {
                        if (pdf.isEncrypted()) {
                            throw new IllegalStateException("Encrypted PDF");
                        }
                        rawText = new PDFTextStripper().getText(pdf);
                    }
                } else {
                    skipped.add(file.getName() + " (Unsupported format)");
                    continue;
                }

                List<String> paragraphs = splitIntoChunks(rawText, 400);
                for (String para : paragraphs) {
                    subjectStore.get(subjectId).add(new DocumentChunk(file.getName(), subjectId, para, 0.0));
                    chunksAdded++;
                }
                imported.add(file.getName());
            } catch (Exception e) {
                skipped.add(file.getName() + " (" + e.getMessage() + ")");
            }
        }
        return new IngestionResult(imported, skipped, chunksAdded);
    }

    public List<DocumentChunk> retrieveRelevantChunks(String subjectId, String query, int topK) {
        List<DocumentChunk> chunks = subjectStore.getOrDefault(subjectId, new ArrayList<>());
        List<DocumentChunk> scored = new ArrayList<>();

        for (DocumentChunk chunk : chunks) {
            double score = computeKeywordOverlapSimilarity(query, chunk.getText());
            scored.add(new DocumentChunk(chunk.getSourceFileName(), subjectId, chunk.getText(), score));
        }

        return scored.stream()
                .sorted(Comparator.comparingDouble(DocumentChunk::getSimilarityScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    public boolean hasDocuments(String subjectId) {
        return subjectStore.containsKey(subjectId) && !subjectStore.get(subjectId).isEmpty();
    }

    private List<String> splitIntoChunks(String text, int maxChars) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) return result;
        String[] paragraphs = text.split("\\r?\\n\\r?\\n");
        for (String p : paragraphs) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed.length() > maxChars ? trimmed.substring(0, maxChars) : trimmed);
            }
        }
        return result;
    }

    private double computeKeywordOverlapSimilarity(String query, String chunkText) {
        if (query == null || query.isBlank() || chunkText == null || chunkText.isBlank()) return 0.0;
        String[] qTokens = query.toLowerCase().replaceAll("[^a-z0-9 ]", "").split("\\s+");
        Set<String> cTokens = new HashSet<>(List.of(chunkText.toLowerCase().replaceAll("[^a-z0-9 ]", "").split("\\s+")));
        if (qTokens.length == 0) return 0.0;

        int matches = 0;
        for (String token : qTokens) {
            if (token.length() > 2 && cTokens.contains(token)) {
                matches++;
            }
        }
        return Math.min(1.0, (double) matches / qTokens.length);
    }
}