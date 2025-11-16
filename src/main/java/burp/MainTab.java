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
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;

public class MainTab {

    private final MontoyaApi api;
    private final Map<String, JSFileData> allJSFiles;
    private final JSFileTableModel jsFileTableModel;
    private JTable jsFileTable;
    private LineNumberTextArea endpointsTextArea;
    private JScrollPane leftScrollPane;
    private JScrollPane rightScrollPane;

    public MainTab(MontoyaApi api, ConcurrentHashMap<String, JSFileData> allJSFiles) {
        this.api = api;
        this.allJSFiles = allJSFiles;

        try {
            // Initialize model
            this.jsFileTableModel = new JSFileTableModel(allJSFiles);

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

        TableRowSorter<javax.swing.table.TableModel> sorter = new TableRowSorter<>(jsFileTable.getModel());
        jsFileTable.setRowSorter(sorter);

        sorter.setComparator(0, (Comparator<Object>) (o1, o2) -> {
            if (o1 == null || o2 == null) return 0;
            try {
                Integer num1 = (Integer) o1;
                Integer num2 = (Integer) o2;
                return num1.compareTo(num2);
            } catch (Exception e) {
                return 0;
            }
        });

        sorter.setComparator(2, (Comparator<Object>) (o1, o2) -> {
            int num1 = extractNumberFromCount(o1);
            int num2 = extractNumberFromCount(o2);
            return Integer.compare(num1, num2);
        });

        jsFileTable.setAutoCreateRowSorter(false);

        jsFileTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        jsFileTable.getColumnModel().getColumn(1).setPreferredWidth(500);
        jsFileTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        jsFileTable.getColumnModel().getColumn(3).setPreferredWidth(100);
        jsFileTable.getColumnModel().getColumn(1).setCellRenderer(new FileCategoryRenderer());
        jsFileTable.getColumnModel().getColumn(3).setCellEditor(new TagCellEditor());
        jsFileTable.getColumnModel().getColumn(3).setCellRenderer(new TagCellRenderer());

        jsFileTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = jsFileTable.getSelectedRow();
                if (selectedRow >= 0) {
                    int modelRow = jsFileTable.convertRowIndexToModel(selectedRow);
                    TableRow rowObject = jsFileTableModel.getRow(modelRow);
                    if (rowObject != null && !rowObject.isParent()) {
                        for (int i = modelRow - 1; i >= 0; i--) {
                            if (jsFileTableModel.getRow(i).isParent()) {
                                updateRightPanel(rowObject, jsFileTableModel.getRow(i));
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

        leftScrollPane = new JScrollPane(jsFileTable);
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

    private void initializeRightPanel() {
        endpointsTextArea = new LineNumberTextArea();
        rightScrollPane = new JScrollPane(endpointsTextArea);
    }

    private void updateRightPanel(TableRow categoryRow, TableRow parentRow) {
        JSFileData jsFileData = allJSFiles.get(parentRow.getJsFileUrl());
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm a");
        String timestamp = sdf.format(new Date(jsFileData.getLastScanTimestamp()));

        StringBuilder sb = new StringBuilder();
        sb.append("Category: ").append(categoryRow.getCategory()).append(" [").append(categoryRow.getCount()).append(" endpoints] | ");
        sb.append(categoryRow.getCategory().equals("Latest") ? "Last Update: " : "Discovered: ").append(timestamp).append("\n");
        sb.append("Source: ").append(parentRow.getJsFileUrl()).append("\n");
        sb.append("Total: ").append(categoryRow.getCount()).append(categoryRow.getCategory().equals("Latest") ? " new endpoints\n\n" : " endpoints\n\n");

        List<String> endpoints = categoryRow.getCategory().equals("Latest") ? jsFileData.getLatest() : jsFileData.getFirstFinding();
        for (String endpoint : endpoints) {
            sb.append(endpoint).append("\n");
        }

        endpointsTextArea.setText(sb.toString());
    }

    public Component getComponent() {
        try {
            JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                leftScrollPane,
                rightScrollPane
            );
            splitPane.setDividerLocation(400);
            splitPane.setResizeWeight(0.4);

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

            mainPanel.revalidate();
            return mainPanel;

        } catch (Exception e) {
            api.logging().logToError("Error creating component: " + e.getMessage(), e);
            return new JLabel("Error loading JSLink Finder");
        }
    }

    public JSFileTableModel getTableModel() {
        return jsFileTableModel;
    }
}
