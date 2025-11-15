package burp;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class JSFileTableModel extends AbstractTableModel {
    private final List<ParentRow> parentRows;
    private final String[] columnNames = {"", "JS File URL", "Endpoints", "Status"};
    private final ConcurrentHashMap<String, List<Endpoint>> data;

    public JSFileTableModel(ConcurrentHashMap<String, List<Endpoint>> data) {
        this.data = data;
        this.parentRows = new ArrayList<>();
        updateRows();
    }

    public void updateRows() {
        parentRows.clear();
        data.forEach((url, endpoints) -> parentRows.add(new ParentRow(url, endpoints)));
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return parentRows.size();
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
        ParentRow parentRow = getParentRow(rowIndex);
        switch (columnIndex) {
            case 0: return "▶";
            case 1: return parentRow.getJsFileUrl();
            case 2: return "[" + parentRow.getEndpointCount() + " endpoints]";
            case 3: return "Complete";
        }
        return null;
    }

    public ParentRow getParentRow(int rowIndex) {
        if (rowIndex >= 0 && rowIndex < parentRows.size()) {
            return parentRows.get(rowIndex);
        }
        return null;
    }
}
