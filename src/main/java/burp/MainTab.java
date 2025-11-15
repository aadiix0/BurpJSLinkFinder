package burp;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class MainTab extends JPanel {
    private JTable jsFileTable;
    private JSFileTableModel tableModel;
    private JTextArea endpointsTextArea;
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

        tableModel = new JSFileTableModel(currentData);
        jsFileTable = new JTable(tableModel);
        jsFileTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = jsFileTable.getSelectedRow();
                if (selectedRow != -1) {
                    ParentRow parentRow = tableModel.getParentRow(selectedRow);
                    updateRightPanel(parentRow);
                }
            }
        });

        endpointsTextArea = new JTextArea();
        endpointsTextArea.setEditable(false);
        endpointsTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        endpointsTextArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_C) {
                    String selectedText = endpointsTextArea.getSelectedText();
                    if (selectedText != null && !selectedText.isEmpty()) {
                        copyToClipboard(selectedText);
                    }
                }
            }
        });

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(jsFileTable), new JScrollPane(endpointsTextArea));
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

    public JSFileTableModel getTableModel() {
        return tableModel;
    }

    private void updateRightPanel(ParentRow parentRow) {
        StringBuilder sb = new StringBuilder();
        sb.append("Endpoints from: ").append(parentRow.getJsFileUrl()).append("\n");
        sb.append("Total: ").append(parentRow.getEndpointCount()).append(" endpoints\n\n");
        for (Endpoint endpoint : parentRow.getEndpoints()) {
            sb.append(endpoint.getUrl()).append("\n");
        }
        endpointsTextArea.setText(sb.toString());
        endpointsTextArea.setCaretPosition(0);
    }

    private void copyToClipboard(String text) {
        StringSelection stringSelection = new StringSelection(text);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
    }
}
