package burp;

import java.util.List;

public class ParentRow {
    private final String jsFileUrl;
    private final List<Endpoint> endpoints;

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
}
