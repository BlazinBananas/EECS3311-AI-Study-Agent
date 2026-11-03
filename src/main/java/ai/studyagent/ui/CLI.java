package ai.studyagent.ui;

import ai.studyagent.config.EngineConfig;
import ai.studyagent.controller.StudyAgentFacade;
import ai.studyagent.domain.*;

import java.util.List;
import java.util.Scanner;

public class CLI {
    private final StudyAgentFacade facade;

    public CLI(StudyAgentFacade facade) {
        this.facade = facade;
    }

    public void start() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== AI Study & Learning Agent (CLI Mode) ===");
        boolean running = true;
        Quiz lastGeneratedQuiz = null;
        CodingProblem lastProblem = null;

        while (running) {
            System.out.println("\n1. Toggle Engine (F01) | 2. Ingest Folder (F02) | 3. Ask Question / Web Search (F03/F04)");
            System.out.println("4. Flashcards (F05)    | 5. Adaptive Quiz (F06) | 6. Coding Problem (F07)");
            System.out.println("7. Grade Code (F08)    | 8. Study Session (F09) | 9. Analytics (F10) | 0. Exit");
            System.out.print("Select option: ");
            String choice = scanner.nextLine().trim();

            try {
                switch (choice) {
                    case "1" -> {
                        System.out.print("Use Local Ollama? (true/false): ");
                        boolean isLocal = Boolean.parseBoolean(scanner.nextLine().trim());
                        boolean ok = facade.setEngineMode(isLocal, new EngineConfig());
                        System.out.println("Engine updated: " + ok);
                    }
                    case "2" -> {
                        System.out.print("Subject ID (e.g., EECS3311): ");
                        String sub = scanner.nextLine().trim();
                        System.out.print("Folder path: ");
                        String path = scanner.nextLine().trim();
                        IngestionResult res = facade.ingestFolder(sub, path);
                        System.out.println("Imported: " + res.getImportedFileNames() + " | Chunks: " + res.getTotalChunksCreated());
                    }
                    case "3" -> {
                        System.out.print("Subject ID: ");
                        String sub = scanner.nextLine().trim();
                        System.out.print("Question: ");
                        String q = scanner.nextLine().trim();
                        ChatResponse resp = facade.askQuestion(sub, q);
                        System.out.println("Answer: " + resp.getAnswerText());
                    }
                    case "4" -> {
                        List<Flashcard> cards = facade.requestFlashcards("EECS3311", 3);
                        cards.forEach(c -> System.out.println(c.getSummary()));
                    }
                    case "5" -> {
                        lastGeneratedQuiz = facade.requestQuiz("EECS3311", "Intermediate", 1);
                        System.out.println("Generated: " + lastGeneratedQuiz.getSummary());
                    }
                    case "6" -> {
                        System.out.print("Enter CS Topic: ");
                        lastProblem = facade.generateCodingProblem(scanner.nextLine().trim());
                        System.out.println(lastProblem.getProblemTitle() + "\n" + lastProblem.getDescription());
                    }
                    case "7" -> {
                        if (lastProblem == null) {
                            lastProblem = facade.generateCodingProblem("Java OOP");
                        }
                        System.out.print("Paste solution: ");
                        GradeEvaluation eval = facade.gradeSubmission(lastProblem, scanner.nextLine());
                        System.out.println("Score: " + eval.getScore() + " | Feedback: " + eval.getFeedback());
                    }
                    case "8" -> {
                        if (lastGeneratedQuiz == null) {
                            lastGeneratedQuiz = facade.requestQuiz("EECS3311", "Intermediate", 1);
                        }
                        facade.startStudySession(lastGeneratedQuiz);
                        System.out.println("Answering Question 1 with option 0...");
                        boolean correct = facade.submitQuizAnswer(0);
                        System.out.println("Correct: " + correct);
                    }
                    case "9" -> System.out.println(facade.viewAnalytics());
                    case "0" -> running = false;
                }
            } catch (Exception e) {
                System.out.println("[Alert] " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        new CLI(new StudyAgentFacade()).start();
    }
}