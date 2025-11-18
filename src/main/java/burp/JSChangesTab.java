package burp;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class JSChangesTab {
    private final MontoyaApi api;
    private final Map<String, JSFileSnapshot> snapshots;
    private final Map<String, JSFileData> allJSFiles;
    private JTable changesTable;
    private JEditorPane diffViewer;

    public JSChangesTab(MontoyaApi api, Map<String, JSFileData> allJSFiles) {
        this.api = api;
        this.allJSFiles = allJSFiles;
        this.snapshots = new ConcurrentHashMap<>();
    }

    public Component getComponent() {
        JPanel mainPanel = new JPanel(new BorderLayout());

        // LEFT: Table of JS files with snapshot status
        JPanel leftPanel = createLeftPanel();

        // RIGHT: Diff viewer
        JPanel rightPanel = createDiffViewerPanel();

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                                              leftPanel, rightPanel);
        splitPane.setDividerLocation(400);

        mainPanel.add(splitPane, BorderLayout.CENTER);

        // Populate the table with initial data
        populateTable();

        return mainPanel;
    }

    private void populateTable() {
        DefaultTableModel model = (DefaultTableModel) changesTable.getModel();
        for (String jsFileUrl : allJSFiles.keySet()) {
            model.addRow(new Object[]{jsFileUrl, "", "No Baseline"});
        }
    }

    private JPanel createLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));

        JButton snapshotBtn = new JButton("📸 Snapshot");
        snapshotBtn.setToolTipText("Save current version as baseline");
        snapshotBtn.addActionListener(e -> createSnapshot());

        JButton compareBtn = new JButton("🔍 Compare");
        compareBtn.setToolTipText("Compare with baseline");
        compareBtn.addActionListener(e -> compareWithBaseline());

        JButton clearBtn = new JButton("🗑 Clear");
        clearBtn.setToolTipText("Clear snapshot");
        clearBtn.addActionListener(e -> clearSnapshot());

        toolbar.add(snapshotBtn);
        toolbar.add(compareBtn);
        toolbar.add(clearBtn);

        // Table: JS URL | Snapshot Date | Status
        String[] columns = {"JS File", "Snapshot Date", "Status"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        changesTable = new JTable(model);

        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(new JScrollPane(changesTable), BorderLayout.CENTER);

        return panel;
    }

    private JPanel createDiffViewerPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        diffViewer = new JEditorPane();
        diffViewer.setContentType("text/html");
        diffViewer.setEditable(false);

        panel.add(new JScrollPane(diffViewer), BorderLayout.CENTER);

        return panel;
    }

    private void createSnapshot() {
        int selectedRow = changesTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(null, "Select a JS file first");
            return;
        }

        String jsUrl = (String) changesTable.getValueAt(selectedRow, 0);

        // Fetch current JS content
        HttpRequest request = HttpRequest.httpRequestFromUrl(jsUrl);
        HttpResponse response = api.http().sendRequest(request).response();

        if (response != null) {
            String content = response.bodyToString();
            List<String> endpoints = LinkParser.findEndpoints(content).stream()
                                                .map(Endpoint::getUrl)
                                                .collect(Collectors.toList());

            JSFileSnapshot snapshot = new JSFileSnapshot(jsUrl, content, endpoints);
            snapshots.put(jsUrl, snapshot);

            // Update table
            changesTable.setValueAt(snapshot.getTimestamp().toString(), selectedRow, 1);
            changesTable.setValueAt("Baseline Saved", selectedRow, 2);

            api.logging().logToOutput("Snapshot created for: " + jsUrl);
        }
    }

    private void compareWithBaseline() {
        int selectedRow = changesTable.getSelectedRow();
        if (selectedRow == -1) return;

        String jsUrl = (String) changesTable.getValueAt(selectedRow, 0);
        JSFileSnapshot baseline = snapshots.get(jsUrl);

        if (baseline == null) {
            JOptionPane.showMessageDialog(null, "No baseline snapshot exists. Create one first.");
            return;
        }

        // Fetch current version
        HttpRequest request = HttpRequest.httpRequestFromUrl(jsUrl);
        HttpResponse response = api.http().sendRequest(request).response();

        if (response != null) {
            String content = response.bodyToString();
            List<String> endpoints = LinkParser.findEndpoints(content).stream()
                                               .map(Endpoint::getUrl)
                                               .collect(Collectors.toList());

            JSFileSnapshot current = new JSFileSnapshot(jsUrl, content, endpoints);

            // Compare
            DiffEngine.DiffResult diff = DiffEngine.compareSnapshots(baseline, current);

            // Display diff
            diffViewer.setText(diff.diffHtml);

            // Update status
            if (diff.addedEndpoints.isEmpty() && diff.removedEndpoints.isEmpty()) {
                changesTable.setValueAt("✓ No Changes", selectedRow, 2);
            } else {
                changesTable.setValueAt("⚠ " + diff.addedEndpoints.size() + " added, " +
                                       diff.removedEndpoints.size() + " removed", selectedRow, 2);
            }
        }
    }

    private void clearSnapshot() {
        int selectedRow = changesTable.getSelectedRow();
        if (selectedRow == -1) return;

        String jsUrl = (String) changesTable.getValueAt(selectedRow, 0);
        snapshots.remove(jsUrl);

        changesTable.setValueAt("", selectedRow, 1);
        changesTable.setValueAt("No Baseline", selectedRow, 2);
    }
}
