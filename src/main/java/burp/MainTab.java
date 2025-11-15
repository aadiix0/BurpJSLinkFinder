package burp;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class MainTab extends JPanel {
    private JTable table;
    private TreeTableModel tableModel;
    private JTextArea endpointTextArea;
    private final ConcurrentHashMap<String, List<Endpoint>> currentData;
    private final ConcurrentHashMap<String, List<Endpoint>> historicData;
    private JCheckBox showHistoricCheckbox;

    public MainTab(ConcurrentHashMap<String, List<Endpoint>> currentData, ConcurrentHashMap<String, List<Endpoint>> historicData) {
        this.currentData = currentData;
        this.historicData = historicData;
        setLayout(new BorderLayout());
    }

    public void initialize() {
        showHistoricCheckbox = new JCheckBox("Show Historic Endpoints");

        tableModel = new TreeTableModel(currentData, historicData);
        table = new JTable(tableModel);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = table.getSelectedRow();
                if (selectedRow != -1) {
                    ParentRow parentRow = tableModel.getParentRow(selectedRow);
                    updateEndpointTextArea(parentRow);
                }
            }
        });

        endpointTextArea = new JTextArea();
        endpointTextArea.setEditable(false);
        endpointTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        endpointTextArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_C) {
                    String selectedText = endpointTextArea.getSelectedText();
                    if (selectedText != null && !selectedText.isEmpty()) {
                        copyToClipboard(selectedText);
                    }
                }
            }
        });

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(table), new JScrollPane(endpointTextArea));
        splitPane.setDividerLocation(0.4);

        add(showHistoricCheckbox, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        JButton clearButton = new JButton("Clear");
        JButton exportButton = new JButton("Export");
        buttonPanel.add(clearButton);
        buttonPanel.add(exportButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    public TreeTableModel getTableModel() {
        return tableModel;
    }

    private void updateEndpointTextArea(ParentRow parentRow) {
        StringBuilder sb = new StringBuilder();
        sb.append("Endpoints from: ").append(parentRow.getJsFileUrl()).append("\n");
        sb.append("Total: ").append(parentRow.getEndpointCount()).append(" endpoints\n\n");
        for (Endpoint endpoint : parentRow.getEndpoints()) {
            sb.append(endpoint.getUrl()).append("\n");
        }
        endpointTextArea.setText(sb.toString());
        endpointTextArea.setCaretPosition(0); // Scroll to top
    }

    private void copyToClipboard(String text) {
        StringSelection stringSelection = new StringSelection(text);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
    }
}
