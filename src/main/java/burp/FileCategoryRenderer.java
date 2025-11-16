package burp;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class FileCategoryRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(
            JTable table, Object value, boolean isSelected,
            boolean hasFocus, int row, int column) {

        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        int modelRow = table.convertRowIndexToModel(row);
        JSFileTableModel model = (JSFileTableModel) table.getModel();
        Object rowObject = model.getRow(modelRow);

        if (rowObject instanceof JSFileRow) {
            JSFileRow jsFileRow = (JSFileRow) rowObject;
            setFont(new Font(getFont().getName(), Font.BOLD, 12));

            if (jsFileRow.isExpanded()) {
                setText("▼ " + value);
            } else {
                setText(value.toString());
            }
        } else if (rowObject instanceof CategoryRow) {
            setFont(new Font(getFont().getName(), Font.PLAIN, 12));
            setText("    " + value);
        }

        return this;
    }
}
