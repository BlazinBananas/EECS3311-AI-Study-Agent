package ai.studyagent.ui;

import ai.studyagent.controller.StudyAgentFacade;
import ai.studyagent.domain.DocumentChunk;
import ai.studyagent.session.StudySession;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

public class MainGUI extends JFrame {
    private static final String WORKSPACE_FILE = "studyagent-workspace.json";

    private static class PersistedWorkspace {
        List<CourseDashboardPanel.NotebookInfo> notebooks;
        Map<String, StudyWorkspacePanel.NotebookState> notebookStates;
        Map<String, List<DocumentChunk>> ingestedChunks;
        List<List<String>> scheduleGrid;
    }

    private final StudyAgentFacade facade;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private boolean darkMode = true;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentArea = new JPanel(cardLayout);
    private final JLabel statusBanner = new JLabel(" Active Engine: Local AI (qwen3-8b) | Ready");
    private final JLabel importedDocsLabel = new JLabel("Active Notebook: 0 sources ");

    private CourseDashboardPanel courseDashboardPanel;
    private StudyWorkspacePanel studyWorkspacePanel;
    private ScheduleCalendarPanel scheduleCalendarPanel;
    private PomodoroPanel pomodoroPanel;

    public MainGUI(StudyAgentFacade facade) {
        super("StudyAgent Workbench — EECS 3311 AI Study & Learning Agent");
        this.facade = facade;

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleWindowClose();
            }
        });

        setLayout(new BorderLayout());

        this.studyWorkspacePanel = new StudyWorkspacePanel(
                facade,
                () -> {
                    if (courseDashboardPanel != null) {
                        courseDashboardPanel.refreshGrid("");
                    }
                    cardLayout.show(contentArea, "COURSES");
                },
                statusBanner,
                importedDocsLabel);
        this.courseDashboardPanel = new CourseDashboardPanel(facade, (code, title) -> {
            studyWorkspacePanel.setActiveCourse(code, title);
            cardLayout.show(contentArea, "WORKSPACE");
        });
        this.scheduleCalendarPanel = new ScheduleCalendarPanel();
        this.pomodoroPanel = new PomodoroPanel();

        // Load saved workspace from disk before attaching change listeners
        loadWorkspaceFromDisk();

        Runnable autoSaveHook = this::saveWorkspaceToDisk;
        this.courseDashboardPanel.setOnStateChanged(autoSaveHook);
        this.studyWorkspacePanel.setOnStateChanged(autoSaveHook);
        this.scheduleCalendarPanel.setOnStateChanged(autoSaveHook);

        contentArea.add(courseDashboardPanel, "COURSES");
        contentArea.add(studyWorkspacePanel, "WORKSPACE");
        contentArea.add(scheduleCalendarPanel, "SCHEDULE");
        contentArea.add(pomodoroPanel, "POMODORO");
        cardLayout.show(contentArea, "COURSES");

        add(buildNavigationHeader(), BorderLayout.NORTH);
        add(contentArea, BorderLayout.CENTER);
        add(buildFooterBar(), BorderLayout.SOUTH);

        setPreferredSize(new Dimension(1240, 790));
        pack();
        setLocationRelativeTo(null);
    }

    private synchronized void saveWorkspaceToDisk() {
        try {
            studyWorkspacePanel.getNotebookMap().keySet().removeIf(subjectId -> courseDashboardPanel.getNotebooks()
                    .stream().noneMatch(n -> n.id().equalsIgnoreCase(subjectId)));
            PersistedWorkspace dto = new PersistedWorkspace();
            dto.notebooks = courseDashboardPanel.getNotebooks();
            dto.notebookStates = studyWorkspacePanel.getNotebookMap();
            dto.ingestedChunks = facade.getAllIngestedChunks();
            dto.scheduleGrid = scheduleCalendarPanel.getScheduleGrid();
            Files.writeString(new File(WORKSPACE_FILE).toPath(), gson.toJson(dto));
        } catch (Exception ignored) {
        }
    }

    private void loadWorkspaceFromDisk() {
        File file = new File(WORKSPACE_FILE);
        if (!file.exists())
            return;
        try {
            String raw = Files.readString(file.toPath());
            PersistedWorkspace dto = gson.fromJson(raw, PersistedWorkspace.class);
            if (dto == null)
                return;
            if (dto.ingestedChunks != null) {
                for (Map.Entry<String, List<DocumentChunk>> entry : dto.ingestedChunks.entrySet()) {
                    facade.restoreIngestedChunks(entry.getKey(), entry.getValue());
                }
            }
            if (dto.notebooks != null) {
                courseDashboardPanel.setNotebooks(dto.notebooks);
            }
            if (dto.notebookStates != null) {
                studyWorkspacePanel.setNotebookMap(dto.notebookStates);
            }
            if (dto.scheduleGrid != null) {
                scheduleCalendarPanel.setScheduleGrid(dto.scheduleGrid);
            }
        } catch (Exception ignored) {
        }
    }

    private JPanel buildNavigationHeader() {
        JPanel nav = new JPanel(new BorderLayout(12, 0));
        nav.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Component.borderColor")),
                new EmptyBorder(10, 22, 10, 22)));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        JLabel brand = new JLabel("🎓 StudyAgent Workbench");
        brand.setFont(new Font("SansSerif", Font.BOLD, 18));
        left.add(brand);

        JButton coursesBtn = new JButton("My notebooks");
        JButton workspaceBtn = new JButton("Active Notebook Studio");
        JButton scheduleBtn = new JButton("Study Schedule");
        JButton pomodoroBtn = new JButton("⏱ Pomodoro");

        coursesBtn.addActionListener(e -> {
            courseDashboardPanel.refreshGrid("");
            cardLayout.show(contentArea, "COURSES");
        });
        workspaceBtn.addActionListener(e -> cardLayout.show(contentArea, "WORKSPACE"));
        scheduleBtn.addActionListener(e -> cardLayout.show(contentArea, "SCHEDULE"));
        pomodoroBtn.addActionListener(e -> cardLayout.show(contentArea, "POMODORO"));

        left.add(coursesBtn);
        left.add(workspaceBtn);
        left.add(scheduleBtn);
        left.add(pomodoroBtn);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton themeBtn = new JButton("☀ Light Mode");
        themeBtn.addActionListener(e -> {
            darkMode = !darkMode;
            applyTheme(darkMode);
            themeBtn.setText(darkMode ? "☀ Light Mode" : "🌙 Dark Mode");
            studyWorkspacePanel.refreshTheme();
        });
        right.add(themeBtn);

        nav.add(left, BorderLayout.WEST);
        nav.add(right, BorderLayout.EAST);
        return nav;
    }

    private JPanel buildFooterBar() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(new EmptyBorder(6, 18, 6, 18));
        footer.add(statusBanner, BorderLayout.WEST);
        footer.add(importedDocsLabel, BorderLayout.EAST);
        return footer;
    }

    private void handleWindowClose() {
        StudySession active = facade.getActiveStudySession();
        if (active != null && !active.isCompleted() && active.getCurrentQuestionIndex() < active.getTotalQuestions()) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Session in progress. Save progress before exiting?",
                    "F09 Guided Session Warning",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (choice == JOptionPane.CANCEL_OPTION)
                return;
        }
        saveWorkspaceToDisk();
        dispose();
        System.exit(0);
    }

    private static final String[] CUSTOM_THEME_KEYS = {
            "Panel.background", "OptionPane.background", "RootPane.background",
            "SplitPane.background", "ScrollPane.background", "Viewport.background",
            "TabbedPane.background", "TabbedPane.selectedBackground",
            "TextArea.background", "TextArea.inactiveBackground", "TextArea.foreground",
            "EditorPane.background", "EditorPane.inactiveBackground", "EditorPane.foreground",
            "TextField.background", "TextField.inactiveBackground", "TextField.foreground",
            "List.background", "List.foreground", "Table.background", "Table.foreground",
            "ComboBox.background", "ComboBox.foreground", "Button.background",
            "Button.foreground", "Label.foreground", "CheckBox.foreground",
            "TitledBorder.titleColor", "Component.borderColor", "TextComponent.border"
    };

    private static void applyTheme(boolean dark) {
        try {
            for (String key : CUSTOM_THEME_KEYS) {
                UIManager.put(key, null);
            }

            if (dark) {
                FlatDarkLaf.setup();
                javax.swing.plaf.ColorUIResource deepBg = new javax.swing.plaf.ColorUIResource(18, 20, 26);
                javax.swing.plaf.ColorUIResource surfaceBg = new javax.swing.plaf.ColorUIResource(26, 29, 38);
                javax.swing.plaf.ColorUIResource buttonBg = new javax.swing.plaf.ColorUIResource(36, 41, 54);
                javax.swing.plaf.ColorUIResource borderCol = new javax.swing.plaf.ColorUIResource(42, 48, 60);
                javax.swing.plaf.ColorUIResource lightText = new javax.swing.plaf.ColorUIResource(230, 235, 245);

                UIManager.put("Panel.background", deepBg);
                UIManager.put("OptionPane.background", deepBg);
                UIManager.put("RootPane.background", deepBg);
                UIManager.put("SplitPane.background", deepBg);
                UIManager.put("ScrollPane.background", surfaceBg);
                UIManager.put("Viewport.background", surfaceBg);
                UIManager.put("TabbedPane.background", deepBg);
                UIManager.put("TabbedPane.selectedBackground", surfaceBg);

                // Set BOTH active and inactive (read-only) backgrounds so no gray boxes appear!
                UIManager.put("TextArea.background", surfaceBg);
                UIManager.put("TextArea.inactiveBackground", surfaceBg);
                UIManager.put("TextArea.foreground", lightText);
                UIManager.put("EditorPane.background", surfaceBg);
                UIManager.put("EditorPane.inactiveBackground", surfaceBg);
                UIManager.put("EditorPane.foreground", lightText);
                UIManager.put("TextField.background", surfaceBg);
                UIManager.put("TextField.inactiveBackground", surfaceBg);
                UIManager.put("TextField.foreground", lightText);

                UIManager.put("List.background", surfaceBg);
                UIManager.put("List.foreground", lightText);
                UIManager.put("Table.background", surfaceBg);
                UIManager.put("Table.foreground", lightText);
                UIManager.put("ComboBox.background", surfaceBg);
                UIManager.put("ComboBox.foreground", lightText);

                UIManager.put("Button.background", buttonBg);
                UIManager.put("Button.foreground", lightText);
                UIManager.put("Label.foreground", lightText);
                UIManager.put("CheckBox.foreground", lightText);
                UIManager.put("TitledBorder.titleColor", lightText);
                UIManager.put("Component.borderColor", borderCol);
            } else {
                FlatLightLaf.setup();
            }
            FlatLaf.updateUI();
        } catch (Exception ignored) {
        }
    }

    public static void main(String[] args) {
        System.setProperty("flatlaf.uiScale", "1.25");
        UIManager.put("Button.arc", 14);
        UIManager.put("Component.arc", 12);
        UIManager.put("TextComponent.arc", 12);
        applyTheme(true);
        SwingUtilities.invokeLater(() -> new MainGUI(new StudyAgentFacade()).setVisible(true));
    }
}