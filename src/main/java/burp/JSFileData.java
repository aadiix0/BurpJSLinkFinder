package burp;

import java.util.ArrayList;
import java.util.List;

public class JSFileData {
    private String jsFileUrl;
    private List<String> firstFinding;
    private List<String> latest;
    private long firstScanTimestamp;
    private long lastScanTimestamp;

    public JSFileData(String jsFileUrl, List<String> endpoints) {
        this.jsFileUrl = jsFileUrl;
        this.firstFinding = new ArrayList<>(endpoints);
        this.latest = new ArrayList<>();
        this.firstScanTimestamp = System.currentTimeMillis();
        this.lastScanTimestamp = System.currentTimeMillis();
    }

    // Getters and setters
    public String getJsFileUrl() { return jsFileUrl; }
    public List<String> getFirstFinding() { return firstFinding; }
    public List<String> getLatest() { return latest; }
    public long getFirstScanTimestamp() { return firstScanTimestamp; }
    public long getLastScanTimestamp() { return lastScanTimestamp; }

    public void setLatest(List<String> latest) {
        this.latest = latest;
    }

    public void setLastScanTimestamp(long lastScanTimestamp) {
        this.lastScanTimestamp = lastScanTimestamp;
    }
}
