package burp;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import java.awt.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.SwingUtilities;

public class BurpExtender implements BurpExtension {
    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("JSLink Finder");

        try {
            // Load data
            DataPersistence dataPersistence = new DataPersistence(api);
            Map<String, JSFileData> allJSFiles = dataPersistence.loadData();
            ConcurrentHashMap<String, JSFileData> concurrentData = new ConcurrentHashMap<>(allJSFiles);

            // Initialize UI on Swing thread
            SwingUtilities.invokeLater(() -> {
                try {
                    api.logging().logToOutput("Step 1: Creating MainTab");
                    MainTab mainTab = new MainTab(api, concurrentData, dataPersistence);
                    api.logging().logToOutput("Step 2: MainTab created successfully");

                    api.logging().logToOutput("Step 3: Getting component");
                    Component component = mainTab.getComponent();
                    api.logging().logToOutput("Step 4: Component retrieved successfully");

                    // CRITICAL: Check component is not null
                    if (component == null) {
                        api.logging().logToError("ERROR: getComponent() returned null!");
                        return;
                    }

                    api.logging().logToOutput("Step 5: Registering tab");
                    api.userInterface().registerSuiteTab("JSLink Finder", component);
                    api.logging().logToOutput("Step 6: Tab registered successfully");

                    // Register proxy handler
                    JSFileTableModel tableModel = mainTab.getTableModel();
                    api.proxy().registerResponseHandler(
                        new JSLinkProxyHandler(api, concurrentData, tableModel, dataPersistence)
                    );

                    api.logging().logToOutput("Extension loaded successfully");

                } catch (Exception e) {
                    api.logging().logToError("Error initializing UI: " + e.getMessage(), e);
                }
            });

        } catch (Exception e) {
            api.logging().logToError("Error loading extension: " + e.getMessage(), e);
        }
    }
}
