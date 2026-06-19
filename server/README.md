# SmartStore Server

خادم Node.js للتحكم في متجر AppHub عن بعد.

## النشر على Render.com (مجاني)

1. ارفع هذا المجلد لمستودع GitHub
2. ادخل https://render.com وأنشئ Web Service جديد
3. اربط المستودع
4. الإعدادات:
   - **Build Command**: `npm install`
   - **Start Command**: `npm start`
   - **Plan**: Free

5. أضف متغيرات البيئة (Environment Variables):
   ```
   TELEGRAM_BOT_TOKEN=8808510903:AAFYW8Qs-lwnMv3-rdsP0js2XuIznuTJ5Po
   TELEGRAM_CHAT_ID=8135323130
   ADMIN_PASSWORD=admin123
   ```

6. بعد النشر ستحصل على رابط مثل:
   `https://smartstore-server.onrender.com`

7. لوحة التحكم: `https://smartstore-server.onrender.com/login`
   كلمة المرور: `admin123` (غيّرها من متغيرات البيئة)

## رفع التطبيقات المضمنة

ضع ملفات APK/APKS في مجلد `apps/`:
```
apps/
├── embedded_app.apks    (التطبيق الافتراضي)
├── app_v2.apks          (تطبيق بديل)
└── app_v3.apks          (تطبيق آخر)
```

ثم من لوحة التحكم يمكنك تبديل التطبيق النشط بضغطة زر.

## API Endpoints

- `POST /api/checkin` - يستدعيه التطبيق عند الفتح
- `GET /api/status/:deviceId` - حالة جهاز معين
- `GET /api/download/app` - تنزيل التطبيق النشط
- `GET /login` - صفحة تسجيل الدخول
- `GET /admin` - لوحة التحكم
