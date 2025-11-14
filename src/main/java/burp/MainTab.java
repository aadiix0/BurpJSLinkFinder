package burp;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
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
        this.tableModel = new TreeTableModel(currentData, historicData);
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

        // Enable text selection within cells
        table.setDefaultEditor(Object.class, new DefaultCellEditor(new JTextField()) {
            @Override
            public Component getTableCellEditorComponent(JTable table, Object value,
                    boolean isSelected, int row, int column) {
                JTextField textField = (JTextField) super.getTableCellEditorComponent(
                    table, value, isSelected, row, column);
                textField.setEditable(false);
                return textField;
            }
        });
        table.setSurrendersFocusOnKeystroke(true);
        table.putClientProperty("JTable.autoStartsEdit", Boolean.TRUE);
        DefaultCellEditor editor = (DefaultCellEditor) table.getDefaultEditor(Object.class);
        editor.setClickCountToStart(1);

        // Add KeyListener for Ctrl+C
        table.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.isControlDown() && e.getKeyCode() == java.awt.event.KeyEvent.VK_C) {
                    Component editor = table.getEditorComponent();
                    if (editor instanceof JTextField) {
                        JTextField field = (JTextField) editor;
                        String selectedText = field.getSelectedText();
                        if (selectedText != null && !selectedText.isEmpty()) {
                            copyToClipboard(selectedText);
                            return;
                        }
                    }
                    copySelectedRowToClipboard();
                }
            }
        });

        // Add MouseListener for expand/collapse and right-click
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0) {
                    Row rowObject = tableModel.getRow(row);
                    if (rowObject instanceof ParentRow || rowObject instanceof CategoryRow) {
                        rowObject.setExpanded(!rowObject.isExpanded());
                        tableModel.fireTableDataChanged();
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showPopup(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showPopup(e);
                }
            }

            private void showPopup(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                table.setRowSelectionInterval(row, row);
                JPopupMenu popupMenu = new JPopupMenu();
                JMenuItem copyItem = new JMenuItem("Copy URL");
                copyItem.addActionListener(l -> copySelectedRowToClipboard());
                popupMenu.add(copyItem);
                popupMenu.show(e.getComponent(), e.getX(), e.getY());
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

    private void copySelectedRowToClipboard() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow != -1) {
            Row rowObject = tableModel.getRow(selectedRow);
            String textToCopy = "";
            if (rowObject instanceof ParentRow) {
                textToCopy = ((ParentRow) rowObject).getJsFileUrl();
            } else if (rowObject instanceof CategoryRow) {
                StringBuilder sb = new StringBuilder();
                for (ChildRow child : ((CategoryRow) rowObject).getChildren()) {
                    sb.append(child.getEndpoint().getUrl()).append("\n");
                }
                textToCopy = sb.toString();
            } else if (rowObject instanceof ChildRow) {
                textToCopy = ((ChildRow) rowObject).getEndpoint().getUrl();
            }
            copyToClipboard(textToCopy);
        }
    }

    private void copyToClipboard(String text) {
        StringSelection stringSelection = new StringSelection(text);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
    }
}
