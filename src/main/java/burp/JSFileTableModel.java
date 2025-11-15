package burp;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class JSFileTableModel extends AbstractTableModel {

    private final ConcurrentHashMap<String, List<Endpoint>> data;
    private final List<String> jsFiles;
    private final String[] columnNames = {"JS File URL", "Endpoints"};

    public JSFileTableModel(ConcurrentHashMap<String, List<Endpoint>> data) {
        this.data = data;
        this.jsFiles = new ArrayList<>(data.keySet());
    }

    @Override
    public int getRowCount() {
        return jsFiles.size();
    }

    @Override
    public int getColumnCount() {
        return 2;
    }

    @Override
    public String getColumnName(int column) {
        return columnNames[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        String jsFileUrl = jsFiles.get(rowIndex);

        switch (columnIndex) {
            case 0: return jsFileUrl;
            case 1: return "[" + data.get(jsFileUrl).size() + " endpoints]";
            default: return "";
        }
    }

    public void addJSFile(String jsFileUrl, List<Endpoint> endpoints) {
        if (!jsFiles.contains(jsFileUrl)) {
            jsFiles.add(jsFileUrl);
            data.put(jsFileUrl, endpoints);
            fireTableRowsInserted(jsFiles.size() - 1, jsFiles.size() - 1);
        } else {
            // Update existing
            int index = jsFiles.indexOf(jsFileUrl);
            data.put(jsFileUrl, endpoints);
            fireTableRowsUpdated(index, index);
        }
    }

    public String getJSFileUrl(int row) {
        return jsFiles.get(row);
    }

    public List<Endpoint> getEndpoints(String jsFileUrl) {
        return data.get(jsFileUrl);
    }
}
