package burp;

import burp.api.montoya.MontoyaApi;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class MainTab {

    private final MontoyaApi api;
    private final ConcurrentHashMap<String, List<Endpoint>> currentData;
    private final JSFileTableModel jsFileTableModel;
    private final JTextArea endpointsTextArea;
    private final JTable jsFileTable;

    public MainTab(MontoyaApi api, ConcurrentHashMap<String, List<Endpoint>> currentData) {
        this.api = api;
        this.currentData = currentData;
        this.jsFileTableModel = new JSFileTableModel(currentData);

        // Create table for JS files
        jsFileTable = new JTable(jsFileTableModel);
        jsFileTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane leftScrollPane = new JScrollPane(jsFileTable);

        // Create text area for endpoints
        endpointsTextArea = new JTextArea();
        endpointsTextArea.setEditable(false);
        endpointsTextArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        JScrollPane rightScrollPane = new JScrollPane(endpointsTextArea);

        // Add selection listener
        jsFileTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = jsFileTable.getSelectedRow();
                if (selectedRow >= 0) {
                    String jsFileUrl = jsFileTableModel.getJSFileUrl(selectedRow);
                    List<Endpoint> endpoints = jsFileTableModel.getEndpoints(jsFileUrl);
                    updateRightPanel(jsFileUrl, endpoints);
                }
            }
        });
    }

    private void updateRightPanel(String jsFileUrl, List<Endpoint> endpoints) {
        StringBuilder sb = new StringBuilder();
        sb.append("Endpoints from: ").append(jsFileUrl).append("\n");
        sb.append("Total: ").append(endpoints.size()).append(" endpoints\n\n");

        for (Endpoint endpoint : endpoints) {
            sb.append(endpoint.getUrl()).append("\n");
        }

        endpointsTextArea.setText(sb.toString());
        endpointsTextArea.setCaretPosition(0);
    }

    public Component getComponent() {
        JSplitPane splitPane = new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            new JScrollPane(jsFileTable),
            new JScrollPane(endpointsTextArea)
        );
        splitPane.setDividerLocation(0.4);
        return splitPane;
    }

    public JSFileTableModel getTableModel() {
        return jsFileTableModel;
    }
}
