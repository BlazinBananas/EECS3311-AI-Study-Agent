package ai.studyagent.ui;

import ai.studyagent.config.EngineConfig;
import ai.studyagent.controller.StudyAgentFacade;
import ai.studyagent.domain.*;
import ai.studyagent.session.StudySession;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainGUI extends JFrame {
    private final StudyAgentFacade facade;
    private boolean localMode = true;

    // Global Top Bar Controls (F01, F02, F03)
    private final JComboBox<String> subjectCombo = new JComboBox<>(new String[]{"EECS3311", "MATH1090 (Empty Subject)"});
    private final JTextField folderPathField = new JTextField("sample-course-notes", 16);
    private final JLabel importedDocsLabel = new JLabel("Course Material: None imported yet");
    private final JLabel statusBanner = new JLabel(" Active Engine: Local Ollama (llama3.1:8b-instruct-q4_K_M) | Ready");

    // F03 & F04 Chat Tab
    private final JTextArea chatTranscript = new JTextArea(14, 55);
    private final JTextField chatInput = new JTextField("What is the Liskov Substitution Principle?", 32);
    private ChatResponse lastChatResponse;

    // F05 & F06 Study Material Tab
    private final JSpinner countSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 15, 1));
    private final JComboBox<String> difficultyBox = new JComboBox<>(new String[]{"Beginner", "Intermediate", "Advanced"});
    private final JLabel flashcardCounterLabel = new JLabel("No deck loaded", SwingConstants.CENTER);
    private final JTextArea flashcardCardView = new JTextArea(6, 40);
    private final JTextArea quizPreviewArea = new JTextArea(8, 50);
    private List<Flashcard> currentDeck = new ArrayList<>();
    private int currentCardIndex = 0;
    private boolean showingCardDefinition = false;
    private Quiz latestQuiz;

    // F07 & F08 Coding Synthesis & Grading Tab
    private final JTextField codingTopicField = new JTextField("Java Observer Pattern", 22);
    private final JTextArea problemStatementArea = new JTextArea(5, 55);
    private final JTextArea codeEditorArea = new JTextArea(8, 55);
    private final JTextArea gradeFeedbackArea = new JTextArea(5, 55);
    private CodingProblem currentProblem;

    // F09 & F10 Guided Session & Analytics Tab
    private final JLabel sessionTimerLabel = new JLabel("Pomodoro: 25:00");
    private final JLabel sessionProgressLabel = new JLabel("Session Status: Idle");
    private final JTextArea activeQuestionArea = new JTextArea(4, 50);
    private final JRadioButton[] optionButtons = new JRadioButton[4];
    private final ButtonGroup optionGroup = new ButtonGroup();
    private final JTextArea analyticsTextArea = new JTextArea(6, 45);
    private final MasteryChartPanel chartPanel = new MasteryChartPanel();
    private Timer pomodoroTimer;
    private int remainingSeconds = 25 * 60;

    public MainGUI(StudyAgentFacade facade) {
        super("EECS 3311 — AI Study and Learning Agent");
        this.facade = facade;
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleApplicationClose();
            }
        });

        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(8, 10, 8, 10));

        add(buildHeaderAndGlobalBar(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("SansSerif", Font.BOLD, 13));
        tabs.addTab("1. Chat & Web Search (F03 / F04)", buildChatTab());
        tabs.addTab("2. Flashcards & Quizzes (F05 / F06)", buildMaterialTab());
        tabs.addTab("3. Coding & AI Grader (F07 / F08)", buildCodingTab());
        tabs.addTab("4. Study Session & Analytics (F09 / F10)", buildSessionAndAnalyticsTab());
        add(tabs, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY));
        statusBanner.setFont(new Font("SansSerif", Font.PLAIN, 12));
        footer.add(statusBanner, BorderLayout.WEST);
        footer.add(importedDocsLabel, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);

        setPreferredSize(new Dimension(980, 700));
        pack();
        setLocationRelativeTo(null);
    }

    private void handleApplicationClose() {
        StudySession active = facade.getActiveStudySession();
        // F09 Alternative Flow: warn user if closing mid-session
        if (active != null && !active.isCompleted() && active.getCurrentQuestionIndex() < active.getTotalQuestions()) {
            if (pomodoroTimer != null) pomodoroTimer.stop();
            int choice = JOptionPane.showConfirmDialog(this,
                    "Session in progress. Save progress before exiting?",
                    "F09 Guided Session Warning",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (choice == JOptionPane.CANCEL_OPTION) {
                if (pomodoroTimer != null) pomodoroTimer.start();
                return;
            }
        }
        dispose();
        System.exit(0);
    }

    private JPanel buildHeaderAndGlobalBar() {
        JPanel container = new JPanel(new GridLayout(2, 1, 4, 4));
        container.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Global Agent Configuration & Local RAG Ingestion (F01, F02, F03)",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("SansSerif", Font.BOLD, 12)));

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        row1.add(new JLabel("Active Subject:"));
        subjectCombo.setEditable(true);
        row1.add(subjectCombo);

        JButton toggleEngineBtn = new JButton("F01: Switch to Cloud OpenAI (gpt-4o-mini)");
        toggleEngineBtn.addActionListener(e -> {
            boolean targetLocal = !localMode;
            boolean ok = facade.setEngineMode(targetLocal, new EngineConfig());
            if (ok) {
                localMode = targetLocal;
                String currentEngine = localMode
                        ? "Local Ollama (llama3.1:8b-instruct-q4_K_M)"
                        : "Cloud OpenAI (gpt-4o-mini)";
                toggleEngineBtn.setText(localMode
                        ? "F01: Switch to Cloud OpenAI (gpt-4o-mini)"
                        : "F01: Switch to Local Ollama (llama3.1:8b)");
                statusBanner.setText(" Active Engine: " + currentEngine);
                chatTranscript.append("[F01 Engine Toggle] Active LLMStrategy swapped to: " + currentEngine + "\n");
            }
        });
        row1.add(toggleEngineBtn);

        JButton testBadEndpointBtn = new JButton("F01 Error Demo");
        testBadEndpointBtn.setToolTipText("Demonstrates F01 Alternative Flow: invalid endpoint reverts cleanly");
        testBadEndpointBtn.addActionListener(e -> {
            EngineConfig badConfig = new EngineConfig("invalid_url", "llama3.1:8b", "INVALID_KEY", "gpt-4o-mini", true);
            boolean ok = facade.setEngineMode(true, badConfig);
            if (!ok) {
                JOptionPane.showMessageDialog(this,
                        "Connection to endpoint 'invalid_url' failed.\nSystem caught exception and reverted to previous active LLMStrategy.",
                        "F01 Engine Error & Revert", JOptionPane.ERROR_MESSAGE);
            }
        });
        row1.add(testBadEndpointBtn);

        JCheckBox webSearchCheck = new JCheckBox("F03: Permit Web Search (< 0.65 similarity)", true);
        webSearchCheck.addActionListener(e -> {
            facade.setWebSearchPermitted(webSearchCheck.isSelected());
            chatTranscript.append("[F03 Tool Contract] Permit Web Search set to: " + webSearchCheck.isSelected() + "\n");
        });
        row1.add(webSearchCheck);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        row2.add(new JLabel("Directory Path:"));
        row2.add(folderPathField);

        JButton browseBtn = new JButton("Browse...");
        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(new File("."));
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                folderPathField.setText(chooser.getSelectedFile().getPath());
            }
        });
        row2.add(browseBtn);

        JButton ingestBtn = new JButton("F02: Import Materials");
        ingestBtn.setBackground(new Color(40, 116, 166));
        ingestBtn.setForeground(Color.WHITE);
        ingestBtn.addActionListener(e -> {
            String sub = getSelectedSubjectId();
            String path = folderPathField.getText().trim();
            IngestionResult res = facade.ingestFolder(sub, path);
            importedDocsLabel.setText("Course Material (" + sub + "): " + res.getImportedFileNames() + " ");
            chatTranscript.append(String.format(
                    "\n[F02 Local Ingestion] Subject: %s\n  ✓ Imported Titles: %s (%d chunks)\n  ⚠ Skipped Files: %s\n",
                    sub, res.getImportedFileNames(), res.getTotalChunksCreated(), res.getSkippedFiles()));
            JOptionPane.showMessageDialog(this,
                    "Subject: " + sub
                            + "\nSuccessfully Imported Titles: " + res.getImportedFileNames()
                            + "\nTotal Vector Chunks Indexed: " + res.getTotalChunksCreated()
                            + "\nSkipped / Unsupported Files: " + res.getSkippedFiles(),
                    "F02 Document Ingestion Report", JOptionPane.INFORMATION_MESSAGE);
        });
        row2.add(ingestBtn);

        container.add(row1);
        container.add(row2);
        return container;
    }

    private String getSelectedSubjectId() {
        Object item = subjectCombo.getSelectedItem();
        if (item == null) return "EECS3311";
        String raw = item.toString().trim();
        return raw.contains(" ") ? raw.substring(0, raw.indexOf(" ")) : raw;
    }

    private JPanel buildChatTab() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        chatTranscript.setEditable(false);
        chatTranscript.setLineWrap(true);
        chatTranscript.setWrapStyleWord(true);
        chatTranscript.setFont(new Font("Monospaced", Font.PLAIN, 13));
        chatTranscript.setText("=== Subject-Grounded RAG Chat (F04) & Web Search Tool (F03) ===\n"
                + "1. Click 'F02: Import Materials' above to ingest 'sample-course-notes' into EECS3311.\n"
                + "2. Ask an EECS3311 question (e.g., 'What is the Liskov Substitution Principle?') for local RAG grounding.\n"
                + "3. Ask an out-of-notes question (e.g., 'Who invented the World Wide Web?') to trigger F03 Web Search.\n"
                + "4. Switch Subject to 'MATH1090 (Empty Subject)' and click Ask to demonstrate the F04 empty-folder guard.\n\n");
        panel.add(new JScrollPane(chatTranscript), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bottom.add(new JLabel("Ask Agent:"));
        bottom.add(chatInput);

        JButton askBtn = new JButton("F04: Send Question");
        askBtn.addActionListener(e -> {
            try {
                String sub = getSelectedSubjectId();
                String q = chatInput.getText().trim();
                lastChatResponse = facade.askQuestion(sub, q);
                chatTranscript.append("\n------------------------------------------------------------\n");
                chatTranscript.append("Student [" + sub + "]: " + q + "\n");
                chatTranscript.append(lastChatResponse.getAnswerText() + "\n");
                if (lastChatResponse.isUsedWebFallback()) {
                    chatTranscript.append("🌐 [F03 Web Citations Used]: " + lastChatResponse.getWebCitations() + "\n");
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "F04 Subject Folder Warning", JOptionPane.WARNING_MESSAGE);
            }
        });
        bottom.add(askBtn);

        JButton viewSourceBtn = new JButton("View Source Material");
        viewSourceBtn.addActionListener(e -> {
            if (lastChatResponse == null) {
                JOptionPane.showMessageDialog(this, "Ask a question first to view source paragraphs.");
                return;
            }
            StringBuilder sb = new StringBuilder();
            if (lastChatResponse.isUsedWebFallback()) {
                sb.append("=== F03 Live Web Search Snippets Used (Local Similarity < 0.65) ===\n\n");
                for (WebSnippet s : lastChatResponse.getWebCitations()) {
                    sb.append("• Title: ").append(s.getTitle()).append("\n  URL: ").append(s.getUrl())
                            .append("\n  Snippet: ").append(s.getSummaryText()).append("\n\n");
                }
            } else {
                sb.append("=== F04 Local Course Paragraphs Retrieved ===\n\n");
                for (DocumentChunk c : lastChatResponse.getSourceChunks()) {
                    sb.append(String.format("File: %s | Cosine/Keyword Similarity: %.2f\nParagraph: %s\n\n",
                            c.getSourceFileName(), c.getSimilarityScore(), c.getText()));
                }
            }
            JTextArea popupArea = new JTextArea(sb.toString(), 14, 55);
            popupArea.setEditable(false);
            popupArea.setLineWrap(true);
            popupArea.setWrapStyleWord(true);
            JOptionPane.showMessageDialog(this, new JScrollPane(popupArea), "F04 Source Material Verification", JOptionPane.INFORMATION_MESSAGE);
        });
        bottom.add(viewSourceBtn);

        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildMaterialTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        JPanel topControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        topControls.add(new JLabel("Item Count:"));
        topControls.add(countSpinner);

        JButton genFlashcardsBtn = new JButton("F05: Generate Flashcard Deck");
        JButton flipBtn = new JButton("Flip Card (Term ↔ Definition)");
        JButton nextCardBtn = new JButton("Next Card ➡");
        difficultyBox.setSelectedIndex(1);
        JButton genQuizBtn = new JButton("F06: Generate Adaptive Quiz");

        genFlashcardsBtn.addActionListener(e -> {
            try {
                int count = (Integer) countSpinner.getValue();
                currentDeck = facade.requestFlashcards(getSelectedSubjectId(), count);
                currentCardIndex = 0;
                showingCardDefinition = false;
                updateFlashcardView();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "F05 Generation Warning", JOptionPane.WARNING_MESSAGE);
            }
        });

        flipBtn.addActionListener(e -> {
            if (!currentDeck.isEmpty()) {
                showingCardDefinition = !showingCardDefinition;
                updateFlashcardView();
            }
        });

        nextCardBtn.addActionListener(e -> {
            if (!currentDeck.isEmpty()) {
                currentCardIndex = (currentCardIndex + 1) % currentDeck.size();
                showingCardDefinition = false;
                updateFlashcardView();
            }
        });

        genQuizBtn.addActionListener(e -> {
            try {
                int count = (Integer) countSpinner.getValue();
                String diff = (String) difficultyBox.getSelectedItem();
                latestQuiz = facade.requestQuiz(getSelectedSubjectId(), diff, count);
                StringBuilder sb = new StringBuilder("=== F06 Generated " + latestQuiz.getSummary() + " ===\n\n");
                int qNum = 1;
                for (QuizQuestion q : latestQuiz.getQuestions()) {
                    sb.append(qNum++).append(". ").append(q.getPrompt()).append("\n");
                    List<String> opts = q.getOptions();
                    for (int i = 0; i < opts.size(); i++) {
                        sb.append("    (").append((char) ('A' + i)).append(") ").append(opts.get(i)).append("\n");
                    }
                    sb.append("\n");
                }
                sb.append("✓ Quiz ready! Open Tab 4 ('Study Session & Analytics') to execute this quiz interactively.");
                quizPreviewArea.setText(sb.toString());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "F06 Insufficient Material / Alert", JOptionPane.WARNING_MESSAGE);
            }
        });

        topControls.add(genFlashcardsBtn);
        topControls.add(flipBtn);
        topControls.add(nextCardBtn);
        topControls.add(new JLabel(" | Quiz Difficulty:"));
        topControls.add(difficultyBox);
        topControls.add(genQuizBtn);

        // Split pane: Top = Flashcard Viewer (F05), Bottom = Adaptive Quiz Viewer (F06)
        JPanel cardPanel = new JPanel(new BorderLayout(4, 4));
        cardPanel.setBorder(BorderFactory.createTitledBorder("F05: Interactive Digital Flashcard Deck Viewer"));
        flashcardCounterLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        flashcardCardView.setEditable(false);
        flashcardCardView.setLineWrap(true);
        flashcardCardView.setWrapStyleWord(true);
        flashcardCardView.setFont(new Font("SansSerif", Font.BOLD, 15));
        flashcardCardView.setBackground(new Color(254, 249, 231));
        flashcardCardView.setBorder(new EmptyBorder(16, 20, 16, 20));
        flashcardCardView.setText("Click 'F05: Generate Flashcard Deck' above to synthesize cards from your subject notes.");
        cardPanel.add(flashcardCounterLabel, BorderLayout.NORTH);
        cardPanel.add(new JScrollPane(flashcardCardView), BorderLayout.CENTER);

        JPanel quizPanel = new JPanel(new BorderLayout(4, 4));
        quizPanel.setBorder(BorderFactory.createTitledBorder("F06: Adaptive Multiple-Choice Quiz Preview (Set Count > 10 to test Insufficient Material Alert)"));
        quizPreviewArea.setEditable(false);
        quizPreviewArea.setLineWrap(true);
        quizPreviewArea.setWrapStyleWord(true);
        quizPreviewArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        quizPreviewArea.setText("Select Difficulty (Beginner / Intermediate / Advanced) and click 'F06: Generate Adaptive Quiz'.");
        quizPanel.add(new JScrollPane(quizPreviewArea), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, cardPanel, quizPanel);
        split.setDividerLocation(230);

        panel.add(topControls, BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private void updateFlashcardView() {
        if (currentDeck.isEmpty()) return;
        Flashcard c = currentDeck.get(currentCardIndex);
        flashcardCounterLabel.setText(String.format("Deck Subject: %s | Card %d of %d (%s)",
                c.getSubjectId(), currentCardIndex + 1, currentDeck.size(),
                showingCardDefinition ? "BACK - DEFINITION" : "FRONT - KEY TERM"));
        flashcardCardView.setBackground(showingCardDefinition ? new Color(232, 248, 245) : new Color(254, 249, 231));
        flashcardCardView.setText(showingCardDefinition
                ? "DEFINITION:\n\n" + c.getDefinition()
                : "TERM:\n\n" + c.getTerm() + "\n\n(Click 'Flip Card' to reveal definition)");
    }

    private JPanel buildCodingTab() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        top.add(new JLabel("CS Topic:"));
        top.add(codingTopicField);

        JButton genProblemBtn = new JButton("F07: Generate Practice Problem");
        JButton submitCodeBtn = new JButton("F08: Submit Answer for Grading");
        JButton clearEditorBtn = new JButton("Clear Editor (Test F08 Guard)");

        genProblemBtn.addActionListener(e -> {
            try {
                currentProblem = facade.generateCodingProblem(codingTopicField.getText().trim());
                problemStatementArea.setText(currentProblem.getProblemTitle() + "\n\n"
                        + currentProblem.getDescription() + "\nConstraints: " + currentProblem.getConstraints());
                codeEditorArea.setText(currentProblem.getStarterCode());
                gradeFeedbackArea.setText("Complete your solution in the editor above and click 'F08: Submit Answer for Grading'.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "F07 Academic Safety & Scope Guard", JOptionPane.WARNING_MESSAGE);
            }
        });

        submitCodeBtn.addActionListener(e -> {
            try {
                if (currentProblem == null) {
                    currentProblem = facade.generateCodingProblem(codingTopicField.getText().trim());
                }
                GradeEvaluation eval = facade.gradeSubmission(currentProblem, codeEditorArea.getText());
                StringBuilder fb = new StringBuilder();
                fb.append("SCORE: ").append(eval.getScore()).append(" / 100\n");
                fb.append(eval.getFeedback()).append("\n\nQUALITATIVE HINTS:\n");
                for (String h : eval.getHints()) {
                    fb.append(" • ").append(h).append("\n");
                }
                gradeFeedbackArea.setText(fb.toString());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "F08 Input Validation Guard", JOptionPane.WARNING_MESSAGE);
            }
        });

        clearEditorBtn.addActionListener(e -> codeEditorArea.setText(""));

        top.add(genProblemBtn);
        top.add(submitCodeBtn);
        top.add(clearEditorBtn);

        problemStatementArea.setEditable(false);
        problemStatementArea.setLineWrap(true);
        problemStatementArea.setWrapStyleWord(true);
        problemStatementArea.setBorder(BorderFactory.createTitledBorder("F07: Synthesized CS Problem Statement (Try typing 'Bake a cake' to test refusal guard)"));

        codeEditorArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        codeEditorArea.setBorder(BorderFactory.createTitledBorder("Java Solution Editor"));

        gradeFeedbackArea.setEditable(false);
        gradeFeedbackArea.setLineWrap(true);
        gradeFeedbackArea.setWrapStyleWord(true);
        gradeFeedbackArea.setBorder(BorderFactory.createTitledBorder("F08: Semantic Answer Grading, Score & Hints"));

        JPanel center = new JPanel(new GridLayout(3, 1, 6, 6));
        center.add(new JScrollPane(problemStatementArea));
        center.add(new JScrollPane(codeEditorArea));
        center.add(new JScrollPane(gradeFeedbackArea));

        panel.add(top, BorderLayout.NORTH);
        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildSessionAndAnalyticsTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));

        JButton startSessionBtn = new JButton("F09: Start Study Session");
        JButton submitAnsBtn = new JButton("Submit Selected Answer");
        JButton undoBtn = new JButton("Undo Last Answer (Command.undo)");
        JButton exitMidSessionBtn = new JButton("Exit Mid-Session (F09 Warning)");
        sessionTimerLabel.setFont(new Font("Monospaced", Font.BOLD, 14));
        sessionTimerLabel.setForeground(new Color(180, 40, 40));

        startSessionBtn.addActionListener(e -> {
            try {
                String sub = getSelectedSubjectId();
                if (latestQuiz == null) {
                    facade.ingestFolder(sub, folderPathField.getText().trim());
                    latestQuiz = facade.requestQuiz(sub, "Intermediate", 2);
                }
                facade.startStudySession(latestQuiz);
                startPomodoroTimer();
                refreshSessionView();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "F09 Session Alert", JOptionPane.WARNING_MESSAGE);
            }
        });

        submitAnsBtn.addActionListener(e -> {
            StudySession s = facade.getActiveStudySession();
            if (s == null || s.isCompleted()) {
                JOptionPane.showMessageDialog(this, "Click 'F09: Start Study Session' first.");
                return;
            }
            int selected = getSelectedOptionIndex();
            boolean correct = facade.submitQuizAnswer(selected);
            refreshSessionView();
            refreshAnalyticsDisplay();
            JOptionPane.showMessageDialog(this,
                    "Submitted Option " + (char) ('A' + selected) + " -> " + (correct ? "CORRECT! (+1)" : "INCORRECT"),
                    "F09 SubmitAnswerCommand Executed", JOptionPane.INFORMATION_MESSAGE);
        });

        undoBtn.addActionListener(e -> {
            boolean undone = facade.undoLastSessionAnswer();
            if (undone) {
                refreshSessionView();
                JOptionPane.showMessageDialog(this,
                        "SubmitAnswerCommand.undo() restored previousQuestionIndex and previousScore!",
                        "F09 Command Undone", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Command history stack is empty — no action to undo.");
            }
        });

        exitMidSessionBtn.addActionListener(e -> {
            if (pomodoroTimer != null) pomodoroTimer.stop();
            int res = JOptionPane.showConfirmDialog(this,
                    "Session in progress. Save progress before exiting?",
                    "F09 Early Exit Warning", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (res == JOptionPane.CANCEL_OPTION && pomodoroTimer != null) {
                pomodoroTimer.start();
            }
        });

        top.add(startSessionBtn);
        top.add(submitAnsBtn);
        top.add(undoBtn);
        top.add(exitMidSessionBtn);
        top.add(sessionTimerLabel);

        // Session Question & Radio Options Panel
        JPanel sessionBox = new JPanel(new BorderLayout(6, 6));
        sessionBox.setBorder(BorderFactory.createTitledBorder("F09: Guided Study Session (Command Pattern Queue & Pomodoro Timer)"));
        activeQuestionArea.setEditable(false);
        activeQuestionArea.setLineWrap(true);
        activeQuestionArea.setWrapStyleWord(true);
        activeQuestionArea.setFont(new Font("SansSerif", Font.BOLD, 14));
        activeQuestionArea.setText("Click 'F09: Start Study Session' to load questions into SessionManager.");
        sessionBox.add(new JScrollPane(activeQuestionArea), BorderLayout.CENTER);

        JPanel optionsPanel = new JPanel(new GridLayout(2, 2, 6, 6));
        for (int i = 0; i < 4; i++) {
            optionButtons[i] = new JRadioButton("Option " + (char) ('A' + i));
            if (i == 0) optionButtons[i].setSelected(true);
            optionGroup.add(optionButtons[i]);
            optionsPanel.add(optionButtons[i]);
        }
        sessionBox.add(optionsPanel, BorderLayout.SOUTH);

        // Analytics Panel (Text + Visual Bar Chart)
        JPanel analyticsBox = new JPanel(new GridLayout(1, 2, 8, 8));
        analyticsBox.setBorder(BorderFactory.createTitledBorder("F10: Real-Time Progress Analytics Dashboard (Observer Pattern)"));
        analyticsTextArea.setEditable(false);
        analyticsTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        analyticsBox.add(new JScrollPane(analyticsTextArea));
        analyticsBox.add(chartPanel);

        refreshAnalyticsDisplay();

        JSplitPane centerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, sessionBox, analyticsBox);
        centerSplit.setDividerLocation(240);

        panel.add(top, BorderLayout.NORTH);
        panel.add(centerSplit, BorderLayout.CENTER);
        panel.add(sessionProgressLabel, BorderLayout.SOUTH);
        return panel;
    }

    private int getSelectedOptionIndex() {
        for (int i = 0; i < optionButtons.length; i++) {
            if (optionButtons[i].isSelected()) return i;
        }
        return 0;
    }

    private void startPomodoroTimer() {
        remainingSeconds = 25 * 60;
        if (pomodoroTimer != null) pomodoroTimer.stop();
        pomodoroTimer = new Timer(1000, e -> {
            if (remainingSeconds > 0) {
                remainingSeconds--;
                sessionTimerLabel.setText(String.format("Pomodoro: %02d:%02d", remainingSeconds / 60, remainingSeconds % 60));
            }
        });
        pomodoroTimer.start();
    }

    private void refreshSessionView() {
        StudySession s = facade.getActiveStudySession();
        if (s == null) return;
        sessionProgressLabel.setText(String.format(
                "Session Status: Question %d of %d | Current Score: %d | Completed: %s",
                s.getCurrentQuestionIndex(), s.getTotalQuestions(), s.getCurrentScore(), s.isCompleted()));

        if (s.isCompleted()) {
            if (pomodoroTimer != null) pomodoroTimer.stop();
            activeQuestionArea.setText(String.format(
                    "🎉 STUDY SESSION COMPLETED!\nFinal Score: %d / %d (%.1f%%)\n"
                            + "SessionManager.notifyObservers() automatically updated the F10 Analytics chart below!",
                    s.getCurrentScore(), s.getTotalQuestions(),
                    (s.getCurrentScore() * 100.0) / Math.max(1, s.getTotalQuestions())));
            return;
        }

        QuizQuestion q = s.getCurrentQuestion();
        if (q != null) {
            activeQuestionArea.setText("Question " + (s.getCurrentQuestionIndex() + 1) + " of " + s.getTotalQuestions() + ":\n" + q.getPrompt());
            List<String> opts = q.getOptions();
            for (int i = 0; i < optionButtons.length; i++) {
                if (i < opts.size()) {
                    optionButtons[i].setText("(" + (char) ('A' + i) + ") " + opts.get(i));
                    optionButtons[i].setEnabled(true);
                } else {
                    optionButtons[i].setText("-");
                    optionButtons[i].setEnabled(false);
                }
            }
        }
    }

    private void refreshAnalyticsDisplay() {
        String text = facade.viewAnalytics();
        analyticsTextArea.setText(text);
        chartPanel.updateFromSummaryText(text);
    }

    private static class MasteryChartPanel extends JPanel {
        private double masteryPercent = -1;
        private String subjectLabel = "EECS3311";

        public void updateFromSummaryText(String summary) {
            if (summary == null || summary.contains("No data available")) {
                masteryPercent = -1;
            } else {
                try {
                    int idx = summary.indexOf("Mastery:");
                    if (idx != -1) {
                        String sub = summary.substring(idx + 8, summary.indexOf("%", idx)).trim();
                        masteryPercent = Double.parseDouble(sub);
                    }
                } catch (Exception ignored) {
                    masteryPercent = 75.0;
                }
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, w, h);

            if (masteryPercent < 0) {
                g2.setColor(Color.DARK_GRAY);
                g2.setFont(new Font("SansSerif", Font.ITALIC, 13));
                g2.drawString("No data available yet. Start a study session!", 20, h / 2);
                return;
            }

            g2.setColor(new Color(44, 62, 80));
            g2.setFont(new Font("SansSerif", Font.BOLD, 13));
            g2.drawString("Visual Subject Mastery (" + subjectLabel + ")", 20, 28);

            int barWidth = w - 60;
            int barHeight = 34;
            int barY = (h / 2) - 10;

            g2.setColor(new Color(230, 230, 230));
            g2.fillRoundRect(20, barY, barWidth, barHeight, 10, 10);

            int fillWidth = (int) ((masteryPercent / 100.0) * barWidth);
            g2.setColor(masteryPercent >= 70 ? new Color(39, 174, 96) : new Color(243, 156, 18));
            g2.fillRoundRect(20, barY, Math.max(12, fillWidth), barHeight, 10, 10);

            g2.setColor(Color.BLACK);
            g2.drawRoundRect(20, barY, barWidth, barHeight, 10, 10);
            g2.drawString(String.format("%.1f%% Mastery", masteryPercent), 30, barY + 22);
        }
    }

    public static void main(String[] args) {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new MainGUI(new StudyAgentFacade()).setVisible(true));
    }
}