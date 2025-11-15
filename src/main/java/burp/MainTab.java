package burp;

import burp.api.montoya.MontoyaApi;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class MainTab {

    private final MontoyaApi api;
    private final Map<String, JSFileData> allJSFiles;
    private final JSFileTableModel jsFileTableModel;
    private final LineNumberTextArea endpointsTextArea;
    private final JTable jsFileTable;

    public MainTab(MontoyaApi api, Map<String, JSFileData> allJSFiles) {
        this.api = api;
        this.allJSFiles = allJSFiles;
        this.jsFileTableModel = new JSFileTableModel(allJSFiles);

        jsFileTable = new JTable(jsFileTableModel);
        jsFileTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        jsFileTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        jsFileTable.getColumnModel().getColumn(1).setPreferredWidth(500);
        jsFileTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        jsFileTable.getColumnModel().getColumn(3).setPreferredWidth(100);
        jsFileTable.getColumnModel().getColumn(1).setCellRenderer(new FileCategoryRenderer());

        endpointsTextArea = new LineNumberTextArea();

        jsFileTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = jsFileTable.getSelectedRow();
                if (selectedRow >= 0) {
                    Object rowObject = jsFileTableModel.getRow(selectedRow);
                    if (rowObject instanceof CategoryRow) {
                        updateRightPanel((CategoryRow) rowObject, (JSFileRow) jsFileTableModel.getRow(selectedRow - 1));
                    }
                }
            }
        });

        jsFileTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = jsFileTable.rowAtPoint(e.getPoint());
                if (row >= 0) {
                    if (e.getClickCount() == 2 || (e.getClickCount() == 1 && jsFileTable.columnAtPoint(e.getPoint()) == 0)) {
                        jsFileTableModel.toggleRow(row);
                    }
                }
            }
        });
    }

    private void updateRightPanel(CategoryRow categoryRow, JSFileRow parentRow) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm a");
        String timestamp = sdf.format(new Date(parentRow.getJsFileData().getLastScanTimestamp()));

        StringBuilder sb = new StringBuilder();
        sb.append("Category: ").append(categoryRow.getCategoryName()).append(" [").append(categoryRow.getEndpointCount()).append(" endpoints] | ");
        sb.append(categoryRow.getCategoryName().equals("Latest") ? "Last Update: " : "Discovered: ").append(timestamp).append("\n");
        sb.append("Source: ").append(parentRow.getJsFileData().getJsFileUrl()).append("\n");
        sb.append("Total: ").append(categoryRow.getEndpointCount()).append(categoryRow.getCategoryName().equals("Latest") ? " new endpoints\n\n" : " endpoints\n\n");

        for (String endpoint : categoryRow.getEndpoints()) {
            sb.append(endpoint).append("\n");
        }

        endpointsTextArea.setText(sb.toString());
    }

    public Component getComponent() {
        JSplitPane splitPane = new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            new JScrollPane(jsFileTable),
            endpointsTextArea
        );
        splitPane.setDividerLocation(0.4);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(splitPane, BorderLayout.CENTER);

        return mainPanel;
    }

    public JSFileTableModel getTableModel() {
        return jsFileTableModel;
    }
}
