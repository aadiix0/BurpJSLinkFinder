package burp;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class TreeTableModel extends AbstractTableModel {
    private final ConcurrentHashMap<String, List<Endpoint>> currentData;
    private final ConcurrentHashMap<String, List<Endpoint>> historicData;
    private final List<ParentRow> parentRows;
    private final String[] columnNames = {"", "URL/Category/Endpoint", "Count/Type", "Status/Method"};
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
        int rowCount = 0;
        for (ParentRow parent : parentRows) {
            rowCount++; // for the parent row
            if (parent.isExpanded()) {
                rowCount += parent.getChildren().size(); // for the category rows
                for (CategoryRow category : parent.getChildren()) {
                    if (category.isExpanded()) {
                        rowCount += category.getChildren().size(); // for the child rows
                    }
                }
            }
        }
        return rowCount;
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
            ParentRow parentRow = (ParentRow) row;
            switch (columnIndex) {
                case 0: return parentRow.isExpanded() ? "▼" : "▶";
                case 1: return parentRow.getJsFileUrl();
                case 2: return parentRow.getEndpointCount();
                case 3: return parentRow.getStatus();
            }
        } else if (row instanceof CategoryRow) {
            CategoryRow categoryRow = (CategoryRow) row;
            switch (columnIndex) {
                case 0: return categoryRow.isExpanded() ? "▼" : "▶";
                case 1: return categoryRow.getCategoryName() + " (" + categoryRow.getEndpointCount() + " endpoints)";
                case 2:
                case 3: return "";
            }
        } else if (row instanceof ChildRow) {
            ChildRow childRow = (ChildRow) row;
            switch (columnIndex) {
                case 0: return "•";
                case 1: return childRow.getEndpoint().getUrl();
                case 2: return childRow.getEndpoint().getType();
                case 3: return childRow.getEndpoint().getMethod();
            }
        }
        return null;
    }

    public Row getRow(int rowIndex) {
        int currentRow = 0;
        for (ParentRow parent : parentRows) {
            if (currentRow == rowIndex) {
                return parent;
            }
            currentRow++;
            if (parent.isExpanded()) {
                for (CategoryRow category : parent.getChildren()) {
                    if (currentRow == rowIndex) {
                        return category;
                    }
                    currentRow++;
                    if (category.isExpanded()) {
                        if (rowIndex < currentRow + category.getChildren().size()) {
                            return category.getChildren().get(rowIndex - currentRow);
                        }
                        currentRow += category.getChildren().size();
                    }
                }
            }
        }
        return null;
    }
}
