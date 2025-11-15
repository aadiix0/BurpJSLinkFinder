package burp;

import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;

public class ParentRow {
    private final String jsFileUrl;
    private final List<Endpoint> endpoints;
    private boolean expanded = false;

    public ParentRow(String jsFileUrl, List<Endpoint> endpoints) {
        this.jsFileUrl = jsFileUrl;
        this.endpoints = endpoints;
    }

    public String getJsFileUrl() {
        return jsFileUrl;
    }

    public int getEndpointCount() {
        return endpoints.size();
    }

    public List<Endpoint> getEndpoints() {
        return endpoints;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }
}
