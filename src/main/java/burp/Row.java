package burp;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;

public abstract class Row {
    private boolean expanded = false;
    public abstract int getLevel();

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }
}

class ParentRow extends Row {
    private final String jsFileUrl;
    private final int endpointCount;
    private final String status;
    private final List<CategoryRow> children;

    public ParentRow(String jsFileUrl, List<Endpoint> endpoints) {
        this.jsFileUrl = jsFileUrl;
        this.endpointCount = endpoints.size();
        this.status = "Complete";

        Map<String, List<Endpoint>> groupedEndpoints = endpoints.stream()
                .collect(Collectors.groupingBy(Endpoint::getType));

        this.children = groupedEndpoints.entrySet().stream()
                .map(entry -> new CategoryRow(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    @Override
    public int getLevel() {
        return 0;
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

    public List<CategoryRow> getChildren() {
        return children;
    }
}

class CategoryRow extends Row {
    private final String categoryName;
    private final List<ChildRow> children;

    public CategoryRow(String categoryName, List<Endpoint> endpoints) {
        this.categoryName = categoryName;
        this.children = endpoints.stream().map(ChildRow::new).collect(Collectors.toList());
    }

    @Override
    public int getLevel() {
        return 1;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public int getEndpointCount() {
        return children.size();
    }

    public List<ChildRow> getChildren() {
        return children;
    }
}

class ChildRow extends Row {
    private final Endpoint endpoint;

    public ChildRow(Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public int getLevel() {
        return 2;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }
}
