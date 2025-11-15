package burp;

import burp.api.montoya.MontoyaApi;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

public class MainTab {

    private final MontoyaApi api;
    private final Map<String, JSFileData> allJSFiles;
    private final JSFileTableModel jsFileTableModel;
    private final JTextArea endpointsTextArea;
    private final JTable jsFileTable;

    public MainTab(MontoyaApi api, Map<String, JSFileData> allJSFiles) {
        this.api = api;
        this.allJSFiles = allJSFiles;
        this.jsFileTableModel = new JSFileTableModel(allJSFiles);

        jsFileTable = new JTable(jsFileTableModel);
        jsFileTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        endpointsTextArea = new JTextArea();
        endpointsTextArea.setEditable(false);
        endpointsTextArea.setFont(new Font("Consolas", Font.PLAIN, 12));

        jsFileTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = jsFileTable.getSelectedRow();
                if (selectedRow >= 0) {
                    Object rowObject = jsFileTableModel.getRow(selectedRow);
                    if (rowObject instanceof CategoryRow) {
                        updateRightPanel((CategoryRow) rowObject);
                    }
                }
            }
        });

        jsFileTable.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                int row = jsFileTable.rowAtPoint(evt.getPoint());
                if (row >= 0 && jsFileTable.columnAtPoint(evt.getPoint()) == 0) {
                    jsFileTableModel.toggleRow(row);
                }
            }
        });
    }

    private void updateRightPanel(CategoryRow categoryRow) {
        StringBuilder sb = new StringBuilder();
        sb.append("Category: ").append(categoryRow.getCategoryName()).append("\n");
        sb.append("Total: ").append(categoryRow.getEndpointCount()).append(" endpoints\n\n");

        for (String endpoint : categoryRow.getEndpoints()) {
            sb.append(endpoint).append("\n");
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

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(splitPane, BorderLayout.CENTER);

        // Add other controls like checkboxes or buttons here if needed

        return mainPanel;
    }

    public JSFileTableModel getTableModel() {
        return jsFileTableModel;
    }
}
