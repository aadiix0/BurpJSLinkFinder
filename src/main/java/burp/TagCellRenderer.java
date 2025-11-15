package burp;

import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.JTable;
import java.awt.Color;
import java.awt.Component;

public class TagCellRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        String tag = (String) value;
        setHorizontalAlignment(CENTER);

        switch (tag) {
            case "New":
                setBackground(new Color(173, 216, 230)); // Light blue
                setForeground(Color.BLUE);
                break;
            case "Working":
                setBackground(new Color(255, 255, 224)); // Light yellow
                setForeground(new Color(184, 134, 11));  // Dark goldenrod
                break;
            case "Later":
                setBackground(new Color(255, 228, 196)); // Bisque
                setForeground(new Color(255, 140, 0));   // Dark orange
                break;
            case "Ignore":
                setBackground(Color.LIGHT_GRAY);
                setForeground(Color.DARK_GRAY);
                break;
            case "Done":
                setBackground(new Color(144, 238, 144)); // Light green
                setForeground(new Color(0, 100, 0));     // Dark green
                break;
        }

        return this;
    }
}
