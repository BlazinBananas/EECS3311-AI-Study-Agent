package ai.studyagent.ui;

import ai.studyagent.config.EngineConfig;
import ai.studyagent.controller.StudyAgentFacade;
import ai.studyagent.domain.*;
import ai.studyagent.session.StudySession;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;

public class MainGUI extends JFrame {
    private final StudyAgentFacade facade;
    private boolean localMode = true;

    private final JTextField subjectField = new JTextField("EECS3311", 10);
    private final JTextField folderPathField = new JTextField("sample-course-notes", 18);
    private final JLabel statusBanner = new JLabel("Active Engine: Local Ollama (llama3.1:8b-instruct-q4_K_M)");

    // F04 Chat components
    private final JTextArea chatTranscript = new JTextArea(14, 55);
    private final JTextField chatInput = new JTextField(35);
    private ChatResponse lastChatResponse;

    // F05 & F06 Material components
    private final JTextArea materialDisplayArea = new JTextArea(12, 55);
    private List<Flashcard> currentDeck;
    private int currentCardIndex = 0;
    private boolean showingCardDefinition = false;
    private Quiz latestQuiz;

    // F07 & F08 Coding & Grading components
    private final JTextField codingTopicField = new JTextField("Java Observer Pattern", 20);
    private final JTextArea problemStatementArea = new JTextArea(5, 55);
    private final JTextArea codeEditorArea = new JTextArea(8, 55);
    private final JTextArea gradeFeedbackArea = new JTextArea(5, 55);
    private CodingProblem currentProblem;

    // F09 & F10 Guided Session & Analytics components
    private final JLabel sessionTimerLabel = new JLabel("Pomodoro Timer: 25:00");
    private final JLabel sessionProgressLabel = new JLabel("Session Status: Not Started");
    private final JTextArea activeQuestionArea = new JTextArea(5, 50);
    private final JComboBox<String> answerChoiceBox = new JComboBox<>(new String[]{"Option 0", "Option 1", "Option 2", "Option 3"});
    private final JTextArea analyticsArea = new JTextArea(7, 50);
    private Timer pomodoroTimer;
    private int remainingSeconds = 25 * 60;

    public MainGUI(StudyAgentFacade facade) {
        super("EECS 3311 - AI Study and Learning Agent");
        this.facade = facade;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        add(buildTopControlBar(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("F03/F04: Grounded Chat & Web Search", buildChatTab());
        tabs.addTab("F05/F06: Flashcards & Adaptive Quizzes", buildMaterialTab());
        tabs.addTab("F07/F08: Coding Synthesis & AI Grading", buildCodingTab());
        tabs.addTab("F09/F10: Guided Session & Analytics", buildSessionAndAnalyticsTab());
        add(tabs, BorderLayout.CENTER);

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottomBar.add(statusBanner);
        add(bottomBar, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
    }

    private JPanel buildTopControlBar() {
        JPanel topContainer = new JPanel(new GridLayout(2, 1, 4, 4));
        topContainer.setBorder(BorderFactory.createTitledBorder("Global Agent Controls (F01, F02, F03)"));

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row1.add(new JLabel("Subject ID:"));
        row1.add(subjectField);

        JButton toggleEngineBtn = new JButton("F01: Toggle Local/Cloud (Current: Local Ollama)");
        toggleEngineBtn.addActionListener(e -> {
            localMode = !localMode;
            facade.setEngineMode(localMode, new EngineConfig());
            String label = localMode ? "Local Ollama (llama3.1:8b)" : "Cloud OpenAI (gpt-4o-mini)";
            toggleEngineBtn.setText("F01: Toggle Local/Cloud (Current: " + (localMode ? "Local" : "Cloud") + ")");
            statusBanner.setText("Active Engine: " + label);
            chatTranscript.append("[F01 Engine Switched] Now using " + label + "\n");
        });
        row1.add(toggleEngineBtn);

        JCheckBox webSearchCheck = new JCheckBox("F03: Permit Web Search Fallback (< 0.65 similarity)", true);
        webSearchCheck.addActionListener(e -> {
            facade.setWebSearchPermitted(webSearchCheck.isSelected());
            chatTranscript.append("[F03 Config] Permit Web Search set to: " + webSearchCheck.isSelected() + "\n");
        });
        row1.add(webSearchCheck);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row2.add(new JLabel("Course Folder:"));
        row2.add(folderPathField);

        JButton browseBtn = new JButton("Browse...");
        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(new File("."));
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                folderPathField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        row2.add(browseBtn);

        JButton ingestBtn = new JButton("F02: Import Materials");
        ingestBtn.addActionListener(e -> {
            String sub = subjectField.getText().trim();
            String path = folderPathField.getText().trim();
            IngestionResult res = facade.ingestFolder(sub, path);
            chatTranscript.append(String.format("[F02 Ingestion] Subject: %s | Imported: %s | Skipped: %s | Chunks: %d\n",
                    sub, res.getImportedFileNames(), res.getSkippedFiles(), res.getTotalChunksCreated()));
            JOptionPane.showMessageDialog(this,
                    "Imported Files: " + res.getImportedFileNames() + "\nSkipped Files: " + res.getSkippedFiles()
                            + "\nTotal Chunks Indexed: " + res.getTotalChunksCreated(),
                    "F02 Ingestion Complete", JOptionPane.INFORMATION_MESSAGE);
        });
        row2.add(ingestBtn);

        topContainer.add(row1);
        topContainer.add(row2);
        return topContainer;
    }

    private JPanel buildChatTab() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        chatTranscript.setEditable(false);
        chatTranscript.setLineWrap(true);
        chatTranscript.setWrapStyleWord(true);
        chatTranscript.setText("Welcome! Click 'F02: Import Materials' above to ingest 'sample-course-notes', then ask a question below.\n");
        panel.add(new JScrollPane(chatTranscript), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottom.add(new JLabel("Question:"));
        bottom.add(chatInput);

        JButton askBtn = new JButton("F04: Ask Grounded Question");
        askBtn.addActionListener(e -> {
            try {
                String sub = subjectField.getText().trim();
                String q = chatInput.getText().trim();
                lastChatResponse = facade.askQuestion(sub, q);
                chatTranscript.append("\nYou (" + sub + "): " + q + "\n");
                chatTranscript.append("Agent: " + lastChatResponse.getAnswerText() + "\n");
                if (lastChatResponse.isUsedWebFallback()) {
                    chatTranscript.append("  -> [F03 Web Citations]: " + lastChatResponse.getWebCitations() + "\n");
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "F04 Chat Warning", JOptionPane.WARNING_MESSAGE);
            }
        });
        bottom.add(askBtn);

        JButton viewSourcesBtn = new JButton("View Source Material");
        viewSourcesBtn.addActionListener(e -> {
            if (lastChatResponse == null) {
                JOptionPane.showMessageDialog(this, "Ask a question first to inspect retrieved chunks.");
                return;
            }
            StringBuilder sb = new StringBuilder();
            if (lastChatResponse.isUsedWebFallback()) {
                sb.append("Used F03 Web Search Fallback (Local similarity < 0.65):\n");
                for (WebSnippet w : lastChatResponse.getWebCitations()) {
                    sb.append(" - ").append(w.toString()).append("\n");
                }
            } else {
                sb.append("Top Local Document Chunks Used:\n");
                for (DocumentChunk c : lastChatResponse.getSourceChunks()) {
                    sb.append(String.format(" [Score: %.2f | File: %s] %s\n\n",
                            c.getSimilarityScore(), c.getSourceFileName(), c.getText()));
                }
            }
            JOptionPane.showMessageDialog(this, new JScrollPane(new JTextArea(sb.toString(), 12, 50)),
                    "F04 Source Material Inspector", JOptionPane.INFORMATION_MESSAGE);
        });
        bottom.add(viewSourcesBtn);

        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildMaterialTab() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JSpinner countSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 10, 1));
        JButton genFlashcardsBtn = new JButton("F05: Generate Flashcards");
        JButton flipCardBtn = new JButton("Flip Current Flashcard");
        JButton nextCardBtn = new JButton("Next Flashcard");

        String[] difficulties = {"Beginner", "Intermediate", "Advanced"};
        JComboBox<String> diffBox = new JComboBox<>(difficulties);
        diffBox.setSelectedIndex(1);
        JButton genQuizBtn = new JButton("F06: Generate Adaptive Quiz");

        genFlashcardsBtn.addActionListener(e -> {
            try {
                int count = (Integer) countSpinner.getValue();
                currentDeck = facade.requestFlashcards(subjectField.getText().trim(), count);
                currentCardIndex = 0;
                showingCardDefinition = false;
                renderCurrentFlashcard();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "F05 Alert", JOptionPane.WARNING_MESSAGE);
            }
        });

        flipCardBtn.addActionListener(e -> {
            if (currentDeck != null && !currentDeck.isEmpty()) {
                showingCardDefinition = !showingCardDefinition;
                renderCurrentFlashcard();
            }
        });

        nextCardBtn.addActionListener(e -> {
            if (currentDeck != null && !currentDeck.isEmpty()) {
                currentCardIndex = (currentCardIndex + 1) % currentDeck.size();
                showingCardDefinition = false;
                renderCurrentFlashcard();
            }
        });

        genQuizBtn.addActionListener(e -> {
            try {
                int count = (Integer) countSpinner.getValue();
                String diff = (String) diffBox.getSelectedItem();
                latestQuiz = facade.requestQuiz(subjectField.getText().trim(), diff, count);
                StringBuilder sb = new StringBuilder("=== Generated " + latestQuiz.getSummary() + " ===\n\n");
                int idx = 1;
                for (QuizQuestion q : latestQuiz.getQuestions()) {
                    sb.append("Q").append(idx++).append(": ").append(q.getPrompt()).append("\n");
                    List<String> opts = q.getOptions();
                    for (int i = 0; i < opts.size(); i++) {
                        sb.append("   [").append(i).append("] ").append(opts.get(i)).append("\n");
                    }
                    sb.append("\n");
                }
                sb.append("-> Switch to Tab 'F09/F10' to execute this quiz in a Guided Study Session!");
                materialDisplayArea.setText(sb.toString());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "F06 Alert", JOptionPane.WARNING_MESSAGE);
            }
        });

        controls.add(new JLabel("Count:"));
        controls.add(countSpinner);
        controls.add(genFlashcardsBtn);
        controls.add(flipCardBtn);
        controls.add(nextCardBtn);
        controls.add(new JLabel("Difficulty:"));
        controls.add(diffBox);
        controls.add(genQuizBtn);

        materialDisplayArea.setEditable(false);
        materialDisplayArea.setLineWrap(true);
        materialDisplayArea.setWrapStyleWord(true);
        panel.add(controls, BorderLayout.NORTH);
        panel.add(new JScrollPane(materialDisplayArea), BorderLayout.CENTER);
        return panel;
    }

    private void renderCurrentFlashcard() {
        if (currentDeck == null || currentDeck.isEmpty()) return;
        Flashcard c = currentDeck.get(currentCardIndex);
        materialDisplayArea.setText(String.format("=== F05 Flashcard Deck (%d of %d) ===\n\nTERM:\n%s\n\n%s",
                currentCardIndex + 1, currentDeck.size(), c.getTerm(),
                showingCardDefinition ? "DEFINITION:\n" + c.getDefinition() : "[Click 'Flip Current Flashcard' to reveal definition]"));
    }

    private JPanel buildCodingTab() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("CS Topic:"));
        top.add(codingTopicField);

        JButton genProblemBtn = new JButton("F07: Generate Practice Problem");
        JButton submitCodeBtn = new JButton("F08: Submit Answer for Grading");
        top.add(genProblemBtn);
        top.add(submitCodeBtn);

        genProblemBtn.addActionListener(e -> {
            try {
                currentProblem = facade.generateCodingProblem(codingTopicField.getText().trim());
                problemStatementArea.setText(currentProblem.getProblemTitle() + "\n"
                        + currentProblem.getDescription() + "\nConstraints: " + currentProblem.getConstraints());
                codeEditorArea.setText(currentProblem.getStarterCode());
                gradeFeedbackArea.setText("Type your solution above and click 'F08: Submit Answer for Grading'.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "F07 Error", JOptionPane.WARNING_MESSAGE);
            }
        });

        submitCodeBtn.addActionListener(e -> {
            try {
                if (currentProblem == null) {
                    currentProblem = facade.generateCodingProblem(codingTopicField.getText().trim());
                }
                GradeEvaluation eval = facade.gradeSubmission(currentProblem, codeEditorArea.getText());
                gradeFeedbackArea.setText("Score: " + eval.getScore() + "/100\nFeedback: " + eval.getFeedback()
                        + "\nHints: " + eval.getHints());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "F08 Validation Guard", JOptionPane.WARNING_MESSAGE);
            }
        });

        problemStatementArea.setEditable(false);
        problemStatementArea.setBorder(BorderFactory.createTitledBorder("F07: Synthesized Coding Problem"));
        codeEditorArea.setBorder(BorderFactory.createTitledBorder("Student Solution Editor"));
        gradeFeedbackArea.setEditable(false);
        gradeFeedbackArea.setBorder(BorderFactory.createTitledBorder("F08: Intelligent Grading & Qualitative Hints"));

        JPanel center = new JPanel(new GridLayout(3, 1, 5, 5));
        center.add(new JScrollPane(problemStatementArea));
        center.add(new JScrollPane(codeEditorArea));
        center.add(new JScrollPane(gradeFeedbackArea));

        panel.add(top, BorderLayout.NORTH);
        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildSessionAndAnalyticsTab() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton startSessionBtn = new JButton("F09: Start Study Session");
        JButton submitAnsBtn = new JButton("Submit Answer (Execute Command)");
        JButton undoBtn = new JButton("Undo Last Answer (Command.undo)");
        JButton refreshAnalyticsBtn = new JButton("F10: Refresh Analytics");

        startSessionBtn.addActionListener(e -> {
            try {
                if (latestQuiz == null) {
                    if (!facade.ingestFolder(subjectField.getText().trim(), folderPathField.getText().trim()).getImportedFileNames().isEmpty()
                            || latestQuiz == null) {
                        facade.ingestFolder(subjectField.getText().trim(), folderPathField.getText().trim());
                    }
                    latestQuiz = facade.requestQuiz(subjectField.getText().trim(), "Intermediate", 2);
                }
                facade.startStudySession(latestQuiz);
                startPomodoroTimer();
                refreshSessionView();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "F09 Session Error", JOptionPane.WARNING_MESSAGE);
            }
        });

        submitAnsBtn.addActionListener(e -> {
            StudySession s = facade.getActiveStudySession();
            if (s == null || s.isCompleted()) {
                JOptionPane.showMessageDialog(this, "Start an active study session first.");
                return;
            }
            int choice = answerChoiceBox.getSelectedIndex();
            boolean correct = facade.submitQuizAnswer(choice);
            refreshSessionView();
            analyticsArea.setText(facade.viewAnalytics());
            JOptionPane.showMessageDialog(this,
                    "Submitted Option " + choice + " -> " + (correct ? "CORRECT!" : "INCORRECT"),
                    "F09 Command Executed", JOptionPane.INFORMATION_MESSAGE);
        });

        undoBtn.addActionListener(e -> {
            boolean undone = facade.undoLastSessionAnswer();
            if (undone) {
                refreshSessionView();
                JOptionPane.showMessageDialog(this,
                        "Reverted to previous question index and previous score via SubmitAnswerCommand.undo()!",
                        "F09 Command Undone", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Command history stack is empty — nothing to undo.");
            }
        });

        refreshAnalyticsBtn.addActionListener(e -> analyticsArea.setText(facade.viewAnalytics()));

        top.add(startSessionBtn);
        top.add(answerChoiceBox);
        top.add(submitAnsBtn);
        top.add(undoBtn);
        top.add(refreshAnalyticsBtn);
        top.add(sessionTimerLabel);

        activeQuestionArea.setEditable(false);
        activeQuestionArea.setBorder(BorderFactory.createTitledBorder("F09: Guided Study Session Question Queue"));
        analyticsArea.setEditable(false);
        analyticsArea.setBorder(BorderFactory.createTitledBorder("F10: Real-Time Progress Analytics (Observer Pattern)"));
        analyticsArea.setText(facade.viewAnalytics());

        JPanel center = new JPanel(new GridLayout(2, 1, 6, 6));
        center.add(new JScrollPane(activeQuestionArea));
        center.add(new JScrollPane(analyticsArea));

        panel.add(top, BorderLayout.NORTH);
        panel.add(center, BorderLayout.CENTER);
        panel.add(sessionProgressLabel, BorderLayout.SOUTH);
        return panel;
    }

    private void startPomodoroTimer() {
        remainingSeconds = 25 * 60;
        if (pomodoroTimer != null) {
            pomodoroTimer.stop();
        }
        pomodoroTimer = new Timer(1000, e -> {
            if (remainingSeconds > 0) {
                remainingSeconds--;
                sessionTimerLabel.setText(String.format("Pomodoro Timer: %02d:%02d", remainingSeconds / 60, remainingSeconds % 60));
            }
        });
        pomodoroTimer.start();
    }

    private void refreshSessionView() {
        StudySession s = facade.getActiveStudySession();
        if (s == null) {
            activeQuestionArea.setText("No active session.");
            return;
        }
        sessionProgressLabel.setText(String.format("Session Status: QuestionIndex=%d/%d | Score=%d | Completed=%s",
                s.getCurrentQuestionIndex(), s.getTotalQuestions(), s.getCurrentScore(), s.isCompleted()));

        if (s.isCompleted()) {
            activeQuestionArea.setText(String.format("=== SESSION COMPLETED! ===\nFinal Score: %d / %d\nAnalytics Dashboard (F10) has been notified via Observer pattern.\n(You can still click 'Undo Last Answer' to test Command rollback!)",
                    s.getCurrentScore(), s.getTotalQuestions()));
            return;
        }

        QuizQuestion q = s.getCurrentQuestion();
        if (q != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("Question ").append(s.getCurrentQuestionIndex() + 1).append(" of ").append(s.getTotalQuestions()).append(":\n");
            sb.append(q.getPrompt()).append("\n\n");
            List<String> opts = q.getOptions();
            for (int i = 0; i < opts.size(); i++) {
                sb.append("  Option ").append(i).append(": ").append(opts.get(i)).append("\n");
            }
            activeQuestionArea.setText(sb.toString());
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainGUI(new StudyAgentFacade()).setVisible(true));
    }
}