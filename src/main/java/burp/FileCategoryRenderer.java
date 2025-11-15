package burp;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class FileCategoryRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        JSFileTableModel model = (JSFileTableModel) table.getModel();
        Object rowObject = model.getRow(row);

        if (rowObject instanceof JSFileRow) {
            setFont(new Font("Arial", Font.BOLD, 14));
        } else {
            setFont(new Font("Arial", Font.PLAIN, 12));
        }
        setText(value.toString());
        return this;
    }
}
