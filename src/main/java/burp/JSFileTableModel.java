package burp;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JSFileTableModel extends AbstractTableModel {
    private final List<Object> rows;
    private final String[] columnNames = {"#", "File / Category", "Count", "Status"};
    private final Map<String, JSFileData> allJSFiles;

    public JSFileTableModel(Map<String, JSFileData> allJSFiles) {
        this.allJSFiles = allJSFiles;
        this.rows = new ArrayList<>();
        updateRows();
    }

    public void updateRows() {
        rows.clear();
        allJSFiles.values().forEach(jsFileData -> {
            rows.add(new JSFileRow(jsFileData));
        });
        fireTableDataChanged();
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
        Object rowObject = getRow(rowIndex);
        if (rowObject instanceof JSFileRow) {
            JSFileRow jsFileRow = (JSFileRow) rowObject;
            switch (columnIndex) {
                case 0: return rowIndex + 1;
                case 1: return (jsFileRow.isExpanded() ? "▼ " : "▶ ") + jsFileRow.getJsFileData().getJsFileUrl();
                case 2: return "[" + (jsFileRow.getJsFileData().getFirstFinding().size() + jsFileRow.getJsFileData().getLatest().size()) + " total]";
                case 3: return "Complete";
            }
        } else if (rowObject instanceof CategoryRow) {
            CategoryRow categoryRow = (CategoryRow) rowObject;
            switch (columnIndex) {
                case 0: return "";
                case 1: return "    [+] " + categoryRow.getCategoryName() + " [" + categoryRow.getEndpointCount() + " endpoints]";
                case 2:
                case 3: return "";
            }
        }
        return "";
    }

    public Object getRow(int rowIndex) {
        if (rowIndex >= 0 && rowIndex < rows.size()) {
            return rows.get(rowIndex);
        }
        return null;
    }

    public void toggleRow(int rowIndex) {
        Object rowObject = getRow(rowIndex);
        if (rowObject instanceof JSFileRow) {
            JSFileRow jsFileRow = (JSFileRow) rowObject;
            jsFileRow.setExpanded(!jsFileRow.isExpanded());

            if (jsFileRow.isExpanded()) {
                rows.addAll(rowIndex + 1, jsFileRow.getChildren());
            } else {
                rows.removeAll(jsFileRow.getChildren());
            }
            fireTableDataChanged();
        }
    }
}
