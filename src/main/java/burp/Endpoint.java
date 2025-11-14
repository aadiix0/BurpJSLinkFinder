package burp;

public class Endpoint {
    private final String url;
    private final String type;
    private final String method;

    public Endpoint(String url, String type, String method) {
        this.url = url;
        this.type = type;
        this.method = method;
    }

    public String getUrl() {
        return url;
    }

    public String getType() {
        return type;
    }

    public String getMethod() {
        return method;
    }
}
