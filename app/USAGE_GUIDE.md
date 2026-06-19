# 🎯 SmartStore - دليل الاستخدام الكامل

## 📋 نظرة عامة

**SmartStore** هو تطبيق متجر ذكي يسمح لك بتضمين أي APK داخل مجلد `assets` وعرضه بواجهة احترافية شبيهة بمتجر Google Play.

---

## ✨ المميزات الرئيسية

### 1. 📱 **قراءة APK تلقائياً**
- يقرأ ملف APK من مجلد `assets` تلقائياً
- استخراج معلومات التطبيق (الاسم، الأيقونة، الإصدار، الحجم)
- لا يحتاج إلى أي تعديل في الكود عند تغيير APK

### 2. 🎨 **واجهة احترافية**
- تصميم شبيه بمتجر Google Play
- عرض تقييمات وتنزيلات افتراضية
- معلومات شاملة عن التطبيق
- أزرار ديناميكية (تثبيت/فتح)

### 3. 🚀 **تثبيت ذكي**
- تثبيت صامت بدون أخطاء
- دعم جميع إصدارات Android (5.0+)
- معالجة الصلاحيات تلقائياً
- فتح التطبيق تلقائياً بعد التثبيت

### 4. 🧠 **ذكاء التحقق**
- **إذا كان التطبيق مثبتاً**: يفتحه مباشرة
- **إذا كان غير مثبت**: يعرض زر التثبيت
- تحديث حالة الزر تلقائياً

---

## 📂 هيكل المشروع

```
SmartStore/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/smartstore/installer/
│   │       │   ├── StoreActivity.java       # Activity الرئيسي
│   │       │   └── InstallReceiver.java     # مستقبل بث التثبيت
│   │       ├── res/
│   │       │   ├── layout/
│   │       │   │   └── activity_store.xml   # الواجهة الرئيسية
│   │       │   ├── values/
│   │       │   │   ├── strings.xml          # النصوص
│   │       │   │   ├── colors.xml           # الألوان
│   │       │   │   └── styles.xml           # الأنماط
│   │       │   ├── drawable/                # الرسومات
│   │       │   └── xml/
│   │       │       └── file_paths.xml       # FileProvider paths
│   │       ├── assets/
│   │       │   └── embedded_app.apk         # ضع APK هنا ⚠️
│   │       └── AndroidManifest.xml
│   ├── build.gradle                         # إعدادات البناء
│   └── proguard-rules.pro
├── gradle/
│   └── wrapper/
├── build.gradle                             # إعدادات Project
├── settings.gradle
└── gradle.properties
```

---

## 🔧 كيفية الاستخدام

### **الخطوة 1: إضافة APK الخاص بك**

1. انسخ ملف APK الذي تريد تضمينه
2. ضعه في مجلد: `app/src/main/assets/`
3. **أعد تسمية الملف إلى**: `embedded_app.apk`

```bash
# مثال:
cp /path/to/your/app.apk SmartStore/app/src/main/assets/embedded_app.apk
```

⚠️ **ملاحظة مهمة**: اسم الملف يجب أن يكون `embedded_app.apk` بالضبط!

---

### **الخطوة 2: فتح المشروع في Android Studio**

1. افتح Android Studio
2. اختر `File` → `Open`
3. اختر مجلد `SmartStore`
4. انتظر حتى يتم تحميل Gradle

---

### **الخطوة 3: بناء التطبيق**

#### **أ) من Android Studio (GUI):**

1. اختر `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`
2. انتظر حتى تكتمل عملية البناء
3. اضغط على `locate` في الإشعار
4. ستجد APK في: `app/build/outputs/apk/debug/app-debug.apk`

#### **ب) من Terminal (CLI):**

```bash
cd SmartStore

# بناء APK Debug
./gradlew assembleDebug

# بناء APK Release (موقع)
./gradlew assembleRelease

# تثبيت مباشرة على الجهاز
./gradlew installDebug
```

---

### **الخطوة 4: اختبار التطبيق**

1. **تثبيت SmartStore APK** على الهاتف
2. افتح التطبيق
3. ستظهر واجهة المتجر مع معلومات التطبيق المضمن
4. اضغط على زر **"تثبيت"**
5. سيُطلب منك منح صلاحية التثبيت (إذا لم تكن ممنوحة)
6. بعد التثبيت، سيفتح التطبيق المضمن **تلقائياً**
7. في المرات القادمة، عند فتح SmartStore، سيظهر زر **"فتح التطبيق"** بدلاً من التثبيت

---

## 🎯 الصلاحيات المطلوبة

التطبيق يطلب الصلاحيات التالية:

| الصلاحية | السبب |
|---------|-------|
| `REQUEST_INSTALL_PACKAGES` | لتثبيت APK من مصادر غير معروفة |
| `READ_EXTERNAL_STORAGE` | لقراءة APK من assets |
| `QUERY_ALL_PACKAGES` | للتحقق من التطبيقات المثبتة |
| `INTERNET` | للتحديثات المستقبلية (اختياري) |

---

## 🔄 تغيير APK المضمن

عندما تريد تضمين APK جديد:

1. **احذف** APK القديم من `app/src/main/assets/`
2. **ضع** APK الجديد في نفس المجلد
3. **أعد تسميته** إلى `embedded_app.apk`
4. **أعد بناء** التطبيق:
   ```bash
   ./gradlew clean
   ./gradlew assembleDebug
   ```
5. **ثبّت** APK الجديد على الهاتف

⚠️ **لا تحتاج** لتعديل أي كود! سيتعرف التطبيق على APK الجديد تلقائياً.

---

## 🎨 تخصيص الواجهة

### تغيير الألوان:
عدّل ملف `app/src/main/res/values/colors.xml`:

```xml
<color name="primary_color">#01875f</color>     <!-- اللون الأساسي -->
<color name="accent_color">#00d97e</color>       <!-- اللون الثانوي -->
```

### تغيير النصوص:
عدّل ملف `app/src/main/res/values/strings.xml`:

```xml
<string name="store_title">متجر التطبيقات الذكي</string>
<string name="btn_install">تثبيت</string>
```

### تغيير التقييم والتنزيلات:
عدّل في `StoreActivity.java` (السطور 282-285):

```java
txtRating.setText("4.8");           // التقييم
ratingBar.setRating(4.8f);          // عدد النجوم
txtRatingCount.setText("1.2K تقييم"); // عدد التقييمات
txtDownloads.setText("10K+");       // عدد التنزيلات
```

---

## 🐛 حل المشاكل الشائعة

### 1. **"لم يتم العثور على ملف APK"**
**السبب**: اسم الملف خاطئ أو غير موجود في assets

**الحل**:
```bash
# تأكد من وجود الملف
ls -la app/src/main/assets/

# يجب أن يكون الاسم embedded_app.apk
mv app/src/main/assets/your_app.apk app/src/main/assets/embedded_app.apk
```

---

### 2. **"فشل التثبيت"**
**السبب**: صلاحية التثبيت غير ممنوحة

**الحل**:
1. افتح **الإعدادات** → **التطبيقات** → **SmartStore**
2. اذهب إلى **الأذونات**
3. فعّل **"تثبيت تطبيقات غير معروفة"**

---

### 3. **"التطبيق المضمن لا يفتح"**
**السبب**: التطبيق المضمن لا يحتوي على Activity رئيسية

**الحل**:
تأكد من أن APK المضمن يحتوي على:
```xml
<intent-filter>
    <action android:name="android.intent.action.MAIN" />
    <category android:name="android.intent.category.LAUNCHER" />
</intent-filter>
```

---

### 4. **"Gradle Build Failed"**
**السبب**: مشكلة في إعدادات Gradle

**الحل**:
```bash
# تنظيف المشروع
./gradlew clean

# تحديث Gradle Wrapper
./gradlew wrapper --gradle-version=7.5

# إعادة البناء
./gradlew assembleDebug
```

---

## 📊 معلومات تقنية

| المعلومة | القيمة |
|---------|--------|
| **أدنى إصدار Android** | 5.0 (API 21) |
| **الإصدار المستهدف** | 14 (API 34) |
| **حجم التطبيق الأساسي** | ~2 MB |
| **اللغة البرمجية** | Java |
| **حجم APK النهائي** | 2 MB + حجم APK المضمن |

---

## 🚀 البناء للإصدار النهائي (Release)

### 1. إنشاء Keystore (مفتاح التوقيع):

```bash
keytool -genkey -v -keystore smartstore.keystore -alias smartstore_key \
  -keyalg RSA -keysize 2048 -validity 10000
```

### 2. إنشاء ملف `keystore.properties`:

في مجلد `SmartStore/`:

```properties
storeFile=smartstore.keystore
storePassword=your_store_password
keyAlias=smartstore_key
keyPassword=your_key_password
```

### 3. تعديل `app/build.gradle`:

أضف قبل `android {`:

```gradle
def keystorePropertiesFile = rootProject.file("keystore.properties")
def keystoreProperties = new Properties()
keystoreProperties.load(new FileInputStream(keystorePropertiesFile))

android {
    signingConfigs {
        release {
            keyAlias keystoreProperties['keyAlias']
            keyPassword keystoreProperties['keyPassword']
            storeFile file(keystoreProperties['storeFile'])
            storePassword keystoreProperties['storePassword']
        }
    }
    
    buildTypes {
        release {
            signingConfig signingConfigs.release
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}
```

### 4. بناء Release APK:

```bash
./gradlew assembleRelease
```

APK سيكون في: `app/build/outputs/apk/release/app-release.apk`

---

## 📝 ملاحظات مهمة

### ✅ **المميزات**:
- ✅ يدعم أي APK تضعه في assets
- ✅ واجهة احترافية وجذابة
- ✅ تثبيت سلس بدون أخطاء
- ✅ فتح تلقائي بعد التثبيت
- ✅ ذكاء في التحقق من حالة التطبيق
- ✅ دعم جميع أحجام الشاشات
- ✅ دعم Android 5.0 - 14+

### ⚠️ **القيود**:
- ⚠️ يدعم APK واحد فقط في كل مرة
- ⚠️ اسم APK يجب أن يكون `embedded_app.apk`
- ⚠️ يحتاج صلاحية "تثبيت من مصادر غير معروفة"
- ⚠️ لا يدعم التحديث التلقائي من الإنترنت (حالياً)

---

## 🔮 التطويرات المستقبلية

- [ ] دعم عدة APKات في نفس الوقت
- [ ] التحديث التلقائي من خادم
- [ ] نظام إشعارات للتحديثات
- [ ] دعم Split APKs
- [ ] إضافة لقطات شاشة من APK
- [ ] نظام تقييمات حقيقي

---

## 💡 أمثلة استخدام

### مثال 1: تضمين تطبيق Rainbow V16

```bash
# انسخ Rainbow APK
cp Rainbow-V16-RealScreenshot/app-debug.apk SmartStore/app/src/main/assets/embedded_app.apk

# ابنِ التطبيق
cd SmartStore
./gradlew assembleDebug

# ستجد APK في
# app/build/outputs/apk/debug/app-debug.apk
```

### مثال 2: تضمين أي تطبيق آخر

```bash
# انسخ أي APK
cp /path/to/any_app.apk SmartStore/app/src/main/assets/embedded_app.apk

# ابنِ التطبيق
./gradlew clean assembleDebug
```

---

## 📞 الدعم والمساعدة

إذا واجهت أي مشكلة:

1. تأكد من اسم APK: `embedded_app.apk`
2. تأكد من صلاحية التثبيت
3. تحقق من Logs في Logcat:
   ```bash
   adb logcat | grep SmartStore
   ```
4. أعد بناء التطبيق نظيفاً:
   ```bash
   ./gradlew clean
   ./gradlew assembleDebug
   ```

---

## 🎓 الخلاصة

**SmartStore** هو الحل الأمثل لتضمين أي APK داخل تطبيق متجر احترافي:

✅ **سهل الاستخدام**: ضع APK في assets وابنِ التطبيق
✅ **واجهة احترافية**: شبيهة بمتجر Google Play
✅ **ذكي**: يتعرف على APK تلقائياً
✅ **موثوق**: تثبيت بدون أخطاء

---

**تم التطوير بواسطة**: Smart Developer  
**الإصدار**: 1.0.0  
**التاريخ**: 8 مارس 2025

🎉 **استمتع باستخدام SmartStore!** 🎉
