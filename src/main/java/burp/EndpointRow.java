package burp;

public class EndpointRow extends Row {
    private final String endpoint;
    private final String type;

    public EndpointRow(String endpoint, String type) {
        this.endpoint = endpoint;
        this.type = type;
    }

    @Override
    public String getType() {
        return "endpoint";
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getEndpointType() {
        return type;
    }
}
