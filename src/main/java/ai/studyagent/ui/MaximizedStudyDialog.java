package ai.studyagent.ui;

import ai.studyagent.controller.StudyAgentFacade;
import ai.studyagent.domain.CodingProblem;
import ai.studyagent.domain.Flashcard;
import ai.studyagent.domain.GradeEvaluation;
import ai.studyagent.domain.Quiz;
import ai.studyagent.domain.QuizQuestion;
import ai.studyagent.session.StudySession;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MaximizedStudyDialog {

    // =========================================================================
    // 1. MAXIMIZED FLASHCARD FOCUS VIEW (With Bounded Per-Card Verdict Map)
    // =========================================================================
    public static void showFlashcardsModal(
            JFrame parent,
            String deckTitle,
            String subtitleMeta,
            String subjectId,
            List<Flashcard> deck,
            Runnable onDataChanged
    ) {
        if (deck == null || deck.isEmpty()) return;

        JDialog dialog = new JDialog(parent, "Flashcard Focus Studio — " + deckTitle, true);
        dialog.setSize(1140, 740);
        dialog.setLocationRelativeTo(parent);

        final int[] index = {0};
        final boolean[] showingAnswer = {false};
        // Map<CardIndex, Boolean> guarantees missed + gotIt never exceeds deck.size()!
        final Map<Integer, Boolean> cardVerdictMap = new HashMap<>();
        final int[] elapsedSeconds = {0};

        JLabel stopwatchLabel = new JLabel("");
        stopwatchLabel.setForeground(new Color(148, 163, 184));
        stopwatchLabel.setFont(new Font("Monospaced", Font.BOLD, 13));
        stopwatchLabel.setVisible(false);

        Timer stopwatch = new Timer(1000, e -> {
            elapsedSeconds[0]++;
            stopwatchLabel.setText(String.format("⏱ %02d:%02d", elapsedSeconds[0] / 60, elapsedSeconds[0] % 60));
        });

        JPanel root = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                GradientPaint bgGradient = new GradientPaint(
                        0, 0, new Color(15, 23, 42),
                        w, h, new Color(23, 37, 84)
                );
                g2.setPaint(bgGradient);
                g2.fillRect(0, 0, w, h);
                g2.dispose();
            }
        };
        root.setBorder(new EmptyBorder(18, 28, 24, 28));

        // Top Bar
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);

        JPanel titleBox = new JPanel(new GridLayout(2, 1, 0, 4));
        titleBox.setOpaque(false);
        JLabel titleLbl = new JLabel(deckTitle);
        titleLbl.setForeground(Color.WHITE);
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 18));
        JLabel sourcePill = new JLabel("  " + subtitleMeta + "  ");
        sourcePill.setForeground(new Color(186, 230, 253));
        sourcePill.setFont(new Font("SansSerif", Font.PLAIN, 12));
        sourcePill.setBorder(BorderFactory.createLineBorder(new Color(56, 189, 248, 100), 1, true));
        titleBox.add(titleLbl);
        titleBox.add(sourcePill);

        JLabel shortcutHint = new JLabel("Shortcuts: [Space] Flip Card   •   [← / →] Prev / Next Card", SwingConstants.CENTER);
        shortcutHint.setForeground(new Color(148, 163, 184));
        shortcutHint.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightActions.setOpaque(false);
        rightActions.add(stopwatchLabel);

        JButton closeBtn = new JButton("✕ Close");
        closeBtn.setForeground(Color.WHITE);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> {
            stopwatch.stop();
            dialog.dispose();
        });
        rightActions.add(closeBtn);

        topBar.add(titleBox, BorderLayout.WEST);
        topBar.add(shortcutHint, BorderLayout.CENTER);
        topBar.add(rightActions, BorderLayout.EAST);
        root.add(topBar, BorderLayout.NORTH);

        // Center Card
        JLabel cardCountLabel = new JLabel("1 / " + deck.size());
        cardCountLabel.setForeground(new Color(148, 163, 184));
        cardCountLabel.setFont(new Font("SansSerif", Font.BOLD, 13));

        JLabel cardStatusBadge = new JLabel("Ungraded", SwingConstants.CENTER);
        cardStatusBadge.setForeground(new Color(148, 163, 184));
        cardStatusBadge.setFont(new Font("SansSerif", Font.BOLD, 12));

        JTextArea cardBodyArea = new JTextArea();
        cardBodyArea.setEditable(false);
        cardBodyArea.setLineWrap(true);
        cardBodyArea.setWrapStyleWord(true);
        cardBodyArea.setOpaque(false);
        cardBodyArea.setForeground(Color.WHITE);
        cardBodyArea.setFont(new Font("SansSerif", Font.PLAIN, 22));
        cardBodyArea.setBorder(new EmptyBorder(28, 12, 20, 12));

        JLabel seeAnswerHint = new JLabel("Click card or press Space to flip", SwingConstants.CENTER);
        seeAnswerHint.setForeground(new Color(148, 163, 184));
        seeAnswerHint.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JButton missedBtn = new JButton("✕ Review (0)");
        JButton gotItBtn = new JButton("✓ Mastered (0)");

        Runnable renderCard = () -> {
            if (deck.isEmpty()) return;
            if (index[0] >= deck.size()) index[0] = 0;
            Flashcard c = deck.get(index[0]);
            cardCountLabel.setText("Card " + (index[0] + 1) + " of " + deck.size());

            Boolean verdict = cardVerdictMap.get(index[0]);
            if (verdict == null) {
                cardStatusBadge.setText("Status: Not yet marked");
                cardStatusBadge.setForeground(new Color(148, 163, 184));
            } else if (verdict) {
                cardStatusBadge.setText("Status: ✓ Mastered");
                cardStatusBadge.setForeground(new Color(74, 222, 128));
            } else {
                cardStatusBadge.setText("Status: ✕ Needs Review");
                cardStatusBadge.setForeground(new Color(248, 113, 113));
            }

            if (showingAnswer[0]) {
                cardBodyArea.setForeground(new Color(167, 243, 208));
                cardBodyArea.setFont(new Font("SansSerif", Font.PLAIN, 19));
                cardBodyArea.setText("DEFINITION:\n\n" + c.getDefinition());
                seeAnswerHint.setText("Showing Definition — Click or press Space to view Term");
            } else {
                cardBodyArea.setForeground(Color.WHITE);
                cardBodyArea.setFont(new Font("SansSerif", Font.BOLD, 22));
                cardBodyArea.setText("TERM:\n\n" + c.getTerm());
                seeAnswerHint.setText("Showing Term — Click or press Space to reveal Definition");
            }

            long missedTotal = cardVerdictMap.values().stream().filter(v -> !v).count();
            long gotItTotal = cardVerdictMap.values().stream().filter(v -> v).count();
            missedBtn.setText("✕ Needs Review (" + missedTotal + "/" + deck.size() + ")");
            gotItBtn.setText("✓ Mastered (" + gotItTotal + "/" + deck.size() + ")");
        };

        // ⋮ Options Menu
        JButton cardMenuBtn = new JButton("⋮ Options");
        cardMenuBtn.setForeground(Color.WHITE);
        cardMenuBtn.setContentAreaFilled(false);
        cardMenuBtn.setBorderPainted(false);
        cardMenuBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        cardMenuBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPopupMenu cardPopup = new JPopupMenu();
        JMenuItem restartItem = new JMenuItem("↻  Reset progress & restart deck");
        restartItem.addActionListener(e -> {
            index[0] = 0;
            showingAnswer[0] = false;
            cardVerdictMap.clear();
            renderCard.run();
        });

        JMenuItem shuffleItem = new JMenuItem("🔀  Shuffle cards");
        shuffleItem.addActionListener(e -> {
            Collections.shuffle(deck);
            index[0] = 0;
            showingAnswer[0] = false;
            cardVerdictMap.clear();
            onDataChanged.run();
            renderCard.run();
        });

        JMenuItem addCardItem = new JMenuItem("＋  Add new flashcard");
        addCardItem.addActionListener(e -> {
            JTextField termF = new JTextField(20);
            JTextArea defA = new JTextArea(4, 20);
            defA.setLineWrap(true);
            defA.setWrapStyleWord(true);
            JPanel p = new JPanel(new BorderLayout(6, 6));
            p.add(new JLabel("Term / Question:"), BorderLayout.NORTH);
            p.add(termF, BorderLayout.CENTER);
            JPanel b = new JPanel(new BorderLayout(4, 4));
            b.add(new JLabel("Definition / Answer:"), BorderLayout.NORTH);
            b.add(new JScrollPane(defA), BorderLayout.CENTER);
            p.add(b, BorderLayout.SOUTH);
            if (JOptionPane.showConfirmDialog(dialog, p, "Add Flashcard", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                if (!termF.getText().isBlank()) {
                    deck.add(new Flashcard(UUID.randomUUID().toString(), subjectId, termF.getText().trim(), defA.getText().trim()));
                    index[0] = deck.size() - 1;
                    showingAnswer[0] = false;
                    onDataChanged.run();
                    renderCard.run();
                }
            }
        });

        JMenuItem editCardItem = new JMenuItem("✎  Edit current flashcard");
        editCardItem.addActionListener(e -> {
            if (deck.isEmpty()) return;
            Flashcard current = deck.get(index[0]);
            JTextField termF = new JTextField(current.getTerm(), 20);
            JTextArea defA = new JTextArea(current.getDefinition(), 4, 20);
            defA.setLineWrap(true);
            defA.setWrapStyleWord(true);
            JPanel p = new JPanel(new BorderLayout(6, 6));
            p.add(new JLabel("Term / Question:"), BorderLayout.NORTH);
            p.add(termF, BorderLayout.CENTER);
            JPanel b = new JPanel(new BorderLayout(4, 4));
            b.add(new JLabel("Definition / Answer:"), BorderLayout.NORTH);
            b.add(new JScrollPane(defA), BorderLayout.CENTER);
            p.add(b, BorderLayout.SOUTH);
            if (JOptionPane.showConfirmDialog(dialog, p, "Edit Flashcard", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                current.setTerm(termF.getText().trim());
                current.setDefinition(defA.getText().trim());
                onDataChanged.run();
                renderCard.run();
            }
        });

        JMenuItem deleteCardItem = new JMenuItem("🗑  Delete current flashcard");
        deleteCardItem.addActionListener(e -> {
            if (deck.size() <= 1) {
                JOptionPane.showMessageDialog(dialog, "Cannot delete the last card in the set.");
                return;
            }
            deck.remove(index[0]);
            cardVerdictMap.clear();
            if (index[0] >= deck.size()) index[0] = deck.size() - 1;
            showingAnswer[0] = false;
            onDataChanged.run();
            renderCard.run();
        });

        JMenuItem stopwatchItem = new JMenuItem("⏱  Toggle stopwatch");
        stopwatchItem.addActionListener(e -> {
            if (stopwatch.isRunning()) {
                stopwatch.stop();
                stopwatchLabel.setVisible(false);
            } else {
                elapsedSeconds[0] = 0;
                stopwatchLabel.setText("⏱ 00:00");
                stopwatchLabel.setVisible(true);
                stopwatch.start();
            }
        });

        cardPopup.add(restartItem);
        cardPopup.add(shuffleItem);
        cardPopup.addSeparator();
        cardPopup.add(addCardItem);
        cardPopup.add(editCardItem);
        cardPopup.add(deleteCardItem);
        cardPopup.addSeparator();
        cardPopup.add(stopwatchItem);

        cardMenuBtn.addActionListener(e -> cardPopup.show(cardMenuBtn, 0, cardMenuBtn.getHeight()));

        JPanel centerCard = new JPanel(new BorderLayout(0, 12)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(30, 41, 59));
                g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 28, 28));
                g2.setColor(new Color(71, 85, 105));
                g2.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 28, 28));
                g2.dispose();
            }
        };
        centerCard.setOpaque(false);
        centerCard.setPreferredSize(new Dimension(520, 400));
        centerCard.setBorder(new EmptyBorder(22, 28, 22, 28));
        centerCard.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel cardHeader = new JPanel(new BorderLayout());
        cardHeader.setOpaque(false);
        cardHeader.add(cardCountLabel, BorderLayout.WEST);
        cardHeader.add(cardStatusBadge, BorderLayout.CENTER);
        cardHeader.add(cardMenuBtn, BorderLayout.EAST);

        centerCard.add(cardHeader, BorderLayout.NORTH);
        centerCard.add(cardBodyArea, BorderLayout.CENTER);
        centerCard.add(seeAnswerHint, BorderLayout.SOUTH);

        MouseAdapter flipAdapter = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showingAnswer[0] = !showingAnswer[0];
                renderCard.run();
            }
        };
        centerCard.addMouseListener(flipAdapter);
        cardBodyArea.addMouseListener(flipAdapter);
        seeAnswerHint.addMouseListener(flipAdapter);

        // Bottom Controls
        JPanel bottomNav = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 14));
        bottomNav.setOpaque(false);

        JButton prevBtn = createPillButton("← Prev", new Color(226, 232, 240));
        missedBtn.setForeground(new Color(248, 113, 113));
        stylePillButton(missedBtn);
        gotItBtn.setForeground(new Color(74, 222, 128));
        stylePillButton(gotItBtn);
        JButton nextBtn = createPillButton("Next →", new Color(147, 197, 253));

        Runnable goPrev = () -> {
            index[0] = (index[0] - 1 + deck.size()) % deck.size();
            showingAnswer[0] = false;
            renderCard.run();
        };
        Runnable goNext = () -> {
            index[0] = (index[0] + 1) % deck.size();
            showingAnswer[0] = false;
            renderCard.run();
        };

        Runnable recordVerdictAndAdvance = () -> {
            renderCard.run();
            if (cardVerdictMap.size() == deck.size()) {
                long gotItTotal = cardVerdictMap.values().stream().filter(v -> v).count();
                long missedTotal = deck.size() - gotItTotal;
                int opt = JOptionPane.showConfirmDialog(dialog,
                        String.format("Deck Complete!\n\n✓ Mastered: %d of %d\n✕ Needs Review: %d of %d\n\nWould you like to reset the deck and practice again?",
                                gotItTotal, deck.size(), missedTotal, deck.size()),
                        "Flashcard Set Completed", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
                if (opt == JOptionPane.YES_OPTION) {
                    cardVerdictMap.clear();
                    index[0] = 0;
                    showingAnswer[0] = false;
                    renderCard.run();
                }
            } else if (index[0] < deck.size() - 1) {
                index[0]++;
                showingAnswer[0] = false;
                renderCard.run();
            }
        };

        prevBtn.addActionListener(e -> goPrev.run());
        nextBtn.addActionListener(e -> goNext.run());
        missedBtn.addActionListener(e -> {
            cardVerdictMap.put(index[0], false); // Overwrites verdict for this specific card index!
            recordVerdictAndAdvance.run();
        });
        gotItBtn.addActionListener(e -> {
            cardVerdictMap.put(index[0], true);  // Overwrites verdict for this specific card index!
            recordVerdictAndAdvance.run();
        });

        bottomNav.add(prevBtn);
        bottomNav.add(missedBtn);
        bottomNav.add(gotItBtn);
        bottomNav.add(nextBtn);

        JPanel centerWrapper = new JPanel(new GridBagLayout());
        centerWrapper.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        centerWrapper.add(centerCard, gbc);
        gbc.gridy = 1;
        centerWrapper.add(bottomNav, gbc);

        root.add(centerWrapper, BorderLayout.CENTER);

        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "flipCard");
        root.getActionMap().put("flipCard", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                showingAnswer[0] = !showingAnswer[0];
                renderCard.run();
            }
        });
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "prevCard");
        root.getActionMap().put("prevCard", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) { goPrev.run(); }
        });
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "nextCard");
        root.getActionMap().put("nextCard", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) { goNext.run(); }
        });

        renderCard.run();
        dialog.setContentPane(root);
        dialog.setVisible(true);
    }

    // =========================================================================
    // 2. MAXIMIZED QUIZ FOCUS VIEW
    // =========================================================================
    public static void showQuizModal(
            JFrame parent,
            String quizTitle,
            String subtitleMeta,
            Quiz quiz,
            StudyAgentFacade facade,
            Runnable onSessionUpdated
    ) {
        showQuizModal(parent, quizTitle, subtitleMeta, quiz, facade, onSessionUpdated, 0);
    }
    
    public static void showQuizModal(
            JFrame parent,
            String quizTitle,
            String subtitleMeta,
            Quiz quiz,
            StudyAgentFacade facade,
            Runnable onSessionUpdated,
            int examCountdownSeconds
    ) {
        if (quiz == null || quiz.getQuestions().isEmpty()) return;

        JDialog dialog = new JDialog(parent, "Interactive Quiz & Exam Runner — " + quizTitle, true);
        dialog.setSize(1140, 740);
        dialog.setLocationRelativeTo(parent);

        facade.startStudySession(quiz);

        final int[] timerSeconds = {examCountdownSeconds > 0 ? examCountdownSeconds : 0};
        final boolean isCountdownExam = examCountdownSeconds > 0;

        JLabel stopwatchLabel = new JLabel(isCountdownExam
                ? String.format("⏳ Exam Time Remaining: %02d:%02d", timerSeconds[0] / 60, timerSeconds[0] % 60)
                : "");
        stopwatchLabel.setForeground(isCountdownExam ? new Color(248, 113, 113) : new Color(148, 163, 184));
        stopwatchLabel.setFont(new Font("Monospaced", Font.BOLD, 14));
        stopwatchLabel.setVisible(isCountdownExam);

        Timer stopwatch = new Timer(1000, null);
        stopwatch.addActionListener(e -> {
            if (isCountdownExam) {
                if (timerSeconds[0] > 0) {
                    timerSeconds[0]--;
                    stopwatchLabel.setText(String.format("⏳ Exam Time Remaining: %02d:%02d", timerSeconds[0] / 60, timerSeconds[0] % 60));
                } else {
                    stopwatch.stop();
                    JOptionPane.showMessageDialog(dialog,
                            "⏳ Exam time limit reached! Submitting remaining questions.",
                            "Time Expired", JOptionPane.WARNING_MESSAGE);
                    StudySession s = facade.getActiveStudySession();
                    while (s != null && !s.isCompleted()) {
                        facade.submitQuizAnswer(0);
                    }
                    onSessionUpdated.run();
                    dialog.dispose();
                }
            } else {
                timerSeconds[0]++;
                stopwatchLabel.setText(String.format("⏱ %02d:%02d", timerSeconds[0] / 60, timerSeconds[0] % 60));
            }
        });
        if (isCountdownExam) {
            stopwatch.start();
        }

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(new Color(15, 23, 42));
        root.setBorder(new EmptyBorder(18, 28, 24, 28));

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);

        JPanel titleBox = new JPanel(new GridLayout(2, 1, 0, 4));
        titleBox.setOpaque(false);
        JLabel titleLbl = new JLabel(quizTitle);
        titleLbl.setForeground(Color.WHITE);
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 18));
        JLabel sourcePill = new JLabel("  " + subtitleMeta + "  ");
        sourcePill.setForeground(new Color(186, 230, 253));
        sourcePill.setFont(new Font("SansSerif", Font.PLAIN, 12));
        sourcePill.setBorder(BorderFactory.createLineBorder(new Color(56, 189, 248, 100), 1, true));
        titleBox.add(titleLbl);
        titleBox.add(sourcePill);

        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightActions.setOpaque(false);
        rightActions.add(stopwatchLabel);

        JButton closeBtn = new JButton("✕ Close");
        closeBtn.setForeground(Color.WHITE);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> {
            stopwatch.stop();
            dialog.dispose();
        });
        rightActions.add(closeBtn);

        topBar.add(titleBox, BorderLayout.WEST);
        topBar.add(rightActions, BorderLayout.EAST);
        root.add(topBar, BorderLayout.NORTH);

        JPanel quizCenterPanel = new JPanel(new BorderLayout(0, 16));
        quizCenterPanel.setOpaque(false);
        quizCenterPanel.setPreferredSize(new Dimension(700, 540));

        JLabel qCountLabel = new JLabel("Question 1 of " + quiz.getQuestions().size());
        qCountLabel.setForeground(new Color(148, 163, 184));
        qCountLabel.setFont(new Font("SansSerif", Font.BOLD, 13));

        JButton quizMenuBtn = new JButton("⋮ Options");
        quizMenuBtn.setForeground(Color.WHITE);
        quizMenuBtn.setContentAreaFilled(false);
        quizMenuBtn.setBorderPainted(false);
        quizMenuBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        quizMenuBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel qTopRow = new JPanel(new BorderLayout());
        qTopRow.setOpaque(false);
        qTopRow.add(qCountLabel, BorderLayout.WEST);
        qTopRow.add(quizMenuBtn, BorderLayout.EAST);

        JTextArea promptArea = new JTextArea();
        promptArea.setEditable(false);
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);
        promptArea.setOpaque(false);
        promptArea.setForeground(Color.WHITE);
        promptArea.setFont(new Font("SansSerif", Font.BOLD, 18));

        JPanel headerAndPrompt = new JPanel(new BorderLayout(0, 10));
        headerAndPrompt.setOpaque(false);
        headerAndPrompt.add(qTopRow, BorderLayout.NORTH);
        headerAndPrompt.add(promptArea, BorderLayout.CENTER);
        quizCenterPanel.add(headerAndPrompt, BorderLayout.NORTH);

        JPanel optionsStack = new JPanel(new GridLayout(4, 1, 0, 12));
        optionsStack.setOpaque(false);
        JButton[] optionCards = new JButton[4];
        final int[] selectedOption = {-1};

        JLabel feedbackBanner = new JLabel(" ");
        feedbackBanner.setForeground(new Color(167, 243, 208));
        feedbackBanner.setFont(new Font("SansSerif", Font.BOLD, 13));

       JButton nextBtn = new JButton("Next Question →");

        Runnable renderQuizState = () -> {
            StudySession s = facade.getActiveStudySession();
            if (s == null) return;
            if (s.isCompleted()) {
                qCountLabel.setText("Quiz Completed");
                promptArea.setText(String.format("🎉 Session Completed! Final Score: %d / %d (%.1f%%)\n\n"
                                + "Your F10 Progress Analytics Dashboard has been automatically updated via Observer Pattern.",
                        s.getCurrentScore(), s.getTotalQuestions(),
                        (s.getCurrentScore() * 100.0) / Math.max(1, s.getTotalQuestions())));
                for (JButton b : optionCards) {
                    b.setVisible(false);
                }
                nextBtn.setEnabled(false);
                feedbackBanner.setForeground(new Color(167, 243, 208));
                feedbackBanner.setText("Click '↶ Undo (F09)' below to revert your last answer, or close this window.");
                quizCenterPanel.revalidate();
                quizCenterPanel.repaint();
                return;
            }

            QuizQuestion q = s.getCurrentQuestion();
            if (q == null) return;
            nextBtn.setEnabled(true);
            qCountLabel.setText("Question " + (s.getCurrentQuestionIndex() + 1) + " of " + s.getTotalQuestions()
                    + "   (Current Score: " + s.getCurrentScore() + ")");
            promptArea.setText(q.getPrompt());
            selectedOption[0] = -1;
            feedbackBanner.setText("Select an option below, then click 'Next Question →'.");
            feedbackBanner.setForeground(new Color(148, 163, 184));

            List<String> opts = q.getOptions();
            for (int i = 0; i < 4; i++) {
                if (i < opts.size()) {
                    optionCards[i].setVisible(true);
                    optionCards[i].setBackground(new Color(30, 41, 59));
                    optionCards[i].setForeground(Color.WHITE);
                    optionCards[i].setText("<html><div style='padding:6px;'><b>" + (char) ('A' + i) + ".</b>  " + opts.get(i) + "</div></html>");
                } else {
                    optionCards[i].setVisible(false);
                }
            }
            quizCenterPanel.revalidate();
            quizCenterPanel.repaint();
        };

        for (int i = 0; i < 4; i++) {
            final int optIdx = i;
            JButton cardBtn = new JButton();
            cardBtn.setHorizontalAlignment(SwingConstants.LEFT);
            cardBtn.setFocusPainted(false);
            cardBtn.setBackground(new Color(30, 41, 59));
            cardBtn.setForeground(Color.WHITE);
            cardBtn.setFont(new Font("SansSerif", Font.PLAIN, 15));
            cardBtn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(71, 85, 105), 1, true),
                    new EmptyBorder(10, 14, 10, 14)
            ));
            cardBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            cardBtn.addActionListener(e -> {
                StudySession s = facade.getActiveStudySession();
                if (s == null || s.isCompleted()) return;
                QuizQuestion currentQ = s.getCurrentQuestion();
                if (currentQ == null) return;

                selectedOption[0] = optIdx;
                for (int j = 0; j < 4; j++) {
                    if (j == currentQ.getCorrectAnswerIndex()) {
                        optionCards[j].setBackground(new Color(6, 78, 59));
                    } else if (j == optIdx) {
                        optionCards[j].setBackground(new Color(127, 29, 29));
                    } else {
                        optionCards[j].setBackground(new Color(30, 41, 59));
                    }
                }
                if (optIdx == currentQ.getCorrectAnswerIndex()) {
                    feedbackBanner.setForeground(new Color(74, 222, 128));
                    feedbackBanner.setText("✓ Correct! Click 'Next Question →' to record your answer.");
                } else {
                    feedbackBanner.setForeground(new Color(248, 113, 113));
                    feedbackBanner.setText("✕ Incorrect. Correct option is " + (char) ('A' + currentQ.getCorrectAnswerIndex()) + ". Click 'Next Question →' to continue.");
                }
            });
            optionCards[i] = cardBtn;
            optionsStack.add(cardBtn);
        }

        quizCenterPanel.add(optionsStack, BorderLayout.CENTER);

        JPanel bottomRow = new JPanel(new BorderLayout(10, 8));
        bottomRow.setOpaque(false);
        bottomRow.add(feedbackBanner, BorderLayout.NORTH);

        JButton hintBtn = createPillButton("💡 Hint", new Color(226, 232, 240));
        hintBtn.addActionListener(e -> {
            StudySession s = facade.getActiveStudySession();
            if (s != null && s.getCurrentQuestion() != null) {
                QuizQuestion q = s.getCurrentQuestion();
                String correctText = q.getOptions().get(q.getCorrectAnswerIndex());
                String preview = correctText.length() > 14 ? correctText.substring(0, 14) + "..." : correctText;
                JOptionPane.showMessageDialog(dialog,
                        "Study Hint: Recall how this concept connects to '" + preview + "' in your course material.",
                        "Quiz Hint", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        JPanel rightBottomBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightBottomBtns.setOpaque(false);

        JButton undoBtn = createPillButton("↶ Undo (F09)", new Color(226, 232, 240));
        undoBtn.addActionListener(e -> {
            if (facade.undoLastSessionAnswer()) {
                renderQuizState.run();
                onSessionUpdated.run();
            } else {
                JOptionPane.showMessageDialog(dialog, "Command stack is empty — nothing to undo.");
            }
        });

        nextBtn.setFocusPainted(false);
        nextBtn.setBackground(new Color(37, 99, 235));
        nextBtn.setForeground(Color.WHITE);
        nextBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        nextBtn.setBorder(new EmptyBorder(8, 20, 8, 20));
        nextBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        nextBtn.addActionListener(e -> {
            StudySession s = facade.getActiveStudySession();
            if (s == null || s.isCompleted()) return;
            if (selectedOption[0] < 0) {
                JOptionPane.showMessageDialog(dialog, "Please select an option (A, B, C, or D) first!");
                return;
            }
            facade.submitQuizAnswer(selectedOption[0]);
            renderQuizState.run();
            onSessionUpdated.run();
        });

        rightBottomBtns.add(undoBtn);
        rightBottomBtns.add(nextBtn);

        bottomRow.add(hintBtn, BorderLayout.WEST);
        bottomRow.add(rightBottomBtns, BorderLayout.EAST);
        quizCenterPanel.add(bottomRow, BorderLayout.SOUTH);

        JPopupMenu quizPopup = new JPopupMenu();
        JMenuItem addQItem = new JMenuItem("＋  Add question");
        addQItem.addActionListener(e -> {
            JTextField promptF = new JTextField(22);
            JTextField optA = new JTextField(18);
            JTextField optB = new JTextField(18);
            JTextField optC = new JTextField(18);
            JTextField optD = new JTextField(18);
            JComboBox<String> correctBox = new JComboBox<>(new String[]{"A (0)", "B (1)", "C (2)", "D (3)"});

            JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
            form.add(new JLabel("Question Prompt:")); form.add(promptF);
            form.add(new JLabel("Option A:")); form.add(optA);
            form.add(new JLabel("Option B:")); form.add(optB);
            form.add(new JLabel("Option C:")); form.add(optC);
            form.add(new JLabel("Option D:")); form.add(optD);
            form.add(new JLabel("Correct Answer:")); form.add(correctBox);

            if (JOptionPane.showConfirmDialog(dialog, form, "Add Quiz Question", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                if (!promptF.getText().isBlank()) {
                    quiz.getQuestions().add(new QuizQuestion(
                            promptF.getText().trim(),
                            List.of(optA.getText().trim(), optB.getText().trim(), optC.getText().trim(), optD.getText().trim()),
                            correctBox.getSelectedIndex()
                    ));
                    onSessionUpdated.run();
                    renderQuizState.run();
                }
            }
        });

        JMenuItem editQItem = new JMenuItem("✎  Edit question");
        editQItem.addActionListener(e -> {
            StudySession s = facade.getActiveStudySession();
            if (s == null || s.getCurrentQuestion() == null) return;
            QuizQuestion q = s.getCurrentQuestion();
            List<String> o = q.getOptions();
            JTextField promptF = new JTextField(q.getPrompt(), 22);
            JTextField optA = new JTextField(o.size() > 0 ? o.get(0) : "", 18);
            JTextField optB = new JTextField(o.size() > 1 ? o.get(1) : "", 18);
            JTextField optC = new JTextField(o.size() > 2 ? o.get(2) : "", 18);
            JTextField optD = new JTextField(o.size() > 3 ? o.get(3) : "", 18);
            JComboBox<String> correctBox = new JComboBox<>(new String[]{"A (0)", "B (1)", "C (2)", "D (3)"});
            correctBox.setSelectedIndex(Math.min(3, Math.max(0, q.getCorrectAnswerIndex())));

            JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
            form.add(new JLabel("Question Prompt:")); form.add(promptF);
            form.add(new JLabel("Option A:")); form.add(optA);
            form.add(new JLabel("Option B:")); form.add(optB);
            form.add(new JLabel("Option C:")); form.add(optC);
            form.add(new JLabel("Option D:")); form.add(optD);
            form.add(new JLabel("Correct Answer:")); form.add(correctBox);

            if (JOptionPane.showConfirmDialog(dialog, form, "Edit Quiz Question", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                q.setPrompt(promptF.getText().trim());
                q.setOptions(List.of(optA.getText().trim(), optB.getText().trim(), optC.getText().trim(), optD.getText().trim()));
                q.setCorrectAnswerIndex(correctBox.getSelectedIndex());
                onSessionUpdated.run();
                renderQuizState.run();
            }
        });

        JMenuItem deleteQItem = new JMenuItem("🗑  Delete question");
        deleteQItem.addActionListener(e -> {
            StudySession s = facade.getActiveStudySession();
            if (s == null || quiz.getQuestions().size() <= 1) {
                JOptionPane.showMessageDialog(dialog, "Cannot delete the only question in the quiz.");
                return;
            }
            int idx = s.getCurrentQuestionIndex();
            if (idx < quiz.getQuestions().size()) {
                quiz.getQuestions().remove(idx);
                facade.startStudySession(quiz);
                onSessionUpdated.run();
                renderQuizState.run();
            }
        });

        JMenuItem stopwatchItem = new JMenuItem("⏱  Toggle stopwatch");
        stopwatchItem.addActionListener(e -> {
            if (stopwatch.isRunning()) {
                stopwatch.stop();
                stopwatchLabel.setVisible(false);
            } else {
                timerSeconds[0] = 0;
                stopwatchLabel.setText("⏱ 00:00");
                stopwatchLabel.setVisible(true);
                stopwatch.start();
            }
        });

        quizPopup.add(addQItem);
        quizPopup.add(editQItem);
        quizPopup.add(deleteQItem);
        quizPopup.addSeparator();
        quizPopup.add(stopwatchItem);

        quizMenuBtn.addActionListener(e -> quizPopup.show(quizMenuBtn, 0, quizMenuBtn.getHeight()));

        JPanel centerWrapper = new JPanel(new GridBagLayout());
        centerWrapper.setOpaque(false);
        centerWrapper.add(quizCenterPanel);
        root.add(centerWrapper, BorderLayout.CENTER);

        renderQuizState.run();
        dialog.setContentPane(root);
        dialog.setVisible(true);
    }

    // =========================================================================
    // 3. MAXIMIZED CODE LAB IDE VIEW (F07 Synthesis & F08 AI Grader)
    // =========================================================================
    public static void showCodeLabModal(
            JFrame parent,
            String subjectId,
            StudyAgentFacade facade,
            JTextField sharedTopicField,
            JTextArea sharedProblemArea,
            JTextArea sharedEditorArea,
            JTextArea sharedFeedbackArea,
            Runnable onDataChanged
    ) {
        JDialog dialog = new JDialog(parent, "Maximized Code Lab IDE (F07 & F08) — " + subjectId, true);
        dialog.setSize(1200, 760);
        dialog.setLocationRelativeTo(parent);

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBackground(new Color(15, 23, 42));
        root.setBorder(new EmptyBorder(16, 22, 18, 22));

        // 2-Row Top Toolbar prevents any button overlap in fullscreen!
        JPanel topToolbar = new JPanel(new BorderLayout(0, 10));
        topToolbar.setOpaque(false);

        // Row 1: Title on Left | Close Button on Right
        JPanel headerRow1 = new JPanel(new BorderLayout());
        headerRow1.setOpaque(false);
        JLabel titleLbl = new JLabel("💻 Code Synthesis & AI Grader Workbench (" + subjectId + ")");
        titleLbl.setForeground(Color.WHITE);
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 18));

        JButton closeBtn = createPillButton("✕ Close IDE", Color.WHITE);
        closeBtn.addActionListener(e -> dialog.dispose());

        headerRow1.add(titleLbl, BorderLayout.WEST);
        headerRow1.add(closeBtn, BorderLayout.EAST);

        // Row 2: Topic Input on Left | Action Buttons on Right
        JPanel headerRow2 = new JPanel(new BorderLayout(12, 0));
        headerRow2.setOpaque(false);

        JPanel topicInputBox = new JPanel(new BorderLayout(8, 0));
        topicInputBox.setOpaque(false);
        JLabel topicLbl = new JLabel("CS Topic:");
        topicLbl.setForeground(new Color(203, 213, 225));
        topicLbl.setFont(new Font("SansSerif", Font.BOLD, 13));
        JTextField modalTopicField = new JTextField(sharedTopicField.getText());
        topicInputBox.add(topicLbl, BorderLayout.WEST);
        topicInputBox.add(modalTopicField, BorderLayout.CENTER);

        JPanel actionBtnsBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionBtnsBox.setOpaque(false);

        JButton genProblemBtn = new JButton("⚡ F07: Synthesize Problem");
        genProblemBtn.setBackground(new Color(37, 99, 235));
        genProblemBtn.setForeground(Color.WHITE);
        genProblemBtn.setFont(new Font("SansSerif", Font.BOLD, 13));

        JButton clearBtn = new JButton("Clear Editor (Test F08 Guard)");

        JButton gradeBtn = new JButton("✓ F08: Grade Solution");
        gradeBtn.setBackground(new Color(16, 185, 129));
        gradeBtn.setForeground(Color.WHITE);
        gradeBtn.setFont(new Font("SansSerif", Font.BOLD, 13));

        actionBtnsBox.add(genProblemBtn);
        actionBtnsBox.add(clearBtn);
        actionBtnsBox.add(gradeBtn);

        headerRow2.add(topicInputBox, BorderLayout.CENTER);
        headerRow2.add(actionBtnsBox, BorderLayout.EAST);

        JProgressBar ideProgress = new JProgressBar();
        ideProgress.setStringPainted(true);
        ideProgress.setVisible(false);

        JPanel headerStack = new JPanel(new GridLayout(2, 1, 0, 8));
        headerStack.setOpaque(false);
        headerStack.add(headerRow1);
        headerStack.add(headerRow2);

        topToolbar.add(headerStack, BorderLayout.CENTER);
        topToolbar.add(ideProgress, BorderLayout.SOUTH);
        root.add(topToolbar, BorderLayout.NORTH);

        // Left Split: Problem Specification (Top) + AI Grading Feedback (Bottom)
        JTextArea modalProblemArea = new JTextArea(sharedProblemArea.getText());
        modalProblemArea.setEditable(false);
        modalProblemArea.setLineWrap(true);
        modalProblemArea.setWrapStyleWord(true);
        modalProblemArea.setFont(new Font("SansSerif", Font.PLAIN, 14));
        modalProblemArea.setBorder(new EmptyBorder(10, 12, 10, 12));

        JPanel problemPanel = new JPanel(new BorderLayout());
        problemPanel.setBorder(BorderFactory.createTitledBorder("1. Synthesized Coding Problem Specification (F07)"));
        problemPanel.add(new JScrollPane(modalProblemArea), BorderLayout.CENTER);

        JTextArea modalFeedbackArea = new JTextArea(sharedFeedbackArea.getText());
        modalFeedbackArea.setEditable(false);
        modalFeedbackArea.setLineWrap(true);
        modalFeedbackArea.setWrapStyleWord(true);
        modalFeedbackArea.setFont(new Font("SansSerif", Font.PLAIN, 14));
        modalFeedbackArea.setBorder(new EmptyBorder(10, 12, 10, 12));

        JPanel feedbackPanel = new JPanel(new BorderLayout());
        feedbackPanel.setBorder(BorderFactory.createTitledBorder("3. AI Semantic Grading, Score & Hints (F08)"));
        feedbackPanel.add(new JScrollPane(modalFeedbackArea), BorderLayout.CENTER);

        JSplitPane leftVerticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, problemPanel, feedbackPanel);
        leftVerticalSplit.setDividerLocation(300);

        // Right Split: Full-Height Monospaced Java Code Editor
        JTextArea modalCodeEditor = new JTextArea(sharedEditorArea.getText());
        modalCodeEditor.setFont(new Font("Monospaced", Font.PLAIN, 15));
        modalCodeEditor.setTabSize(4);
        modalCodeEditor.setBorder(new EmptyBorder(12, 14, 12, 14));

        JPanel editorPanel = new JPanel(new BorderLayout());
        editorPanel.setBorder(BorderFactory.createTitledBorder("2. Java Solution Editor (Write your code here)"));
        editorPanel.add(new JScrollPane(modalCodeEditor), BorderLayout.CENTER);

        JSplitPane mainIdeSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftVerticalSplit, editorPanel);
        mainIdeSplit.setDividerLocation(470);
        root.add(mainIdeSplit, BorderLayout.CENTER);

        final CodingProblem[] activeProblem = {null};

        clearBtn.addActionListener(e -> {
            modalCodeEditor.setText("");
            sharedEditorArea.setText("");
        });

        genProblemBtn.addActionListener(e -> {
            String topic = modalTopicField.getText().trim();
            sharedTopicField.setText(topic);
            genProblemBtn.setEnabled(false);
            ideProgress.setIndeterminate(true);
            ideProgress.setString("⚡ Synthesizing coding problem for '" + topic + "'...");
            ideProgress.setVisible(true);

            new SwingWorker<CodingProblem, Void>() {
                @Override
                protected CodingProblem doInBackground() throws Exception {
                    return facade.generateCodingProblem(topic);
                }

                @Override
                protected void done() {
                    genProblemBtn.setEnabled(true);
                    ideProgress.setVisible(false);
                    try {
                        activeProblem[0] = get();
                        String desc = activeProblem[0].getProblemTitle() + "\n\n"
                                + activeProblem[0].getDescription() + "\n\nConstraints: " + activeProblem[0].getConstraints();
                        modalProblemArea.setText(desc);
                        sharedProblemArea.setText(desc);
                        modalCodeEditor.setText(activeProblem[0].getStarterCode());
                        sharedEditorArea.setText(activeProblem[0].getStarterCode());
                        onDataChanged.run();
                    } catch (Exception ex) {
                        Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                        JOptionPane.showMessageDialog(dialog, c.getMessage(), "F07 Academic Safety & Scope Guard", JOptionPane.WARNING_MESSAGE);
                    }
                }
            }.execute();
        });

        gradeBtn.addActionListener(e -> {
            String code = modalCodeEditor.getText();
            sharedEditorArea.setText(code);
            gradeBtn.setEnabled(false);
            ideProgress.setIndeterminate(true);
            ideProgress.setString("⚡ Evaluating Java solution with AI Grader (F08)...");
            ideProgress.setVisible(true);

            new SwingWorker<GradeEvaluation, Void>() {
                @Override
                protected GradeEvaluation doInBackground() throws Exception {
                    if (activeProblem[0] == null) {
                        activeProblem[0] = facade.generateCodingProblem(modalTopicField.getText().trim());
                    }
                    return facade.gradeSubmission(activeProblem[0], code);
                }

                @Override
                protected void done() {
                    gradeBtn.setEnabled(true);
                    ideProgress.setVisible(false);
                    try {
                        GradeEvaluation eval = get();
                        String fb = "SCORE: " + eval.getScore() + " / 100\n\n"
                                + eval.getFeedback() + "\n\nQUALITATIVE HINTS:\n• "
                                + String.join("\n• ", eval.getHints());
                        modalFeedbackArea.setText(fb);
                        sharedFeedbackArea.setText(fb);
                    } catch (Exception ex) {
                        Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                        JOptionPane.showMessageDialog(dialog, c.getMessage(), "F08 Input Validation Guard", JOptionPane.WARNING_MESSAGE);
                    }
                }
            }.execute();
        });

        dialog.setContentPane(root);
        dialog.setVisible(true);
    }

    private static JButton createPillButton(String text, Color fg) {
        JButton b = new JButton(text);
        b.setForeground(fg);
        stylePillButton(b);
        return b;
    }

    private static void stylePillButton(JButton b) {
        b.setFocusPainted(false);
        b.setBackground(new Color(30, 41, 59));
        b.setFont(new Font("SansSerif", Font.BOLD, 13));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(71, 85, 105), 1, true),
                new EmptyBorder(8, 16, 8, 16)
        ));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
}