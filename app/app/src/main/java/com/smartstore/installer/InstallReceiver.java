package com.smartstore.installer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

/**
 * مستقبل بث لمراقبة عملية تثبيت التطبيقات
 * يتم تفعيله عند تثبيت أو تحديث أي تطبيق
 */
public class InstallReceiver extends BroadcastReceiver {

    private static final String TAG = "InstallReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        
        if (Intent.ACTION_PACKAGE_ADDED.equals(action) || 
            Intent.ACTION_PACKAGE_REPLACED.equals(action)) {
            
            String packageName = intent.getData().getSchemeSpecificPart();
            Log.d(TAG, "Package installed/updated: " + packageName);
            
            // يمكن إضافة منطق إضافي هنا (مثل إرسال إشعار)
            if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
                Log.d(TAG, "New installation detected");
            } else {
                Log.d(TAG, "Update detected");
            }
        }
    }
}
