package burp;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.proxy.http.ProxyResponseHandler;
import burp.api.montoya.proxy.http.ProxyResponseReceivedAction;
import burp.api.montoya.proxy.http.ProxyResponseToBeSentAction;
import burp.api.montoya.proxy.http.InterceptedResponse;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.responses.HttpResponse;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.SwingUtilities;

public class JSLinkProxyHandler implements ProxyResponseHandler {

    private final MontoyaApi api;
    private final ConcurrentHashMap<String, List<Endpoint>> data;
    private final TreeTableModel tableModel;

    public JSLinkProxyHandler(MontoyaApi api, ConcurrentHashMap<String, List<Endpoint>> data, TreeTableModel tableModel) {
        this.api = api;
        this.data = data;
        this.tableModel = tableModel;
    }

    @Override
    public ProxyResponseReceivedAction handleResponseReceived(InterceptedResponse interceptedResponse) {

        try {
            HttpResponse response = interceptedResponse;
            String url = interceptedResponse.initiatingRequest().url();

            String contentType = getHeaderValue(response.headers(), "Content-Type");

            boolean isJavaScript = false;

            if (contentType != null) {
                String lowerContentType = contentType.toLowerCase();
                isJavaScript = lowerContentType.contains("javascript") ||
                              lowerContentType.contains("application/javascript") ||
                              lowerContentType.contains("text/javascript");
            }

            if (!isJavaScript && url != null) {
                isJavaScript = url.toLowerCase().endsWith(".js");
            }

            if (isJavaScript && url != null) {
                String lowerUrl = url.toLowerCase();
                String[] excludeList = {"jquery", "google-analytics", "gpt.js",
                                       "modernizr", "gtm", "fbevents"};

                for (String exclude : excludeList) {
                    if (lowerUrl.contains(exclude)) {
                        isJavaScript = false;
                        break;
                    }
                }
            }

            if (isJavaScript) {
                api.logging().logToOutput("JS file detected: " + url);

                String body = response.bodyToString();
                List<Endpoint> endpoints = LinkParser.findEndpoints(body);

                if (!endpoints.isEmpty()) {
                    data.put(url, endpoints);
                    SwingUtilities.invokeLater(() -> tableModel.addRow(url, endpoints));
                    api.logging().logToOutput("Processing JS file: " + url + " (size: " + body.length() + " bytes)");
                }
            }

        } catch (Exception e) {
            api.logging().logToError("Error processing response: " + e.getMessage());
            e.printStackTrace(new java.io.PrintWriter(api.logging().error()));
        }

        return ProxyResponseReceivedAction.continueWith(interceptedResponse);
    }

    @Override
    public ProxyResponseToBeSentAction handleResponseToBeSent(InterceptedResponse interceptedResponse) {
        return ProxyResponseToBeSentAction.continueWith(interceptedResponse);
    }

    private String getHeaderValue(List<HttpHeader> headers, String headerName) {
        for (HttpHeader header : headers) {
            if (header.name().equalsIgnoreCase(headerName)) {
                return header.value();
            }
        }
        return null;
    }
}
