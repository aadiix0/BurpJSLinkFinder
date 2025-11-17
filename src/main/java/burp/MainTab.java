package burp;

import burp.api.montoya.MontoyaApi;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import java.util.concurrent.ConcurrentHashMap;

public class MainTab {

    private final MontoyaApi api;
    private final Map<String, JSFileData> allJSFiles;
    private final JSFileTableModel jsFileTableModel;
    private JTable jsFileTable;
    private JTextArea endpointsTextArea;
    private JScrollPane leftScrollPane;
    private JScrollPane rightScrollPane;
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
        jsFileTable = new JTable(jsFileTableModel);
        jsFileTable.setFillsViewportHeight(true);
        jsFileTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        jsFileTable.setAutoCreateRowSorter(false);
        jsFileTable.setRowSorter(null);

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

                    setBackground(Color.WHITE);
                    setForeground(Color.BLACK);

                    int modelRow = table.convertRowIndexToModel(row);
                    TableRow tableRow = jsFileTableModel.getRowData(modelRow);

                    if (tableRow == null) return this;

                    if (tableRow.isParent()) {
                        setFont(new Font(getFont().getName(), Font.BOLD, 12));
                        setText(value.toString());
                        if (!isSelected) {
                            applyStatusColor(tableRow.getStatus());
                        }
                    } else {
                        setFont(new Font(getFont().getName(), Font.PLAIN, 12));
                        setText("    " + value);
                        if (!isSelected) {
                            TableRow parentRow = findParentRow(modelRow);
                            if (parentRow != null) {
                                applyStatusColor(parentRow.getStatus());
                            }
                        }
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
                    if (status == null) status = "New";
                    switch (status) {
                        case "New": setBackground(new Color(173, 216, 230)); setForeground(Color.BLUE); break;
                        case "Working": setBackground(new Color(255, 255, 102)); setForeground(new Color(139, 69, 19)); break;
                        case "Later": setBackground(new Color(255, 102, 102)); setForeground(new Color(139, 0, 0)); break;
                        case "Ignore": setBackground(Color.LIGHT_GRAY); setForeground(Color.DARK_GRAY); break;
                        case "Done": setBackground(new Color(144, 238, 144)); setForeground(new Color(0, 100, 0)); break;
                        default: setBackground(Color.WHITE); setForeground(Color.BLACK);
                    }
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
                String status = value != null ? value.toString() : "New";
                setText(status);
                setHorizontalAlignment(CENTER);
                setFont(new Font("Arial", Font.BOLD, 11));
                if (!isSelected) {
                    switch (status) {
                        case "New": setBackground(new Color(173, 216, 230)); setForeground(Color.BLUE); break;
                        case "Working": setBackground(new Color(255, 255, 102)); setForeground(new Color(139, 69, 19)); break;
                        case "Later": setBackground(new Color(255, 102, 102)); setForeground(new Color(139, 0, 0)); break;
                        case "Ignore": setBackground(Color.LIGHT_GRAY); setForeground(Color.DARK_GRAY); break;
                        case "Done": setBackground(new Color(144, 238, 144)); setForeground(new Color(0, 100, 0)); break;
                        default: setBackground(Color.WHITE); setForeground(Color.BLACK);
                    }
                }
                return this;
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

        leftScrollPane = new JScrollPane(jsFileTable);
    }

    private void initializeRightPanel() {
        JPanel rightPanel = new JPanel(new BorderLayout());
        endpointsTextArea = new JTextArea();
        endpointsTextArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        endpointsTextArea.setEditable(false);
        endpointsTextArea.setBackground(new Color(60, 63, 65));
        endpointsTextArea.setForeground(new Color(220, 220, 220));
        JScrollPane scrollPane = new JScrollPane(endpointsTextArea);

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

        rightPanel.add(buttonPanel, BorderLayout.NORTH);
        rightPanel.add(scrollPane, BorderLayout.CENTER);

        rightScrollPane = new JScrollPane(rightPanel);
        rightScrollPane.setBorder(BorderFactory.createEmptyBorder());
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
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScrollPane, rightScrollPane);
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
