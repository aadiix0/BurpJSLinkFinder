package burp;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import java.util.Map;
import javax.swing.SwingUtilities;

public class BurpExtender implements BurpExtension {
    @Override
    public void initialize(MontoyaApi api) {
        SwingUtilities.invokeLater(() -> {
            try {
                api.extension().setName("NewJSLink Finder");

                DataPersistence dataPersistence = new DataPersistence(api);
                Map<String, JSFileData> allJSFiles = dataPersistence.loadData();

                MainTab mainTab = new MainTab(api, allJSFiles);

                api.userInterface().registerSuiteTab("JSLink Finder", mainTab.getComponent());

                JSFileTableModel tableModel = mainTab.getTableModel();
                api.proxy().registerResponseHandler(new JSLinkProxyHandler(api, allJSFiles, tableModel, dataPersistence));

                api.logging().logToOutput("NewJSLink Finder loaded successfully.");
            } catch (Exception e) {
                api.logging().logToError("Error initializing extension: " + e.getMessage());
            }
        });
    }
}
