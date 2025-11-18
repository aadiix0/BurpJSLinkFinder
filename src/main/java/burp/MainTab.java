package burp;

import burp.api.montoya.MontoyaApi;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Type;
import java.util.ArrayList;
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
    private JSFileRefresher jsFileRefresher;
    private DataPersistence dataPersistence;


    public MainTab(MontoyaApi api, ConcurrentHashMap<String, JSFileData> allJSFiles, DataPersistence dataPersistence) {
        this.api = api;
        this.allJSFiles = allJSFiles;
        this.dataPersistence = dataPersistence;

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

            // Step 1: Initialize table model FIRST
            this.jsFileTableModel = new JSFileTableModel(allJSFiles, api);

            // Step 2: Initialize UI components
            initializeLeftPanel();
            initializeRightPanel();

            // Step 3: Create refresher AFTER table model exists
            this.jsFileRefresher = new JSFileRefresher(api, allJSFiles, jsFileTableModel);


            api.logging().logToOutput("MainTab initialized successfully");

        } catch (Exception e) {
            api.logging().logToError("Error in MainTab constructor: " + e.getMessage(), e);
            throw e;
        }
    }

    private void initializeLeftPanel() {
        JPanel leftPanel = new JPanel(new BorderLayout());

    // Toolbar
    JPanel toolbarPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 3));
    toolbarPanel.setBackground(new Color(240, 240, 240));

    // Search panel - GRAY with icon inside
    JPanel searchPanel = new JPanel(new BorderLayout());
    searchPanel.setBackground(new Color(240, 240, 240));
    searchPanel.setPreferredSize(new Dimension(200, 28));
    searchPanel.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1),
        BorderFactory.createEmptyBorder(2, 5, 2, 5)
    ));

    JTextField searchField = new JTextField("  🔍 Search...");
    searchField.setForeground(Color.GRAY);
    searchField.setBackground(new Color(240, 240, 240));  // STAY GRAY
    searchField.setOpaque(true);
    searchField.setBorder(null);
    searchField.setFont(new Font("Arial", Font.PLAIN, 11));

    searchField.addFocusListener(new FocusAdapter() {
        @Override
        public void focusGained(FocusEvent e) {
            if (searchField.getText().equals("  🔍 Search...")) {
                searchField.setText("");
                searchField.setForeground(Color.BLACK);
            }
        }

        @Override
        public void focusLost(FocusEvent e) {
            if (searchField.getText().isEmpty()) {
                searchField.setText("  🔍 Search...");
                searchField.setForeground(Color.GRAY);
            }
        }
    });

    // Search logic (unchanged)
    searchField.getDocument().addDocumentListener(new DocumentListener() {
        public void insertUpdate(DocumentEvent e) { filter(); }
        public void removeUpdate(DocumentEvent e) { filter(); }
        public void changedUpdate(DocumentEvent e) { filter(); }

        private void filter() {
            String text = searchField.getText();
            if (text.equals("  🔍 Search...")) return;

            TableRowSorter<TableModel> sorter =
                (TableRowSorter<TableModel>) jsFileTable.getRowSorter();
            if (text.isEmpty()) {
                sorter.setRowFilter(null);
            } else {
                sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text));
            }
        }
    });

    searchPanel.add(searchField, BorderLayout.CENTER);

    // Refresh button - with text
    JButton refreshButton = new JButton("⟳ Refresh");  // ADDED TEXT
    refreshButton.setPreferredSize(new Dimension(90, 28));  // Wider for text
    refreshButton.setFont(new Font("Arial", Font.BOLD, 11));
    refreshButton.setFocusPainted(false);
    refreshButton.setBackground(new Color(70, 130, 180));
    refreshButton.setForeground(Color.WHITE);
    refreshButton.setBorder(BorderFactory.createLineBorder(
        new Color(50, 100, 150), 1
    ));
    refreshButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
    refreshButton.setToolTipText("Re-scan all JS files");

    refreshButton.addActionListener(e -> {
        refreshButton.setEnabled(false);
        refreshButton.setText("⏳");
        new Thread(() -> {
            jsFileRefresher.refreshAllJSFiles();
            SwingUtilities.invokeLater(() -> {
                refreshButton.setEnabled(true);
                refreshButton.setText("⟳ Refresh");
            });
        }).start();
    });

    // Filter dropdown
    JComboBox<String> filterCombo = new JComboBox<>(new String[]{
        "All", "New", "Working", "Later", "Ignore", "Done"
    });
    filterCombo.setPreferredSize(new Dimension(90, 28));
    filterCombo.setFont(new Font("Arial", Font.PLAIN, 11));
    filterCombo.setBackground(Color.WHITE);
    filterCombo.setToolTipText("Filter by status");

    filterCombo.addActionListener(e -> {
        String selected = (String) filterCombo.getSelectedItem();
        filterTableByStatus(selected);
    });

    // Add to toolbar
    toolbarPanel.add(searchPanel);
    toolbarPanel.add(refreshButton);
    toolbarPanel.add(filterCombo);  // NEW

        // Table
        jsFileTable = new JTable(jsFileTableModel);
        leftPanel.add(toolbarPanel, BorderLayout.NORTH);
        leftPanel.add(new JScrollPane(jsFileTable), BorderLayout.CENTER);

        TableRowSorter<TableModel> sorter = new TableRowSorter<>(jsFileTableModel);
        jsFileTable.setRowSorter(sorter);

        leftComponent = leftPanel;

        // Disable sorting on all columns to protect the parent-child hierarchy,
        // but keep the sorter for filtering via the search box.
        for (int i = 0; i < jsFileTable.getColumnCount(); i++) {
            sorter.setSortable(i, false);
        }

        // Enable multi-selection
        jsFileTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        // Add right-click menu
        JPopupMenu contextMenu = new JPopupMenu();

        // Option 1: Copy URLs
        JMenuItem copyItem = new JMenuItem("Copy Selected URLs");
        copyItem.addActionListener(e -> copySelectedURLs());

        // Option 2: Delete
        JMenuItem deleteItem = new JMenuItem("Delete Selected");
        deleteItem.addActionListener(e -> deleteSelectedRows());

        // Option 3: Move to Blacklist
        JMenuItem blacklistItem = new JMenuItem("Move to Blacklist");
        blacklistItem.addActionListener(e -> moveToBlacklist());

        contextMenu.add(copyItem);
        contextMenu.add(deleteItem);
        contextMenu.addSeparator();  // Visual separator
        contextMenu.add(blacklistItem);

        jsFileTable.setComponentPopupMenu(contextMenu);


        // Add Ctrl+A for select all
        jsFileTable.getInputMap().put(
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_A, java.awt.event.InputEvent.CTRL_DOWN_MASK),
                "selectAll"
        );
        jsFileTable.getActionMap().put("selectAll", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                jsFileTable.selectAll();
            }
        });


        jsFileTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        jsFileTable.getColumnModel().getColumn(1).setPreferredWidth(500);
        jsFileTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        jsFileTable.getColumnModel().getColumn(3).setPreferredWidth(100);

        jsFileTable.setFillsViewportHeight(true);
        jsFileTable.setShowGrid(false);
        jsFileTable.setIntercellSpacing(new Dimension(0, 0));
        jsFileTable.setRowHeight(22);

        // Configure Status column (column index 3)
        TableColumn statusColumn = jsFileTable.getColumnModel().getColumn(3);
        String[] statusOptions = {"New", "Working", "Later", "Ignore", "Done"};
        JComboBox<String> statusComboBox = new JComboBox<>(statusOptions);
        DefaultCellEditor statusEditor = new DefaultCellEditor(statusComboBox);
        statusEditor.setClickCountToStart(2);
        statusColumn.setCellEditor(statusEditor);

        statusColumn.setCellRenderer(
    new DefaultTableCellRenderer() {
        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {

            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            int modelRow = table.convertRowIndexToModel(row);
            TableRow tableRow = jsFileTableModel.getRowData(modelRow);

            String status = "New";

            // Get status from parent if this is a child row
            if (tableRow != null && !tableRow.isParent()) {
                // Find parent status
                for (int i = modelRow - 1; i >= 0; i--) {
                    TableRow potentialParent = jsFileTableModel.getRowData(i);
                    if (potentialParent != null && potentialParent.isParent()) {
                        status = potentialParent.getStatus();
                        break;
                    }
                }
                // Show category name for child
                setText(value != null ? value.toString() : "");
            } else {
                // Parent row - use its status
                status = value != null ? value.toString() : "New";
                setText(status);
            }

            setHorizontalAlignment(CENTER);
            setFont(new Font("Arial", Font.BOLD, 11));

            if (!isSelected) {
                // Apply color based on status
                switch (status) {
                    case "New":
                        setBackground(new Color(173, 216, 230));
                        setForeground(Color.BLUE);
                        break;
                    case "Working":
                        setBackground(new Color(255, 255, 102));
                        setForeground(new Color(139, 69, 19));
                        break;
                    case "Later":
                        setBackground(new Color(255, 102, 102));
                        setForeground(new Color(139, 0, 0));
                        break;
                    case "Ignore":
                        setBackground(Color.LIGHT_GRAY);
                        setForeground(Color.DARK_GRAY);
                        break;
                    case "Done":
                        setBackground(new Color(144, 238, 144));
                        setForeground(new Color(0, 100, 0));
                        break;
                }
            } else {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground());
            }

            return this;
        }
    }
);

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

        // FIX: FILE/CATEGORY COLUMN - NORMAL BACKGROUND, BOLD PARENT
        jsFileTable.getColumnModel().getColumn(1).setCellRenderer(
            new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(
                    JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {

                    int modelRow = table.convertRowIndexToModel(row);
                    TableRow tableRow = jsFileTableModel.getRowData(modelRow);

                    // For rendering purposes, pretend child rows are never selected.
                    // This removes the blue selection highlight while preserving the
                    // underlying alternating row stripe color.
                    boolean isRowSelected = isSelected && (tableRow != null && tableRow.isParent());

                    super.getTableCellRendererComponent(table, value, isRowSelected, hasFocus, row, column);

                    // Now, apply font and text styling without touching the background color.
                    if (tableRow != null) {
                        if (tableRow.isParent()) {
                            setFont(new Font(getFont().getName(), Font.BOLD, 12));
                            setText(value.toString());
                        } else {
                            setFont(new Font(getFont().getName(), Font.PLAIN, 11));
                            // Indent child rows for clarity
                            setText("    " + value);
                        }
                    }
                    return this;
                }
            }
        );

        leftPanel.add(toolbarPanel, BorderLayout.NORTH);
        leftPanel.add(scrollPane, BorderLayout.CENTER);

        // Step 2: Assign the composite panel directly, removing the outer scroll pane
        leftComponent = leftPanel;
    }

    private void initializeRightPanel() {
        // This is the main component for the right side
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBackground(new Color(60, 63, 65));

        // TOP: Styled search box
        JPanel searchPanel = createRightSearchPanel();

        endpointsTextArea = new JTextArea();
        endpointsTextArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        endpointsTextArea.setEditable(false);
        endpointsTextArea.setBackground(new Color(60, 63, 65));
        endpointsTextArea.setForeground(new Color(220, 220, 220));

        // The text area needs its own scroll pane
        JScrollPane scrollPane = new JScrollPane(endpointsTextArea);

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
        JSChangesTab jsChangesTab = new JSChangesTab(api, allJSFiles);

        tabbedPane.addTab("Links", linksPanel);
        tabbedPane.addTab("JS Changes", jsChangesTab.getComponent());
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

    private void copySelectedURLs() {
        int[] selectedRows = jsFileTable.getSelectedRows();
        if (selectedRows.length == 0) return;

        StringBuilder sb = new StringBuilder();
        for (int viewRow : selectedRows) {
            int modelRow = jsFileTable.convertRowIndexToModel(viewRow);
            TableRow row = jsFileTableModel.getRowData(modelRow);

            if (row != null && row.isParent()) {
                sb.append(row.getJsFileUrl()).append("\n");
            }
        }

        // Copy to clipboard
        java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(sb.toString());
        java.awt.datatransfer.Clipboard clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(selection, selection);

        JOptionPane.showMessageDialog(null, "Copied " + selectedRows.length + " URLs to clipboard");
    }

    private void deleteSelectedRows() {
        int[] selectedRows = jsFileTable.getSelectedRows();
        if (selectedRows.length == 0) return;

        int confirm = JOptionPane.showConfirmDialog(null,
                "Delete " + selectedRows.length + " selected JS files?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            java.util.List<String> deletedUrls = new java.util.ArrayList<>();
            // Delete in reverse order to maintain indices
            for (int i = selectedRows.length - 1; i >= 0; i--) {
                int modelRow = jsFileTable.convertRowIndexToModel(selectedRows[i]);
                TableRow row = jsFileTableModel.getRowData(modelRow);

                if (row != null && row.isParent()) {
                    String jsFileUrl = row.getJsFileUrl();
                    deletedUrls.add(jsFileUrl);

                    // Remove from data
                    allJSFiles.remove(jsFileUrl);
                    jsFileTableModel.removeRow(modelRow);
                }
            }

            dataPersistence.saveData(allJSFiles);
            api.logging().logToOutput("Deleted and persisted: " + deletedUrls.size() + " JS files");

            JOptionPane.showMessageDialog(null, "Deleted " + selectedRows.length + " JS files");
        }
    }

    private void moveToBlacklist() {
        int[] selectedRows = jsFileTable.getSelectedRows();
        if (selectedRows.length == 0) return;

        int confirm = JOptionPane.showConfirmDialog(null,
                "Move " + selectedRows.length + " JS files to blacklist?",
                "Confirm Blacklist",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            java.util.List<String> blacklistedUrls = new java.util.ArrayList<>();

            // Collect URLs
            for (int i = selectedRows.length - 1; i >= 0; i--) {
                int modelRow = jsFileTable.convertRowIndexToModel(selectedRows[i]);
                TableRow row = jsFileTableModel.getRowData(modelRow);

                if (row != null && row.isParent()) {
                    String jsFileUrl = row.getJsFileUrl();
                    blacklistedUrls.add(jsFileUrl);

                    // Remove from Links tab
                    allJSFiles.remove(jsFileUrl);
                    jsFileTableModel.removeRow(modelRow);
                }
            }

            // Add to blacklist (for Blacklist tab - future feature)
            for (String url : blacklistedUrls) {
                // Store in blacklist data structure
                api.persistence().extensionData().setString("blacklist_" + url.hashCode(), url);
            }

            // Persist changes
            dataPersistence.saveData(allJSFiles);

            api.logging().logToOutput("Blacklisted " + blacklistedUrls.size() + " JS files");

            JOptionPane.showMessageDialog(null,
                    "Moved " + blacklistedUrls.size() + " files to blacklist");
        }
    }

    private JPanel createLeftSearchPanel() {
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setBackground(Color.WHITE);
        searchPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(180, 180, 180), 1),
                BorderFactory.createEmptyBorder(2, 5, 2, 5)
        ));

        JLabel searchIcon = new JLabel("🔍");
        searchIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 12));
        searchIcon.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));

        JTextField searchField = new JTextField(15);
        searchField.setFont(new Font("Arial", Font.PLAIN, 11));
        searchField.setBorder(null);
        searchField.setBackground(Color.WHITE);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filterTable(searchField.getText()); }
            public void removeUpdate(DocumentEvent e) { filterTable(searchField.getText()); }
            public void changedUpdate(DocumentEvent e) { filterTable(searchField.getText()); }
        });

        searchPanel.add(searchIcon, BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);

        return searchPanel;
    }

    private void filterTable(String text) {
        if (text.isEmpty()) {
            ((TableRowSorter) jsFileTable.getRowSorter()).setRowFilter(null);
        } else {
            ((TableRowSorter) jsFileTable.getRowSorter()).setRowFilter(
                    RowFilter.regexFilter("(?i)" + text)
            );
        }
    }

private void filterTableByStatus(String status) {
    TableRowSorter<TableModel> sorter =
        (TableRowSorter<TableModel>) jsFileTable.getRowSorter();

    if (status.equals("All")) {
        sorter.setRowFilter(null);
    } else {
        sorter.setRowFilter(new RowFilter<TableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends TableModel, ? extends Integer> entry) {
                int rowIndex = entry.getIdentifier();
                TableRow row = jsFileTableModel.getRowData(rowIndex);

                if (row == null) return false;

                // Show parent if it matches filter
                if (row.isParent()) {
                    return row.getStatus().equals(status);
                }

                // Show child if parent matches filter
                for (int i = rowIndex - 1; i >= 0; i--) {
                    TableRow parentRow = jsFileTableModel.getRowData(i);
                    if (parentRow != null && parentRow.isParent()) {
                        return parentRow.getStatus().equals(status);
                    }
                }

                return false;
            }
        });
    }
}

    private JPanel createRightSearchPanel() {
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setBackground(new Color(60, 63, 65));
        searchPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(80, 83, 85), 1),
                BorderFactory.createEmptyBorder(2, 5, 2, 5)
        ));

        JLabel searchIcon = new JLabel("🔍");
        searchIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 12));
        searchIcon.setForeground(Color.LIGHT_GRAY);
        searchIcon.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));

        JTextField searchField = new JTextField(20);
        searchField.setFont(new Font("Consolas", Font.PLAIN, 11));
        searchField.setBorder(null);
        searchField.setBackground(new Color(60, 63, 65));
        searchField.setForeground(Color.LIGHT_GRAY);
        searchField.setCaretColor(Color.WHITE);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filterEndpoints(searchField.getText()); }
            public void removeUpdate(DocumentEvent e) { filterEndpoints(searchField.getText()); }
            public void changedUpdate(DocumentEvent e) { filterEndpoints(searchField.getText()); }
        });

        searchPanel.add(searchIcon, BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        return searchPanel;
    }

    private void filterEndpoints(String searchText) {
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
}
