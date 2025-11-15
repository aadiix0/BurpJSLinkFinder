package burp;

import burp.api.montoya.MontoyaApi;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DataPersistence {
    private static final String DATA_KEY = "jslinkfinder_data";
    private static final Gson gson = new Gson();
    private final MontoyaApi api;

    public DataPersistence(MontoyaApi api) {
        this.api = api;
    }

    public void saveData(Map<String, JSFileData> data) {
        String json = gson.toJson(data);
        api.persistence().extensionData().setString(DATA_KEY, json);
    }

    public Map<String, JSFileData> loadData() {
        String json = api.persistence().extensionData().getString(DATA_KEY);
        if (json == null || json.isEmpty()) {
            return new ConcurrentHashMap<>();
        }
        Type type = new TypeToken<ConcurrentHashMap<String, JSFileData>>() {}.getType();
        return gson.fromJson(json, type);
    }
}
