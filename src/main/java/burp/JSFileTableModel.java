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
        boolean exists = false;
        for (TableRow row : rows) {
            if (row.isParent() && row.getJsFileUrl().equals(jsFileUrl)) {
                exists = true;
                break;
            }
        }

        if (!exists) {
            int rowNum = getNextRowNumber();
            TableRow newRow = new TableRow(rowNum, jsFileUrl, endpoints.size(), "New");
            rows.add(newRow);

            List<String> endpointUrls = endpoints.stream()
                .map(Endpoint::getUrl)
                .collect(Collectors.toList());

            JSFileData jsData = new JSFileData(jsFileUrl, endpointUrls);
            allJSFilesData.put(jsFileUrl, jsData);

            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);

            api.logging().logToOutput("Added JS file: " + jsFileUrl + " with " + endpoints.size() + " endpoints");
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
