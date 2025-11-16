package burp;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
        for (JSFileData jsFileData : allJSFilesData.values()) {
            rows.add(new TableRow(rowNum++, jsFileData.getJsFileUrl(), jsFileData.getFirstFinding().size() + jsFileData.getLatest().size(), jsFileData.getStatus()));
        }
        fireTableDataChanged();
    }

    public void addRow(String jsFileUrl, List<Endpoint> newEndpoints) {
        JSFileData jsFileData = allJSFilesData.get(jsFileUrl);

        if (jsFileData == null) {
            // It's a brand new JS file
            jsFileData = new JSFileData(jsFileUrl);
            jsFileData.setFirstFinding(newEndpoints);
            jsFileData.setStatus("New");
            allJSFilesData.put(jsFileUrl, jsFileData);

            // Add to the table view
            int parentRowCount = 0;
            for (TableRow row : rows) {
                if (row.isParent()) {
                    parentRowCount++;
                }
            }
            TableRow newRow = new TableRow(
                parentRowCount + 1,
                jsFileUrl,
                newEndpoints.size(),
                "New"
            );
            rows.add(newRow);
            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);

        } else {
            // It's an existing file, check for new endpoints
            List<Endpoint> firstFinding = jsFileData.getFirstFinding();
            List<Endpoint> latest = jsFileData.getLatest();
            List<Endpoint> newlyFoundInLatest = new ArrayList<>();

            for (Endpoint newEndpoint : newEndpoints) {
                boolean inFirst = firstFinding.stream().anyMatch(e -> e.getEndpoint().equals(newEndpoint.getEndpoint()));
                boolean inLatest = latest.stream().anyMatch(e -> e.getEndpoint().equals(newEndpoint.getEndpoint()));
                if (!inFirst && !inLatest) {
                    newlyFoundInLatest.add(newEndpoint);
                }
            }

            if (!newlyFoundInLatest.isEmpty()) {
                latest.addAll(newlyFoundInLatest);
                jsFileData.setStatus("Updated");

                // Find the corresponding row and update it
                for (int i = 0; i < rows.size(); i++) {
                    TableRow row = rows.get(i);
                    if (row.isParent() && row.getJsFileUrl().equals(jsFileUrl)) {
                        row.setCount(firstFinding.size() + latest.size());
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
        TableRow row = getRow(rowIndex);
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
}
