package burp;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class BurpExtender implements BurpExtension {
    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("NewJSLink Finder");

        DataPersistence dataPersistence = new DataPersistence(api);
        Map<String, JSFileData> allJSFiles = dataPersistence.loadData();

        MainTab mainTab = new MainTab(api, allJSFiles);

        api.userInterface().registerSuiteTab("JSLink Finder", mainTab.getComponent());

        JSFileTableModel tableModel = mainTab.getTableModel();
        api.proxy().registerResponseHandler(new JSLinkProxyHandler(api, allJSFiles, tableModel, dataPersistence));

        api.logging().logToOutput("NewJSLink Finder loaded successfully.");
    }
}
