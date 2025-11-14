package burp;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class BurpExtender implements BurpExtension {
    private MontoyaApi api;
    private ConcurrentHashMap<String, List<Endpoint>> currentData;
    private ConcurrentHashMap<String, List<Endpoint>> historicData;
    private MainTab mainTab;

    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;
        this.currentData = new ConcurrentHashMap<>();
        this.historicData = new ConcurrentHashMap<>();
        this.mainTab = new MainTab(currentData, historicData);
        mainTab.initialize();

        api.extension().setName("NewJSLink Finder");

        api.userInterface().registerSuiteTab("NewJSLink", mainTab);
        api.proxy().registerResponseHandler(new JSLinkProxyHandler(api, currentData, mainTab.getTableModel()));

        api.logging().logToOutput("NewJSLink Finder loaded successfully.");
    }
}
