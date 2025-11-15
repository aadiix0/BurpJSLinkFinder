package burp;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class TreeTableModel extends AbstractTableModel {
    private final List<Row> rows;
    private final String[] columnNames = {"", "JS File URL / Endpoint", "Count/Type", "Status"};
    private final ConcurrentHashMap<String, List<Endpoint>> data;

    public TreeTableModel(ConcurrentHashMap<String, List<Endpoint>> data) {
        this.data = data;
        this.rows = new ArrayList<>();
        updateRows();
    }

    public void updateRows() {
        rows.clear();
        data.forEach((url, endpoints) -> {
            ParentRow parentRow = new ParentRow(url, endpoints);
            rows.add(parentRow);
        });
        fireTableDataChanged();
    }

    public void toggleRow(int rowIndex) {
        Row row = getRow(rowIndex);
        if (row instanceof ParentRow) {
            ParentRow parentRow = (ParentRow) row;
            parentRow.setExpanded(!parentRow.isExpanded());

            if (parentRow.isExpanded()) {
                int i = rowIndex + 1;
                for (Endpoint endpoint : parentRow.getEndpoints()) {
                    rows.add(i++, new EndpointRow(endpoint.getUrl(), endpoint.getType()));
                }
            } else {
                rows.removeIf(r -> r instanceof EndpointRow && ((EndpointRow) r).getEndpoint().startsWith(parentRow.getJsFileUrl())));
            }
            fireTableDataChanged();
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
        Row row = getRow(rowIndex);
        if (row instanceof ParentRow) {
            ParentRow parent = (ParentRow) row;
            switch (columnIndex) {
                case 0: return parent.isExpanded() ? "▼" : "▶";
                case 1: return parent.getJsFileUrl();
                case 2: return "[" + parent.getEndpointCount() + " endpoints]";
                case 3: return "Complete";
            }
        } else if (row instanceof EndpointRow) {
            EndpointRow endpoint = (EndpointRow) row;
            switch (columnIndex) {
                case 0: return "  ";
                case 1: return endpoint.getEndpoint();
                case 2: return endpoint.getEndpointType();
                case 3: return "";
            }
        }
        return "";
    }

    public Row getRow(int rowIndex) {
        if (rowIndex >= 0 && rowIndex < rows.size()) {
            return rows.get(rowIndex);
        }
        return null;
    }

    public ParentRow getParentRow(int rowIndex) {
        Row row = getRow(rowIndex);
        if (row instanceof ParentRow) {
            return (ParentRow) row;
        }
        return null;
    }
}
