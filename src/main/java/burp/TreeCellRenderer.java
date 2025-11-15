package burp;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class TreeCellRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        TreeTableModel model = (TreeTableModel) table.getModel();
        Row rowObject = model.getRow(row);

        if (rowObject instanceof ParentRow) {
            setText(value.toString());
            setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0));
        } else if (rowObject instanceof EndpointRow) {
            setText(value.toString());
            setBorder(BorderFactory.createEmptyBorder(0, 25, 0, 0));
        }

        return this;
    }
}
