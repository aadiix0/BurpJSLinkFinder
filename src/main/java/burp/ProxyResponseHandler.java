package burp;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.proxy.http.ProxyResponseHandler;
import burp.api.montoya.proxy.http.ProxyResponseReceivedAction;
import burp.api.montoya.proxy.http.ProxyResponseReceived;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.SwingUtilities;

public class ProxyResponseHandler implements burp.api.montoya.proxy.ProxyResponseHandler {
    private final MontoyaApi api;
    private final ConcurrentHashMap<String, List<Endpoint>> data;
    private final TreeTableModel tableModel;
    private static final List<String> EXCLUDED_LIBS = List.of("jquery", "google-analytics", "gpt.js", "modernizr", "gtm.js", "fbevents.js");

    public ProxyResponseHandler(MontoyaApi api, ConcurrentHashMap<String, List<Endpoint>> data, TreeTableModel tableModel) {
        this.api = api;
        this.data = data;
        this.tableModel = tableModel;
    }

    @Override
    public ProxyResponseReceivedAction handleResponseReceived(ProxyResponseReceived responseReceived) {
        String url = responseReceived.request().url();
        HttpResponse response = responseReceived.response();

        boolean isJsFile = url.endsWith(".js") ||
                (response.mimeType() != null &&
                        (response.mimeType().toString().toLowerCase().contains("application/javascript") ||
                                response.mimeType().toString().toLowerCase().contains("text/javascript")));

        if (isJsFile) {
            for (String excludedLib : EXCLUDED_LIBS) {
                if (url.contains(excludedLib)) {
                    return ProxyResponseReceivedAction.continueWith(responseReceived);
                }
            }

            String jsContent = response.bodyToString();
            List<Endpoint> endpoints = LinkParser.findEndpoints(jsContent);

            if (!endpoints.isEmpty()) {
                data.put(url, endpoints);
                SwingUtilities.invokeLater(() -> tableModel.addRow(url, endpoints));
            }
        }

        return ProxyResponseReceivedAction.continueWith(responseReceived);
    }
}
