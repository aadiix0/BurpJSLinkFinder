package burp;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class MainTab extends JPanel {
    private JTable table;
    private final TreeTableModel tableModel;
    private final ConcurrentHashMap<String, List<Endpoint>> currentData;
    private final ConcurrentHashMap<String, List<Endpoint>> historicData;
    private JCheckBox showHistoricCheckbox;

    public MainTab(ConcurrentHashMap<String, List<Endpoint>> currentData, ConcurrentHashMap<String, List<Endpoint>> historicData) {
        this.currentData = currentData;
        this.historicData = historicData;
        this.tableModel = new TreeTableModel(currentData);
        setLayout(new BorderLayout());
    }

    public void initialize() {
        showHistoricCheckbox = new JCheckBox("Show Historic Endpoints");
        showHistoricCheckbox.addActionListener(e -> {
            tableModel.setShowHistoric(showHistoricCheckbox.isSelected());
            tableModel.fireTableDataChanged();
        });
        add(showHistoricCheckbox, BorderLayout.NORTH);

        table = new JTable(tableModel);
        table.getColumnModel().getColumn(0).setCellRenderer(new TreeCellRenderer());
        table.getColumnModel().getColumn(0).setPreferredWidth(20);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0) {
                    Row rowObject = tableModel.getRow(row);
                    if (rowObject instanceof ParentRow) {
                        ParentRow parentRow = (ParentRow) rowObject;
                        parentRow.setExpanded(!parentRow.isExpanded());
                        tableModel.fireTableDataChanged();
                    }
                }
            }
        });
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> {
            currentData.clear();
            if (showHistoricCheckbox.isSelected()) {
                historicData.clear();
            }
            tableModel.fireTableDataChanged();
        });
        JButton exportButton = new JButton("Export");
        exportButton.addActionListener(e -> exportData());
        buttonPanel.add(clearButton);
        buttonPanel.add(exportButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    public TreeTableModel getTableModel() {
        return tableModel;
    }

    private void exportData() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try (PrintWriter writer = new PrintWriter(file)) {
                writer.println("Current Endpoints:");
                for (String url : currentData.keySet()) {
                    writer.println("JS File: " + url);
                    for (Endpoint endpoint : currentData.get(url)) {
                        writer.println("  - " + endpoint.getUrl() + " (" + endpoint.getType() + ")");
                    }
                }
                if (showHistoricCheckbox.isSelected()) {
                    writer.println("\nHistoric Endpoints:");
                    for (String url : historicData.keySet()) {
                        writer.println("JS File: " + url);
                        for (Endpoint endpoint : historicData.get(url)) {
                            writer.println("  - " + endpoint.getUrl() + " (" + endpoint.getType() + ")");
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
