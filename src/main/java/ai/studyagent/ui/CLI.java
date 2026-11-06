package ai.studyagent.ui;

import ai.studyagent.config.EngineConfig;
import ai.studyagent.controller.StudyAgentFacade;
import ai.studyagent.domain.*;
import ai.studyagent.session.StudySession;

import java.util.List;
import java.util.Scanner;

public class CLI {
    private final StudyAgentFacade facade;

    public CLI(StudyAgentFacade facade) {
        this.facade = facade;
    }

    public void start() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=========================================================");
        System.out.println("     EECS 3311 - AI Study & Learning Agent (CLI Mode)    ");
        System.out.println("=========================================================");
        boolean running = true;
        Quiz lastGeneratedQuiz = null;
        CodingProblem lastProblem = null;

        while (running) {
            System.out.println("\n[1] F01: Toggle Local/Cloud Engine   [2] F02: Ingest Local Folder");
            System.out.println("[3] F03: Toggle Web Search Tool      [4] F04: Grounded Subject Chat");
            System.out.println("[5] F05: Generate Flashcard Deck     [6] F06: Generate Adaptive Quiz");
            System.out.println("[7] F07: Synthesize Coding Problem   [8] F08: Grade Code Solution");
            System.out.println("[9] F09: Guided Session & Undo       [10] F10: View Analytics | [0] Exit");
            System.out.print("Select feature > ");
            String choice = scanner.nextLine().trim();

            try {
                switch (choice) {
                    case "1" -> {
                        System.out.print("Use Local Ollama? (true/false): ");
                        boolean isLocal = Boolean.parseBoolean(scanner.nextLine().trim());
                        boolean ok = facade.setEngineMode(isLocal, new EngineConfig());
                        System.out.println("-> [F01] Engine switched successfully: " + ok);
                    }
                    case "2" -> {
                        System.out.print("Enter Subject ID (default EECS3311): ");
                        String sub = scanner.nextLine().trim();
                        if (sub.isEmpty()) sub = "EECS3311";
                        System.out.print("Enter Folder Path (default sample-course-notes): ");
                        String path = scanner.nextLine().trim();
                        if (path.isEmpty()) path = "sample-course-notes";
                        IngestionResult res = facade.ingestFolder(sub, path);
                        System.out.println("-> [F02] Imported: " + res.getImportedFileNames()
                                + " | Chunks: " + res.getTotalChunksCreated()
                                + " | Skipped: " + res.getSkippedFiles());
                    }
                    case "3" -> {
                        System.out.print("Permit Web Search fallback (< 0.65 similarity)? (true/false): ");
                        boolean permit = Boolean.parseBoolean(scanner.nextLine().trim());
                        facade.setWebSearchPermitted(permit);
                        System.out.println("-> [F03] Web Search Permitted: " + facade.isWebSearchPermitted());
                    }
                    case "4" -> {
                        System.out.print("Subject ID (e.g., EECS3311): ");
                        String sub = scanner.nextLine().trim();
                        System.out.print("Question: ");
                        String q = scanner.nextLine().trim();
                        ChatResponse resp = facade.askQuestion(sub, q);
                        System.out.println("-> [F04 Answer]\n" + resp.getAnswerText());
                        if (resp.isUsedWebFallback()) {
                            System.out.println("-> [F03 Citations]: " + resp.getWebCitations());
                        }
                    }
                    case "5" -> {
                        System.out.print("Subject ID (default EECS3311): ");
                        String sub = scanner.nextLine().trim();
                        if (sub.isEmpty()) sub = "EECS3311";
                        List<Flashcard> cards = facade.requestFlashcards(sub, 3);
                        System.out.println("-> [F05] Generated " + cards.size() + " Flashcards:");
                        cards.forEach(c -> System.out.println("   • " + c.getSummary()));
                    }
                    case "6" -> {
                        System.out.print("Subject ID (default EECS3311): ");
                        String sub = scanner.nextLine().trim();
                        if (sub.isEmpty()) sub = "EECS3311";
                        System.out.print("Difficulty (Beginner/Intermediate/Advanced): ");
                        String diff = scanner.nextLine().trim();
                        if (diff.isEmpty()) diff = "Intermediate";
                        System.out.print("Number of questions (e.g., 2): ");
                        int count = Integer.parseInt(scanner.nextLine().trim());
                        lastGeneratedQuiz = facade.requestQuiz(sub, diff, count);
                        System.out.println("-> [F06] Generated: " + lastGeneratedQuiz.getSummary());
                    }
                    case "7" -> {
                        System.out.print("Enter CS Topic (e.g., Java Strategy Pattern): ");
                        lastProblem = facade.generateCodingProblem(scanner.nextLine().trim());
                        System.out.println("-> [F07] " + lastProblem.getProblemTitle() + "\n" + lastProblem.getDescription());
                    }
                    case "8" -> {
                        if (lastProblem == null) {
                            lastProblem = facade.generateCodingProblem("Java Strategy Pattern");
                        }
                        System.out.print("Enter your Java solution: ");
                        GradeEvaluation eval = facade.gradeSubmission(lastProblem, scanner.nextLine());
                        System.out.println("-> [F08] Score: " + eval.getScore() + "/100\n   Feedback: " + eval.getFeedback()
                                + "\n   Hints: " + eval.getHints());
                    }
                    case "9" -> {
                        if (lastGeneratedQuiz == null) {
                            facade.ingestFolder("EECS3311", "sample-course-notes");
                            lastGeneratedQuiz = facade.requestQuiz("EECS3311", "Intermediate", 2);
                        }
                        StudySession session = facade.startStudySession(lastGeneratedQuiz);
                        while (!session.isCompleted()) {
                            QuizQuestion q = session.getCurrentQuestion();
                            System.out.println("\nQ" + (session.getCurrentQuestionIndex() + 1) + ": " + q.getPrompt());
                            for (int i = 0; i < q.getOptions().size(); i++) {
                                System.out.println("  [" + i + "] " + q.getOptions().get(i));
                            }
                            System.out.print("Enter option index (0-3), 'u' to Undo last answer, or 'q' to exit mid-session: ");
                            String in = scanner.nextLine().trim();
                            if (in.equalsIgnoreCase("u")) {
                                System.out.println("-> Undo executed: " + facade.undoLastSessionAnswer());
                            } else if (in.equalsIgnoreCase("q")) {
                                System.out.println("-> [F09 Warning] Session in progress. Progress paused.");
                                break;
                            } else {
                                boolean ok = facade.submitQuizAnswer(Integer.parseInt(in));
                                System.out.println("-> Answer correct: " + ok);
                            }
                        }
                        if (session.isCompleted()) {
                            System.out.println("-> [F09] Session Complete! Final Score: "
                                    + session.getCurrentScore() + "/" + session.getTotalQuestions());
                        }
                    }
                    case "10" -> System.out.println("-> [F10]\n" + facade.viewAnalytics());
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