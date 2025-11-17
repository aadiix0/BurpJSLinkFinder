package burp;

import burp.api.montoya.MontoyaApi;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.util.concurrent.ConcurrentHashMap;

public class MainTab {

    private final MontoyaApi api;
    private final Map<String, JSFileData> allJSFiles;
    private final JSFileTableModel jsFileTableModel;
    private JTable jsFileTable;
    private JTextArea endpointsTextArea;
    // Step 1: Change JScrollPane to Component
    private Component leftComponent;
    private Component rightComponent;
    private final Map<String, String> userNotes = new ConcurrentHashMap<>();
    private String currentlyDisplayedKey = null;

    public MainTab(MontoyaApi api, ConcurrentHashMap<String, JSFileData> allJSFiles) {
        this.api = api;
        this.allJSFiles = allJSFiles;

        try {
            // Load user notes
            String savedNotes = api.persistence().extensionData().getString("user_notes");
            if (savedNotes != null && !savedNotes.isEmpty()) {
                Type type = new TypeToken<ConcurrentHashMap<String, String>>(){}.getType();
                Map<String, String> loadedNotes = new Gson().fromJson(savedNotes, type);
                if (loadedNotes != null) {
                    userNotes.putAll(loadedNotes);
                }
            }

            // Initialize model
            this.jsFileTableModel = new JSFileTableModel(allJSFiles, api);

            // Initialize left and right panels
            initializeLeftPanel();
            initializeRightPanel();

            api.logging().logToOutput("MainTab initialized successfully");

        } catch (Exception e) {
            api.logging().logToError("Error in MainTab constructor: " + e.getMessage(), e);
            throw e;
        }
    }

    private void initializeLeftPanel() {
        // This is the main component for the left side
        JPanel leftPanel = new JPanel(new BorderLayout());

        // Search box at top
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JTextField searchField = new JTextField();
        searchField.setToolTipText("Search JS files...");

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filterTable(); }
            public void removeUpdate(DocumentEvent e) { filterTable(); }
            public void changedUpdate(DocumentEvent e) { filterTable(); }

            private void filterTable() {
                String searchText = searchField.getText().toLowerCase();
                TableRowSorter<TableModel> sorter = (TableRowSorter<TableModel>) jsFileTable.getRowSorter();

                if (searchText.isEmpty()) {
                    sorter.setRowFilter(null);
                } else {
                    sorter.setRowFilter(RowFilter.regexFilter("(?i)" + searchText));
                }
            }
        });

        searchPanel.add(new JLabel("🔍 "), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);

        jsFileTable = new JTable(jsFileTableModel);
        jsFileTable.setFillsViewportHeight(true);
        jsFileTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        TableRowSorter<TableModel> sorter = new TableRowSorter<>(jsFileTableModel);
        jsFileTable.setRowSorter(sorter);

        // Disable sorting on all columns to protect the parent-child hierarchy,
        // but keep the sorter for filtering via the search box.
        for (int i = 0; i < jsFileTable.getColumnCount(); i++) {
            sorter.setSortable(i, false);
        }

        jsFileTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        jsFileTable.getColumnModel().getColumn(1).setPreferredWidth(500);
        jsFileTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        jsFileTable.getColumnModel().getColumn(3).setPreferredWidth(100);

        // Custom renderer for File/Category column
        jsFileTable.getColumnModel().getColumn(1).setCellRenderer(
            new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(
                        JTable table, Object value, boolean isSelected,
                        boolean hasFocus, int row, int column) {

                    super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                    // NO COLORS - always white background
                    setBackground(Color.WHITE);
                    setForeground(Color.BLACK);

                    int modelRow = table.convertRowIndexToModel(row);
                    TableRow tableRow = jsFileTableModel.getRowData(modelRow);

                    if (tableRow != null && tableRow.isParent()) {
                        // Parent row - bold
                        setFont(new Font(getFont().getName(), Font.BOLD, 12));
                        setText(value.toString());
                    } else {
                        // Child row - normal, indented
                        setFont(new Font(getFont().getName(), Font.PLAIN, 12));
                        setText("    " + value);
                    }

                    return this;
                }
            }
        );

        // Configure Status column (column index 3)
        TableColumn statusColumn = jsFileTable.getColumnModel().getColumn(3);
        String[] statusOptions = {"New", "Working", "Later", "Ignore", "Done"};
        JComboBox<String> statusComboBox = new JComboBox<>(statusOptions);
        DefaultCellEditor statusEditor = new DefaultCellEditor(statusComboBox);
        statusEditor.setClickCountToStart(2);
        statusColumn.setCellEditor(statusEditor);

        statusColumn.setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

                setHorizontalAlignment(CENTER);
                setFont(new Font("Arial", Font.BOLD, 11));

                int modelRow = table.convertRowIndexToModel(row);
                TableRow tableRow = jsFileTableModel.getRowData(modelRow);

                String status;
                if (tableRow != null && tableRow.isParent()) {
                    status = tableRow.getStatus();
                } else {
                    TableRow parentRow = findParentRow(modelRow);
                    status = parentRow != null ? parentRow.getStatus() : "New";
                }

                setText(status);

                if (!isSelected) {
                    applyStatusColor(status);
                }

                return this;
            }

            private TableRow findParentRow(int childRowIndex) {
                for (int i = childRowIndex - 1; i >= 0; i--) {
                    TableRow row = jsFileTableModel.getRowData(i);
                    if (row != null && row.isParent()) {
                        return row;
                    }
                }
                return null;
            }

            private void applyStatusColor(String status) {
                switch (status) {
                    case "New":
                        setBackground(new Color(173, 216, 230)); // Light blue
                        setForeground(Color.BLUE);
                        break;
                    case "Working":
                        setBackground(new Color(255, 255, 102)); // Yellow
                        setForeground(new Color(139, 69, 19));
                        break;
                    case "Later":
                        setBackground(new Color(255, 102, 102)); // Red
                        setForeground(new Color(139, 0, 0));
                        break;
                    case "Ignore":
                        setBackground(Color.LIGHT_GRAY);
                        setForeground(Color.DARK_GRAY);
                        break;
                    case "Done":
                        setBackground(new Color(144, 238, 144)); // Light green
                        setForeground(new Color(0, 100, 0));
                        break;
                    default:
                        setBackground(Color.WHITE);
                        setForeground(Color.BLACK);
                }
            }
        });

        jsFileTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = jsFileTable.getSelectedRow();
                if (selectedRow >= 0) {
                    int modelRow = jsFileTable.convertRowIndexToModel(selectedRow);
                    TableRow rowObject = jsFileTableModel.getRow(modelRow);
                    if (rowObject != null && !rowObject.isParent()) {
                        for (int i = modelRow - 1; i >= 0; i--) {
                            TableRow parentRow = jsFileTableModel.getRow(i);
                            if (parentRow != null && parentRow.isParent()) {
                                updateRightPanel(parentRow, rowObject);
                                break;
                            }
                        }
                    }
                }
            }
        });

        jsFileTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = jsFileTable.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        int modelRow = jsFileTable.convertRowIndexToModel(row);
                        jsFileTableModel.toggleRow(modelRow);
                    }
                }
            }
        });

        // The JTable needs to be in a JScrollPane to see headers
        JScrollPane scrollPane = new JScrollPane(jsFileTable);

        leftPanel.add(searchPanel, BorderLayout.NORTH);
        leftPanel.add(scrollPane, BorderLayout.CENTER);

        // Step 2: Assign the composite panel directly, removing the outer scroll pane
        leftComponent = leftPanel;
    }

    private void initializeRightPanel() {
        // This is the main component for the right side
        JPanel rightPanel = new JPanel(new BorderLayout());
        endpointsTextArea = new JTextArea();
        endpointsTextArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        endpointsTextArea.setEditable(false);
        endpointsTextArea.setBackground(new Color(60, 63, 65));
        endpointsTextArea.setForeground(new Color(220, 220, 220));

        // The text area needs its own scroll pane
        JScrollPane scrollPane = new JScrollPane(endpointsTextArea);

        // Search box at top
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        JTextField searchField = new JTextField();
        searchField.setToolTipText("Search endpoints...");

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filterEndpoints(); }
            public void removeUpdate(DocumentEvent e) { filterEndpoints(); }
            public void changedUpdate(DocumentEvent e) { filterEndpoints(); }

            private void filterEndpoints() {
                String searchText = searchField.getText();
                javax.swing.text.Highlighter highlighter = endpointsTextArea.getHighlighter();
                highlighter.removeAllHighlights();

                if (searchText.isEmpty()) {
                    return;
                }

                String content = endpointsTextArea.getText();
                int index = content.toLowerCase().indexOf(searchText.toLowerCase());
                while (index >= 0) {
                    try {
                        highlighter.addHighlight(index, index + searchText.length(),
                                new javax.swing.text.DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW));
                        index = content.toLowerCase().indexOf(searchText.toLowerCase(), index + 1);
                    } catch (javax.swing.text.BadLocationException ex) {
                        // Ignore
                    }
                }
            }
        });

        searchPanel.add(new JLabel("🔍 "), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);

        // Button panel at bottom
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.setBackground(new Color(45, 45, 45));

        JToggleButton editModeButton = new JToggleButton("Edit Mode: OFF");
        editModeButton.addActionListener(e -> {
            boolean editMode = editModeButton.isSelected();
            endpointsTextArea.setEditable(editMode);
            editModeButton.setText(editMode ? "Edit Mode: ON" : "Edit Mode: OFF");
        });

        JButton saveButton = new JButton("Save Notes");
        saveButton.addActionListener(e -> {
            if (currentlyDisplayedKey != null) {
                userNotes.put(currentlyDisplayedKey, endpointsTextArea.getText());
                api.logging().logToOutput("Saved notes for: " + currentlyDisplayedKey);
                saveAllData();
                JOptionPane.showMessageDialog(rightPanel, "Notes saved!");
            }
        });

        buttonPanel.add(editModeButton);
        buttonPanel.add(saveButton);

        rightPanel.add(searchPanel, BorderLayout.NORTH);
        rightPanel.add(scrollPane, BorderLayout.CENTER);
        rightPanel.add(buttonPanel, BorderLayout.SOUTH);

        rightComponent = rightPanel;
    }

    private void updateRightPanel(TableRow parentRow, TableRow categoryRow) {
        String key = parentRow.getJsFileUrl() + ":" + categoryRow.getCategory();
        if (currentlyDisplayedKey != null && !currentlyDisplayedKey.equals(key)) {
            userNotes.put(currentlyDisplayedKey, endpointsTextArea.getText());
        }

        String savedContent = userNotes.get(key);
        if (savedContent != null) {
            endpointsTextArea.setText(savedContent);
        } else {
            JSFileData jsFileData = allJSFiles.get(parentRow.getJsFileUrl());
            if (jsFileData == null) return;

            List<String> endpoints = categoryRow.getCategory().equals("Latest") ? jsFileData.getLatest() : jsFileData.getFirstFinding();
            StringBuilder sb = new StringBuilder();
            sb.append("Category: ").append(categoryRow.getCategory()).append(" [").append(endpoints.size()).append(" endpoints]\n");
            sb.append("Source: ").append(parentRow.getJsFileUrl()).append("\n");
            sb.append("Total: ").append(endpoints.size()).append(" endpoints\n\n");
            for (String endpoint : endpoints) {
                sb.append(endpoint).append("\n");
            }
            endpointsTextArea.setText(sb.toString());
        }

        currentlyDisplayedKey = key;
        endpointsTextArea.setCaretPosition(0);
    }

    public void saveAllData() {
        String notesJson = new Gson().toJson(userNotes);
        api.persistence().extensionData().setString("user_notes", notesJson);
    }

    public Component getComponent() {
        JTabbedPane tabbedPane = new JTabbedPane();
        JPanel linksPanel = createLinksPanel();
        tabbedPane.addTab("Links", linksPanel);
        tabbedPane.addTab("JS Changes", createPlaceholderPanel("JS Changes - Coming Soon"));
        tabbedPane.addTab("Blacklist", createPlaceholderPanel("Blacklist - Coming Soon"));
        tabbedPane.addTab("Settings", createPlaceholderPanel("Settings - Coming Soon"));
        return tabbedPane;
    }

    private JPanel createLinksPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        // Step 3: Use the new Component fields in the JSplitPane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftComponent, rightComponent);
        splitPane.setDividerLocation(400);
        splitPane.setResizeWeight(0.4);
        panel.add(splitPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createPlaceholderPanel(String text) {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(new Font("Arial", Font.BOLD, 16));
        label.setForeground(Color.GRAY);
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }

    public JSFileTableModel getTableModel() {
        return jsFileTableModel;
    }
}
