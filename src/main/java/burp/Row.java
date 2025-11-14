package burp;

import java.util.ArrayList;
import java.util.List;

public abstract class Row {
}

class ParentRow extends Row {
    private final String jsFileUrl;
    private final int endpointCount;
    private final String status;
    private final List<ChildRow> children;
    private boolean expanded;

    public ParentRow(String jsFileUrl, List<Endpoint> endpoints) {
        this.jsFileUrl = jsFileUrl;
        this.endpointCount = endpoints.size();
        this.status = "Complete";
        this.children = new ArrayList<>();
        for (Endpoint endpoint : endpoints) {
            this.children.add(new ChildRow(endpoint));
        }
        this.expanded = false;
    }

    public String getJsFileUrl() {
        return jsFileUrl;
    }

    public int getEndpointCount() {
        return endpointCount;
    }

    public String getStatus() {
        return status;
    }

    public List<ChildRow> getChildren() {
        return children;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }
}

class ChildRow extends Row {
    private final Endpoint endpoint;

    public ChildRow(Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }
}
