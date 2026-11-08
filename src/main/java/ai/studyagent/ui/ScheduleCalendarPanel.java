package ai.studyagent.ui;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

public class ScheduleCalendarPanel extends JPanel {
    private DefaultTableModel tableModel;
    private JTable scheduleTable;
    private Runnable onStateChanged = () -> {};

    public ScheduleCalendarPanel() {
        setLayout(new BorderLayout(12, 12));
        setBorder(new EmptyBorder(20, 36, 20, 36));

        JPanel topBar = new JPanel(new BorderLayout(10, 10));
        JLabel title = new JLabel("Weekly Study Schedule & Timetable (Automatically Saved)");
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        topBar.add(title, BorderLayout.NORTH);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        String[] times = {
                "08:00 - 09:30", "09:30 - 11:00", "11:00 - 12:30",
                "13:00 - 14:30", "14:30 - 16:00", "16:00 - 17:30",
                "18:00 - 19:30", "19:30 - 21:00"
        };

        JComboBox<String> dayBox = new JComboBox<>(days);
        JComboBox<String> timeBox = new JComboBox<>(times);
        JTextField eventField = new JTextField(18);

        JButton addBtn = new JButton("+ Add Event");
        addBtn.setBackground(new Color(37, 99, 235));
        addBtn.setForeground(Color.WHITE);
        addBtn.addActionListener(e -> {
            String text = eventField.getText().trim();
            if (!text.isEmpty()) {
                tableModel.setValueAt(text, timeBox.getSelectedIndex(), dayBox.getSelectedIndex() + 1);
                eventField.setText("");
                onStateChanged.run();
            }
        });

        JButton clearSelectedBtn = new JButton("Clear Selected Cell");
        clearSelectedBtn.addActionListener(e -> {
            int r = scheduleTable.getSelectedRow();
            int c = scheduleTable.getSelectedColumn();
            if (r != -1 && c > 0) {
                tableModel.setValueAt("", r, c);
                onStateChanged.run();
            }
        });

        JButton clearAllBtn = new JButton("Clear Entire Schedule");
        clearAllBtn.addActionListener(e -> {
            for (int r = 0; r < tableModel.getRowCount(); r++) {
                for (int c = 1; c < tableModel.getColumnCount(); c++) {
                    tableModel.setValueAt("", r, c);
                }
            }
            onStateChanged.run();
        });

        controls.add(new JLabel("Day:"));
        controls.add(dayBox);
        controls.add(new JLabel("Time:"));
        controls.add(timeBox);
        controls.add(new JLabel("Activity:"));
        controls.add(eventField);
        controls.add(addBtn);
        controls.add(clearSelectedBtn);
        controls.add(clearAllBtn);
        topBar.add(controls, BorderLayout.SOUTH);

        String[] columns = {"Time Slot", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        Object[][] blankData = new Object[times.length][columns.length];
        for (int i = 0; i < times.length; i++) {
            blankData[i][0] = times[i];
            for (int j = 1; j < columns.length; j++) {
                blankData[i][j] = "";
            }
        }

        tableModel = new DefaultTableModel(blankData, columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column > 0;
            }
        };
        tableModel.addTableModelListener(e -> onStateChanged.run());

        scheduleTable = new JTable(tableModel);
        scheduleTable.setRowHeight(54);
        scheduleTable.setShowGrid(true);
        scheduleTable.setGridColor(UIManager.getColor("Component.borderColor"));
        scheduleTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 13));

        add(topBar, BorderLayout.NORTH);
        add(new JScrollPane(scheduleTable), BorderLayout.CENTER);
    }

    public void setOnStateChanged(Runnable onStateChanged) {
        this.onStateChanged = onStateChanged != null ? onStateChanged : () -> {};
    }

    public List<List<String>> getScheduleGrid() {
        List<List<String>> rows = new ArrayList<>();
        for (int r = 0; r < tableModel.getRowCount(); r++) {
            List<String> row = new ArrayList<>();
            for (int c = 0; c < tableModel.getColumnCount(); c++) {
                Object val = tableModel.getValueAt(r, c);
                row.add(val == null ? "" : val.toString());
            }
            rows.add(row);
        }
        return rows;
    }

    public void setScheduleGrid(List<List<String>> grid) {
        if (grid == null) return;
        for (int r = 0; r < Math.min(grid.size(), tableModel.getRowCount()); r++) {
            List<String> row = grid.get(r);
            for (int c = 1; c < Math.min(row.size(), tableModel.getColumnCount()); c++) {
                tableModel.setValueAt(row.get(c), r, c);
            }
        }
    }
}