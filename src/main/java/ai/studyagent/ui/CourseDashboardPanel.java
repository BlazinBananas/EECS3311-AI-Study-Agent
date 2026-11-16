package ai.studyagent.ui;

import ai.studyagent.controller.StudyAgentFacade;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class CourseDashboardPanel extends JPanel {
    public record NotebookInfo(String id, String title, String icon, String createdDate) {
    }

    private final StudyAgentFacade facade;
    private final List<NotebookInfo> notebooks = new ArrayList<>();
    private final JPanel gridContainer = new JPanel();
    private final JTextField searchField = new JTextField(18);
    private final BiConsumer<String, String> onOpenNotebook;
    private Runnable onStateChanged = () -> {
    };

    public CourseDashboardPanel(StudyAgentFacade facade, BiConsumer<String, String> onOpenNotebook) {
        this.facade = facade;
        this.onOpenNotebook = onOpenNotebook;
        setLayout(new BorderLayout(0, 18));
        setBorder(new EmptyBorder(24, 42, 24, 42));

        add(buildHeaderBar(), BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(gridContainer);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        refreshGrid("");
    }

    public void setOnStateChanged(Runnable onStateChanged) {
        this.onStateChanged = onStateChanged != null ? onStateChanged : () -> {
        };
    }

    public List<NotebookInfo> getNotebooks() {
        return notebooks;
    }

    public void setNotebooks(List<NotebookInfo> loaded) {
        notebooks.clear();
        if (loaded != null) {
            notebooks.addAll(loaded);
        }
        refreshGrid(searchField.getText().trim());
    }

    private JPanel buildHeaderBar() {
        JPanel header = new JPanel(new BorderLayout(12, 12));

        JLabel title = new JLabel("My notebooks");
        title.setFont(new Font("SansSerif", Font.BOLD, 24));
        header.add(title, BorderLayout.WEST);

        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        searchField.putClientProperty("JTextField.placeholderText", "Search notebooks...");
        searchField.addActionListener(e -> refreshGrid(searchField.getText().trim()));
        rightControls.add(searchField);

        JButton sampleBtn = new JButton("Load Sample EECS 3311 Notebook");
        sampleBtn.addActionListener(e -> {
            if (notebooks.stream().noneMatch(n -> n.id().equalsIgnoreCase("EECS3311"))) {
                notebooks.add(new NotebookInfo("EECS3311", "EECS 3311 - Software Design", "💻", "6 Nov 2026"));
                onStateChanged.run();
                refreshGrid(searchField.getText().trim());
            }
        });
        rightControls.add(sampleBtn);

        JButton newNotebookBtn = new JButton("+ New notebook");
        newNotebookBtn.setBackground(new Color(59, 130, 246));
        newNotebookBtn.setForeground(Color.WHITE);
        newNotebookBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        newNotebookBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        newNotebookBtn.addActionListener(e -> showNewNotebookDialog());
        rightControls.add(newNotebookBtn);

        header.add(rightControls, BorderLayout.EAST);
        return header;
    }

    private void showNewNotebookDialog() {
        JTextField idField = new JTextField("", 12);
        JTextField titleField = new JTextField("", 22);
        String[] icons = { "💻 Laptop", "🤖 AI Robot", "📜 Scroll / History", "📐 Math / Logic", "🧬 Science",
                "📚 Course Book" };
        JComboBox<String> iconBox = new JComboBox<>(icons);

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Course / Notebook ID (e.g., EECS3311):"));
        form.add(idField);
        form.add(new JLabel("Notebook Title (e.g., EECS 3311 - Software Design):"));
        form.add(titleField);
        form.add(new JLabel("Notebook Icon:"));
        form.add(iconBox);

        int res = JOptionPane.showConfirmDialog(this, form, "Create New Study Notebook",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res == JOptionPane.OK_OPTION) {
            String rawId = idField.getText().trim().replaceAll("\\s+", "");
            String fullTitle = titleField.getText().trim();
            if (rawId.isEmpty() && fullTitle.isEmpty())
                return;
            if (rawId.isEmpty())
                rawId = fullTitle.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
            if (fullTitle.isEmpty())
                fullTitle = rawId;

            String selectedIcon = iconBox.getSelectedItem().toString().split(" ")[0];
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM yyyy"));
            notebooks.add(new NotebookInfo(rawId.toUpperCase(), fullTitle, selectedIcon, dateStr));
            onStateChanged.run();
            refreshGrid(searchField.getText().trim());
        }
    }

    public void refreshGrid(String filter) {
        gridContainer.removeAll();
        String lower = filter == null ? "" : filter.toLowerCase();

        List<NotebookInfo> filtered = notebooks.stream()
                .filter(n -> lower.isEmpty() || n.title().toLowerCase().contains(lower)
                        || n.id().toLowerCase().contains(lower))
                .toList();

        if (filtered.isEmpty()) {
            gridContainer.setLayout(new GridBagLayout());
            JPanel emptyBox = new JPanel(new GridLayout(3, 1, 0, 8));
            emptyBox.setOpaque(false);
            JLabel emptyTitle = new JLabel("No notebooks yet", SwingConstants.CENTER);
            emptyTitle.setFont(new Font("SansSerif", Font.BOLD, 18));
            JLabel emptySub = new JLabel(
                    "Click '+ New notebook' in the top right to create isolated course workspaces.",
                    SwingConstants.CENTER);
            emptyBox.add(emptyTitle);
            emptyBox.add(emptySub);
            gridContainer.add(emptyBox);
        } else {
            gridContainer.setLayout(new FlowLayout(FlowLayout.LEFT, 18, 18));
            for (NotebookInfo nb : filtered) {
                gridContainer.add(createNotebookCard(nb));
            }
        }
        gridContainer.revalidate();
        gridContainer.repaint();
    }

    private JPanel createNotebookCard(NotebookInfo nb) {
        JPanel card = new JPanel(new BorderLayout(0, 12)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color cardBg = UIManager.getBoolean("laf.dark") ? new Color(30, 32, 38) : new Color(245, 247, 250);
                g2.setColor(cardBg);
                g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 22, 22));
                g2.setColor(UIManager.getColor("Component.borderColor"));
                g2.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 22, 22));
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setPreferredSize(new Dimension(245, 165));
        card.setBorder(new EmptyBorder(18, 18, 18, 18));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        JLabel iconLbl = new JLabel(nb.icon());
        iconLbl.setFont(new Font("SansSerif", Font.PLAIN, 28));

        JButton menuBtn = new JButton("⋮");
        menuBtn.setFont(new Font("SansSerif", Font.BOLD, 16));
        menuBtn.setBorderPainted(false);
        menuBtn.setContentAreaFilled(false);
        menuBtn.setToolTipText("Delete notebook");
        menuBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Remove notebook '" + nb.title() + "' from dashboard?",
                    "Delete Notebook", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                notebooks.remove(nb);
                facade.deleteSubjectData(nb.id());
                onStateChanged.run();
                refreshGrid(searchField.getText().trim());
            }
        });
        topRow.add(iconLbl, BorderLayout.WEST);
        topRow.add(menuBtn, BorderLayout.EAST);

        JPanel bottomBox = new JPanel(new BorderLayout(0, 6));
        bottomBox.setOpaque(false);
        JLabel titleLbl = new JLabel("<html><b>" + nb.title() + "</b></html>");
        titleLbl.setFont(new Font("SansSerif", Font.PLAIN, 15));

        int sourceCount = facade.getSubjectSources(nb.id()).size();
        JLabel metaLbl = new JLabel(
                nb.createdDate() + " • " + sourceCount + (sourceCount == 1 ? " source" : " sources"));
        metaLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        metaLbl.setForeground(UIManager.getColor("Label.disabledForeground"));

        bottomBox.add(titleLbl, BorderLayout.CENTER);
        bottomBox.add(metaLbl, BorderLayout.SOUTH);

        card.add(topRow, BorderLayout.NORTH);
        card.add(bottomBox, BorderLayout.SOUTH);

        MouseAdapter clickOpen = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onOpenNotebook.accept(nb.id(), nb.title());
            }
        };
        card.addMouseListener(clickOpen);
        topRow.addMouseListener(clickOpen);
        bottomBox.addMouseListener(clickOpen);
        titleLbl.addMouseListener(clickOpen);
        return card;
    }
}