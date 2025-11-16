package burp;

import burp.api.montoya.MontoyaApi;
import javax.swing.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.swing.table.TableRowSorter;

public class MainTab {

    private final MontoyaApi api;
    private final Map<String, JSFileData> allJSFiles;
    private final JSFileTableModel jsFileTableModel;
    private final LineNumberTextArea endpointsTextArea;
    private final JTable jsFileTable;
    private JScrollPane leftScrollPane;
    private JScrollPane rightScrollPane;
    private JSplitPane splitPane;

    public MainTab(MontoyaApi api, Map<String, JSFileData> allJSFiles) {
        this.api = api;
        this.allJSFiles = allJSFiles;
        this.jsFileTableModel = new JSFileTableModel(allJSFiles);

        initializeLeftPanel();
        initializeRightPanel();
    }

    private void initializeLeftPanel() {
        jsFileTable = new JTable(jsFileTableModel);
        jsFileTable.setFillsViewportHeight(true);
        jsFileTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        TableRowSorter<javax.swing.table.TableModel> sorter = new TableRowSorter<>(jsFileTable.getModel());
        jsFileTable.setRowSorter(sorter);

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

        leftScrollPane = new JScrollPane(jsFileTable);
        leftScrollPane.setPreferredSize(new Dimension(400, 600));
    }

    private void initializeRightPanel() {
        endpointsTextArea = new LineNumberTextArea();
        rightScrollPane = new JScrollPane(endpointsTextArea);
        rightScrollPane.setPreferredSize(new Dimension(400, 600));
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
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setPreferredSize(new Dimension(800, 600));
        mainPanel.setMinimumSize(new Dimension(400, 300));

        splitPane = new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            leftScrollPane,
            rightScrollPane
        );
        splitPane.setDividerLocation(0.4);
        splitPane.setResizeWeight(0.4);
        splitPane.setOneTouchExpandable(true);
        splitPane.setContinuousLayout(true);

        mainPanel.add(splitPane, BorderLayout.CENTER);

        mainPanel.revalidate();
        mainPanel.repaint();

        return mainPanel;
    }

    public JSFileTableModel getTableModel() {
        return jsFileTableModel;
    }
}
