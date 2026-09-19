# معماری و مستندات فنی — دژ پاسارگاد

**آخرین به‌روزرسانی:** فاز ۷ (QA و Audit) — سپتامبر ۲۰۲۶
**این سند:** architecture، packages، security model، crypto design، DB schema، backup format، restore behavior، build configuration، dependencies، testing strategy، تصمیم‌های مهم و محدودیت‌ها.

---

## ۱. نمای کلی معماری

تک‌ماژول (`app`)، تک‌اکتیویتی، Compose-first، آفلاین محض:

```
UI (Compose, M3, RTL)
   │  events ↑ / state ↓ (StateFlow)
ViewModels (presentation/*)
   │
Use Cases (domain/*)            ← قواعد کسب‌وکار، بدون Android
   │
Repositories (data/vault, data/security, data/backup, settings)
   │
Room 2.8.5 (metadata ستونی + payload رمزشده) │ Files (security/backup) │ DataStore (settings)
```

- **DI دستی** با `AppContainer` (بدون Hilt — طبق تصمیم فاز ۱، justified بودن لازم بود و نبود).
- **قفل=session:** `VaultLockState` تنها مرجع ناوبری است؛ Navigation 3 (`rememberNavBackStack`) back stack را از روی lockState بازمی‌سازد — کاربر قفل‌شده هرگز صفحه‌ای از گاوصندوق باز نمی‌بیند.
- **هیچ secret ای از navigation arguments عبور نمی‌کند** (فقط idهای UUID غیرحساس).

## ۲. پکیج‌ها

| پکیج | مسئولیت |
|-------|----------|
| `cryptography/` | اولیه‌های رمزنگاری خالص JVM: AES-256-GCM، PBKDF2، KeyWrapper، EncryptionContainer (DPVG) |
| `security/` | VaultSession (تنها دارندهٔ DEK در حافظه)، AutoLockController، LoginAttemptPolicy، VaultSecurityStorage، KeystoreGateway |
| `domain/` | مدل‌ها (VaultEntry/Draft/VaultLockState)، VaultEntryRepository، ۸+ use case، VaultSortOption، PasswordStrengthMeter، PasswordStrengthValidator |
| `data/vault/` | Room: Entity/DAO/DB/Migrations + RoomVaultEntryRepository (مهر/بازکردن payload، فیلتر/جستجو/مرتب‌سازی in-memory، import/replace) |
| `data/security/` | FileVaultSecurityRepository: setup/unlock/lock روی فایل‌ها با KDF+wrap |
| `data/backup/` | BackupFileGateway (SAF، دسته‌بندی خطا) |
| `backup/` | BackupCodec (قالب پاکت)، BackupManager، RestorePlanner، مدل‌ها |
| `settings/` | DezhSettings، SettingsRepository، DataStoreSettingsRepository، ThemeModeController |
| `generator/` | PasswordGenerator (SecureRandom، min-per-class، Fisher–Yates) |
| `presentation/*` | ViewModel/Screen هر صفحه (vault، search، categories، generator، backup، settings، setup، unlock، startup) |
| `navigation/` | DezhDestination (serializable NavKeyها) + DezhApp |
| `ui/theme/`، `ui/` | توکن‌های M3، تم، accentها، SecureScreenPolicy |
| `di/` | AppContainer |

## ۳. مدل امنیتی

- **راز اصلی:** master password کاربر. هیچ‌جا ذخیره نمی‌شود.
- **DEK (کلید داده):** AES-256 تصادفی، فقط در حافظهٔ `VaultSession`؛ با قفل شدن صفر می‌شود.
- **wrap:** DEK با کلید مشتق‌شده از master password (PBKDF2-SHA256, 600k) + salt تصادفی رمز می‌شود (`keywrap`) و کنارش `meta` شامل پارامترهای KDF که خودش با **AndroidKeystore** (کلید غیرخروجی، AES-GCM) مهر می‌شود.
- **Brute-force:** LoginAttemptPolicy با backoff نمایی و **شمارندهٔ ماندگار روی دیسک** (restart هم شمارنده را نگه می‌دارد؛ لاگین موفق reset می‌کند).
- **Auto-lock:** بلافاصله/۱/۵/۱۵/۳۰ دقیقه؛ «هرگز» توسط سیاست ممنوع و در کد fail-closed است. رویدادها: background، screen-off؛ پس از lock، UI به Unlock هدایت و ViewModelهای حاوی plaintext نابود می‌شوند.
- **نشت:** FLAG_SECURE روی همهٔ صفحه‌های حساس؛ بدون notification/log/analytics؛ clipboard فقط با اقدام صریح + پاک‌سازی خودکار.
- **Offline-first:** صفر permission در manifest (حتی INTERNET).

## ۴. طراحی رمزنگاری (crypto design)

| جزء | انتخاب |
|------|--------|
| رمز متقارن | AES-256-GCM (تگ ۱۲۸ بیتی، IV تصادفی ۱۲ بایتی از SecureRandom) |
| KDF | PBKDF2-HMAC-SHA256، ۶۰۰٬۰۰۰ iteration، salt ۱۶ بایتی تصادفی |
| ساختار payload | `EncryptionContainer` (DPVG): version ‖ iv ‖ ciphertext ‖ tag — با شمارهٔ نسخه برای مهاجرت آینده |
| AAD | در backup: `format|version|entryCount|createdAt` |
| Nonce | منحصربه‌فرد در هر encrypt (تست ۱۰۰۰ موردی موجود)؛ GCM با IV تکراری امن نیست، لذا هیچ مسیر deterministic نداریم |
| خطاها | شکست تگ/AAD → exception نوع‌دار (هیچ‌وقت plaintext ناقص برنمی‌گردد) |

## ۵. Schema دیتابیس (نسخهٔ ۱)

```sql
CREATE TABLE vault_entries (
  id TEXT PRIMARY KEY NOT NULL,
  payload BLOB NOT NULL,        -- EncryptionContainer(JSON EntrySecrets) با DEK session
  category TEXT NOT NULL,       -- متادیتای ستونی (عمداً plaintext)
  favorite INTEGER NOT NULL,
  createdAt INTEGER NOT NULL,
  updatedAt INTEGER NOT NULL
);
-- indices: index_vault_entries_favorite, index_vault_entries_category, index_vault_entries_updatedAt
```

- export شده در `app/schemas/…/1.json`؛ MigrationTestHelper + identity hash Room 2.8.
- جستجوی متنی **in-memory** پس از unlock (بدون index پایدار plaintext)؛ فیلتر دسته/علاقه‌مندی در سطح DB روی ستون‌های متادیتا.

## ۶. قالب پشتیبان (backup format)

JSON envelope با `format="dezh-backup"`، `version=1`، هدر KDF/cipher، checksum SHA-256 روی ciphertext، ciphertext (AES-256-GCM روی payload شامل همهٔ رکوردها). مستقل از فرمت SQLite. iterationهای KDF داخل فایل سفر می‌کنند (migration-friendly). نسخه‌های بالاتر از پشتیبانی → خطای متمایز. جزئیات: `docs/PHASE5-REPORT.md`.

## ۷. رفتار بازیابی (restore behavior)

مرحله‌ای: انتخاب فایل (SAF، بدون اعتماد به filename/MIME، سقف ۶۴MB) → اعتبارسنجی هدر **بدون** passphrase → رمزگشایی → پیش‌نمایش تعداد → انتخاب **ادغام** (درج غایب‌ها + به‌روزرسانی فقط اکیداً جدیدترها، بدون حذف) یا **جایگزینی کامل** (تأیید صریح دو مرحله‌ای؛ حذف کل + درج؛ گارد id تکراری) یا انصراف. هر ۹ خطای تعریف‌شده پیام فارسی متمایز دارند.

## ۸. پیکربندی build

| مورد | مقدار |
|------|-------|
| AGP / Gradle | 9.4.0 / 9.6.0 (wrapper) |
| Kotlin / KSP | 2.4.20 / 2.3.12 |
| compileSdk / targetSdk / minSdk | 37 / 37 / **29 (Android 10+)** |
| JDK | 17 (toolchain سندباکس) |
| Release | **R8 minify + shrinkResources فعال (فاز ۷)**، unsigned، `proguard-android-optimize` + قوانین حداقلی (نگهداشت خوانایی stack trace) |
| APK اندازه | release ≈ **3.10MB** / debug ≈ 33.7MB (اندازه‌گیری‌شده) |
| JVM sandbox | `-Xmx576m`، SerialGC، workers=1 (الگوی اجرای مرحله‌ای برای تست‌ها) |

## ۹. وابستگی‌ها (همه stable، بدون alpha/beta)

compose-bom 2026.09.00، material3 (+ window-size-class 1.4.0، icons-core)، navigation3 1.1.7، lifecycle 2.11.0، room 2.8.5 (+ksp)، datastore-preferences 1.2.1، kotlinx-coroutines 1.11.0 (+test)، kotlinx-serialization-json، robolectric 4.17، junit 4.13.2، androidx.test (junit/runner/core)، detekt 1.23.8. **هیچ وابستگی شبکه‌ای/تحلیلی/تبلیغاتی وجود ندارد.**

## ۱۰. استراتژی تست

- **واحد JVM:** crypto (۹)، security files (۱۲+)، چرخهٔ کامل auth (۲)، repo (۱۴ شامل large ۵۰۰۰)، DAO/DB واقعی Robolectric (۸ شامل migration، transaction، restart، large ۲۰۰۰)، backup codec/manager/planner/errors (۳۱)، settings DataStore (۵) + کنترلر تم (۴)، generator (۸)، meter (۶)، validator، auto-lock (۹)، SecureScreenPolicy (۴)، NoSensitiveData (اسکن).
- **UI (Robolectric Compose، locale fa):** لیست/خالی، نوار بالای گاوصندوق، مولّد، تنظیمات (۷).
- **گیت هر فاز:** BUILD → TESTS → UI → RTL/DARK/LIGHT → A11Y → LINT → detekt (+ SECURITY در ۵–۷).
- **اجرای نهایی فاز ۷:** clean build + ۱۶۴/۱۶۴ + lint ۰ + detekt ۰ + assembleRelease با R8.

## ۱۱. تصمیم‌های مهم

1. DI دستی به‌جای Hilt (سادگی/اندازه).
2. plaintext فقط در RAMِ session باز؛ DB فقط payload رمز + متادیتای حداقلی ستونی؛ جستجو در حافظه.
3. Room 2.8.5 (نسخهٔ 3 منتشرنشده)؛ پین‌های stable.
4. DataStore جایگزین SharedPreferences در فاز ۵.
5. «هرگز» برای auto-lock ممنوع (fail-closed در UI و کد).
6. R8 در فاز ۷ فعال شد چون codebase عاری از reflection است (serializerهای تولیدی، consumer rules کتابخانه‌ها).
7. اندروید ۱۵ به بالا در سندباکس قابل تست نیست (Robolectric sdk=35 روی JDK 17)؛ پوشش با minSdk 29 سعی شده از طریق API-guardهای استاندارد M3/DataStore/SAF برقرار باشد.

## ۱۲. محدودیت‌ها

- تست instrumentation روی emulator (APIهای ۱۰ تا ۱۵ به‌صورت دستگاه واقعی) در سندباکس ممکن نیست؛ معادل Robolectric اجرا می‌شود.
- تشخیص «کمبود فضا» از متن IOException است (API قطعی برای SAF ندارد).
- robustness در برابر OEM-specific behaviors (recents screenshot در برخی لانچرها) تضمین فراتر از FLAG_SECURE ندارد؛ عکس با دستگاه دیگر ذاتاً قابل جلوگیری نیست.
- release unsigned است (توزیع نه؛ امضا مستلزم keystore کاربر).
- PBKDF2 ۶۰۰k روی دستگاه‌های خیلی ضعیف ممکن است unlock را تا ~۱ ثانیه کند کند (عمدی؛ تعادل امنیت/سرعت).
