package burp;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.proxy.http.ProxyHttpResponseHandler;
import burp.api.montoya.proxy.http.ProxyResponseReceivedAction;
import burp.api.montoya.proxy.http.ProxyResponseToBeSentAction;
import burp.api.montoya.proxy.http.InterceptedHttpResponse;
import burp.api.montoya.http.message.responses.HttpResponse;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.SwingUtilities;

public class JSLinkProxyHandler implements ProxyHttpResponseHandler {
    private final MontoyaApi api;
    private final ConcurrentHashMap<String, List<Endpoint>> data;
    private final TreeTableModel tableModel;
    private static final List<String> EXCLUDED_LIBS = List.of("jquery", "google-analytics", "gpt.js", "modernizr", "gtm.js", "fbevents.js");

    public JSLinkProxyHandler(MontoyaApi api, ConcurrentHashMap<String, List<Endpoint>> data, TreeTableModel tableModel) {
        this.api = api;
        this.data = data;
        this.tableModel = tableModel;
    }

    @Override
    public ProxyResponseReceivedAction handleResponseReceived(InterceptedHttpResponse interceptedResponse) {
        String url = interceptedResponse.initiatingRequest().url();

        String contentType = interceptedResponse.headerValue("Content-Type");
        boolean isJS = (contentType != null && (contentType.toLowerCase().contains("javascript") || url.toLowerCase().endsWith(".js")));

        if (isJS) {
            for (String excludedLib : EXCLUDED_LIBS) {
                if (url.contains(excludedLib)) {
                    return ProxyResponseReceivedAction.continueWith(interceptedResponse);
                }
            }

            String body = interceptedResponse.bodyToString();
            List<Endpoint> endpoints = LinkParser.findEndpoints(body);

            if (!endpoints.isEmpty()) {
                data.put(url, endpoints);
                SwingUtilities.invokeLater(() -> tableModel.addRow(url, endpoints));
            }
        }

        return ProxyResponseReceivedAction.continueWith(interceptedResponse);
    }

    @Override
    public ProxyResponseToBeSentAction handleResponseToBeSent(InterceptedHttpResponse interceptedResponse) {
        return ProxyResponseToBeSentAction.continueWith(interceptedResponse);
    }
}
