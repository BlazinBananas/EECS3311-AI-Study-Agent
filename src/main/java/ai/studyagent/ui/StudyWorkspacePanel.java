package ai.studyagent.ui;

import ai.studyagent.config.EngineConfig;
import ai.studyagent.controller.StudyAgentFacade;
import ai.studyagent.domain.ChatResponse;
import ai.studyagent.domain.CodingProblem;
import ai.studyagent.domain.DocumentChunk;
import ai.studyagent.domain.Flashcard;
import ai.studyagent.domain.GradeEvaluation;
import ai.studyagent.domain.IngestionResult;
import ai.studyagent.domain.Quiz;
import ai.studyagent.domain.QuizQuestion;
import ai.studyagent.domain.WebSnippet;
import ai.studyagent.session.StudySession;
import ai.studyagent.strategy.LocalOllamaClient;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StudyWorkspacePanel extends JPanel {

    public static class ChatThread {
        public String title;
        public StringBuilder transcript = new StringBuilder();
        public transient ChatResponse lastResponse;

        public ChatThread(String title, String welcomeText) {
            this.title = title;
            this.transcript.append(welcomeText);
        }

        @Override
        public String toString() { return title; }
    }

    public enum ArtifactType { FLASHCARDS, QUIZ, NOTE }

    public static class StudioArtifact {
        public ArtifactType type;
        public String title;
        public String subtitle;
        public List<Flashcard> deck;
        public Quiz quiz;
        public String noteContent;

        public StudioArtifact(String title, String subtitle, List<Flashcard> deck) {
            this.type = ArtifactType.FLASHCARDS;
            this.title = title;
            this.subtitle = subtitle;
            this.deck = deck;
        }

        public StudioArtifact(String title, String subtitle, Quiz quiz) {
            this.type = ArtifactType.QUIZ;
            this.title = title;
            this.subtitle = subtitle;
            this.quiz = quiz;
        }

        public StudioArtifact(String title, String subtitle, String noteContent) {
            this.type = ArtifactType.NOTE;
            this.title = title;
            this.subtitle = subtitle;
            this.noteContent = noteContent;
        }

        @Override
        public String toString() {
            String icon = switch (type) {
                case FLASHCARDS -> "🗂 ";
                case QUIZ -> "📝 ";
                case NOTE -> "📄 ";
            };
            return icon + title + "  (" + subtitle + ")";
        }
    }

    public static class NotebookState {
        public String subjectId;
        public String displayTitle;
        public List<ChatThread> chatThreads = new ArrayList<>();
        public int activeThreadIndex = 0;
        public Set<String> checkedSources = new LinkedHashSet<>();
        public List<StudioArtifact> studioItems = new ArrayList<>();
        public List<Flashcard> activeDeck = new ArrayList<>();
        public String activeDeckTitle = "Flashcards";
        public String activeDeckSubtitle = "Course Sources";
        public int cardIndex = 0;
        public boolean showDefinition = false;
        public Quiz activeQuiz;
        public String activeQuizTitle = "Quiz";
        public String activeQuizSubtitle = "Course Sources";
        public CodingProblem latestProblem;

        public NotebookState(String subjectId, String displayTitle) {
            this.subjectId = subjectId;
            this.displayTitle = displayTitle;
            this.chatThreads.add(new ChatThread("Chat 1 — General Study",
                    "=== " + displayTitle + " Notebook ===\n"
                            + "• Click '+ Add sources (F02)' on the left to import course files.\n"
                            + "• Check or uncheck individual files in the Sources list to control which files the AI uses.\n"
                            + "• Use the Studio panel on the right to generate Flashcards, Quizzes, and Pinned Notes (click '⤢ Maximize' for full-screen focus mode!).\n"));
        }

        public ChatThread activeThread() {
            if (chatThreads.isEmpty()) {
                chatThreads.add(new ChatThread("Chat 1", ""));
            }
            if (activeThreadIndex < 0 || activeThreadIndex >= chatThreads.size()) {
                activeThreadIndex = 0;
            }
            return chatThreads.get(activeThreadIndex);
        }
    }

    private final StudyAgentFacade facade;
    private final Runnable onBackToNotebooks;
    private final JLabel statusBanner;
    private final JLabel importedDocsLabel;
    private Runnable onStateChanged = () -> {};

    private final Map<String, NotebookState> notebookMap = new HashMap<>();
    private String activeSubjectId = "EECS3311";
    private boolean localMode = true;

    private final JLabel notebookHeaderLabel = new JLabel("💻 EECS 3311 - Software Design");
    private final JButton engineModeBtn = new JButton("F01: Local AI Mode");
    private final JProgressBar generationProgressBar = new JProgressBar();

    // Left Column: Interactive Sources Checkboxes
    private final JPanel sourcesCheckListPanel = new JPanel();
    private final JCheckBox selectAllSourcesCheck = new JCheckBox("Select all", true);
    private final JTextField folderPathField = new JTextField("sample-course-notes", 14);
    private final JCheckBox webSearchToggle = new JCheckBox("🌐 Web Search (F03)", true);
    private final JLabel sourceCountPill = new JLabel("0 sources selected");

    // Center Column: Per-Notebook Chat
    private final JComboBox<ChatThread> chatThreadSelector = new JComboBox<>();
    private final JTextArea chatTranscriptArea = new JTextArea();
    private final JTextField chatInputField = new JTextField();
    private boolean updatingThreadCombo = false;

    // Right Column: Studio Library & Viewers
    private final DefaultListModel<StudioArtifact> studioListModel = new DefaultListModel<>();
    private final JList<StudioArtifact> studioJList = new JList<>(studioListModel);
    private final CardLayout studioCardLayout = new CardLayout();
    private final JPanel studioViewerContainer = new JPanel(studioCardLayout);

    private final JLabel flashcardCounterLabel = new JLabel("Select or generate a Flashcard deck", SwingConstants.CENTER);
    private final JTextArea flashcardTextArea = new JTextArea(5, 24);

    private final JTextField noteTitleField = new JTextField("Study Note", 16);
    private final JTextArea noteEditorArea = new JTextArea(8, 24);
    private StudioArtifact currentlyEditingNote;

    private final JTextField codingTopicField = new JTextField("Java Strategy Pattern", 14);
    private final JTextArea problemStatementArea = new JTextArea(4, 24);
    private final JTextArea codeEditorArea = new JTextArea(6, 24);
    private final JTextArea gradeFeedbackArea = new JTextArea(4, 24);

    private final JLabel sessionTimerLabel = new JLabel("⏱ 25:00");
    private final JLabel sessionProgressLabel = new JLabel("Session: Idle");
    private final JTextArea activeQuestionArea = new JTextArea(4, 24);
    private final JRadioButton[] optionButtons = new JRadioButton[4];
    private final ButtonGroup optionGroup = new ButtonGroup();
    private final JTextArea analyticsTextArea = new JTextArea(4, 24);
    private final MasteryChartPanel chartPanel = new MasteryChartPanel();
    private Timer pomodoroTimer;
    private int remainingSeconds = 25 * 60;

    public StudyWorkspacePanel(StudyAgentFacade facade, Runnable onBackToNotebooks, JLabel statusBanner, JLabel importedDocsLabel) {
        this.facade = facade;
        this.onBackToNotebooks = onBackToNotebooks;
        this.statusBanner = statusBanner;
        this.importedDocsLabel = importedDocsLabel;

        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 16, 10, 16));

        add(buildNotebookTopBar(), BorderLayout.NORTH);

        JPanel leftSources = buildLeftSourcesColumn();
        JPanel centerChat = buildCenterChatColumn();
        JPanel rightStudio = buildRightStudioColumn();

        JSplitPane rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, centerChat, rightStudio);
        rightSplit.setResizeWeight(0.58);
        rightSplit.setDividerLocation(510);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSources, rightSplit);
        mainSplit.setResizeWeight(0.20);
        mainSplit.setDividerLocation(260);

        add(mainSplit, BorderLayout.CENTER);
        setActiveCourse("EECS3311", "EECS 3311 - Software Design");
    }

    public void setOnStateChanged(Runnable onStateChanged) {
        this.onStateChanged = onStateChanged != null ? onStateChanged : () -> {};
    }

    public Map<String, NotebookState> getNotebookMap() {
        return notebookMap;
    }

    public void setNotebookMap(Map<String, NotebookState> loaded) {
        if (loaded != null && !loaded.isEmpty()) {
            notebookMap.clear();
            notebookMap.putAll(loaded);
            setActiveCourse(activeSubjectId, currentNotebook().displayTitle);
        }
    }

    private void setBusyState(boolean busy, String message) {
        generationProgressBar.setIndeterminate(busy);
        generationProgressBar.setVisible(busy);
        if (busy) {
            generationProgressBar.setString("⚡ " + message);
            statusBanner.setText(" ⚡ " + message);
        } else {
            updateEngineStatusLabel();
        }
    }

    public void setActiveCourse(String subjectId, String fullTitle) {
        this.activeSubjectId = subjectId;
        notebookMap.putIfAbsent(subjectId, new NotebookState(subjectId, fullTitle));
        NotebookState state = notebookMap.get(subjectId);
        if (fullTitle != null && !fullTitle.isBlank()) {
            state.displayTitle = fullTitle;
        }

        notebookHeaderLabel.setText("📒 " + state.displayTitle + "  [" + subjectId + "]");
        rebuildSourcesCheckboxes();
        refreshChatThreadSelectorForActiveNotebook();
        refreshStudioLibraryList();
        updateFlashcardView();
        refreshAnalyticsDisplay();
    }

    private NotebookState currentNotebook() {
        notebookMap.putIfAbsent(activeSubjectId, new NotebookState(activeSubjectId, activeSubjectId));
        return notebookMap.get(activeSubjectId);
    }

    private JPanel buildNotebookTopBar() {
        JPanel outer = new JPanel(new BorderLayout(0, 6));
        JPanel top = new JPanel(new BorderLayout(12, 0));
        top.setBorder(new EmptyBorder(2, 4, 4, 4));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        JButton backBtn = new JButton("← My notebooks");
        backBtn.addActionListener(e -> onBackToNotebooks.run());
        left.add(backBtn);

        notebookHeaderLabel.setFont(new Font("SansSerif", Font.BOLD, 17));
        left.add(notebookHeaderLabel);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        engineModeBtn.addActionListener(e -> {
            localMode = !localMode;
            facade.setEngineMode(localMode, facade.getEngineConfig());
            updateEngineStatusLabel();
        });
        right.add(engineModeBtn);

        JButton settingsBtn = new JButton("⚙ AI Engine Settings (F01)");
        settingsBtn.addActionListener(e -> showEngineConfigDialog());
        right.add(settingsBtn);

        top.add(left, BorderLayout.WEST);
        top.add(right, BorderLayout.EAST);

        generationProgressBar.setStringPainted(true);
        generationProgressBar.setVisible(false);
        generationProgressBar.setPreferredSize(new Dimension(100, 22));

        outer.add(top, BorderLayout.NORTH);
        outer.add(generationProgressBar, BorderLayout.SOUTH);
        return outer;
    }

    private JPanel buildLeftSourcesColumn() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1, true),
                new EmptyBorder(12, 12, 12, 12)
        ));
        panel.setPreferredSize(new Dimension(260, 600));

        JPanel topBox = new JPanel(new GridLayout(4, 1, 6, 6));
        JLabel title = new JLabel("Sources");
        title.setFont(new Font("SansSerif", Font.BOLD, 15));
        topBox.add(title);

        JButton addSourcesBtn = new JButton("+ Add sources (F02)");
        addSourcesBtn.setBackground(new Color(59, 130, 246));
        addSourcesBtn.setForeground(Color.WHITE);
        addSourcesBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        addSourcesBtn.addActionListener(e -> showAddSourcesDialog());
        topBox.add(addSourcesBtn);

        webSearchToggle.addActionListener(e -> facade.setWebSearchPermitted(webSearchToggle.isSelected()));
        topBox.add(webSearchToggle);

        JPanel selectAllRow = new JPanel(new BorderLayout());
        selectAllRow.add(new JLabel("Notebook Sources:"), BorderLayout.WEST);
        selectAllSourcesCheck.setHorizontalTextPosition(SwingConstants.LEFT);
        selectAllSourcesCheck.addActionListener(e -> {
            NotebookState nb = currentNotebook();
            List<String> allFiles = facade.getSubjectSources(activeSubjectId);
            if (selectAllSourcesCheck.isSelected()) {
                nb.checkedSources.addAll(allFiles);
            } else {
                nb.checkedSources.clear();
            }
            onStateChanged.run();
            rebuildSourcesCheckboxes();
        });
        selectAllRow.add(selectAllSourcesCheck, BorderLayout.EAST);
        topBox.add(selectAllRow);

        panel.add(topBox, BorderLayout.NORTH);

        sourcesCheckListPanel.setLayout(new BoxLayout(sourcesCheckListPanel, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(sourcesCheckListPanel);
        scroll.setBorder(null);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private void showAddSourcesDialog() {
        JPanel dialogPanel = new JPanel(new BorderLayout(8, 8));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.add(new JLabel("Folder Path:"));
        row.add(folderPathField);

        JButton browseBtn = new JButton("Browse...");
        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(new File("."));
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                folderPathField.setText(chooser.getSelectedFile().getPath());
            }
        });
        row.add(browseBtn);
        dialogPanel.add(row, BorderLayout.CENTER);

        int res = JOptionPane.showConfirmDialog(this, dialogPanel,
                "F02: Import Course Directory into " + activeSubjectId,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (res == JOptionPane.OK_OPTION) {
            String path = folderPathField.getText().trim();
            IngestionResult ing = facade.ingestFolder(activeSubjectId, path);
            NotebookState nb = currentNotebook();
            nb.checkedSources.addAll(ing.getImportedFileNames());
            rebuildSourcesCheckboxes();

            ChatThread thread = nb.activeThread();
            thread.transcript.append(String.format(
                    "\n[F02 Sources Added to %s]\n  ✓ Imported: %s (%d chunks)\n  ⚠ Skipped: %s\n",
                    activeSubjectId, ing.getImportedFileNames(), ing.getTotalChunksCreated(), ing.getSkippedFiles()));
            chatTranscriptArea.setText(thread.transcript.toString());
            onStateChanged.run();

            JOptionPane.showMessageDialog(this,
                    "Notebook: " + activeSubjectId
                            + "\nImported Files: " + ing.getImportedFileNames()
                            + "\nNew Chunks Indexed: " + ing.getTotalChunksCreated()
                            + "\nSkipped Files: " + ing.getSkippedFiles(),
                    "F02 Sources Imported", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void rebuildSourcesCheckboxes() {
        sourcesCheckListPanel.removeAll();
        NotebookState nb = currentNotebook();
        List<String> files = facade.getSubjectSources(activeSubjectId);

        if (files.isEmpty()) {
            JLabel emptyLbl = new JLabel("No sources imported yet.");
            emptyLbl.setBorder(new EmptyBorder(8, 4, 8, 4));
            sourcesCheckListPanel.add(emptyLbl);
        } else {
            for (String fileName : files) {
                JCheckBox cb = new JCheckBox("📄 " + fileName, nb.checkedSources.contains(fileName));
                cb.setBorder(new EmptyBorder(5, 4, 5, 4));
                cb.addActionListener(e -> {
                    if (cb.isSelected()) {
                        nb.checkedSources.add(fileName);
                    } else {
                        nb.checkedSources.remove(fileName);
                    }
                    onStateChanged.run();
                    updateSourceCountLabels();
                });
                sourcesCheckListPanel.add(cb);
            }
        }
        updateSourceCountLabels();
        sourcesCheckListPanel.revalidate();
        sourcesCheckListPanel.repaint();
    }

    private void updateSourceCountLabels() {
        NotebookState nb = currentNotebook();
        int checkedCount = nb.checkedSources.size();
        int totalCount = facade.getSubjectSources(activeSubjectId).size();
        selectAllSourcesCheck.setSelected(totalCount > 0 && checkedCount == totalCount);
        sourceCountPill.setText(checkedCount + " of " + totalCount + (totalCount == 1 ? " source" : " sources"));
        importedDocsLabel.setText("Active Notebook (" + activeSubjectId + "): " + checkedCount + "/" + totalCount + " sources active ");
    }

    private JPanel buildCenterChatColumn() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1, true),
                new EmptyBorder(12, 14, 12, 14)
        ));

        JPanel chatHeader = new JPanel(new BorderLayout(6, 0));
        JPanel leftThreadBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        leftThreadBox.add(new JLabel("Thread:"));
        chatThreadSelector.addActionListener(e -> {
            if (updatingThreadCombo) return;
            int idx = chatThreadSelector.getSelectedIndex();
            if (idx >= 0) {
                NotebookState nb = currentNotebook();
                nb.activeThreadIndex = idx;
                chatTranscriptArea.setText(nb.activeThread().transcript.toString());
            }
        });
        leftThreadBox.add(chatThreadSelector);

        JButton newThreadBtn = new JButton("+ New Chat");
        newThreadBtn.addActionListener(e -> {
            NotebookState nb = currentNotebook();
            int nextNum = nb.chatThreads.size() + 1;
            String name = JOptionPane.showInputDialog(this, "Enter topic for new chat thread:", "Chat " + nextNum);
            if (name != null && !name.isBlank()) {
                nb.chatThreads.add(new ChatThread(name.trim(), "=== Thread: " + name.trim() + " (" + nb.displayTitle + ") ===\n"));
                nb.activeThreadIndex = nb.chatThreads.size() - 1;
                onStateChanged.run();
                refreshChatThreadSelectorForActiveNotebook();
            }
        });
        leftThreadBox.add(newThreadBtn);

        JPanel rightChatBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton pinToNoteBtn = new JButton("📌 Save to Note");
        pinToNoteBtn.addActionListener(e -> pinLastResponseToStudioNote());

        JButton viewCitationsBtn = new JButton("Citations (F04)");
        viewCitationsBtn.addActionListener(e -> showSourceCitationsPopup());

        rightChatBtns.add(pinToNoteBtn);
        rightChatBtns.add(viewCitationsBtn);

        chatHeader.add(leftThreadBox, BorderLayout.WEST);
        chatHeader.add(rightChatBtns, BorderLayout.EAST);
        panel.add(chatHeader, BorderLayout.NORTH);

        chatTranscriptArea.setEditable(false);
        chatTranscriptArea.setLineWrap(true);
        chatTranscriptArea.setWrapStyleWord(true);
        chatTranscriptArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        panel.add(new JScrollPane(chatTranscriptArea), BorderLayout.CENTER);

        JPanel promptBar = new JPanel(new BorderLayout(8, 0));
        promptBar.setBorder(new EmptyBorder(6, 0, 0, 0));
        chatInputField.putClientProperty("JTextField.placeholderText", "Ask a question about your checked sources...");
        chatInputField.setText("What is the Liskov Substitution Principle?");

        JPanel rightPromptControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        sourceCountPill.setForeground(UIManager.getColor("Label.disabledForeground"));
        rightPromptControls.add(sourceCountPill);

        JButton sendBtn = new JButton("Send ➤");
        sendBtn.setBackground(new Color(59, 130, 246));
        sendBtn.setForeground(Color.WHITE);
        sendBtn.setFont(new Font("SansSerif", Font.BOLD, 12));

        Runnable sendAction = () -> {
            String q = chatInputField.getText().trim();
            if (q.isEmpty()) return;
            NotebookState nb = currentNotebook();
            ChatThread activeThread = nb.activeThread();
            Set<String> checked = new LinkedHashSet<>(nb.checkedSources);

            sendBtn.setEnabled(false);
            setBusyState(true, "Generating grounded chat response for " + activeSubjectId + "...");

            new SwingWorker<ChatResponse, Void>() {
                @Override
                protected ChatResponse doInBackground() throws Exception {
                    return facade.askQuestion(activeSubjectId, q, checked);
                }

                @Override
                protected void done() {
                    sendBtn.setEnabled(true);
                    setBusyState(false, "");
                    try {
                        ChatResponse resp = get();
                        activeThread.lastResponse = resp;
                        activeThread.transcript.append("\n────────────────────────────────────────────────────\n");
                        activeThread.transcript.append("You: ").append(q).append("\n\n");
                        activeThread.transcript.append(resp.getAnswerText()).append("\n");
                        if (resp.isUsedWebFallback()) {
                            activeThread.transcript.append("\n🌐 [F03 Live Web Citations]:\n");
                            for (WebSnippet ws : resp.getWebCitations()) {
                                activeThread.transcript.append("  • ").append(ws.getTitle()).append(" (").append(ws.getUrl()).append(")\n");
                            }
                        }
                        chatTranscriptArea.setText(activeThread.transcript.toString());
                        chatInputField.setText("");
                        onStateChanged.run();
                    } catch (Exception ex) {
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        JOptionPane.showMessageDialog(StudyWorkspacePanel.this, cause.getMessage(),
                                "F04 Chat Alert", JOptionPane.WARNING_MESSAGE);
                    }
                }
            }.execute();
        };

        sendBtn.addActionListener(e -> sendAction.run());
        chatInputField.addActionListener(e -> sendAction.run());
        rightPromptControls.add(sendBtn);

        promptBar.add(chatInputField, BorderLayout.CENTER);
        promptBar.add(rightPromptControls, BorderLayout.EAST);
        panel.add(promptBar, BorderLayout.SOUTH);

        return panel;
    }

    private void pinLastResponseToStudioNote() {
        NotebookState nb = currentNotebook();
        ChatResponse last = nb.activeThread().lastResponse;
        String content = (last != null) ? last.getAnswerText() : chatTranscriptArea.getText();
        if (content == null || content.isBlank()) return;

        String title = JOptionPane.showInputDialog(this, "Note Title:",
                activeSubjectId + " Study Guide " + (nb.studioItems.size() + 1));
        if (title != null && !title.isBlank()) {
            String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
            StudioArtifact note = new StudioArtifact(title.trim(), "Pinned Note • " + timestamp, content);
            nb.studioItems.add(0, note);
            onStateChanged.run();
            refreshStudioLibraryList();
            openStudioArtifact(note);
        }
    }

    private void refreshChatThreadSelectorForActiveNotebook() {
        updatingThreadCombo = true;
        chatThreadSelector.removeAllItems();
        NotebookState nb = currentNotebook();
        for (ChatThread t : nb.chatThreads) {
            chatThreadSelector.addItem(t);
        }
        chatThreadSelector.setSelectedIndex(nb.activeThreadIndex);
        chatTranscriptArea.setText(nb.activeThread().transcript.toString());
        updatingThreadCombo = false;
    }

    private void showSourceCitationsPopup() {
        ChatResponse last = currentNotebook().activeThread().lastResponse;
        if (last == null) {
            JOptionPane.showMessageDialog(this, "Ask a question in this thread first to inspect source citations.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (last.isUsedWebFallback()) {
            sb.append("=== F03 Live Web Search Snippets (Local Similarity < 0.65) ===\n\n");
            for (WebSnippet s : last.getWebCitations()) {
                sb.append("• ").append(s.getTitle()).append("\n  URL: ").append(s.getUrl())
                        .append("\n  Extract: ").append(s.getSummaryText()).append("\n\n");
            }
        } else {
            sb.append("=== F04 Checked Notebook Chunks Used ===\n\n");
            for (DocumentChunk c : last.getSourceChunks()) {
                sb.append(String.format("File: %s | Similarity: %.2f\n%s\n\n",
                        c.getSourceFileName(), c.getSimilarityScore(), c.getText()));
            }
        }
        JTextArea area = new JTextArea(sb.toString(), 14, 52);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        JOptionPane.showMessageDialog(this, new JScrollPane(area), "F04 Source Citations", JOptionPane.INFORMATION_MESSAGE);
    }

    private JPanel buildRightStudioColumn() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1, true),
                new EmptyBorder(12, 12, 12, 12)
        ));
        panel.setPreferredSize(new Dimension(380, 600));

        JPanel studioTop = new JPanel(new BorderLayout(0, 8));
        JLabel studioTitle = new JLabel("Studio");
        studioTitle.setFont(new Font("SansSerif", Font.BOLD, 15));
        studioTop.add(studioTitle, BorderLayout.NORTH);

        JPanel tileGrid = new JPanel(new GridLayout(2, 2, 8, 8));
        JButton newFlashcardsTile = new JButton("🗂 + Flashcards (F05)");
        JButton newQuizTile = new JButton("📝 + Quiz (F06)");
        JButton codingTile = new JButton("💻 Code Lab (F07/F08)");
        JButton sessionTile = new JButton("⏱ Session & Stats (F09/F10)");

        newFlashcardsTile.addActionListener(e -> showFlashcardGeneratorModal());
        newQuizTile.addActionListener(e -> showQuizGeneratorModal());
        codingTile.addActionListener(e -> studioCardLayout.show(studioViewerContainer, "CODING"));
        sessionTile.addActionListener(e -> studioCardLayout.show(studioViewerContainer, "SESSION"));

        tileGrid.add(newFlashcardsTile);
        tileGrid.add(newQuizTile);
        tileGrid.add(codingTile);
        tileGrid.add(sessionTile);
        studioTop.add(tileGrid, BorderLayout.CENTER);
        panel.add(studioTop, BorderLayout.NORTH);

        JPanel libraryBox = new JPanel(new BorderLayout(4, 4));
        libraryBox.setBorder(BorderFactory.createTitledBorder("Saved Studio Library (Click to open)"));

        studioJList.setFont(new Font("SansSerif", Font.PLAIN, 12));
        studioJList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                StudioArtifact selected = studioJList.getSelectedValue();
                if (selected != null) {
                    openStudioArtifact(selected);
                }
            }
        });
        libraryBox.add(new JScrollPane(studioJList), BorderLayout.CENTER);

        JPanel libraryButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton addNoteBtn = new JButton("📄 + Add note");
        addNoteBtn.addActionListener(e -> createBlankStudioNote());
        JButton deleteItemBtn = new JButton("Delete Selected");
        deleteItemBtn.addActionListener(e -> {
            StudioArtifact sel = studioJList.getSelectedValue();
            if (sel != null) {
                currentNotebook().studioItems.remove(sel);
                onStateChanged.run();
                refreshStudioLibraryList();
            }
        });
        libraryButtons.add(addNoteBtn);
        libraryButtons.add(deleteItemBtn);
        libraryBox.add(libraryButtons, BorderLayout.SOUTH);

        studioViewerContainer.add(buildFlashcardsStudioSubPanel(), "FLASHCARDS");
        studioViewerContainer.add(buildNoteEditorSubPanel(), "NOTE");
        studioViewerContainer.add(buildCodingStudioSubPanel(), "CODING");
        studioViewerContainer.add(buildSessionStudioSubPanel(), "SESSION");

        JSplitPane studioSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, libraryBox, studioViewerContainer);
        studioSplit.setDividerLocation(165);

        panel.add(studioSplit, BorderLayout.CENTER);
        return panel;
    }

    private void showQuizGeneratorModal() {
        NotebookState nb = currentNotebook();
        int checkedCount = nb.checkedSources.size();

        JTextField titleField = new JTextField(activeSubjectId + " Quiz " + (nb.studioItems.size() + 1), 20);
        JComboBox<String> countPresetBox = new JComboBox<>(new String[]{"Fewer (2 questions)", "Standard (4 questions)", "More (6 questions)", "Insufficient Material Demo (15 questions)"});
        countPresetBox.setSelectedIndex(1);
        JComboBox<String> diffPresetBox = new JComboBox<>(new String[]{"Beginner (Easy)", "Intermediate (Medium)", "Advanced (Hard)"});
        diffPresetBox.setSelectedIndex(1);

        JTextArea topicPromptArea = new JTextArea(3, 24);
        topicPromptArea.setLineWrap(true);
        topicPromptArea.setWrapStyleWord(true);

        JPanel modal = new JPanel(new BorderLayout(8, 8));
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Quiz Title:"));
        form.add(titleField);
        form.add(new JLabel("Number of questions:"));
        form.add(countPresetBox);
        form.add(new JLabel("Level of difficulty:"));
        form.add(diffPresetBox);
        form.add(new JLabel("Checked Sources Used:"));
        form.add(new JLabel(checkedCount + " checked source(s)"));

        JPanel topicPanel = new JPanel(new BorderLayout(4, 4));
        topicPanel.add(new JLabel("What should the topic be? (Optional focus prompt):"), BorderLayout.NORTH);
        topicPanel.add(new JScrollPane(topicPromptArea), BorderLayout.CENTER);

        modal.add(form, BorderLayout.NORTH);
        modal.add(topicPanel, BorderLayout.CENTER);

        int res = JOptionPane.showConfirmDialog(this, modal, "📝 Configure Quiz (F06)",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res != JOptionPane.OK_OPTION) return;

        int count = switch (countPresetBox.getSelectedIndex()) {
            case 0 -> 2;
            case 1 -> 4;
            case 2 -> 6;
            default -> 15;
        };
        String diff = switch (diffPresetBox.getSelectedIndex()) {
            case 0 -> "Beginner";
            case 1 -> "Intermediate";
            default -> "Advanced";
        };
        String customTopic = topicPromptArea.getText().trim();
        String quizTitle = titleField.getText().trim().isEmpty() ? activeSubjectId + " Quiz" : titleField.getText().trim();
        Set<String> checked = new LinkedHashSet<>(nb.checkedSources);

        setBusyState(true, "Generating '" + quizTitle + "' (" + count + " questions)...");

        new SwingWorker<Quiz, Void>() {
            @Override
            protected Quiz doInBackground() throws Exception {
                return facade.requestQuiz(activeSubjectId, diff, count, customTopic, checked);
            }

            @Override
            protected void done() {
                setBusyState(false, "");
                try {
                    Quiz generated = get();
                    StudioArtifact item = new StudioArtifact(quizTitle, checked.size() + " sources • " + diff, generated);
                    nb.studioItems.add(0, item);
                    onStateChanged.run();
                    refreshStudioLibraryList();
                    openStudioArtifact(item);
                } catch (Exception ex) {
                    Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(StudyWorkspacePanel.this, c.getMessage(), "F06 Quiz Alert", JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    private void showFlashcardGeneratorModal() {
        NotebookState nb = currentNotebook();
        int checkedCount = nb.checkedSources.size();

        JTextField titleField = new JTextField(activeSubjectId + " Flashcards " + (nb.studioItems.size() + 1), 20);
        JComboBox<String> countPresetBox = new JComboBox<>(new String[]{"Fewer (3 cards)", "Standard (5 cards)", "More (8 cards)"});
        countPresetBox.setSelectedIndex(0);

        JTextArea topicPromptArea = new JTextArea(3, 24);
        topicPromptArea.setLineWrap(true);
        topicPromptArea.setWrapStyleWord(true);

        JPanel modal = new JPanel(new BorderLayout(8, 8));
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Flashcard Set Title:"));
        form.add(titleField);
        form.add(new JLabel("Number of cards:"));
        form.add(countPresetBox);
        form.add(new JLabel("Checked Sources Used:"));
        form.add(new JLabel(checkedCount + " checked source(s)"));

        JPanel topicPanel = new JPanel(new BorderLayout(4, 4));
        topicPanel.add(new JLabel("What should the topic be? (Optional focus prompt):"), BorderLayout.NORTH);
        topicPanel.add(new JScrollPane(topicPromptArea), BorderLayout.CENTER);

        modal.add(form, BorderLayout.NORTH);
        modal.add(topicPanel, BorderLayout.CENTER);

        int res = JOptionPane.showConfirmDialog(this, modal, "🗂 Configure Flashcards (F05)",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res != JOptionPane.OK_OPTION) return;

        int count = switch (countPresetBox.getSelectedIndex()) {
            case 0 -> 3;
            case 1 -> 5;
            default -> 8;
        };
        String customTopic = topicPromptArea.getText().trim();
        String deckTitle = titleField.getText().trim().isEmpty() ? activeSubjectId + " Flashcards" : titleField.getText().trim();
        Set<String> checked = new LinkedHashSet<>(nb.checkedSources);

        setBusyState(true, "Generating '" + deckTitle + "' (" + count + " flashcards)...");

        new SwingWorker<List<Flashcard>, Void>() {
            @Override
            protected List<Flashcard> doInBackground() throws Exception {
                return facade.requestFlashcards(activeSubjectId, count, customTopic, checked);
            }

            @Override
            protected void done() {
                setBusyState(false, "");
                try {
                    List<Flashcard> deck = get();
                    StudioArtifact item = new StudioArtifact(deckTitle, checked.size() + " sources • " + deck.size() + " cards", deck);
                    nb.studioItems.add(0, item);
                    onStateChanged.run();
                    refreshStudioLibraryList();
                    openStudioArtifact(item);
                } catch (Exception ex) {
                    Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(StudyWorkspacePanel.this, c.getMessage(), "F05 Flashcards Alert", JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    private void createBlankStudioNote() {
        NotebookState nb = currentNotebook();
        String title = JOptionPane.showInputDialog(this, "Enter Note Title:",
                activeSubjectId + " Note " + (nb.studioItems.size() + 1));
        if (title != null && !title.isBlank()) {
            String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
            StudioArtifact note = new StudioArtifact(title.trim(), "Note • " + time, "Write your study notes here...");
            nb.studioItems.add(0, note);
            onStateChanged.run();
            refreshStudioLibraryList();
            openStudioArtifact(note);
        }
    }

    private void openStudioArtifact(StudioArtifact item) {
        NotebookState nb = currentNotebook();
        switch (item.type) {
            case FLASHCARDS -> {
                nb.activeDeck = item.deck;
                nb.activeDeckTitle = item.title;
                nb.activeDeckSubtitle = item.subtitle;
                nb.cardIndex = 0;
                nb.showDefinition = false;
                updateFlashcardView();
                studioCardLayout.show(studioViewerContainer, "FLASHCARDS");
            }
            case QUIZ -> {
                nb.activeQuiz = item.quiz;
                nb.activeQuizTitle = item.title;
                nb.activeQuizSubtitle = item.subtitle;
                facade.startStudySession(item.quiz);
                startPomodoroTimer();
                refreshSessionView();
                studioCardLayout.show(studioViewerContainer, "SESSION");
            }
            case NOTE -> {
                currentlyEditingNote = item;
                noteTitleField.setText(item.title);
                noteEditorArea.setText(item.noteContent);
                studioCardLayout.show(studioViewerContainer, "NOTE");
            }
        }
    }

    private void refreshStudioLibraryList() {
        studioListModel.clear();
        for (StudioArtifact item : currentNotebook().studioItems) {
            studioListModel.addElement(item);
        }
    }

    private JPanel buildFlashcardsStudioSubPanel() {
        JPanel p = new JPanel(new BorderLayout(6, 6));
        p.setBorder(BorderFactory.createTitledBorder("F05: Flashcard Deck Viewer"));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.add(flashcardCounterLabel, BorderLayout.CENTER);

        JButton maximizeBtn = new JButton("⤢ Maximize");
        maximizeBtn.setToolTipText("Open in full-screen distraction-free Flashcard Focus View");
        maximizeBtn.addActionListener(e -> {
            NotebookState nb = currentNotebook();
            if (nb.activeDeck == null || nb.activeDeck.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Generate or select a Flashcard set first.");
                return;
            }
            JFrame parentFrame = (JFrame) SwingUtilities.getWindowAncestor(this);
            MaximizedStudyDialog.showFlashcardsModal(
                    parentFrame, nb.activeDeckTitle, nb.activeDeckSubtitle,
                    activeSubjectId, nb.activeDeck, () -> {
                        onStateChanged.run();
                        updateFlashcardView();
                    }
            );
        });
        topRow.add(maximizeBtn, BorderLayout.EAST);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 2));
        JButton prevBtn = new JButton("⬅ Prev");
        JButton flipBtn = new JButton("Flip Card");
        JButton nextBtn = new JButton("Next ➡");

        prevBtn.addActionListener(e -> {
            NotebookState nb = currentNotebook();
            if (!nb.activeDeck.isEmpty()) {
                nb.cardIndex = (nb.cardIndex - 1 + nb.activeDeck.size()) % nb.activeDeck.size();
                nb.showDefinition = false;
                updateFlashcardView();
            }
        });

        flipBtn.addActionListener(e -> {
            NotebookState nb = currentNotebook();
            if (!nb.activeDeck.isEmpty()) {
                nb.showDefinition = !nb.showDefinition;
                updateFlashcardView();
            }
        });

        nextBtn.addActionListener(e -> {
            NotebookState nb = currentNotebook();
            if (!nb.activeDeck.isEmpty()) {
                nb.cardIndex = (nb.cardIndex + 1) % nb.activeDeck.size();
                nb.showDefinition = false;
                updateFlashcardView();
            }
        });

        controls.add(prevBtn);
        controls.add(flipBtn);
        controls.add(nextBtn);

        flashcardTextArea.setEditable(false);
        flashcardTextArea.setLineWrap(true);
        flashcardTextArea.setWrapStyleWord(true);
        flashcardTextArea.setFont(new Font("SansSerif", Font.BOLD, 14));
        flashcardTextArea.setBorder(new EmptyBorder(12, 12, 12, 12));

        p.add(topRow, BorderLayout.NORTH);
        p.add(new JScrollPane(flashcardTextArea), BorderLayout.CENTER);
        p.add(controls, BorderLayout.SOUTH);
        return p;
    }

    private void updateFlashcardView() {
        NotebookState nb = currentNotebook();
        if (nb.activeDeck == null || nb.activeDeck.isEmpty()) {
            flashcardCounterLabel.setText("No flashcard deck selected");
            flashcardTextArea.setText("Click '🗂 + Flashcards (F05)' above to generate a custom flashcard set.");
            return;
        }
        if (nb.cardIndex >= nb.activeDeck.size()) nb.cardIndex = 0;
        Flashcard c = nb.activeDeck.get(nb.cardIndex);
        flashcardCounterLabel.setText(String.format("%s — %d/%d (%s)",
                nb.activeDeckTitle, nb.cardIndex + 1, nb.activeDeck.size(),
                nb.showDefinition ? "DEF" : "TERM"));
        flashcardTextArea.setText(nb.showDefinition
                ? "DEFINITION:\n\n" + c.getDefinition()
                : "TERM:\n\n" + c.getTerm() + "\n\n(Click 'Flip Card' or '⤢ Maximize')");
    }

    private JPanel buildNoteEditorSubPanel() {
        JPanel p = new JPanel(new BorderLayout(6, 6));
        p.setBorder(BorderFactory.createTitledBorder("Pinned Study Note Viewer"));

        JPanel top = new JPanel(new BorderLayout(6, 0));
        noteTitleField.setEditable(false);
        JButton saveNoteBtn = new JButton("Save Edits");
        saveNoteBtn.addActionListener(e -> {
            if (currentlyEditingNote != null) {
                currentlyEditingNote.noteContent = noteEditorArea.getText();
                onStateChanged.run();
                JOptionPane.showMessageDialog(this, "Note saved to Studio Library!");
            }
        });
        top.add(noteTitleField, BorderLayout.CENTER);
        top.add(saveNoteBtn, BorderLayout.EAST);

        noteEditorArea.setLineWrap(true);
        noteEditorArea.setWrapStyleWord(true);
        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(noteEditorArea), BorderLayout.CENTER);
        return p;
    }

    private JPanel buildCodingStudioSubPanel() {
        JPanel p = new JPanel(new BorderLayout(6, 6));
        p.setBorder(BorderFactory.createTitledBorder("F07 & F08: Coding Practice & AI Grader"));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        top.add(codingTopicField);
        JButton genBtn = new JButton("F07: Problem");
        JButton gradeBtn = new JButton("F08: Grade");
        top.add(genBtn);
        top.add(gradeBtn);

        genBtn.addActionListener(e -> {
            String topic = codingTopicField.getText().trim();
            NotebookState nb = currentNotebook();
            genBtn.setEnabled(false);
            setBusyState(true, "Synthesizing coding problem for '" + topic + "'...");
            new SwingWorker<CodingProblem, Void>() {
                @Override
                protected CodingProblem doInBackground() throws Exception {
                    return facade.generateCodingProblem(topic);
                }

                @Override
                protected void done() {
                    genBtn.setEnabled(true);
                    setBusyState(false, "");
                    try {
                        nb.latestProblem = get();
                        problemStatementArea.setText(nb.latestProblem.getProblemTitle() + "\n" + nb.latestProblem.getDescription());
                        codeEditorArea.setText(nb.latestProblem.getStarterCode());
                        onStateChanged.run();
                    } catch (Exception ex) {
                        Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                        JOptionPane.showMessageDialog(StudyWorkspacePanel.this, c.getMessage(), "F07 Refusal / Alert", JOptionPane.WARNING_MESSAGE);
                    }
                }
            }.execute();
        });

        gradeBtn.addActionListener(e -> {
            String solution = codeEditorArea.getText();
            NotebookState nb = currentNotebook();
            gradeBtn.setEnabled(false);
            setBusyState(true, "Grading Java solution with AI...");
            new SwingWorker<GradeEvaluation, Void>() {
                @Override
                protected GradeEvaluation doInBackground() throws Exception {
                    if (nb.latestProblem == null) {
                        nb.latestProblem = facade.generateCodingProblem(codingTopicField.getText().trim());
                    }
                    return facade.gradeSubmission(nb.latestProblem, solution);
                }

                @Override
                protected void done() {
                    gradeBtn.setEnabled(true);
                    setBusyState(false, "");
                    try {
                        GradeEvaluation eval = get();
                        gradeFeedbackArea.setText("Score: " + eval.getScore() + "/100\n" + eval.getFeedback() + "\nHints: " + eval.getHints());
                    } catch (Exception ex) {
                        Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                        JOptionPane.showMessageDialog(StudyWorkspacePanel.this, c.getMessage(), "F08 Guard", JOptionPane.WARNING_MESSAGE);
                    }
                }
            }.execute();
        });

        problemStatementArea.setEditable(false);
        problemStatementArea.setLineWrap(true);
        problemStatementArea.setWrapStyleWord(true);
        codeEditorArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        gradeFeedbackArea.setEditable(false);
        gradeFeedbackArea.setLineWrap(true);
        gradeFeedbackArea.setWrapStyleWord(true);

        JPanel stack = new JPanel(new GridLayout(3, 1, 4, 4));
        stack.add(new JScrollPane(problemStatementArea));
        stack.add(new JScrollPane(codeEditorArea));
        stack.add(new JScrollPane(gradeFeedbackArea));

        p.add(top, BorderLayout.NORTH);
        p.add(stack, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildSessionStudioSubPanel() {
        JPanel p = new JPanel(new BorderLayout(6, 6));
        p.setBorder(BorderFactory.createTitledBorder("F09 & F10: Guided Quiz Session & Analytics"));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton submitBtn = new JButton("Submit");
        JButton undoBtn = new JButton("Undo (F09)");
        JButton maximizeQuizBtn = new JButton("⤢ Maximize Quiz");
        maximizeQuizBtn.setToolTipText("Open in full-screen Quiz Focus View");

        top.add(submitBtn);
        top.add(undoBtn);
        top.add(maximizeQuizBtn);
        top.add(sessionTimerLabel);

        maximizeQuizBtn.addActionListener(e -> {
            NotebookState nb = currentNotebook();
            if (nb.activeQuiz == null) {
                JOptionPane.showMessageDialog(this, "Click '📝 + Quiz (F06)' above or select a saved Quiz in the Studio Library first.");
                return;
            }
            JFrame parentFrame = (JFrame) SwingUtilities.getWindowAncestor(this);
            MaximizedStudyDialog.showQuizModal(
                    parentFrame, nb.activeQuizTitle, nb.activeQuizSubtitle,
                    nb.activeQuiz, facade, () -> {
                        onStateChanged.run();
                        refreshSessionView();
                        refreshAnalyticsDisplay();
                    }
            );
        });

        submitBtn.addActionListener(e -> {
            StudySession s = facade.getActiveStudySession();
            if (s == null || s.isCompleted()) {
                JOptionPane.showMessageDialog(this, "Click '📝 + Quiz (F06)' above or click any saved Quiz in the Studio Library to start!");
                return;
            }
            int sel = 0;
            for (int i = 0; i < optionButtons.length; i++) {
                if (optionButtons[i].isSelected()) sel = i;
            }
            facade.submitQuizAnswer(sel);
            refreshSessionView();
            refreshAnalyticsDisplay();
        });

        undoBtn.addActionListener(e -> {
            if (facade.undoLastSessionAnswer()) {
                refreshSessionView();
            } else {
                JOptionPane.showMessageDialog(this, "Command stack is empty — nothing to undo.");
            }
        });

        JPanel qPanel = new JPanel(new BorderLayout(4, 4));
        activeQuestionArea.setEditable(false);
        activeQuestionArea.setLineWrap(true);
        activeQuestionArea.setWrapStyleWord(true);
        activeQuestionArea.setText("Click '📝 + Quiz (F06)' above to generate a quiz, or click '⤢ Maximize Quiz'.");
        qPanel.add(new JScrollPane(activeQuestionArea), BorderLayout.CENTER);

        JPanel radioGrid = new JPanel(new GridLayout(4, 1, 2, 2));
        for (int i = 0; i < 4; i++) {
            optionButtons[i] = new JRadioButton("Option " + (char) ('A' + i));
            if (i == 0) optionButtons[i].setSelected(true);
            optionGroup.add(optionButtons[i]);
            radioGrid.add(optionButtons[i]);
        }
        qPanel.add(radioGrid, BorderLayout.SOUTH);

        JPanel statsPanel = new JPanel(new GridLayout(2, 1, 4, 4));
        analyticsTextArea.setEditable(false);
        statsPanel.add(new JScrollPane(analyticsTextArea));
        statsPanel.add(chartPanel);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, qPanel, statsPanel);
        split.setDividerLocation(180);

        p.add(top, BorderLayout.NORTH);
        p.add(split, BorderLayout.CENTER);
        p.add(sessionProgressLabel, BorderLayout.SOUTH);
        return p;
    }

    public void showEngineConfigDialog() {
        EngineConfig current = facade.getEngineConfig();
        JTextField endpointField = new JTextField(current.getOllamaEndpoint(), 24);
        JTextField localModelField = new JTextField(current.getOllamaModel(), 18);
        JPasswordField apiKeyField = new JPasswordField(current.getOpenAiApiKey(), 18);
        JTextField cloudModelField = new JTextField(current.getOpenAiModel(), 14);
        JComboBox<String> detectedModelsBox = new JComboBox<>(new String[]{"Click 'Detect Local Models' ->"});

        JButton detectBtn = new JButton("Detect Installed LM Studio / Ollama Models");
        detectBtn.addActionListener(e -> {
            List<String> found = LocalOllamaClient.detectInstalledLocalModels();
            detectedModelsBox.removeAllItems();
            if (found.isEmpty()) {
                detectedModelsBox.addItem("No server detected on :1234 or :11434");
            } else {
                for (String m : found) detectedModelsBox.addItem(m);
                localModelField.setText(found.get(0));
            }
        });

        detectedModelsBox.addActionListener(e -> {
            Object sel = detectedModelsBox.getSelectedItem();
            if (sel != null && !sel.toString().startsWith("No server") && !sel.toString().startsWith("Click ")) {
                localModelField.setText(sel.toString());
            }
        });

        JButton lmStudioPresetBtn = new JButton("LM Studio Preset (:1234)");
        lmStudioPresetBtn.addActionListener(e -> endpointField.setText("http://127.0.0.1:1234/v1/chat/completions"));

        JButton ollamaPresetBtn = new JButton("Ollama Preset (:11434)");
        ollamaPresetBtn.addActionListener(e -> endpointField.setText("http://127.0.0.1:11434/api/generate"));

        JPanel panel = new JPanel(new GridLayout(0, 2, 8, 8));
        panel.add(new JLabel("Local Endpoint:"));
        panel.add(endpointField);
        panel.add(lmStudioPresetBtn);
        panel.add(ollamaPresetBtn);
        panel.add(detectBtn);
        panel.add(detectedModelsBox);
        panel.add(new JLabel("Local Model ID:"));
        panel.add(localModelField);
        panel.add(new JLabel("OpenAI Cloud API Key:"));
        panel.add(apiKeyField);
        panel.add(new JLabel("OpenAI Cloud Model:"));
        panel.add(cloudModelField);

        int res = JOptionPane.showConfirmDialog(this, panel, "F01: AI Engine Configuration",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res == JOptionPane.OK_OPTION) {
            EngineConfig updated = new EngineConfig(
                    endpointField.getText().trim(),
                    localModelField.getText().trim(),
                    new String(apiKeyField.getPassword()).trim(),
                    cloudModelField.getText().trim(),
                    facade.isWebSearchPermitted()
            );
            if (!facade.setEngineMode(localMode, updated)) {
                JOptionPane.showMessageDialog(this, "Invalid endpoint or API key — reverted to previous setting.",
                        "F01 Error", JOptionPane.ERROR_MESSAGE);
            } else {
                updateEngineStatusLabel();
            }
        }
    }

    private void updateEngineStatusLabel() {
        EngineConfig cfg = facade.getEngineConfig();
        String desc = localMode
                ? "Local AI (" + cfg.getOllamaModel() + ")"
                : "Cloud OpenAI (" + cfg.getOpenAiModel() + ")";
        engineModeBtn.setText(localMode ? "F01: Local AI Mode" : "F01: Cloud OpenAI Mode");
        statusBanner.setText(" Active Engine: " + desc);
    }

    private void startPomodoroTimer() {
        remainingSeconds = 25 * 60;
        if (pomodoroTimer != null) pomodoroTimer.stop();
        pomodoroTimer = new Timer(1000, e -> {
            if (remainingSeconds > 0) {
                remainingSeconds--;
                sessionTimerLabel.setText(String.format("⏱ %02d:%02d", remainingSeconds / 60, remainingSeconds % 60));
            }
        });
        pomodoroTimer.start();
    }

    private void refreshSessionView() {
        StudySession s = facade.getActiveStudySession();
        if (s == null) return;
        sessionProgressLabel.setText(String.format("Question %d/%d | Score: %d | Done: %s",
                s.getCurrentQuestionIndex(), s.getTotalQuestions(), s.getCurrentScore(), s.isCompleted()));

        if (s.isCompleted()) {
            if (pomodoroTimer != null) pomodoroTimer.stop();
            activeQuestionArea.setText("🎉 Session Complete! Score: " + s.getCurrentScore() + "/" + s.getTotalQuestions()
                    + "\n(Click 'Undo (F09)' to revert last answer, or select another Quiz in Studio Library)");
            return;
        }
        QuizQuestion q = s.getCurrentQuestion();
        if (q != null) {
            activeQuestionArea.setText("Q" + (s.getCurrentQuestionIndex() + 1) + ": " + q.getPrompt());
            List<String> opts = q.getOptions();
            for (int i = 0; i < optionButtons.length; i++) {
                optionButtons[i].setText(i < opts.size() ? "(" + (char) ('A' + i) + ") " + opts.get(i) : "-");
            }
        }
    }

    private void refreshAnalyticsDisplay() {
        String summary = facade.viewAnalytics();
        analyticsTextArea.setText(summary);
        chartPanel.updateFromSummaryText(summary);
    }

    private class MasteryChartPanel extends JPanel {
        private double masteryPercent = -1;

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
            int w = getWidth(), h = getHeight();

            if (masteryPercent < 0) {
                g2.drawString("No session data yet.", 14, h / 2);
                return;
            }
            int barW = Math.max(40, w - 30), barH = 24, barY = (h / 2) - 8;
            g2.setColor(new Color(148, 163, 184, 60));
            g2.fill(new RoundRectangle2D.Double(12, barY, barW, barH, 10, 10));

            int fillW = (int) ((masteryPercent / 100.0) * barW);
            g2.setColor(new Color(16, 185, 129));
            g2.fill(new RoundRectangle2D.Double(12, barY, Math.max(12, fillW), barH, 10, 10));

            g2.setColor(Color.WHITE);
            g2.drawString(String.format("%.1f%% Mastery", masteryPercent), 20, barY + 17);
        }
    }
}