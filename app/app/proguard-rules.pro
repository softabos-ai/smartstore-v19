# ====================================================================
# SmartStore V8 - ProGuard/R8 Rules
# هدف: ضغط وتشويش أكواد Java + الحفاظ على الوظائف الأساسية
# ====================================================================

# --- الإعدادات العامة ---
# تحسين أكثر صرامة
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''
-mergeinterfacesaggressively

# إخفاء أسماء الملفات المصدرية وأرقام الأسطر (صعوبة الهندسة العكسية)
-renamesourcefileattribute SourceFile
-keepattributes SourceFile, LineNumberTable
# إخفاء الـ annotations الداخلية
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations
-keepattributes Signature, InnerClasses, EnclosingMethod

# --- الحفاظ على نقاط الدخول الأساسية ---

# Activity الرئيسية (Launcher)
-keep public class com.smartstore.installer.StoreActivity {
    public *;
}

# Activity نتيجة التثبيت (مُسجّلة في Manifest)
-keep public class com.smartstore.installer.InstallResultActivity {
    public *;
}

# ApksInstaller - يُستخدم عبر reflection من StoreActivity
-keep class com.smartstore.installer.ApksInstaller {
    public *;
}

# --- الحفاظ على Application class (إن وُجد) ---
-keep public class * extends android.app.Application {
    public *;
}

# --- الحفاظ على مكوّنات Android العامة ---
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference

# الحفاظ على كل المُنشئات الافتراضية (يحتاجها Android لـ Fragment/Activity instantiation)
-keep public class * extends android.app.Fragment {
    public <init>();
}
-keep public class * extends androidx.fragment.app.Fragment {
    public <init>();
}

# --- ViewBindings (مُفعّل في build.gradle) ---
-keep class com.smartstore.installer.databinding.** { *; }

# --- R class ---
-keep class com.smartstore.installer.R { *; }
-keep class com.smartstore.installer.R$* { *; }

# --- AndroidX ---
-dontwarn androidx.**
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

# Material Components
-dontwarn com.google.android.material.**
-keep class com.google.android.material.** { *; }

# --- FileProvider ---
-keep class androidx.core.content.FileProvider { *; }

# --- Parcelable (لنقل البيانات بين الـ Activities) ---
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# --- Serializable ---
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ---SuppressWarnings للتحذيرات الشائعة ---
-dontwarn org.jetbrains.annotations.**
-dontwarn javax.lang.model.element.**
-dontwarn java.lang.invoke.StringConcatFactory

# --- إزالة الـ logging لتقليل الحجم وإخفاء آثار التطوير ---
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    # نُبقي Log.e للتحقق من الأخطاء الحرجة
}

# --- Native methods ---
-keepclasseswithmembernames class * {
    native <methods>;
}

# --- Enums ---
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- Annotation classes ---
-keepattributes *Annotation*
