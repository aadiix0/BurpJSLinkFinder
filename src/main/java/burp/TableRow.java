package burp;

public class TableRow {
    private int rowNumber;
    private String jsFileUrl;
    private String displayText;
    private int count;
    private String status;
    private boolean isParent;
    private boolean isExpanded;
    private String category;  // "First Finding" or "Latest"

    // Constructor for parent rows (JS files)
    public TableRow(int rowNumber, String jsFileUrl, int count, String status) {
        this.rowNumber = rowNumber;
        this.jsFileUrl = jsFileUrl;
        this.displayText = jsFileUrl;
        this.count = count;
        this.status = status;
        this.isParent = true;
        this.isExpanded = false;
    }

    // Constructor for child rows (categories)
    public TableRow(String displayText, int count, String category) {
        this.displayText = displayText;
        this.count = count;
        this.category = category;
        this.isParent = false;
        this.isExpanded = false;
        this.status = "";
    }

    // Getters and setters
    public boolean isParent() { return isParent; }
    public boolean isExpanded() { return isExpanded; }
    public void setExpanded(boolean expanded) { this.isExpanded = expanded; }
    public String getJsFileUrl() { return jsFileUrl; }
    public String getDisplayText() { return displayText; }
    public int getCount() { return count; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public void setCount(int count) { this.count = count; }
    public String getCategory() { return category; }
    public int getRowNumber() { return rowNumber; }
}
