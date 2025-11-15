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
        TableRowSorter<javax.swing.table.TableModel> sorter = new TableRowSorter<>(jsFileTable.getModel());
        jsFileTable.setRowSorter(sorter);
        sorter.setComparator(2, new java.util.Comparator<String>() {
            @Override
            public int compare(String s1, String s2) {
                int num1 = extractNumber(s1);
                int num2 = extractNumber(s2);
                return Integer.compare(num1, num2);
            }

            private int extractNumber(String s) {
                if (s == null || s.isEmpty()) return 0;
                try {
                    String num = s.replaceAll("[^0-9]", "");
                    return Integer.parseInt(num);
                } catch (Exception e) {
                    return 0;
                }
            }
        });
        sorter.setComparator(0, java.util.Comparator.comparingInt(o -> (Integer) o));
        jsFileTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        jsFileTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        jsFileTable.getColumnModel().getColumn(1).setPreferredWidth(500);
        jsFileTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        jsFileTable.getColumnModel().getColumn(3).setPreferredWidth(100);
        jsFileTable.getColumnModel().getColumn(1).setCellRenderer(new FileCategoryRenderer());
        jsFileTable.getColumnModel().getColumn(3).setCellEditor(new TagCellEditor());
        jsFileTable.getColumnModel().getColumn(3).setCellRenderer(new TagCellRenderer());

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

        JToggleButton editModeButton = new JToggleButton("Edit Mode");
        editModeButton.addActionListener(e -> {
            boolean editMode = editModeButton.isSelected();
            endpointsTextArea.getTextArea().setEditable(editMode);
            endpointsTextArea.getTextArea().setBackground(editMode ? Color.WHITE : new Color(245, 245, 245));
        });

        JPanel southPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        southPanel.add(editModeButton);
        mainPanel.add(southPanel, BorderLayout.SOUTH);

        return mainPanel;
    }

    public JSFileTableModel getTableModel() {
        return jsFileTableModel;
    }
}
