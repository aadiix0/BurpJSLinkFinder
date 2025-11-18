package burp;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class JSChangesTab {

    private final MontoyaApi api;
    private Map<String, List<JSFileSnapshot>> snapshotHistory;  // URL -> List of snapshots
    private Map<String, JSFileData> allJSFilesData;

    private JTable changesTable;
    private DefaultTableModel tableModel;
    private JEditorPane diffViewer;
    private JComboBox<String> viewModeCombo;
    private JComboBox<String> filterCombo;
    private JButton snapshotBtn;
    private JButton compareBtn;
    private JButton exportBtn;
    private JCheckBox autoCompareCheckbox;

    private String currentFilter = "All";
    private String currentViewMode = "Endpoint Diff";

    public JSChangesTab(MontoyaApi api, Map<String, JSFileData> allJSFilesData) {
        this.api = api;
        this.allJSFilesData = allJSFilesData;
        this.snapshotHistory = new ConcurrentHashMap<>();
        loadSnapshotsFromDisk();
    }

    public Component getComponent() {
        JPanel mainPanel = new JPanel(new BorderLayout());

        // Toolbar
        JPanel toolbar = createToolbar();

        // Split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            createLeftPanel(),
            createRightPanel()
        );
        splitPane.setDividerLocation(450);

        mainPanel.add(toolbar, BorderLayout.NORTH);
        mainPanel.add(splitPane, BorderLayout.CENTER);

        return mainPanel;
    }

    private JPanel createToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        toolbar.setBackground(new Color(240, 240, 240));

        // Snapshot button
        snapshotBtn = new JButton("📸 Snapshot");
        snapshotBtn.setToolTipText("Save current version as baseline");
        snapshotBtn.addActionListener(e -> createSnapshot());

        // Compare button
        compareBtn = new JButton("🔍 Compare");
        compareBtn.setToolTipText("Compare with baseline");
        compareBtn.addActionListener(e -> compareWithBaseline());

        // Export button
        exportBtn = new JButton("💾 Export");
        exportBtn.setToolTipText("Export changes to JSON");
        exportBtn.addActionListener(e -> exportChanges());

        // Clear button
        JButton clearBtn = new JButton("🗑️ Clear");
        clearBtn.setToolTipText("Delete all snapshots");
        clearBtn.addActionListener(e -> clearSnapshots());

        // Filter dropdown
        filterCombo = new JComboBox<>(new String[]{"All", "Added Only", "Removed Only", "Changed", "Unchanged"});
        filterCombo.setToolTipText("Filter by change type");
        filterCombo.addActionListener(e -> applyFilter());

        // View mode dropdown
        viewModeCombo = new JComboBox<>(new String[]{"Endpoint Diff", "Side-by-Side Code", "Timeline"});
        viewModeCombo.setToolTipText("Change view mode");
        viewModeCombo.addActionListener(e -> changeViewMode());

        // Auto-compare checkbox
        autoCompareCheckbox = new JCheckBox("Auto-compare on refresh");
        autoCompareCheckbox.setToolTipText("Automatically compare when refresh button is clicked");

        toolbar.add(snapshotBtn);
        toolbar.add(compareBtn);
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));
        toolbar.add(exportBtn);
        toolbar.add(clearBtn);
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));
        toolbar.add(new JLabel("Filter:"));
        toolbar.add(filterCombo);
        toolbar.add(new JLabel("View:"));
        toolbar.add(viewModeCombo);
        toolbar.add(new JSeparator(SwingConstants.VERTICAL));
        toolbar.add(autoCompareCheckbox);

        return toolbar;
    }

    private JPanel createLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Table
        String[] columns = {"#", "JS File", "Snapshots", "Last Status"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        changesTable = new JTable(tableModel);
        changesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        changesTable.getColumnModel().getColumn(0).setPreferredWidth(30);
        changesTable.getColumnModel().getColumn(1).setPreferredWidth(250);
        changesTable.getColumnModel().getColumn(2).setPreferredWidth(70);
        changesTable.getColumnModel().getColumn(3).setPreferredWidth(100);

        changesTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onTableSelectionChanged();
            }
        });

        // Populate table with JS files
        refreshTable();

        panel.add(new JScrollPane(changesTable), BorderLayout.CENTER);

        return panel;
    }

    private JPanel createRightPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        diffViewer = new JEditorPane();
        diffViewer.setContentType("text/html");
        diffViewer.setEditable(false);
        diffViewer.setText(getWelcomeHtml());

        panel.add(new JScrollPane(diffViewer), BorderLayout.CENTER);

        return panel;
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        int index = 1;

        for (String jsUrl : allJSFilesData.keySet()) {
            List<JSFileSnapshot> history = snapshotHistory.get(jsUrl);
            int snapshotCount = history != null ? history.size() : 0;
            String status = getStatusForUrl(jsUrl);

            tableModel.addRow(new Object[]{
                index++,
                truncateUrl(jsUrl, 50),
                snapshotCount > 0 ? snapshotCount + " v" : "No snapshots",
                status
            });
        }
    }

    private void createSnapshot() {
        int selectedRow = changesTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(null, "Select a JS file first");
            return;
        }

        String jsUrl = getFullUrlFromRow(selectedRow);

        // Fetch current content
        try {
            HttpRequest request = HttpRequest.httpRequestFromUrl(jsUrl);
            HttpResponse response = api.http().sendRequest(request).response();

            if (response != null) {
                String content = response.bodyToString();
                List<String> endpoints = LinkParser.findEndpoints(content).stream()
                                                .map(Endpoint::getUrl)
                                                .collect(Collectors.toList());

                List<JSFileSnapshot> history = snapshotHistory.computeIfAbsent(jsUrl, k -> new ArrayList<>());
                int version = history.size() + 1;

                JSFileSnapshot snapshot = new JSFileSnapshot(jsUrl, content, endpoints, version);
                history.add(snapshot);

                saveSnapshotsToDisk();
                refreshTable();

                api.logging().logToOutput("✓ Snapshot v" + version + " created for: " + jsUrl);
                JOptionPane.showMessageDialog(null, "Snapshot v" + version + " created successfully!");
            }
        } catch (Exception e) {
            api.logging().logToError("Error creating snapshot: " + e.getMessage());
        }
    }

    private void compareWithBaseline() {
        int selectedRow = changesTable.getSelectedRow();
        if (selectedRow == -1) return;

        String jsUrl = getFullUrlFromRow(selectedRow);
        List<JSFileSnapshot> history = snapshotHistory.get(jsUrl);

        if (history == null || history.isEmpty()) {
            JOptionPane.showMessageDialog(null, "No baseline snapshot. Create one first.");
            return;
        }

        JSFileSnapshot baseline = history.get(0);  // First snapshot is baseline

        // Fetch current version
        try {
            HttpRequest request = HttpRequest.httpRequestFromUrl(jsUrl);
            HttpResponse response = api.http().sendRequest(request).response();

            if (response != null) {
                String content = response.bodyToString();
                List<String> endpoints = LinkParser.findEndpoints(content).stream()
                                               .map(Endpoint::getUrl)
                                               .collect(Collectors.toList());

                JSFileSnapshot current = new JSFileSnapshot(jsUrl, content, endpoints, history.size() + 1);

                // Compare
                String selectedFilter = (String) filterCombo.getSelectedItem();
                DiffEngine.DiffResult diff = DiffEngine.compareSnapshots(baseline, current, selectedFilter);

                // Display based on view mode
                displayDiff(diff);

                // Update status
                String status = diff.getTotalChanges() == 0 ? "✓ No Changes" :
                               "⚠️ " + diff.addedEndpoints.size() + " added, " +
                               diff.removedEndpoints.size() + " removed";
                tableModel.setValueAt(status, selectedRow, 3);
            }
        } catch (Exception e) {
            api.logging().logToError("Error comparing: " + e.getMessage());
        }
    }

    private void displayDiff(DiffEngine.DiffResult diff) {
        String viewMode = (String) viewModeCombo.getSelectedItem();

        switch (viewMode) {
            case "Endpoint Diff":
                diffViewer.setText(diff.diffHtml);
                break;
            case "Side-by-Side Code":
                diffViewer.setText(diff.sideBySideHtml);
                break;
            case "Timeline":
                displayTimeline();
                break;
        }

        diffViewer.setCaretPosition(0);
    }

    private void displayTimeline() {
        int selectedRow = changesTable.getSelectedRow();
        if (selectedRow == -1) return;

        String jsUrl = getFullUrlFromRow(selectedRow);
        List<JSFileSnapshot> history = snapshotHistory.get(jsUrl);

        if (history == null || history.isEmpty()) {
            diffViewer.setText("<html><body><h2>No snapshot history</h2></body></html>");
            return;
        }

        StringBuilder html = new StringBuilder();
        html.append("<html><head><style>");
        html.append("body { font-family: Arial; padding: 20px; background: #1e1e1e; color: #d4d4d4; }");
        html.append(".timeline { position: relative; padding-left: 30px; }");
        html.append(".snapshot { margin: 20px 0; padding: 15px; background: #252526; border-left: 3px solid #007acc; }");
        html.append(".version { font-size: 18px; font-weight: bold; color: #4ec9b0; }");
        html.append(".timestamp { color: #858585; font-size: 12px; }");
        html.append(".stats { margin-top: 10px; }");
        html.append("</style></head><body>");

        html.append("<h2>📅 Snapshot Timeline</h2>");
        html.append("<div class='timeline'>");

        for (int i = history.size() - 1; i >= 0; i--) {
            JSFileSnapshot snapshot = history.get(i);
            html.append("<div class='snapshot'>");
            html.append("<div class='version'>Version ").append(snapshot.getVersion()).append("</div>");
            html.append("<div class='timestamp'>").append(snapshot.getFormattedTimestamp()).append("</div>");
            html.append("<div class='stats'>");
            html.append("📊 Endpoints: ").append(snapshot.getEndpoints().size());
            html.append(" | 🔖 Hash: ").append(snapshot.getHash().substring(0, 8)).append("...");
            html.append("</div>");
            html.append("</div>");
        }

        html.append("</div></body></html>");
        diffViewer.setText(html.toString());
    }

    private void exportChanges() {
        int selectedRow = changesTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(null, "Select a JS file first");
            return;
        }

        String jsUrl = getFullUrlFromRow(selectedRow);
        List<JSFileSnapshot> history = snapshotHistory.get(jsUrl);

        if (history == null || history.size() < 2) {
            JOptionPane.showMessageDialog(null, "Need at least 2 snapshots to export diff");
            return;
        }

        JSFileSnapshot baseline = history.get(0);
        JSFileSnapshot current = history.get(history.size() - 1);

        DiffEngine.DiffResult diff = DiffEngine.compareSnapshots(baseline, current, "All");

        // Create JSON
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"js_file\": \"").append(jsUrl).append("\",\n");
        json.append("  \"baseline_version\": ").append(baseline.getVersion()).append(",\n");
        json.append("  \"baseline_timestamp\": \"").append(baseline.getFormattedTimestamp()).append("\",\n");
        json.append("  \"current_version\": ").append(current.getVersion()).append(",\n");
        json.append("  \"current_timestamp\": \"").append(current.getFormattedTimestamp()).append("\",\n");
        json.append("  \"added_endpoints\": [\n");
        for (int i = 0; i < diff.addedEndpoints.size(); i++) {
            json.append("    \"").append(diff.addedEndpoints.get(i)).append("\"");
            if (i < diff.addedEndpoints.size() - 1) json.append(",");
            json.append("\n");
        }
        json.append("  ],\n");
        json.append("  \"removed_endpoints\": [\n");
        for (int i = 0; i < diff.removedEndpoints.size(); i++) {
            json.append("    \"").append(diff.removedEndpoints.get(i)).append("\"");
            if (i < diff.removedEndpoints.size() - 1) json.append(",");
            json.append("\n");
        }
        json.append("  ]\n");
        json.append("}\n");

        // Save file dialog
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File("js-changes-" + System.currentTimeMillis() + ".json"));

        if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            try {
                Files.write(fileChooser.getSelectedFile().toPath(), json.toString().getBytes());
                JOptionPane.showMessageDialog(null, "Changes exported successfully!");
            } catch (IOException e) {
                api.logging().logToError("Export failed: " + e.getMessage());
            }
        }
    }

    private void applyFilter() {
        currentFilter = (String) filterCombo.getSelectedItem();
        // Re-display current diff with filter
        int selectedRow = changesTable.getSelectedRow();
        if (selectedRow != -1) {
            compareWithBaseline();
        }
    }

    private void changeViewMode() {
        currentViewMode = (String) viewModeCombo.getSelectedItem();
        int selectedRow = changesTable.getSelectedRow();
        if (selectedRow != -1) {
            compareWithBaseline();
        }
    }

    private void clearSnapshots() {
        int confirm = JOptionPane.showConfirmDialog(null,
            "Delete all snapshots? This cannot be undone.",
            "Confirm",
            JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            snapshotHistory.clear();
            saveSnapshotsToDisk();
            refreshTable();
        }
    }

    private void onTableSelectionChanged() {
        int selectedRow = changesTable.getSelectedRow();
        if (selectedRow != -1 && autoCompareCheckbox.isSelected()) {
            compareWithBaseline();
        }
    }

    // Helper methods
    private String getFullUrlFromRow(int row) {
        // Reconstruct full URL from table (you'll need to maintain a mapping)
        // For now, simplified:
        String truncated = (String) tableModel.getValueAt(row, 1);
        for (String url : allJSFilesData.keySet()) {
            if (url.contains(truncated) || truncated.contains(url.substring(Math.max(0, url.length() - 50)))) {
                return url;
            }
        }
        return null;
    }

    private String truncateUrl(String url, int maxLength) {
        if (url.length() <= maxLength) return url;
        return "..." + url.substring(url.length() - maxLength);
    }

    private String getStatusForUrl(String jsUrl) {
        List<JSFileSnapshot> history = snapshotHistory.get(jsUrl);
        if (history == null || history.isEmpty()) return "📸 No snapshots";
        if (history.size() == 1) return "✓ Baseline saved";
        return "📊 " + history.size() + " versions";
    }

    private String getWelcomeHtml() {
        return "<html><body style='font-family: Arial; padding: 40px; text-align: center;'>" +
               "<h1>🔍 JS Changes Tracker</h1>" +
               "<p>Select a JS file and click <strong>📸 Snapshot</strong> to save a baseline.</p>" +
               "<p>Then click <strong>🔍 Compare</strong> to see what changed.</p>" +
               "</body></html>";
    }

    // Persistence
    private void saveSnapshotsToDisk() {
        try {
            File dataFile = new File(System.getProperty("user.home"), ".burp-js-snapshots.dat");
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(dataFile))) {
                oos.writeObject(snapshotHistory);
            }
        } catch (Exception e) {
            api.logging().logToError("Failed to save snapshots: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadSnapshotsFromDisk() {
        try {
            File dataFile = new File(System.getProperty("user.home"), ".burp-js-snapshots.dat");
            if (dataFile.exists()) {
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(dataFile))) {
                    snapshotHistory = (Map<String, List<JSFileSnapshot>>) ois.readObject();
                }
            }
        } catch (Exception e) {
            api.logging().logToError("Failed to load snapshots: " + e.getMessage());
            snapshotHistory = new ConcurrentHashMap<>();
        }
    }
}
