package ai.studyagent.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;

public class PomodoroPanel extends JPanel {
    private int totalDurationSeconds = 25 * 60;
    private int remainingSeconds = 25 * 60;
    private int completedPomodoros = 0;
    private String currentModeTitle = "Focus Session (25m)";

    private final JLabel mainClockLabel = new JLabel("25:00", SwingConstants.CENTER);
    private final JLabel modeBadgeLabel = new JLabel("🎯 Focus Session (25m)", SwingConstants.CENTER);
    private final JLabel completedCountLabel = new JLabel("Completed Focus Cycles Today: 0", SwingConstants.CENTER);
    private final JProgressBar cycleProgressBar = new JProgressBar(0, 100);
    private final JButton startPauseBtn = new JButton("▶ Start Timer");

    // Synchronized Pop-out Mini Widget components
    private JDialog miniWidgetDialog;
    private final JLabel miniClockLabel = new JLabel("25:00", SwingConstants.CENTER);
    private final JLabel miniModeLabel = new JLabel("🎯 Focus", SwingConstants.CENTER);
    private final JButton miniStartPauseBtn = new JButton("▶");

    private final Timer sharedTimer;

    public PomodoroPanel() {
        setLayout(new BorderLayout(16, 16));
        setBorder(new EmptyBorder(28, 48, 28, 48));

        sharedTimer = new Timer(1000, e -> tick());

        add(buildHeaderBar(), BorderLayout.NORTH);
        add(buildCenterClockCard(), BorderLayout.CENTER);
        add(buildBottomPresetBar(), BorderLayout.SOUTH);

        syncAllDisplays();
    }

    private JPanel buildHeaderBar() {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        JLabel title = new JLabel("⏱ Pomodoro Study Timer & Focus Tracker");
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        header.add(title, BorderLayout.WEST);

        JButton popoutBtn = new JButton("📌 Pop Out Mini Widget (Always on Top)");
        popoutBtn.setBackground(new Color(37, 99, 235));
        popoutBtn.setForeground(Color.WHITE);
        popoutBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        popoutBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        popoutBtn.addActionListener(e -> toggleMiniWidget());
        header.add(popoutBtn, BorderLayout.EAST);

        return header;
    }

    private JPanel buildCenterClockCard() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1, true),
                new EmptyBorder(30, 40, 30, 40)
        ));

        JPanel stack = new JPanel(new GridLayout(5, 1, 0, 12));
        stack.setPreferredSize(new Dimension(480, 340));

        modeBadgeLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        mainClockLabel.setFont(new Font("Monospaced", Font.BOLD, 68));
        mainClockLabel.setForeground(new Color(56, 189, 248));

        cycleProgressBar.setStringPainted(true);
        cycleProgressBar.setPreferredSize(new Dimension(420, 24));

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
        startPauseBtn.setBackground(new Color(16, 185, 129));
        startPauseBtn.setForeground(Color.WHITE);
        startPauseBtn.setFont(new Font("SansSerif", Font.BOLD, 15));
        startPauseBtn.setPreferredSize(new Dimension(160, 42));
        startPauseBtn.addActionListener(e -> toggleStartPause());

        JButton resetBtn = new JButton("↻ Reset");
        resetBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        resetBtn.setPreferredSize(new Dimension(120, 42));
        resetBtn.addActionListener(e -> resetTimer(totalDurationSeconds, currentModeTitle));

        JButton plusOneMinBtn = new JButton("+1 Min");
        plusOneMinBtn.setPreferredSize(new Dimension(100, 42));
        plusOneMinBtn.addActionListener(e -> {
            remainingSeconds += 60;
            totalDurationSeconds = Math.max(totalDurationSeconds, remainingSeconds);
            syncAllDisplays();
        });

        actionRow.add(startPauseBtn);
        actionRow.add(resetBtn);
        actionRow.add(plusOneMinBtn);

        completedCountLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));

        stack.add(modeBadgeLabel);
        stack.add(mainClockLabel);
        stack.add(cycleProgressBar);
        stack.add(actionRow);
        stack.add(completedCountLabel);

        GridBagConstraints gbc = new GridBagConstraints();
        card.add(stack, gbc);
        return card;
    }

    private JPanel buildBottomPresetBar() {
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 6));
        bottom.setBorder(BorderFactory.createTitledBorder("Timer Presets & Custom Duration"));

        JButton focus25Btn = new JButton("🎯 Focus (25:00)");
        JButton deep50Btn = new JButton("🔥 Deep Work (50:00)");
        JButton shortBreakBtn = new JButton("☕ Short Break (05:00)");
        JButton longBreakBtn = new JButton("🌿 Long Break (15:00)");

        focus25Btn.addActionListener(e -> resetTimer(25 * 60, "🎯 Focus Session (25m)"));
        deep50Btn.addActionListener(e -> resetTimer(50 * 60, "🔥 Deep Work Session (50m)"));
        shortBreakBtn.addActionListener(e -> resetTimer(5 * 60, "☕ Short Break (5m)"));
        longBreakBtn.addActionListener(e -> resetTimer(15 * 60, "🌿 Long Break (15m)"));

        JSpinner customMinutesSpinner = new JSpinner(new SpinnerNumberModel(30, 1, 180, 5));
        JButton applyCustomBtn = new JButton("Set Custom Minutes");
        applyCustomBtn.addActionListener(e -> {
            int mins = (Integer) customMinutesSpinner.getValue();
            resetTimer(mins * 60, "⏱ Custom Session (" + mins + "m)");
        });

        bottom.add(focus25Btn);
        bottom.add(deep50Btn);
        bottom.add(shortBreakBtn);
        bottom.add(longBreakBtn);
        bottom.add(new JLabel(" | Custom (min):"));
        bottom.add(customMinutesSpinner);
        bottom.add(applyCustomBtn);
        return bottom;
    }

    private void toggleStartPause() {
        if (sharedTimer.isRunning()) {
            sharedTimer.stop();
        } else {
            sharedTimer.start();
        }
        syncAllDisplays();
    }

    private void resetTimer(int seconds, String modeTitle) {
        sharedTimer.stop();
        this.totalDurationSeconds = Math.max(1, seconds);
        this.remainingSeconds = this.totalDurationSeconds;
        this.currentModeTitle = modeTitle;
        syncAllDisplays();
    }

    private void tick() {
        if (remainingSeconds > 0) {
            remainingSeconds--;
            syncAllDisplays();
        } else {
            sharedTimer.stop();
            if (currentModeTitle.contains("Focus") || currentModeTitle.contains("Deep")) {
                completedPomodoros++;
            }
            syncAllDisplays();
            JOptionPane.showMessageDialog(this,
                    currentModeTitle + " finished! Great work.",
                    "⏱ Pomodoro Complete", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void syncAllDisplays() {
        String timeStr = String.format("%02d:%02d", remainingSeconds / 60, remainingSeconds % 60);
        mainClockLabel.setText(timeStr);
        miniClockLabel.setText(timeStr);
        modeBadgeLabel.setText(currentModeTitle);
        miniModeLabel.setText(currentModeTitle);
        completedCountLabel.setText("Completed Focus Cycles Today: " + completedPomodoros);

        int elapsed = totalDurationSeconds - remainingSeconds;
        int pct = (int) ((elapsed * 100.0) / Math.max(1, totalDurationSeconds));
        cycleProgressBar.setValue(pct);
        cycleProgressBar.setString(pct + "% elapsed");

        boolean running = sharedTimer.isRunning();
        startPauseBtn.setText(running ? "⏸ Pause Timer" : "▶ Start Timer");
        startPauseBtn.setBackground(running ? new Color(217, 119, 6) : new Color(16, 185, 129));
        miniStartPauseBtn.setText(running ? "⏸" : "▶");
    }

    public void toggleMiniWidget() {
        if (miniWidgetDialog != null && miniWidgetDialog.isVisible()) {
            miniWidgetDialog.toFront();
            return;
        }
        JFrame parent = (JFrame) SwingUtilities.getWindowAncestor(this);
        miniWidgetDialog = new JDialog(parent, "⏱ StudyAgent Mini Timer", false);
        miniWidgetDialog.setAlwaysOnTop(true);
        miniWidgetDialog.setSize(280, 145);

        JPanel root = new JPanel(new BorderLayout(6, 6));
        root.setBackground(new Color(15, 23, 42));
        root.setBorder(new EmptyBorder(10, 14, 10, 14));

        miniModeLabel.setForeground(new Color(148, 163, 184));
        miniModeLabel.setFont(new Font("SansSerif", Font.BOLD, 12));

        miniClockLabel.setForeground(new Color(56, 189, 248));
        miniClockLabel.setFont(new Font("Monospaced", Font.BOLD, 34));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        controls.setOpaque(false);

        miniStartPauseBtn.addActionListener(e -> toggleStartPause());
        JButton miniResetBtn = new JButton("↻");
        miniResetBtn.addActionListener(e -> resetTimer(totalDurationSeconds, currentModeTitle));
        JButton mini25Btn = new JButton("25m");
        mini25Btn.addActionListener(e -> resetTimer(25 * 60, "🎯 Focus Session (25m)"));
        JButton mini5Btn = new JButton("5m");
        mini5Btn.addActionListener(e -> resetTimer(5 * 60, "☕ Short Break (5m)"));

        controls.add(miniStartPauseBtn);
        controls.add(miniResetBtn);
        controls.add(mini25Btn);
        controls.add(mini5Btn);

        root.add(miniModeLabel, BorderLayout.NORTH);
        root.add(miniClockLabel, BorderLayout.CENTER);
        root.add(controls, BorderLayout.SOUTH);

        miniWidgetDialog.setContentPane(root);
        miniWidgetDialog.setLocationRelativeTo(parent);
        miniWidgetDialog.setVisible(true);
        syncAllDisplays();
    }
}