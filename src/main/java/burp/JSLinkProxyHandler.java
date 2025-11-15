package burp;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.proxy.http.ProxyResponseHandler;
import burp.api.montoya.proxy.http.ProxyResponseReceivedAction;
import burp.api.montoya.proxy.http.ProxyResponseToBeSentAction;
import burp.api.montoya.proxy.http.InterceptedResponse;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.responses.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.SwingUtilities;

public class JSLinkProxyHandler implements ProxyResponseHandler {

    private final MontoyaApi api;
    private final Map<String, JSFileData> allJSFiles;
    private final JSFileTableModel tableModel;
    private final DataPersistence dataPersistence;

    public JSLinkProxyHandler(MontoyaApi api, Map<String, JSFileData> allJSFiles, JSFileTableModel tableModel, DataPersistence dataPersistence) {
        this.api = api;
        this.allJSFiles = allJSFiles;
        this.tableModel = tableModel;
        this.dataPersistence = dataPersistence;
    }

    @Override
    public ProxyResponseReceivedAction handleResponseReceived(InterceptedResponse interceptedResponse) {

        try {
            HttpResponse response = interceptedResponse;
            String url = interceptedResponse.initiatingRequest().url();

            String contentType = getHeaderValue(response.headers(), "Content-Type");

            boolean isJavaScript = false;
            if (contentType != null) {
                isJavaScript = contentType.toLowerCase().contains("javascript");
            }
            if (!isJavaScript && url != null) {
                isJavaScript = url.toLowerCase().endsWith(".js");
            }

            if (isJavaScript) {
                String body = response.bodyToString();
                List<String> currentEndpoints = LinkParser.findEndpoints(body).stream()
                        .map(Endpoint::getUrl)
                        .collect(Collectors.toList());

                if (allJSFiles.containsKey(url)) {
                    // Existing file
                    JSFileData jsFileData = allJSFiles.get(url);
                    List<String> firstFinding = jsFileData.getFirstFinding();
                    List<String> newEndpoints = currentEndpoints.stream()
                            .filter(e -> !firstFinding.contains(e))
                            .collect(Collectors.toList());

                    if (!newEndpoints.isEmpty()) {
                        jsFileData.getLatest().addAll(newEndpoints);
                        jsFileData.setLastScanTimestamp(System.currentTimeMillis());
                    }
                } else {
                    // New file
                    allJSFiles.put(url, new JSFileData(url, currentEndpoints));
                }

                dataPersistence.saveData(allJSFiles);
                SwingUtilities.invokeLater(tableModel::updateRows);
            }

        } catch (Exception e) {
            api.logging().logToError("Error processing response: " + e.getMessage());
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
