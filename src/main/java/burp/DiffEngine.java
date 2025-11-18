package burp;

import java.util.*;

public class DiffEngine {

    public static class DiffResult {
        public List<String> addedEndpoints;
        public List<String> removedEndpoints;
        public List<String> unchangedEndpoints;
        public String diffHtml;

        public DiffResult() {
            addedEndpoints = new ArrayList<>();
            removedEndpoints = new ArrayList<>();
            unchangedEndpoints = new ArrayList<>();
        }
    }

    public static DiffResult compareSnapshots(JSFileSnapshot oldSnapshot, JSFileSnapshot newSnapshot) {
        DiffResult result = new DiffResult();

        // Compare endpoints
        Set<String> oldEps = new HashSet<>(oldSnapshot.getEndpoints());
        Set<String> newEps = new HashSet<>(newSnapshot.getEndpoints());

        // Find added endpoints
        for (String ep : newEps) {
            if (!oldEps.contains(ep)) {
                result.addedEndpoints.add(ep);
            } else {
                result.unchangedEndpoints.add(ep);
            }
        }

        // Find removed endpoints
        for (String ep : oldEps) {
            if (!newEps.contains(ep)) {
                result.removedEndpoints.add(ep);
            }
        }

        // Generate HTML diff
        result.diffHtml = generateDiffHtml(oldSnapshot, newSnapshot, result);

        return result;
    }

    private static String generateDiffHtml(JSFileSnapshot oldSnapshot,
                                          JSFileSnapshot newSnapshot,
                                          DiffResult result) {
        StringBuilder html = new StringBuilder();
        html.append("<html><head><style>");
        html.append("body { font-family: monospace; font-size: 12px; }");
        html.append(".added { background-color: #d4edda; color: #155724; }");
        html.append(".removed { background-color: #f8d7da; color: #721c24; }");
        html.append(".unchanged { color: #333; }");
        html.append(".header { font-weight: bold; margin-top: 20px; }");
        html.append("</style></head><body>");

        // Summary
        html.append("<div class='header'>CHANGE SUMMARY</div>");
        html.append("<p>Old Version: ").append(oldSnapshot.getTimestamp()).append("</p>");
        html.append("<p>New Version: ").append(newSnapshot.getTimestamp()).append("</p>");
        html.append("<p><span style='color:green'>+ Added: ").append(result.addedEndpoints.size()).append("</span></p>");
        html.append("<p><span style='color:red'>- Removed: ").append(result.removedEndpoints.size()).append("</span></p>");

        // Added endpoints
        if (!result.addedEndpoints.isEmpty()) {
            html.append("<div class='header'>ADDED ENDPOINTS</div>");
            for (String ep : result.addedEndpoints) {
                html.append("<div class='added'>+ ").append(escapeHtml(ep)).append("</div>");
            }
        }

        // Removed endpoints
        if (!result.removedEndpoints.isEmpty()) {
            html.append("<div class='header'>REMOVED ENDPOINTS</div>");
            for (String ep : result.removedEndpoints) {
                html.append("<div class='removed'>- ").append(escapeHtml(ep)).append("</div>");
            }
        }

        // Unchanged
        if (!result.unchangedEndpoints.isEmpty()) {
            html.append("<div class='header'>UNCHANGED ENDPOINTS</div>");
            for (String ep : result.unchangedEndpoints) {
                html.append("<div class='unchanged'>  ").append(escapeHtml(ep)).append("</div>");
            }
        }

        html.append("</body></html>");
        return html.toString();
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}
