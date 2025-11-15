package burp;

import java.util.List;

public class CategoryRow {
    private final String categoryName;
    private final List<String> endpoints;

    public CategoryRow(String categoryName, List<String> endpoints) {
        this.categoryName = categoryName;
        this.endpoints = endpoints;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public List<String> getEndpoints() {
        return endpoints;
    }

    public int getEndpointCount() {
        return endpoints.size();
    }
}
