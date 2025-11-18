package burp;

import java.time.LocalDateTime;
import java.util.List;
import java.security.MessageDigest;
import java.math.BigInteger;

public class JSFileSnapshot {
    private String jsFileUrl;
    private String content;
    private List<String> endpoints;
    private LocalDateTime timestamp;
    private String hash;  // MD5 hash for quick change detection

    public JSFileSnapshot(String jsFileUrl, String content, List<String> endpoints) {
        this.jsFileUrl = jsFileUrl;
        this.content = content;
        this.endpoints = endpoints;
        this.timestamp = LocalDateTime.now();
        this.hash = computeHash(content);
    }

    private String computeHash(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(content.getBytes());
            BigInteger no = new BigInteger(1, messageDigest);
            String hashtext = no.toString(16);
            while (hashtext.length() < 32) {
                hashtext = "0" + hashtext;
            }
            return hashtext;
        } catch (Exception e) {
            // Fallback to a simple hashCode if MD5 fails
            return String.valueOf(content.hashCode());
        }
    }

    // Getters
    public String getJsFileUrl() { return jsFileUrl; }
    public String getContent() { return content; }
    public List<String> getEndpoints() { return endpoints; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getHash() { return hash; }
}
