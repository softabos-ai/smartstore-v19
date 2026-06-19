package com.smartstore.installer;
import android.content.*;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

/**
 * ✅ InstallResultReceiver - يستقبل نتيجة PackageInstaller API
 *
 * مطابق لـ V7 الأصلي الذي كان يعمل بنجاح.
 * يستخدم PendingIntent.getBroadcast() (وليس getActivity).
 */
public class InstallResultReceiver extends BroadcastReceiver {
    private static final String TAG = "InstallResult";

    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
        String packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME);
        String statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);

        Log.d(TAG, "Install result: status=" + status + ", pkg=" + packageName + ", msg=" + statusMessage);

        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION: {
                Intent confirmIntent;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
                } else {
                    confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                }
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        context.startActivity(confirmIntent);
                    } catch (Exception e) {
                        Log.e(TAG, "Cannot start confirm intent", e);
                    }
                }
                break;
            }

            case PackageInstaller.STATUS_SUCCESS: {
                Toast.makeText(context, "تم التثبيت بنجاح ✓", Toast.LENGTH_LONG).show();
                launchInstalledApp(context, packageName);
                break;
            }

            case PackageInstaller.STATUS_FAILURE:
            case PackageInstaller.STATUS_FAILURE_ABORTED:
            case PackageInstaller.STATUS_FAILURE_BLOCKED:
            case PackageInstaller.STATUS_FAILURE_CONFLICT:
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
            case PackageInstaller.STATUS_FAILURE_INVALID:
            case PackageInstaller.STATUS_FAILURE_STORAGE: {
                String msg = (statusMessage != null && !statusMessage.isEmpty())
                        ? statusMessage
                        : "خطأ في التثبيت (" + status + ")";
                Toast.makeText(context, "فشل التثبيت: " + msg, Toast.LENGTH_LONG).show();
                break;
            }

            default:
                break;
        }
    }

    private void launchInstalledApp(Context context, String packageName) {
        if (packageName == null) return;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    context.startActivity(launchIntent);
                }
            } catch (Exception e) {
                Log.e(TAG, "Cannot launch installed app: " + packageName, e);
            }
        }, 1000);
    }
}
