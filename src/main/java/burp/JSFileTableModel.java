package burp;

import burp.api.montoya.MontoyaApi;
import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class JSFileTableModel extends AbstractTableModel {
    private final List<TableRow> rows;
    private final String[] columnNames = {"#", "File / Category", "Count", "Status"};
    private final Map<String, JSFileData> allJSFilesData;
    private final MontoyaApi api;

    public JSFileTableModel(Map<String, JSFileData> allJSFilesData, MontoyaApi api) {
        this.allJSFilesData = allJSFilesData;
        this.api = api;
        this.rows = new ArrayList<>();
        initializeRows();
    }

    private void initializeRows() {
        rows.clear();
        int rowNum = 1;
        for (Map.Entry<String, JSFileData> entry : allJSFilesData.entrySet()) {
            String jsFileUrl = entry.getKey();
            JSFileData jsData = entry.getValue();
            int totalCount = jsData.getFirstFinding().size() + jsData.getLatest().size();
            TableRow parentRow = new TableRow(rowNum++, jsFileUrl, totalCount, "New");
            rows.add(parentRow);
        }
    }

    public void addJSFile(String jsFileUrl, List<Endpoint> endpoints) {

        api.logging().logToOutput("=== Adding JS file: " + jsFileUrl);
        api.logging().logToOutput("=== Number of endpoints: " + endpoints.size());
        api.logging().logToOutput("=== Current row count: " + rows.size());

        // Check if this JS file already exists
        for (TableRow row : rows) {
            if (row.isParent() && row.getJsFileUrl().equals(jsFileUrl)) {
                api.logging().logToOutput("=== JS file already exists, skipping");
                return;  // Already exists, don't add again
            }
        }

        // Convert endpoints to strings
        List<String> endpointUrls = new ArrayList<>();
        for (Endpoint ep : endpoints) {
            endpointUrls.add(ep.getUrl());
        }

        // Create JSFileData for this JS file
        JSFileData jsData = new JSFileData(jsFileUrl, endpointUrls);
        allJSFilesData.put(jsFileUrl, jsData);

        // Create NEW parent row
        int rowNum = rows.size() + 1;
        TableRow newParentRow = new TableRow(rowNum, jsFileUrl, endpoints.size(), "New");

        // Add to rows list
        rows.add(newParentRow);

        api.logging().logToOutput("=== Added new parent row");
        api.logging().logToOutput("=== New row count: " + rows.size());

        // Notify table
        fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
    }

    private int getNextRowNumber() {
        int maxNum = 0;
        for (TableRow row : rows) {
            if (row.isParent() && row.getRowNumber() > maxNum) {
                maxNum = row.getRowNumber();
            }
        }
        return maxNum + 1;
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    @Override
    public String getColumnName(int column) {
        return columnNames[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        TableRow row = getRowData(rowIndex);
        if (row == null) return "";

        switch (columnIndex) {
            case 0: return row.isParent() ? row.getRowNumber() : "";
            case 1: return row.getDisplayText();
            case 2: return row.isParent() ? "[" + row.getCount() + " total]" : "";
            case 3: return row.isParent() ? row.getStatus() : "";
            default: return "";
        }
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        if (columnIndex == 3) {
            TableRow row = getRowData(rowIndex);
            return row != null && row.isParent();
        }
        return false;
    }

    @Override
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
        if (columnIndex == 3 && rowIndex >= 0 && rowIndex < rows.size()) {
            TableRow row = rows.get(rowIndex);
            if (row.isParent()) {
                row.setStatus(value.toString());
                fireTableCellUpdated(rowIndex, columnIndex);

                api.logging().logToOutput("Status changed to: " + value + " for row " + rowIndex);
            }
        }
    }

    public TableRow getRowData(int rowIndex) {
        if (rowIndex >= 0 && rowIndex < rows.size()) {
            return rows.get(rowIndex);
        }
        return null;
    }

    public TableRow getRow(int rowIndex) {
        if (rowIndex >= 0 && rowIndex < rows.size()) {
            return rows.get(rowIndex);
        }
        return null;
    }

    public void toggleRow(int rowIndex) {
        TableRow parentRow = rows.get(rowIndex);

        if (!parentRow.isExpanded()) {
            // Get endpoints for THIS JS file only
            String jsFileUrl = parentRow.getJsFileUrl();
            JSFileData jsData = allJSFilesData.get(jsFileUrl);

            if (jsData != null) {
                // Create category row
                List<String> endpoints = jsData.getFirstFinding();
                TableRow categoryRow = new TableRow(
                    "[+] First Finding [" + endpoints.size() + " endpoints]",
                    endpoints.size(),
                    "First Finding"
                );

                // Insert AFTER parent row
                rows.add(rowIndex + 1, categoryRow);
                parentRow.setExpanded(true);

                fireTableRowsInserted(rowIndex + 1, rowIndex + 1);
            }
        } else {
            // Collapse - remove child row
            rows.remove(rowIndex + 1);
            parentRow.setExpanded(false);

            fireTableRowsDeleted(rowIndex + 1, rowIndex + 1);
        }
    }

    private JSFileData getJSFileData(String jsFileUrl) {
        return allJSFilesData.get(jsFileUrl);
    }

    public void updateRows() {
        fireTableDataChanged();
    }
}
