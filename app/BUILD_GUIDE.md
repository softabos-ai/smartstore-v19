# 🚀 دليل البناء السريع - SmartStore

## ⚡ خطوات سريعة للبناء

### 📦 الخطوة 1: إضافة APK الخاص بك

```bash
# انسخ APK الذي تريد تضمينه
cp /path/to/your/app.apk SmartStore/app/src/main/assets/embedded_app.apk
```

⚠️ **مهم جداً**: يجب أن يكون اسم الملف `embedded_app.apk` بالضبط!

---

### 🔨 الخطوة 2: بناء التطبيق

#### **الطريقة الأولى**: من Android Studio

1. افتح مجلد `SmartStore` في Android Studio
2. انتظر تحميل Gradle
3. اختر: `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`
4. انتظر الانتهاء
5. ستجد APK في: `app/build/outputs/apk/debug/app-debug.apk`

#### **الطريقة الثانية**: من Terminal

```bash
cd SmartStore

# بناء Debug APK
./gradlew assembleDebug

# النتيجة ستكون في:
# app/build/outputs/apk/debug/app-debug.apk
```

---

### 📱 الخطوة 3: تثبيت واختبار

```bash
# تثبيت على جهاز متصل
adb install app/build/outputs/apk/debug/app-debug.apk

# أو من Android Studio
./gradlew installDebug
```

---

## 🎯 كيفية العمل

1. **فتح SmartStore** → يعرض معلومات التطبيق المضمن
2. **الضغط على "تثبيت"** → يثبت التطبيق المضمن
3. **بعد التثبيت** → يفتح التطبيق تلقائياً
4. **المرات القادمة** → يعرض زر "فتح التطبيق" مباشرة

---

## 🔄 تغيير APK المضمن

```bash
# احذف APK القديم
rm SmartStore/app/src/main/assets/embedded_app.apk

# ضع APK جديد
cp /path/to/new_app.apk SmartStore/app/src/main/assets/embedded_app.apk

# أعد البناء
cd SmartStore
./gradlew clean assembleDebug
```

---

## 🐛 حل المشاكل السريع

### **مشكلة**: "لم يتم العثور على APK"
```bash
# تحقق من وجود الملف
ls -la SmartStore/app/src/main/assets/

# يجب أن يكون الاسم: embedded_app.apk
```

### **مشكلة**: "فشل التثبيت"
```
الحل: امنح صلاحية "تثبيت من مصادر غير معروفة" من إعدادات الهاتف
```

### **مشكلة**: "Gradle Build Failed"
```bash
./gradlew clean
./gradlew assembleDebug --stacktrace
```

---

## 📊 متطلبات البناء

- ✅ Android Studio 4.0+
- ✅ Java JDK 8+
- ✅ Gradle 7.0+
- ✅ Android SDK 34

---

## 💡 نصيحة سريعة

إذا كنت تريد بناء Release APK موقّع:

```bash
# أنشئ keystore أولاً
keytool -genkey -v -keystore smartstore.keystore \
  -alias smartstore_key -keyalg RSA -keysize 2048 -validity 10000

# ثم ابنِ Release
./gradlew assembleRelease
```

---

**🎉 هذا كل شيء! استمتع باستخدام SmartStore!**
