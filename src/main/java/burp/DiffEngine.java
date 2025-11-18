package burp;

import java.util.*;

public class DiffEngine {

    public static class DiffResult {
        public List<String> addedEndpoints;
        public List<String> removedEndpoints;
        public List<String> unchangedEndpoints;
        public List<CodeChange> codeChanges;
        public String diffHtml;
        public String sideBySideHtml;

        public DiffResult() {
            addedEndpoints = new ArrayList<>();
            removedEndpoints = new ArrayList<>();
            unchangedEndpoints = new ArrayList<>();
            codeChanges = new ArrayList<>();
        }

        public int getTotalChanges() {
            return addedEndpoints.size() + removedEndpoints.size();
        }
    }

    public static class CodeChange {
        public int lineNumber;
        public String oldLine;
        public String newLine;
        public ChangeType type;

        public enum ChangeType { ADDED, REMOVED, MODIFIED }

        public CodeChange(int lineNumber, String oldLine, String newLine, ChangeType type) {
            this.lineNumber = lineNumber;
            this.oldLine = oldLine;
            this.newLine = newLine;
            this.type = type;
        }
    }

    public static DiffResult compareSnapshots(JSFileSnapshot oldSnapshot, JSFileSnapshot newSnapshot, String filter) {
        DiffResult result = new DiffResult();

        // Compare endpoints
        Set<String> oldEps = new HashSet<>(oldSnapshot.getEndpoints());
        Set<String> newEps = new HashSet<>(newSnapshot.getEndpoints());

        for (String ep : newEps) {
            if (!oldEps.contains(ep)) {
                result.addedEndpoints.add(ep);
            } else {
                result.unchangedEndpoints.add(ep);
            }
        }

        for (String ep : oldEps) {
            if (!newEps.contains(ep)) {
                result.removedEndpoints.add(ep);
            }
        }

        // Detect code-level changes
        result.codeChanges = detectCodeChanges(
            oldSnapshot.getContent(),
            newSnapshot.getContent()
        );

        // Generate HTML views
        result.diffHtml = generateDiffHtml(oldSnapshot, newSnapshot, result, filter);
        result.sideBySideHtml = generateSideBySideHtml(oldSnapshot, newSnapshot, result);

        return result;
    }

    private static List<CodeChange> detectCodeChanges(String oldContent, String newContent) {
        List<CodeChange> changes = new ArrayList<>();

        String[] oldLines = oldContent.split("\n");
        String[] newLines = newContent.split("\n");

        int maxLines = Math.max(oldLines.length, newLines.length);

        for (int i = 0; i < maxLines; i++) {
            String oldLine = i < oldLines.length ? oldLines[i] : "";
            String newLine = i < newLines.length ? newLines[i] : "";

            if (!oldLine.equals(newLine)) {
                CodeChange.ChangeType type;
                if (oldLine.isEmpty()) {
                    type = CodeChange.ChangeType.ADDED;
                } else if (newLine.isEmpty()) {
                    type = CodeChange.ChangeType.REMOVED;
                } else {
                    type = CodeChange.ChangeType.MODIFIED;
                }

                changes.add(new CodeChange(i + 1, oldLine, newLine, type));
            }
        }

        return changes;
    }

    private static String generateDiffHtml(JSFileSnapshot oldSnapshot,
                                          JSFileSnapshot newSnapshot,
                                          DiffResult result, String filter) {
        StringBuilder html = new StringBuilder();
        html.append("<html><head><style>");
        html.append("body { font-family: 'Consolas', monospace; font-size: 12px; background: #1e1e1e; color: #d4d4d4; padding: 15px; }");
        html.append(".summary { background: #252526; padding: 15px; border-radius: 5px; margin-bottom: 20px; border-left: 4px solid #007acc; }");
        html.append(".stat { display: inline-block; margin-right: 20px; padding: 5px 10px; border-radius: 3px; }");
        html.append(".stat.added { background: #1a4d1a; color: #4ec94e; }");
        html.append(".stat.removed { background: #4d1a1a; color: #f48771; }");
        html.append(".stat.unchanged { background: #3d3d3d; color: #cccccc; }");
        html.append(".section { margin: 20px 0; }");
        html.append(".section-title { font-size: 14px; font-weight: bold; color: #4ec9b0; margin-bottom: 10px; border-bottom: 1px solid #3d3d3d; padding-bottom: 5px; }");
        html.append(".endpoint { padding: 8px 12px; margin: 3px 0; border-radius: 3px; font-family: monospace; }");
        html.append(".endpoint.added { background: #1a4d1a; color: #4ec94e; border-left: 3px solid #4ec94e; }");
        html.append(".endpoint.removed { background: #4d1a1a; color: #f48771; border-left: 3px solid #f48771; }");
        html.append(".endpoint.unchanged { background: #2d2d2d; color: #9cdcfe; }");
        html.append(".collapse-btn { cursor: pointer; color: #4ec9b0; text-decoration: underline; margin-left: 10px; font-size: 11px; }");
        html.append(".collapsible { display: none; }");
        html.append(".icon { margin-right: 8px; }");
        html.append("</style>");
        html.append("<script>");
        html.append("function toggle(id) { var el = document.getElementById(id); el.style.display = el.style.display === 'none' ? 'block' : 'none'; }");
        html.append("</script></head><body>");

        // Summary
        html.append("<div class='summary'>");
        html.append("<div style='font-size: 16px; font-weight: bold; margin-bottom: 10px;'>📊 CHANGE SUMMARY</div>");
        html.append("<div style='margin: 10px 0;'>");
        html.append("<div>📅 <strong>Baseline:</strong> v").append(oldSnapshot.getVersion())
            .append(" — ").append(oldSnapshot.getFormattedTimestamp()).append("</div>");
        html.append("<div>📅 <strong>Current:</strong> v").append(newSnapshot.getVersion())
            .append(" — ").append(newSnapshot.getFormattedTimestamp()).append("</div>");
        html.append("</div>");
        html.append("<div style='margin-top: 15px;'>");
        html.append("<span class='stat added'><span class='icon'>🟢</span>Added: ").append(result.addedEndpoints.size()).append("</span>");
        html.append("<span class='stat removed'><span class='icon'>🔴</span>Removed: ").append(result.removedEndpoints.size()).append("</span>");
        html.append("<span class='stat unchanged'><span class='icon'>✅</span>Unchanged: ").append(result.unchangedEndpoints.size()).append("</span>");
        html.append("</div>");
        html.append("</div>");

        // Added endpoints
        if ((filter.equals("All") || filter.equals("Added Only") || filter.equals("Changed")) && !result.addedEndpoints.isEmpty()) {
            html.append("<div class='section'>");
            html.append("<div class='section-title'>🟢 ADDED ENDPOINTS (").append(result.addedEndpoints.size()).append(")</div>");
            for (String ep : result.addedEndpoints) {
                html.append("<div class='endpoint added'>+ ").append(escapeHtml(ep)).append("</div>");
            }
            html.append("</div>");
        }

        // Removed endpoints
        if ((filter.equals("All") || filter.equals("Removed Only") || filter.equals("Changed")) && !result.removedEndpoints.isEmpty()) {
            html.append("<div class='section'>");
            html.append("<div class='section-title'>🔴 REMOVED ENDPOINTS (").append(result.removedEndpoints.size()).append(")</div>");
            for (String ep : result.removedEndpoints) {
                html.append("<div class='endpoint removed'>- ").append(escapeHtml(ep)).append("</div>");
            }
            html.append("</div>");
        }

        // Unchanged (collapsible)
        if ((filter.equals("All") || filter.equals("Unchanged")) && !result.unchangedEndpoints.isEmpty()) {
            html.append("<div class='section'>");
            html.append("<div class='section-title'>✅ UNCHANGED ENDPOINTS (")
                .append(result.unchangedEndpoints.size())
                .append(")<span class='collapse-btn' onclick='toggle(\"unchanged\")'>[ Show/Hide ]</span></div>");
            html.append("<div id='unchanged' class='collapsible'>");
            for (String ep : result.unchangedEndpoints) {
                html.append("<div class='endpoint unchanged'>  ").append(escapeHtml(ep)).append("</div>");
            }
            html.append("</div>");
            html.append("</div>");
        }

        html.append("</body></html>");
        return html.toString();
    }

    private static String generateSideBySideHtml(JSFileSnapshot oldSnapshot,
                                                 JSFileSnapshot newSnapshot,
                                                 DiffResult result) {
        StringBuilder html = new StringBuilder();
        html.append("<html><head><style>");
        html.append("body { font-family: 'Consolas', monospace; font-size: 11px; background: #1e1e1e; color: #d4d4d4; padding: 0; margin: 0; }");
        html.append(".diff-container { display: flex; height: 100vh; }");
        html.append(".diff-pane { flex: 1; overflow: auto; padding: 10px; }");
        html.append(".diff-pane.old { background: #1a1a1a; border-right: 2px solid #3d3d3d; }");
        html.append(".diff-pane.new { background: #1e1e1e; }");
        html.append(".pane-title { font-size: 13px; font-weight: bold; padding: 10px; background: #252526; border-bottom: 1px solid #3d3d3d; position: sticky; top: 0; }");
        html.append(".line { padding: 2px 5px; white-space: pre-wrap; word-break: break-all; }");
        html.append(".line-num { display: inline-block; width: 40px; color: #858585; text-align: right; margin-right: 10px; user-select: none; }");
        html.append(".line.added { background: #1a4d1a; }");
        html.append(".line.removed { background: #4d1a1a; }");
        html.append(".line.modified { background: #4d4d1a; }");
        html.append("</style></head><body>");

        html.append("<div class='diff-container'>");

        // Old version pane
        html.append("<div class='diff-pane old'>");
        html.append("<div class='pane-title'>🕐 OLD (Baseline v").append(oldSnapshot.getVersion()).append(")</div>");
        String[] oldLines = oldSnapshot.getContent().split("\n");
        for (int i = 0; i < oldLines.length; i++) {
            String cssClass = getLineClass(result.codeChanges, i + 1, true);
            html.append("<div class='line ").append(cssClass).append("'>");
            html.append("<span class='line-num'>").append(i + 1).append("</span>");
            html.append(escapeHtml(oldLines[i]));
            html.append("</div>");
        }
        html.append("</div>");

        // New version pane
        html.append("<div class='diff-pane new'>");
        html.append("<div class='pane-title'>✨ NEW (Current v").append(newSnapshot.getVersion()).append(")</div>");
        String[] newLines = newSnapshot.getContent().split("\n");
        for (int i = 0; i < newLines.length; i++) {
            String cssClass = getLineClass(result.codeChanges, i + 1, false);
            html.append("<div class='line ").append(cssClass).append("'>");
            html.append("<span class='line-num'>").append(i + 1).append("</span>");
            html.append(escapeHtml(newLines[i]));
            html.append("</div>");
        }
        html.append("</div>");

        html.append("</div>");
        html.append("</body></html>");
        return html.toString();
    }

    private static String getLineClass(List<CodeChange> changes, int lineNum, boolean isOld) {
        for (CodeChange change : changes) {
            if (change.lineNumber == lineNum) {
                switch (change.type) {
                    case ADDED: return isOld ? "" : "added";
                    case REMOVED: return isOld ? "removed" : "";
                    case MODIFIED: return "modified";
                }
            }
        }
        return "";
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}
