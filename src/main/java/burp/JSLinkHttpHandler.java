package burp;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.handler.HttpHandler;
import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.handler.HttpResponseReceived;
import burp.api.montoya.http.handler.RequestToBeSentAction;
import burp.api.montoya.http.handler.ResponseReceivedAction;
import burp.api.montoya.http.message.responses.HttpResponse;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.SwingUtilities;

public class JSLinkHttpHandler implements HttpHandler {
    private final MontoyaApi api;
    private final ConcurrentHashMap<String, List<Endpoint>> data;
    private final TreeTableModel tableModel;
    private static final List<String> EXCLUDED_LIBS = List.of("jquery", "google-analytics", "gpt.js", "modernizr", "gtm.js", "fbevents.js");

    public JSLinkHttpHandler(MontoyaApi api, ConcurrentHashMap<String, List<Endpoint>> data, TreeTableModel tableModel) {
        this.api = api;
        this.data = data;
        this.tableModel = tableModel;
    }

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent requestToBeSent) {
        return RequestToBeSentAction.continueWith(requestToBeSent);
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived responseReceived) {
        String url = responseReceived.initiatingRequest().url();
        HttpResponse response = responseReceived.response();

        String contentType = response.headerValue("Content-Type");
        boolean isJS = (contentType != null && (contentType.toLowerCase().contains("javascript") || url.toLowerCase().endsWith(".js")));

        if (isJS) {
            for (String excludedLib : EXCLUDED_LIBS) {
                if (url.contains(excludedLib)) {
                    return ResponseReceivedAction.continueWith(responseReceived);
                }
            }

            String body = response.bodyToString();
            List<Endpoint> endpoints = LinkParser.findEndpoints(body);

            if (!endpoints.isEmpty()) {
                data.put(url, endpoints);
                SwingUtilities.invokeLater(() -> tableModel.addRow(url, endpoints));
            }
        }

        return ResponseReceivedAction.continueWith(responseReceived);
    }
}
