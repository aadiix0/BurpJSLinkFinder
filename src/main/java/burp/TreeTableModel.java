package burp;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class TreeTableModel extends AbstractTableModel {
    private final List<ParentRow> parentRows;
    private final String[] columnNames = {"", "JS File URL", "Endpoint Count", "Status"};
    private final ConcurrentHashMap<String, List<Endpoint>> currentData;
    private final ConcurrentHashMap<String, List<Endpoint>> historicData;
    private boolean showHistoric = false;

    public TreeTableModel(ConcurrentHashMap<String, List<Endpoint>> currentData, ConcurrentHashMap<String, List<Endpoint>> historicData) {
        this.currentData = currentData;
        this.historicData = historicData;
        this.parentRows = new ArrayList<>();
        updateParentRows();
    }

    public void addRow(String url, List<Endpoint> endpoints) {
        currentData.put(url, endpoints);
        updateParentRows();
        fireTableDataChanged();
    }

    public void setShowHistoric(boolean showHistoric) {
        this.showHistoric = showHistoric;
        updateParentRows();
        fireTableDataChanged();
    }

    private void updateParentRows() {
        parentRows.clear();
        currentData.forEach((url, endpoints) -> parentRows.add(new ParentRow(url, endpoints)));
        if (showHistoric) {
            historicData.forEach((url, endpoints) -> parentRows.add(new ParentRow(url, endpoints)));
        }
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
            case 0: return parentRow.isExpanded() ? "▼" : "▶";
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
