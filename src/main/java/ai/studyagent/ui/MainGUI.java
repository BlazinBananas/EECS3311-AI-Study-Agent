package ai.studyagent.ui;

import ai.studyagent.config.EngineConfig;
import ai.studyagent.controller.StudyAgentFacade;
import ai.studyagent.domain.ChatResponse;
import ai.studyagent.domain.Flashcard;
import ai.studyagent.domain.Quiz;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class MainGUI extends JFrame {
    private final StudyAgentFacade facade;
    private final JTextArea outputArea = new JTextArea(16, 60);
    private boolean localMode = true;

    public MainGUI(StudyAgentFacade facade) {
        super("EECS 3311 - AI Study and Learning Agent");
        this.facade = facade;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton toggleBtn = new JButton("F01: Toggle Local/Cloud (Current: Local)");
        JButton flashcardBtn = new JButton("F05: Generate Flashcards");
        JButton quizSessionBtn = new JButton("F06/F09: Run Quiz & Session");
        JButton analyticsBtn = new JButton("F10: View Analytics");

        toggleBtn.addActionListener(e -> {
            localMode = !localMode;
            facade.setEngineMode(localMode, new EngineConfig());
            toggleBtn.setText("F01: Toggle Local/Cloud (Current: " + (localMode ? "Local" : "Cloud") + ")");
            outputArea.append("\n[F01] Switched engine mode to " + (localMode ? "Local Ollama" : "Cloud OpenAI") + "\n");
        });

        flashcardBtn.addActionListener(e -> {
            try {
                List<Flashcard> deck = facade.requestFlashcards("EECS3311", 2);
                outputArea.append("\n[F05] Generated Flashcards:\n");
                for (Flashcard card : deck) {
                    outputArea.append(" - " + card.getSummary() + "\n");
                }
            } catch (Exception ex) {
                outputArea.append("\n[Error] " + ex.getMessage() + "\n");
            }
        });

        quizSessionBtn.addActionListener(e -> {
            try {
                Quiz quiz = facade.requestQuiz("EECS3311", "Intermediate", 1);
                facade.startStudySession(quiz);
                boolean isCorrect = facade.submitQuizAnswer(0);
                outputArea.append("\n[F06/F09] Completed Study Session on " + quiz.getSummary() + " | Correct: " + isCorrect + "\n");
            } catch (Exception ex) {
                outputArea.append("\n[Error] " + ex.getMessage() + "\n");
            }
        });

        analyticsBtn.addActionListener(e -> outputArea.append("\n" + facade.viewAnalytics() + "\n"));

        topPanel.add(toggleBtn);
        topPanel.add(flashcardBtn);
        topPanel.add(quizSessionBtn);
        topPanel.add(analyticsBtn);

        outputArea.setEditable(false);
        add(topPanel, BorderLayout.NORTH);
        add(new JScrollPane(outputArea), BorderLayout.CENTER);

        JPanel chatPanel = new JPanel(new BorderLayout(5, 5));
        JTextField chatInput = new JTextField();
        JButton sendBtn = new JButton("F04: Ask Subject Question");
        sendBtn.addActionListener(e -> {
            try {
                ChatResponse resp = facade.askQuestion("EECS3311", chatInput.getText());
                outputArea.append("\n[F04 Chat] " + resp.getAnswerText() + "\n");
            } catch (Exception ex) {
                outputArea.append("\n[Chat Alert] " + ex.getMessage() + "\n");
            }
        });
        chatPanel.add(chatInput, BorderLayout.CENTER);
        chatPanel.add(sendBtn, BorderLayout.EAST);
        add(chatPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainGUI(new StudyAgentFacade()).setVisible(true));
    }
}