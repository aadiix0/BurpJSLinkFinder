package burp;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class BurpExtender implements BurpExtension {
    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("NewJSLink Finder");

        ConcurrentHashMap<String, List<Endpoint>> currentData = new ConcurrentHashMap<>();

        MainTab mainTab = new MainTab(api, currentData);

        api.userInterface().registerSuiteTab("JSLink Finder", mainTab.getComponent());

        JSFileTableModel tableModel = mainTab.getTableModel();
        api.proxy().registerResponseHandler(new JSLinkProxyHandler(api, currentData, tableModel));

        api.logging().logToOutput("NewJSLink Finder loaded successfully.");
    }
}
