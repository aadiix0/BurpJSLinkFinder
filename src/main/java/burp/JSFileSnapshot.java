package burp;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class JSFileSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    private String jsFileUrl;
    private String content;
    private List<String> endpoints;
    private LocalDateTime timestamp;
    private String hash;
    private int version;  // Version number for timeline

    public JSFileSnapshot(String jsFileUrl, String content, List<String> endpoints, int version) {
        this.jsFileUrl = jsFileUrl;
        this.content = content;
        this.endpoints = new ArrayList<>(endpoints);
        this.timestamp = LocalDateTime.now();
        this.hash = computeHash(content);
        this.version = version;
    }

    private String computeHash(String content) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(content.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(content.hashCode());
        }
    }

    public String getFormattedTimestamp() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return timestamp.format(formatter);
    }

    public boolean hasChanged(JSFileSnapshot other) {
        return !this.hash.equals(other.hash);
    }

    // Getters
    public String getJsFileUrl() { return jsFileUrl; }
    public String getContent() { return content; }
    public List<String> getEndpoints() { return new ArrayList<>(endpoints); }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getHash() { return hash; }
    public int getVersion() { return version; }
}
