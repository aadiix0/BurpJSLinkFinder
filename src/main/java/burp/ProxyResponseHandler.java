package burp;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.proxy.ProxyResponseHandler;
import burp.api.montoya.proxy.ProxyResponseReceivedAction;
import burp.api.montoya.proxy.ProxyResponseReceivedRequestResponse;

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
    public ProxyResponseReceivedAction handleResponseReceived(ProxyResponseReceivedRequestResponse requestResponse) {
        String url = requestResponse.request().url();
        HttpResponse response = requestResponse.response();

        boolean isJsFile = url.endsWith(".js") ||
                (response.contentType() != null &&
                        (response.contentType().toString().toLowerCase().contains("application/javascript") ||
                                response.contentType().toString().toLowerCase().contains("text/javascript")));

        if (isJsFile) {
            for (String excludedLib : EXCLUDED_LIBS) {
                if (url.contains(excludedLib)) {
                    return ProxyResponseReceivedAction.continueWith(requestResponse);
                }
            }

            String jsContent = response.bodyToString();
            List<Endpoint> endpoints = LinkParser.findEndpoints(jsContent);

            if (!endpoints.isEmpty()) {
                data.put(url, endpoints);
                SwingUtilities.invokeLater(() -> tableModel.addRow(url, endpoints));
            }
        }

        return ProxyResponseReceivedAction.continueWith(requestResponse);
    }
}
