package dev.beyman.pixeltaskbarenabler.utils;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class DefaultSettings {

    private static JSONObject defaults;

    public static void init() {
        if (defaults != null) return;
        try {
            InputStream is = DefaultSettings.class.getClassLoader().getResourceAsStream("assets/app_defaults.json");
            if (is == null) {
                // Fallback or error
                defaults = new JSONObject();
                return;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            defaults = new JSONObject(sb.toString());
        } catch (Exception e) {
            e.printStackTrace();
            defaults = new JSONObject();
        }
    }

    public static String getString(String key, String defValue) {
        init();
        return defaults.optString(key, defValue);
    }

    public static String getString(String key) {
        init();
        return defaults.optString(key);
    }

    public static int getInt(String key, int defValue) {
        init();
        return defaults.optInt(key, defValue);
    }

    public static int getInt(String key) {
        init();
        return defaults.optInt(key);
    }

    public static boolean getBoolean(String key, boolean defValue) {
        init();
        return defaults.optBoolean(key, defValue);
    }

    public static boolean getBoolean(String key) {
        init();
        return defaults.optBoolean(key);
    }
}
