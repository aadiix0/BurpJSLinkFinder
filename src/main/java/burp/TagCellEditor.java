package burp;

import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;

public class TagCellEditor extends DefaultCellEditor {
    public TagCellEditor() {
        super(new JComboBox<>(new String[]{"New", "Working", "Later", "Ignore", "Done"}));
    }
}
