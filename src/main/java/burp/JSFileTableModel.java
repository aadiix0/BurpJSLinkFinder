package burp;

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

    public JSFileTableModel(Map<String, JSFileData> allJSFilesData) {
        this.allJSFilesData = allJSFilesData;
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

    public void addJSFile(String jsFileUrl, List<Endpoint> newEndpoints) {
        // Convert Endpoint list to String list
        List<String> endpointUrls = newEndpoints.stream()
            .map(Endpoint::getUrl)
            .collect(Collectors.toList());

        JSFileData jsFileData = allJSFilesData.get(jsFileUrl);

        if (jsFileData == null) {
            // First time seeing this JS file
            jsFileData = new JSFileData(jsFileUrl, endpointUrls);
            jsFileData.setStatus("New");
            allJSFilesData.put(jsFileUrl, jsFileData);

            // Add new row
            TableRow newRow = new TableRow(rows.size() + 1, jsFileUrl, endpointUrls.size(), "New");
            rows.add(newRow);
            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);

        } else {
            // JS file seen before - check for new endpoints
            List<String> firstFinding = jsFileData.getFirstFinding();
            List<String> latest = jsFileData.getLatest();

            // Find new endpoints not in First Finding or Latest
            List<String> newLatest = new ArrayList<>();
            for (String endpointUrl : endpointUrls) {
                if (!firstFinding.contains(endpointUrl) && !latest.contains(endpointUrl)) {
                    newLatest.add(endpointUrl);
                }
            }

            if (!newLatest.isEmpty()) {
                // Add to latest
                latest.addAll(newLatest);
                jsFileData.setLatest(latest);
                jsFileData.setStatus("Updated");

                // Update row count
                for (int i = 0; i < rows.size(); i++) {
                    TableRow row = rows.get(i);
                    if (row.isParent() && row.getJsFileUrl().equals(jsFileUrl)) {
                        // Update total count
                        int newTotal = firstFinding.size() + latest.size();
                        row.setCount(newTotal);
                        row.setStatus("Updated");
                        fireTableRowsUpdated(i, i);
                        break;
                    }
                }
            }
        }
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
        if (row.isParent()) {
            switch (columnIndex) {
                case 0: return row.getRowNumber();
                case 1: return row.getDisplayText();
                case 2: return "[" + row.getCount() + " total]";
                case 3: return row.getStatus();
            }
        } else {
            switch (columnIndex) {
                case 1: return "    " + row.getDisplayText();
                default: return "";
            }
        }
        return "";
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
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            return;
        }

        TableRow row = rows.get(rowIndex);

        if (!row.isParent()) {
            return;
        }

        if (row.isExpanded()) {
            int removeCount = 0;
            for (int i = rowIndex + 1; i < rows.size(); i++) {
                if (rows.get(i).isParent()) {
                    break;
                }
                removeCount++;
            }

            for (int i = 0; i < removeCount; i++) {
                rows.remove(rowIndex + 1);
            }

            row.setExpanded(false);
            fireTableRowsDeleted(rowIndex + 1, rowIndex + removeCount);
            fireTableRowsUpdated(rowIndex, rowIndex);

        } else {
            String jsFileUrl = row.getJsFileUrl();
            JSFileData jsFileData = getJSFileData(jsFileUrl);

            if (jsFileData == null) {
                return;
            }

            List<TableRow> childRows = new ArrayList<>();

            int firstFindingCount = jsFileData.getFirstFinding().size();
            TableRow firstFindingRow = new TableRow(
                "[+] First Finding [" + firstFindingCount + " endpoints]",
                firstFindingCount,
                "First Finding"
            );
            childRows.add(firstFindingRow);

            int latestCount = jsFileData.getLatest().size();
            if (latestCount > 0) {
                TableRow latestRow = new TableRow(
                    "[+] Latest [" + latestCount + " endpoints]",
                    latestCount,
                    "Latest"
                );
                childRows.add(latestRow);
            }

            rows.addAll(rowIndex + 1, childRows);
            row.setExpanded(true);

            fireTableRowsInserted(rowIndex + 1, rowIndex + childRows.size());
            fireTableRowsUpdated(rowIndex, rowIndex);
        }
    }

    private JSFileData getJSFileData(String jsFileUrl) {
        return allJSFilesData.get(jsFileUrl);
    }

    public void updateRows() {
        fireTableDataChanged();
    }
}
