package dev.beyman.pixeltaskbarenabler;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LogManager {

    private final SharedPreferences prefsProtected;
    private final SharedPreferences prefsNormal;
    private final Context context;
    private final SimpleDateFormat timeFormat;

    public interface LogUpdateListener {
        void onLogUpdated();
    }

    private LogUpdateListener listener;

    public LogManager(Context context, SharedPreferences prefsProtected, SharedPreferences prefsNormal) {
        this.context = context;
        this.prefsProtected = prefsProtected;
        this.prefsNormal = prefsNormal;
        this.timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    }

    public void setLogUpdateListener(LogUpdateListener listener) {
        this.listener = listener;
    }

    public void logMessage(int resId, Object... args) {
        String resName = context.getResources().getResourceEntryName(resId);
        String time = timeFormat.format(new Date());

        try {
            String currentLogsJson = prefsProtected.getString("app_logs_json", "[]");
            JSONArray logsArray = new JSONArray(currentLogsJson);

            JSONObject newLog = new JSONObject();
            newLog.put("time", time);
            newLog.put("resName", resName);
            if (args != null && args.length > 0) {
                newLog.put("arg", args[0]);
            }

            JSONArray newLogsArray = new JSONArray();
            newLogsArray.put(newLog);
            for (int i = 0; i < logsArray.length(); i++) {
                newLogsArray.put(logsArray.getJSONObject(i));
                if (i >= 50) break;
            }

            String newJson = newLogsArray.toString();
            prefsProtected.edit().putString("app_logs_json", newJson).apply();
            prefsNormal.edit().putString("app_logs_json", newJson).apply();

            if (listener != null) {
                listener.onLogUpdated();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String buildLogString() {
        try {
            String currentLogsJson = prefsProtected.getString("app_logs_json", "[]");
            JSONArray logsArray = new JSONArray(currentLogsJson);
            if (logsArray.length() == 0) return "";

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < logsArray.length(); i++) {
                JSONObject logObj = logsArray.getJSONObject(i);
                String time = logObj.getString("time");
                String resName = logObj.getString("resName");

                int resId = context.getResources().getIdentifier(resName, "string", context.getPackageName());
                if (resId != 0) {
                    String msg;
                    if (logObj.has("arg")) {
                        msg = context.getString(resId, logObj.getInt("arg"));
                    } else {
                        msg = context.getString(resId);
                    }
                    sb.append("[").append(time).append("] ").append(msg).append("\n");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public void clearLogs() {
        prefsNormal.edit().putString("app_logs_json", "[]").apply();
        prefsProtected.edit().putString("app_logs_json", "[]").apply();
        if (listener != null) {
            listener.onLogUpdated();
        }
    }
}
