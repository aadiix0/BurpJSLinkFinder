package burp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LinkParser {
    private static final Pattern ENDPOINT_PATTERN = Pattern.compile(
            "(?:\"|')(((?:[a-zA-Z]{1,10}://|//)[^\"'/]{1,}\\.[a-zA-Z]{2,}[^\"']{0,})|" +
            "((?:/|\\.\\./|\\./)[^\"'><,;| *()(%%$^/\\\\\\[\\]][^\"'><,;|()]{1,})|" +
            "([a-zA-Z0-9_\\-/]{1,}/[a-zA-Z0-9_\\-/]{1,}\\.(?:[a-zA-Z]{1,4}|action)(?:[\\?|#][^\"|']{0,}|))|" +
            "([a-zA-Z0-9_\\-/]{1,}/[a-zA-Z0-9_\\-/]{3,}(?:[\\?|#][^\"|']{0,}|))|" +
            "([a-zA-Z0-9_\\-]{1,}\\.(?:php|asp|aspx|jsp|json|action|html|js|txt|xml)(?:\\?[^\"|']{0,}|)))(?:\"|')"
    );

    public static List<Endpoint> findEndpoints(String jsContent) {
        Set<String> uniqueEndpoints = new HashSet<>();
        List<Endpoint> endpoints = new ArrayList<>();
        Matcher matcher = ENDPOINT_PATTERN.matcher(jsContent);

        while (matcher.find()) {
            String endpointUrl = matcher.group(1);
            if (uniqueEndpoints.add(endpointUrl)) {
                String type = categorizeEndpoint(endpointUrl);
                String method = ""; // Method detection is not implemented in this version
                endpoints.add(new Endpoint(endpointUrl, type, method));
            }
        }

        return endpoints;
    }

    private static String categorizeEndpoint(String endpoint) {
        if (endpoint.startsWith("/api/")) {
            return "API";
        }
        if (endpoint.matches(".*\\.(js|json|xml|html|css|png|jpg|gif)$")) {
            return "File";
        }
        if (endpoint.matches(".*[a-zA-Z]{1,10}://.*")) {
            return "Absolute URL";
        }
        return "Path";
    }
}
