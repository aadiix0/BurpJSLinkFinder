package burp;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JSFileRefresher {

    private final MontoyaApi api;
    private final Map<String, JSFileData> allJSFilesData;
    private final JSFileTableModel tableModel;
    private final ExecutorService executor;

    // Regex patterns to extract endpoints (same as JSLinkProxyHandler)
    private static final Pattern[] ENDPOINT_PATTERNS = {
        Pattern.compile("(?:\"|\')(((?:[a-zA-Z]{1,10}://|//)[^\"'/]{1,}\\.[a-zA-Z]{2,}[^\"']{0,})|((?:/|\\.\\./|\\./)[^\"'><,;| *()(%%$^/\\\\\\[\\]][^\"'><,;|()]{1,})|([a-zA-Z0-9_\\-/]{1,}/[a-zA-Z0-9_\\-/]{1,}\\.(?:[a-zA-Z]{1,4}|action)(?:[\\?|#][^\"|']{0,}|))|([a-zA-Z0-9_\\-/]{1,}/[a-zA-Z0-9_\\-/]{3,}(?:[\\?|#][^\"|']{0,}|))|([a-zA-Z0-9_\\-]{1,}\\.(?:php|asp|aspx|jsp|json|action|html|js|txt|xml)(?:[\\?|#][^\"|']{0,}|)))(?:\"|')"),
        Pattern.compile("(?:(?:href|src|action)\\s*=\\s*[\"\'])([^\"'>]+)(?:[\"\'])"),
        Pattern.compile("(?:url|path|endpoint|uri)\\s*[=:]\\s*[\"\']([^\"']+)[\"\']")
    };

    public JSFileRefresher(MontoyaApi api, Map<String, JSFileData> allJSFilesData, JSFileTableModel tableModel) {
        this.api = api;
        this.allJSFilesData = allJSFilesData;
        this.tableModel = tableModel;
        this.executor = Executors.newFixedThreadPool(5); // 5 concurrent requests
    }

    public void refreshAllJSFiles() {
        api.logging().logToOutput("=== Starting refresh of all JS files ===");

        List<String> jsUrls = new ArrayList<>(allJSFilesData.keySet());
        int total = jsUrls.size();

        api.logging().logToOutput("Found " + total + " JS files to refresh");

        for (String jsUrl : jsUrls) {
            executor.submit(() -> refreshSingleJSFile(jsUrl));
        }
    }

    private void refreshSingleJSFile(String jsUrl) {
        try {
            api.logging().logToOutput("Refreshing: " + jsUrl);

            // Create HTTP request
            HttpRequest request = HttpRequest.httpRequestFromUrl(jsUrl);

            // Send request
            HttpRequestResponse httpReqResp = api.http().sendRequest(request);
            HttpResponse response = httpReqResp.response();

            if (response == null) {
                api.logging().logToError("No response for: " + jsUrl);
                return;
            }

            // Extract endpoints from response body
            String responseBody = response.bodyToString();
            List<String> newEndpoints = extractEndpoints(responseBody);

            api.logging().logToOutput("Extracted " + newEndpoints.size() + " endpoints from " + jsUrl);

            // Compare with existing data
            processNewEndpoints(jsUrl, newEndpoints);

        } catch (Exception e) {
            api.logging().logToError("Error refreshing " + jsUrl + ": " + e.getMessage());
        }
    }

    private List<String> extractEndpoints(String content) {
        List<String> endpoints = new ArrayList<>();

        for (Pattern pattern : ENDPOINT_PATTERNS) {
            Matcher matcher = pattern.matcher(content);
            while (matcher.find()) {
                String endpoint = matcher.group(1);
                if (endpoint != null && !endpoint.isEmpty()) {
                    // Clean up endpoint
                    endpoint = endpoint.trim();
                    if (!endpoints.contains(endpoint)) {
                        endpoints.add(endpoint);
                    }
                }
            }
        }

        return endpoints;
    }

    private void processNewEndpoints(String jsUrl, List<String> newEndpoints) {
        JSFileData jsData = allJSFilesData.get(jsUrl);
        if (jsData == null) return;

        List<String> firstFinding = jsData.getFirstFinding();
        List<String> latest = jsData.getLatest();

        List<String> newlyFound = new ArrayList<>();

        // Find endpoints not in First Finding or Latest
        for (String endpoint : newEndpoints) {
            if (!firstFinding.contains(endpoint) && !latest.contains(endpoint)) {
                newlyFound.add(endpoint);
            }
        }

        if (!newlyFound.isEmpty()) {
            api.logging().logToOutput("✓ Found " + newlyFound.size() + " NEW endpoints in " + jsUrl);

            // Add to Latest
            latest.addAll(newlyFound);
            jsData.setLatest(latest);

            // Update table
            tableModel.updateJSFileCount(jsUrl, firstFinding.size() + latest.size());

            // Show Latest section if not already visible
            tableModel.ensureLatestSectionVisible(jsUrl);

        } else {
            api.logging().logToOutput("✓ No new endpoints in " + jsUrl);
        }
    }
}
