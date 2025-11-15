package burp;

import java.util.ArrayList;
import java.util.List;

public class JSFileRow {
    private final JSFileData jsFileData;
    private final List<CategoryRow> children;
    private boolean expanded = false;

    public JSFileRow(JSFileData jsFileData) {
        this.jsFileData = jsFileData;
        this.children = new ArrayList<>();
        this.children.add(new CategoryRow("First Finding", jsFileData.getFirstFinding()));
        if (!jsFileData.getLatest().isEmpty()) {
            this.children.add(new CategoryRow("Latest", jsFileData.getLatest()));
        }
    }

    public JSFileData getJsFileData() {
        return jsFileData;
    }

    public List<CategoryRow> getChildren() {
        return children;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }
}
