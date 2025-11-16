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
import javax.swing.table.TableRowSorter;

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
        TableRowSorter<javax.swing.table.TableModel> sorter = new TableRowSorter<>(jsFileTable.getModel());
        jsFileTable.setRowSorter(sorter);
        sorter.setComparator(0, new java.util.Comparator<Object>() {
            @Override
            public int compare(Object o1, Object o2) {
                if (o1 == null || o2 == null) return 0;
                try {
                    Integer num1 = (Integer) o1;
                    Integer num2 = (Integer) o2;
                    return num1.compareTo(num2);
                } catch (Exception e) {
                    return 0;
                }
            }
        });
        sorter.setComparator(2, new java.util.Comparator<Object>() {
            @Override
            public int compare(Object o1, Object o2) {
                int num1 = extractNumberFromCount(o1);
                int num2 = extractNumberFromCount(o2);
                return Integer.compare(num1, num2);
            }

            private int extractNumberFromCount(Object obj) {
                if (obj == null) return 0;
                String str = obj.toString();
                try {
                    str = str.replaceAll("[^0-9]", "");
                    if (str.isEmpty()) return 0;
                    return Integer.parseInt(str);
                } catch (Exception e) {
                    return 0;
                }
            }
        });
        jsFileTable.setAutoCreateRowSorter(false);
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
                        // Find parent row
                        int modelRow = jsFileTable.convertRowIndexToModel(selectedRow);
                        for (int i = modelRow - 1; i >= 0; i--) {
                            if (jsFileTableModel.getRow(i) instanceof JSFileRow) {
                                updateRightPanel((CategoryRow) rowObject, (JSFileRow) jsFileTableModel.getRow(i));
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
                int row = jsFileTable.rowAtPoint(e.getPoint());
                if (row >= 0) {
                    int modelRow = jsFileTable.convertRowIndexToModel(row);
                    if (e.getClickCount() == 2) {
                        jsFileTableModel.toggleRow(modelRow);
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
