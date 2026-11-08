package ai.studyagent.ui;

import ai.studyagent.controller.StudyAgentFacade;
import ai.studyagent.domain.Flashcard;
import ai.studyagent.domain.Quiz;
import ai.studyagent.domain.QuizQuestion;
import ai.studyagent.session.StudySession;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
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
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class MaximizedStudyDialog {

    // =========================================================================
    // 1. MAXIMIZED FLASHCARD FOCUS VIEW (Matches image_0341e5.png)
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

        JDialog dialog = new JDialog(parent, deckTitle, true);
        dialog.setSize(1180, 760);
        dialog.setLocationRelativeTo(parent);

        final int[] index = {0};
        final boolean[] showingAnswer = {false};
        final int[] missedCount = {0};
        final int[] gotItCount = {0};
        final int[] elapsedSeconds = {0};

        JLabel stopwatchLabel = new JLabel("");
        stopwatchLabel.setForeground(new Color(161, 161, 170));
        stopwatchLabel.setFont(new Font("Monospaced", Font.BOLD, 13));
        stopwatchLabel.setVisible(false);

        Timer stopwatch = new Timer(1000, e -> {
            elapsedSeconds[0]++;
            stopwatchLabel.setText(String.format("⏱ %02d:%02d", elapsedSeconds[0] / 60, elapsedSeconds[0] % 60));
        });

        // Ambient Glowing Dark Canvas
        JPanel root = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                g2.setColor(new Color(24, 25, 28));
                g2.fillRect(0, 0, w, h);

                // Left Indigo/Blue Radial Aura
                RadialGradientPaint leftGlow = new RadialGradientPaint(
                        new Point(w / 2 - 110, h / 2),
                        320f,
                        new float[]{0f, 1f},
                        new Color[]{new Color(59, 130, 246, 55), new Color(24, 25, 28, 0)}
                );
                g2.setPaint(leftGlow);
                g2.fillRect(0, 0, w, h);

                // Right Emerald Green Radial Aura
                RadialGradientPaint rightGlow = new RadialGradientPaint(
                        new Point(w / 2 + 120, h / 2),
                        320f,
                        new float[]{0f, 1f},
                        new Color[]{new Color(16, 185, 129, 55), new Color(24, 25, 28, 0)}
                );
                g2.setPaint(rightGlow);
                g2.fillRect(0, 0, w, h);
                g2.dispose();
            }
        };
        root.setBorder(new EmptyBorder(18, 28, 24, 28));

        // Top Header Bar
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);

        JPanel titleBox = new JPanel(new GridLayout(2, 1, 0, 4));
        titleBox.setOpaque(false);
        JLabel titleLbl = new JLabel(deckTitle);
        titleLbl.setForeground(Color.WHITE);
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 18));
        JLabel sourcePill = new JLabel("  " + subtitleMeta + "  ");
        sourcePill.setForeground(new Color(212, 212, 216));
        sourcePill.setFont(new Font("SansSerif", Font.PLAIN, 12));
        sourcePill.setBorder(BorderFactory.createLineBorder(new Color(63, 63, 70), 1, true));
        titleBox.add(titleLbl);
        titleBox.add(sourcePill);

        JLabel shortcutHint = new JLabel("Press 'Space' to flip, '← / →' to navigate", SwingConstants.CENTER);
        shortcutHint.setForeground(new Color(161, 161, 170));
        shortcutHint.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightActions.setOpaque(false);
        rightActions.add(stopwatchLabel);

        JButton closeBtn = new JButton("✕");
        closeBtn.setForeground(Color.WHITE);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setFont(new Font("SansSerif", Font.BOLD, 16));
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

        // Center Floating Flashcard Card
        JLabel cardCountLabel = new JLabel("1/" + deck.size());
        cardCountLabel.setForeground(new Color(161, 161, 170));
        cardCountLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));

        JTextArea cardBodyArea = new JTextArea();
        cardBodyArea.setEditable(false);
        cardBodyArea.setLineWrap(true);
        cardBodyArea.setWrapStyleWord(true);
        cardBodyArea.setOpaque(false);
        cardBodyArea.setForeground(Color.WHITE);
        cardBodyArea.setFont(new Font("SansSerif", Font.PLAIN, 23));
        cardBodyArea.setBorder(new EmptyBorder(30, 12, 20, 12));

        JLabel seeAnswerHint = new JLabel("See answer", SwingConstants.CENTER);
        seeAnswerHint.setForeground(new Color(161, 161, 170));
        seeAnswerHint.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JButton missedBtn = new JButton("✕  0");
        JButton gotItBtn = new JButton("0  ✓");

        Runnable renderCard = () -> {
            if (deck.isEmpty()) {
                cardCountLabel.setText("0/0");
                cardBodyArea.setText("No cards in deck.");
                return;
            }
            if (index[0] >= deck.size()) index[0] = 0;
            Flashcard c = deck.get(index[0]);
            cardCountLabel.setText((index[0] + 1) + "/" + deck.size());
            if (showingAnswer[0]) {
                cardBodyArea.setForeground(new Color(167, 243, 208));
                cardBodyArea.setFont(new Font("SansSerif", Font.PLAIN, 19));
                cardBodyArea.setText(c.getDefinition());
                seeAnswerHint.setText("Showing Answer (Click or press Space to see Term)");
            } else {
                cardBodyArea.setForeground(Color.WHITE);
                cardBodyArea.setFont(new Font("SansSerif", Font.PLAIN, 23));
                cardBodyArea.setText(c.getTerm());
                seeAnswerHint.setText("See answer");
            }
            missedBtn.setText("✕  " + missedCount[0]);
            gotItBtn.setText(gotItCount[0] + "  ✓");
        };

        // ⋮ Popup Menu inside the card
        JButton cardMenuBtn = new JButton("⋮");
        cardMenuBtn.setForeground(Color.WHITE);
        cardMenuBtn.setContentAreaFilled(false);
        cardMenuBtn.setBorderPainted(false);
        cardMenuBtn.setFont(new Font("SansSerif", Font.BOLD, 18));
        cardMenuBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPopupMenu cardPopup = new JPopupMenu();
        JMenuItem restartItem = new JMenuItem("↻  Restart set");
        restartItem.addActionListener(e -> {
            index[0] = 0;
            showingAnswer[0] = false;
            missedCount[0] = 0;
            gotItCount[0] = 0;
            renderCard.run();
        });

        JMenuItem shuffleItem = new JMenuItem("🔀  Shuffle set");
        shuffleItem.addActionListener(e -> {
            Collections.shuffle(deck);
            index[0] = 0;
            showingAnswer[0] = false;
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

        JMenuItem editCardItem = new JMenuItem("✎  Edit flashcard");
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

        JMenuItem deleteCardItem = new JMenuItem("🗑  Delete flashcard");
        deleteCardItem.addActionListener(e -> {
            if (deck.size() <= 1) {
                JOptionPane.showMessageDialog(dialog, "Cannot delete the last card in the set.");
                return;
            }
            deck.remove(index[0]);
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
                g2.setColor(new Color(39, 39, 42));
                g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 32, 32));
                g2.setColor(new Color(63, 63, 70));
                g2.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 32, 32));
                g2.dispose();
            }
        };
        centerCard.setOpaque(false);
        centerCard.setPreferredSize(new Dimension(440, 410));
        centerCard.setBorder(new EmptyBorder(22, 28, 22, 28));
        centerCard.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel cardHeader = new JPanel(new BorderLayout());
        cardHeader.setOpaque(false);
        cardHeader.add(cardCountLabel, BorderLayout.WEST);
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

        // Bottom Navigation Controls (←  ✕ 0   0 ✓  →)
        JPanel bottomNav = new JPanel(new FlowLayout(FlowLayout.CENTER, 18, 12));
        bottomNav.setOpaque(false);

        JButton prevBtn = createPillButton("←", new Color(212, 212, 216));
        missedBtn.setForeground(new Color(248, 113, 113));
        stylePillButton(missedBtn);
        gotItBtn.setForeground(new Color(74, 222, 128));
        stylePillButton(gotItBtn);
        JButton nextBtn = createPillButton("→", new Color(147, 197, 253));

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

        prevBtn.addActionListener(e -> goPrev.run());
        nextBtn.addActionListener(e -> goNext.run());
        missedBtn.addActionListener(e -> {
            missedCount[0]++;
            goNext.run();
        });
        gotItBtn.addActionListener(e -> {
            gotItCount[0]++;
            goNext.run();
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

        // Keyboard Shortcuts: SPACE to flip, LEFT / RIGHT arrows to navigate
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
    // 2. MAXIMIZED QUIZ FOCUS VIEW (Matches image_0341c5.png)
    // =========================================================================
    public static void showQuizModal(
            JFrame parent,
            String quizTitle,
            String subtitleMeta,
            Quiz quiz,
            StudyAgentFacade facade,
            Runnable onSessionUpdated
    ) {
        if (quiz == null || quiz.getQuestions().isEmpty()) return;

        JDialog dialog = new JDialog(parent, quizTitle, true);
        dialog.setSize(1180, 760);
        dialog.setLocationRelativeTo(parent);

        facade.startStudySession(quiz);

        final int[] elapsedSeconds = {0};
        JLabel stopwatchLabel = new JLabel("");
        stopwatchLabel.setForeground(new Color(161, 161, 170));
        stopwatchLabel.setFont(new Font("Monospaced", Font.BOLD, 13));
        stopwatchLabel.setVisible(false);

        Timer stopwatch = new Timer(1000, e -> {
            elapsedSeconds[0]++;
            stopwatchLabel.setText(String.format("⏱ %02d:%02d", elapsedSeconds[0] / 60, elapsedSeconds[0] % 60));
        });

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(new Color(24, 25, 28));
        root.setBorder(new EmptyBorder(18, 28, 24, 28));

        // Top Header Bar
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);

        JPanel titleBox = new JPanel(new GridLayout(2, 1, 0, 4));
        titleBox.setOpaque(false);
        JLabel titleLbl = new JLabel(quizTitle);
        titleLbl.setForeground(Color.WHITE);
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 18));
        JLabel sourcePill = new JLabel("  " + subtitleMeta + "  ");
        sourcePill.setForeground(new Color(212, 212, 216));
        sourcePill.setFont(new Font("SansSerif", Font.PLAIN, 12));
        sourcePill.setBorder(BorderFactory.createLineBorder(new Color(63, 63, 70), 1, true));
        titleBox.add(titleLbl);
        titleBox.add(sourcePill);

        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightActions.setOpaque(false);
        rightActions.add(stopwatchLabel);

        JButton closeBtn = new JButton("✕");
        closeBtn.setForeground(Color.WHITE);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setFont(new Font("SansSerif", Font.BOLD, 16));
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> {
            stopwatch.stop();
            dialog.dispose();
        });
        rightActions.add(closeBtn);

        topBar.add(titleBox, BorderLayout.WEST);
        topBar.add(rightActions, BorderLayout.EAST);
        root.add(topBar, BorderLayout.NORTH);

        // Center Quiz Container (680px wide)
        JPanel quizCenterPanel = new JPanel(new BorderLayout(0, 16));
        quizCenterPanel.setOpaque(false);
        quizCenterPanel.setPreferredSize(new Dimension(680, 540));

        JLabel qCountLabel = new JLabel("1/" + quiz.getQuestions().size());
        qCountLabel.setForeground(new Color(161, 161, 170));
        qCountLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));

        JButton quizMenuBtn = new JButton("⋮");
        quizMenuBtn.setForeground(Color.WHITE);
        quizMenuBtn.setContentAreaFilled(false);
        quizMenuBtn.setBorderPainted(false);
        quizMenuBtn.setFont(new Font("SansSerif", Font.BOLD, 18));
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

        // Option Cards (A, B, C, D)
        JPanel optionsStack = new JPanel(new GridLayout(4, 1, 0, 12));
        optionsStack.setOpaque(false);
        JButton[] optionCards = new JButton[4];
        final int[] selectedOption = {-1};

        JLabel feedbackBanner = new JLabel(" ");
        feedbackBanner.setForeground(new Color(167, 243, 208));
        feedbackBanner.setFont(new Font("SansSerif", Font.BOLD, 13));

        Runnable renderQuizState = () -> {
            StudySession s = facade.getActiveStudySession();
            if (s == null) return;
            if (s.isCompleted()) {
                qCountLabel.setText("Completed");
                promptArea.setText(String.format("🎉 Quiz Completed! Final Score: %d / %d (%.1f%%)\n"
                                + "Your F10 Progress Analytics Dashboard has been automatically updated via Observer Pattern.",
                        s.getCurrentScore(), s.getTotalQuestions(),
                        (s.getCurrentScore() * 100.0) / Math.max(1, s.getTotalQuestions())));
                for (JButton b : optionCards) b.setVisible(false);
                feedbackBanner.setText("Click 'Undo (F09)' below to revert your last answer, or close this window.");
                return;
            }
            QuizQuestion q = s.getCurrentQuestion();
            if (q == null) return;
            qCountLabel.setText((s.getCurrentQuestionIndex() + 1) + "/" + s.getTotalQuestions());
            promptArea.setText(q.getPrompt());
            selectedOption[0] = -1;
            feedbackBanner.setText(" ");

            List<String> opts = q.getOptions();
            for (int i = 0; i < 4; i++) {
                if (i < opts.size()) {
                    optionCards[i].setVisible(true);
                    optionCards[i].setBackground(new Color(39, 39, 42));
                    optionCards[i].setForeground(Color.WHITE);
                    optionCards[i].setText("<html><div style='padding:6px;'><b>" + (char) ('A' + i) + ".</b>  " + opts.get(i) + "</div></html>");
                } else {
                    optionCards[i].setVisible(false);
                }
            }
        };

        for (int i = 0; i < 4; i++) {
            final int optIdx = i;
            JButton cardBtn = new JButton();
            cardBtn.setHorizontalAlignment(SwingConstants.LEFT);
            cardBtn.setFocusPainted(false);
            cardBtn.setBackground(new Color(39, 39, 42));
            cardBtn.setForeground(Color.WHITE);
            cardBtn.setFont(new Font("SansSerif", Font.PLAIN, 15));
            cardBtn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(63, 63, 70), 1, true),
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
                        optionCards[j].setBackground(new Color(39, 39, 42));
                    }
                }
                if (optIdx == currentQ.getCorrectAnswerIndex()) {
                    feedbackBanner.setForeground(new Color(74, 222, 128));
                    feedbackBanner.setText("✓ Correct! Click 'Next' to record answer and advance.");
                } else {
                    feedbackBanner.setForeground(new Color(248, 113, 113));
                    feedbackBanner.setText("✕ Incorrect. Correct option is " + (char) ('A' + currentQ.getCorrectAnswerIndex()) + ". Click 'Next' to continue.");
                }
            });
            optionCards[i] = cardBtn;
            optionsStack.add(cardBtn);
        }

        quizCenterPanel.add(optionsStack, BorderLayout.CENTER);

        // Bottom Bar: Hint | Undo (F09) | Next
        JPanel bottomRow = new JPanel(new BorderLayout(10, 8));
        bottomRow.setOpaque(false);
        bottomRow.add(feedbackBanner, BorderLayout.NORTH);

        JButton hintBtn = createPillButton("Hint ⌄", new Color(212, 212, 216));
        hintBtn.addActionListener(e -> {
            StudySession s = facade.getActiveStudySession();
            if (s != null && s.getCurrentQuestion() != null) {
                QuizQuestion q = s.getCurrentQuestion();
                String correctText = q.getOptions().get(q.getCorrectAnswerIndex());
                String preview = correctText.length() > 12 ? correctText.substring(0, 12) + "..." : correctText;
                JOptionPane.showMessageDialog(dialog,
                        "Hint: Review how this concept relates to '" + preview + "' in your course notes.",
                        "Quiz Hint", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        JPanel rightBottomBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightBottomBtns.setOpaque(false);

        JButton undoBtn = createPillButton("Undo (F09)", new Color(212, 212, 216));
        undoBtn.addActionListener(e -> {
            if (facade.undoLastSessionAnswer()) {
                onSessionUpdated.run();
                renderQuizState.run();
            } else {
                JOptionPane.showMessageDialog(dialog, "Command stack is empty — nothing to undo.");
            }
        });

        JButton nextBtn = new JButton("Next");
        nextBtn.setFocusPainted(false);
        nextBtn.setBackground(new Color(79, 70, 229));
        nextBtn.setForeground(Color.WHITE);
        nextBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        nextBtn.setBorder(new EmptyBorder(8, 22, 8, 22));
        nextBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        nextBtn.addActionListener(e -> {
            StudySession s = facade.getActiveStudySession();
            if (s == null || s.isCompleted()) return;
            int choiceToSubmit = selectedOption[0] >= 0 ? selectedOption[0] : 0;
            facade.submitQuizAnswer(choiceToSubmit);
            onSessionUpdated.run();
            renderQuizState.run();
        });

        rightBottomBtns.add(undoBtn);
        rightBottomBtns.add(nextBtn);

        bottomRow.add(hintBtn, BorderLayout.WEST);
        bottomRow.add(rightBottomBtns, BorderLayout.EAST);
        quizCenterPanel.add(bottomRow, BorderLayout.SOUTH);

        // ⋮ Popup Menu for Quiz (+ Add question, Edit question, Delete question, Add stopwatch)
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

        JMenuItem stopwatchItem = new JMenuItem("⏱  Add stopwatch");
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

    private static JButton createPillButton(String text, Color fg) {
        JButton b = new JButton(text);
        b.setForeground(fg);
        stylePillButton(b);
        return b;
    }

    private static void stylePillButton(JButton b) {
        b.setFocusPainted(false);
        b.setBackground(new Color(39, 39, 42));
        b.setFont(new Font("SansSerif", Font.BOLD, 14));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(63, 63, 70), 1, true),
                new EmptyBorder(8, 18, 8, 18)
        ));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
}