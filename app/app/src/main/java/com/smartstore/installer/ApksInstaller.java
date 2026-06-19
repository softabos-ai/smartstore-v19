package com.smartstore.installer;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.util.Log;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * ✅ ApksInstaller V13 - مطابق لنمط V7 الأصلي الذي كان يعمل 100%
 *
 * - يستخدم PendingIntent.getBroadcast() مع InstallResultReceiver
 * - يستخدم "split_N.apk" كأسماء (تعمل دائماً)
 * - لا يستخدم setInstallReason أو reflection معقد
 * - يدعم APK مفرد و APKS (split apks)
 */
public class ApksInstaller {
    private static final String TAG = "ApksInstaller";
    private Context context;
    public ApksInstaller(Context context) { this.context = context; }

    /** فحص سريع: هل الملف هو APKS (يحتوي على أكثر من APK)؟ */
    public boolean isApksFile(File file) {
        try {
            ZipInputStream zis = new ZipInputStream(new FileInputStream(file));
            int count = 0;
            while (zis.getNextEntry() != null && ++count > 1) { zis.close(); return true; }
            zis.close();
        } catch (IOException e) {}
        return false;
    }

    /** استخراج جميع ملفات .apk من ملف .apks (zip) */
    public List<File> extractSplitApks(File apksFile, File extractDir) throws IOException {
        List<File> splitApks = new ArrayList<>();
        if (!extractDir.exists()) extractDir.mkdirs();
        ZipInputStream zis = new ZipInputStream(new FileInputStream(apksFile));
        ZipEntry entry;
        byte[] buffer = new byte[8192];
        while ((entry = zis.getNextEntry()) != null) {
            if (entry.getName().endsWith(".apk")) {
                File apkFile = new File(extractDir, new File(entry.getName()).getName());
                OutputStream os = new FileOutputStream(apkFile);
                int length;
                while ((length = zis.read(buffer)) > 0) os.write(buffer, 0, length);
                os.close();
                splitApks.add(apkFile);
            }
        }
        zis.close();
        return splitApks;
    }

    /** تثبيت Split APKs باستخدام PackageInstaller.Session */
    public void installSplitApks(List<File> splitApks, String packageName) throws IOException {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);

        int sessionId = installer.createSession(params);
        PackageInstaller.Session session = installer.openSession(sessionId);

        try {
            for (int i = 0; i < splitApks.size(); i++) {
                File apkFile = splitApks.get(i);
                InputStream in = new FileInputStream(apkFile);
                OutputStream out = session.openWrite("split_" + i + ".apk", 0, apkFile.length());
                byte[] buffer = new byte[8192];
                int length;
                while ((length = in.read(buffer)) > 0) out.write(buffer, 0, length);
                session.fsync(out);
                out.close();
                in.close();
            }

            // ✅ BroadcastReceiver-based callback (مطابق لـ V7 الأصلي)
            Intent intent = new Intent(context, InstallResultReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0);
            session.commit(pendingIntent.getIntentSender());
        } catch (Exception e) {
            session.abandon();
            throw new IOException("Failed: " + e.getMessage());
        }
    }

    /** تثبيت APK مفرد (يُمرّر كقائمة بحجم 1) */
    public void installSingleApk(File apkFile, String packageName) throws IOException {
        List<File> singleApk = new ArrayList<>();
        singleApk.add(apkFile);
        installSplitApks(singleApk, packageName);
    }
}
