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
        api.logging().logToOutput("Processing JS file: " + jsFileUrl);

        // Convert to string URLs
        List<String> newEndpointUrls = endpoints.stream()
                .map(Endpoint::getUrl)
                .collect(Collectors.toList());

        JSFileData jsData = allJSFilesData.get(jsFileUrl);

        if (jsData == null) {
            // FIRST TIME - Add to First Finding
            jsData = new JSFileData(jsFileUrl, newEndpointUrls);
            allJSFilesData.put(jsFileUrl, jsData);

            // Add parent row
            TableRow newRow = new TableRow(rows.size() + 1, jsFileUrl, endpoints.size(), "New");
            rows.add(newRow);
            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);

            api.logging().logToOutput("✓ New JS file added with " + endpoints.size() + " endpoints");

        } else {
            // SUBSEQUENT SCAN - Check for new endpoints
            List<String> firstFinding = jsData.getFirstFinding();
            List<String> latest = jsData.getLatest();
            List<String> newEndpoints = new ArrayList<>();

            for (String url : newEndpointUrls) {
                // If not in First Finding AND not in Latest, it's NEW
                if (!firstFinding.contains(url) && !latest.contains(url)) {
                    newEndpoints.add(url);
                }
            }

            if (!newEndpoints.isEmpty()) {
                // Add to Latest section
                latest.addAll(newEndpoints);
                jsData.setLatest(latest);

                api.logging().logToOutput("✓ Found " + newEndpoints.size() + " NEW endpoints for " + jsFileUrl);

                // Update row count
                for (int i = 0; i < rows.size(); i++) {
                    TableRow row = rows.get(i);
                    if (row.isParent() && row.getJsFileUrl().equals(jsFileUrl)) {
                        int newTotal = firstFinding.size() + latest.size();
                        row.setCount(newTotal);
                        fireTableRowsUpdated(i, i);
                        break;
                    }
                }
            } else {
                api.logging().logToOutput("✓ No new endpoints for " + jsFileUrl);
            }
        }
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
            // EXPAND
            String jsFileUrl = parentRow.getJsFileUrl();
            JSFileData jsData = allJSFilesData.get(jsFileUrl);

            if (jsData != null) {
                // Add First Finding
                TableRow firstFindingRow = new TableRow(
                        "[+] First Finding [" + jsData.getFirstFinding().size() + " endpoints]",
                        jsData.getFirstFinding().size(),
                        "First Finding"
                );
                rows.add(rowIndex + 1, firstFindingRow);

                // Add Latest if exists
                if (!jsData.getLatest().isEmpty()) {
                    TableRow latestRow = new TableRow(
                            "[+] Latest [" + jsData.getLatest().size() + " endpoints]",
                            jsData.getLatest().size(),
                            "Latest"
                    );
                    rows.add(rowIndex + 2, latestRow);
                    fireTableRowsInserted(rowIndex + 1, rowIndex + 2);
                } else {
                    fireTableRowsInserted(rowIndex + 1, rowIndex + 1);
                }

                parentRow.setExpanded(true);
            }

        } else {
            // COLLAPSE
            int removeCount = 0;
            for (int i = rowIndex + 1; i < rows.size(); i++) {
                if (rows.get(i).isParent()) break;
                removeCount++;
            }

            for (int i = 0; i < removeCount; i++) {
                rows.remove(rowIndex + 1);
            }

            fireTableRowsDeleted(rowIndex + 1, rowIndex + removeCount);
            parentRow.setExpanded(false);
        }
    }

    public void updateJSFileCount(String jsFileUrl, int newCount) {
        for (int i = 0; i < rows.size(); i++) {
            TableRow row = rows.get(i);
            if (row.isParent() && row.getJsFileUrl().equals(jsFileUrl)) {
                row.setCount(newCount);
                fireTableRowsUpdated(i, i);
                break;
            }
        }
    }

    public void ensureLatestSectionVisible(String jsFileUrl) {
        // Find parent row
        for (int i = 0; i < rows.size(); i++) {
            TableRow row = rows.get(i);

            if (row.isParent() && row.getJsFileUrl().equals(jsFileUrl)) {

                // Check if expanded
                if (row.isExpanded()) {
                    // Check if Latest section already exists
                    boolean hasLatest = false;

                    for (int j = i + 1; j < rows.size(); j++) {
                        TableRow childRow = rows.get(j);
                        if (childRow.isParent()) break; // Hit next parent

                        if (childRow.getCategory() != null && childRow.getCategory().equals("Latest")) {
                            hasLatest = true;
                            break;
                        }
                    }

                    if (!hasLatest) {
                        // Add Latest section
                        JSFileData jsData = allJSFilesData.get(jsFileUrl);
                        if (jsData != null && !jsData.getLatest().isEmpty()) {
                            TableRow latestRow = new TableRow(
                                    "[+] Latest [" + jsData.getLatest().size() + " endpoints]",
                                    jsData.getLatest().size(),
                                    "Latest"
                            );

                            // Insert after First Finding
                            rows.add(i + 2, latestRow);
                            fireTableRowsInserted(i + 2, i + 2);

                            api.logging().logToOutput("✓ Added Latest section for " + jsFileUrl);
                        }
                    }
                } else {
                    // Auto-expand to show Latest
                    toggleRow(i);
                }

                break;
            }
        }
    }

    public void removeRow(int rowIndex) {
        if (rowIndex >= 0 && rowIndex < rows.size()) {
            rows.remove(rowIndex);
            fireTableRowsDeleted(rowIndex, rowIndex);
        }
    }

    private JSFileData getJSFileData(String jsFileUrl) {
        return allJSFilesData.get(jsFileUrl);
    }

    public void updateRows() {
        fireTableDataChanged();
    }
}
