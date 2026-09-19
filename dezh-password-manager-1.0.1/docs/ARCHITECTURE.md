# معماری فنی — دژ پاسارگاد

**نسخهٔ مستند:** 1.0.1  
**هدف:** شرح لایه‌ها، مدل امنیتی، قالب داده، پیکربندی build و استراتژی تست برای توسعه‌دهندگان.

---

## ۱. نمای کلی

تک‌ماژول (`:app`)، تک‌اکتیویتی، Compose-first و آفلاین‌محور:

```
UI (Compose, Material 3, RTL)
   │  events ↑ / state ↓ (StateFlow)
ViewModels (presentation/*)
   │
Use Cases (domain/*)                 ← قواعد کسب‌وکار، بدون وابستگی Android
   │
Repositories (data/*, settings)
   │
Room (metadata ستونی + payload رمز) │ Files (security/backup) │ DataStore (settings)
```

- **DI دستی** با `AppContainer` (بدون Hilt) برای سادگی و اندازهٔ کوچک APK.
- **قفل = نشست:** `VaultLockState` مرجع ناوبری است؛ با Navigation 3 back stack از روی وضعیت قفل بازسازی می‌شود و کاربر قفل‌شده هرگز به صفحات گاوصندوق دسترسی ندارد.
- **هیچ رازی در navigation arguments** عبور نمی‌کند (فقط شناسه‌های غیرحساس).

## ۲. پکیج‌ها

| پکیج | مسئولیت |
|------|---------|
| `cryptography/` | اولیه‌های رمزنگاری JVM: AES-256-GCM، PBKDF2، KeyWrapper، EncryptionContainer |
| `security/` | VaultSession (دارندهٔ DEK در RAM)، AutoLock، سیاست تلاش ورود، Keystore |
| `domain/` | مدل‌ها، UseCaseها، سنجه/اعتبارسنج قدرت رمز، تقویم جلالی، سیاست بروزرسانی |
| `data/vault/` | Room Entity/DAO/DB + مخزن رکوردها (رمزگشایی در حافظه پس از unlock) |
| `data/security/` | setup/unlock/lock روی فایل‌های keywrap و meta |
| `data/backup/` | درگاه SAF برای پشتیبان |
| `data/update/` | دریافت Release گیت‌هاب، دانلود APK، اعتبارسنجی و نصب |
| `backup/` | کدک پشتیبان، مدیر پشتیبان، برنامه‌ریز بازیابی |
| `settings/` | DataStore تنظیمات و کنترل تم |
| `generator/` | تولید رمز با SecureRandom |
| `presentation/*` | Screen و ViewModel هر بخش |
| `navigation/` | مقصدها و ریشهٔ ناوبری |
| `ui/` | تم برند، کامپوننت‌ها، سیاست صفحهٔ امن |
| `di/` | AppContainer |

## ۳. مدل امنیتی

- **رمز اصلی:** هرگز ذخیره نمی‌شود؛ فقط برای KDF در حافظهٔ کوتاه‌عمر (`char[]`) استفاده و سپس پاک می‌شود.
- **DEK:** کلید دادهٔ AES-256 تصادفی؛ فقط در `VaultSession` (RAM)؛ با قفل‌شدن صفر می‌شود (fail-closed).
- **Wrap:** DEK با کلید مشتق‌شده از رمز اصلی (PBKDF2-HMAC-SHA256، ۶۰۰٬۰۰۰ تکرار، salt تصادفی ۱۶ بایتی) پیچیده می‌شود؛ پارامترهای KDF و شمارندهٔ تلاش در meta رمزنگاری‌شده با کلید **غیرخروجی Android Keystore** نگه داشته می‌شوند.
- **Brute-force:** تأخیر نمایی + شمارندهٔ ماندگار روی دیسک.
- **قفل خودکار:** بلافاصله / ۱ / ۵ / ۱۵ / ۳۰ دقیقه — بدون گزینهٔ «هرگز».
- **نشت UI:** `FLAG_SECURE` روی صفحات حساس (قابل خاموش‌کردن توسط کاربر)، پاک‌سازی خودکار کلیپ‌بورد، بدون لاگ/آنالیتیکس/تبلیغ.
- **شبکه:** فقط برای «بررسی بروزرسانی» اختیاری از GitHub Releases؛ بدون ابر و بدون همگام‌سازی.

## ۴. رمزنگاری

| جزء | انتخاب |
|-----|--------|
| رمز متقارن | AES-256-GCM (تگ ۱۲۸ بیت، IV تصادفی ۱۲ بایتی از SecureRandom) |
| KDF | PBKDF2-HMAC-SHA256، ۶۰۰٬۰۰۰ iteration |
| payload رکورد | ظرف نسخه‌دار (version ‖ iv ‖ ciphertext ‖ tag) |
| AAD پشتیبان | `format\|version\|entryCount\|createdAt` |
| خطا | شکست تگ/AAD → exception نوع‌دار؛ plaintext ناقص برنمی‌گردد |

## ۵. Schema دیتابیس (نسخهٔ ۱)

```sql
CREATE TABLE vault_entries (
  id TEXT PRIMARY KEY NOT NULL,
  payload BLOB NOT NULL,        -- ظرف رمزنگاری‌شدهٔ اسرار رکورد
  category TEXT NOT NULL,       -- متادیتای ستونی (عمداً plaintext)
  favorite INTEGER NOT NULL,
  createdAt INTEGER NOT NULL,
  updatedAt INTEGER NOT NULL
);
```

- Schema در `app/schemas/…/1.json` نسخه‌بندی و commit شده است.
- جستجوی متنی **در حافظه** پس از unlock؛ فیلتر دسته/علاقه‌مندی روی ستون‌های متادیتا در DB.

## ۶. قالب پشتیبان

پاکت JSON با `format="dezh-backup"`، `version=1`، هدر KDF/cipher، checksum SHA-256 روی ciphertext و بدنهٔ AES-256-GCM. مستقل از SQLite؛ iterationهای KDF داخل فایل سفر می‌کنند. سقف حجم ورودی ۶۴MB؛ تشخیص جداگانهٔ «رمز اشتباه» از «فایل خراب».

## ۷. بازیابی

انتخاب فایل (SAF) → اعتبارسنجی هدر → رمزگشایی → پیش‌نمایش → **ادغام** یا **جایگزینی کامل** (با تأیید صریح) یا انصراف. هیچ overwrite خاموشی وجود ندارد.

## ۸. پیکربندی build

| مورد | مقدار |
|------|-------|
| minSdk / targetSdk / compileSdk | 29 / 37 / 37 (اندروید ۱۰ تا ۱۶ و جدیدتر) |
| JDK | 17 |
| Kotlin / Compose | طبق `gradle/libs.versions.toml` |
| Release | R8 minify + shrinkResources |
| ABI | یونیورسال — بدون کد native؛ سازگار با arm64-v8a، armeabi-v7a، x86_64، x86 |
| applicationId | `com.pasargad.dezh` |
| versionName / versionCode | 1.0.1 / 2 |

خروجی release به‌صورت unsigned از Gradle ساخته می‌شود؛ امضا با keystore شخصی **خارج از مخزن** انجام می‌گیرد.

## ۹. وابستگی‌های اصلی

AndroidX Compose (Material 3)، Navigation 3، Lifecycle، Room، DataStore Preferences، Kotlin Coroutines، Kotlinx Serialization. **بدون** SDK تبلیغات، analytics، crash-reporter ابری یا همگام‌سازی ابری.

## ۱۰. تست

- تست واحد JVM برای crypto، security، vault، backup، generator، settings، update.
- تست UI با Robolectric Compose (locale فارسی).
- تست دود end-to-end روی پشتهٔ واقعی رمزنگاری.
- گیت‌های کیفیت: `detekt` و `lintDebug`.

## ۱۱. تصمیم‌های مهم

1. DI دستی به‌جای Hilt (سادگی و اندازه).
2. فقط payload رمز در DB؛ جستجو در RAM پس از unlock.
3. ممنوعیت auto-lock «هرگز» در UI و کد.
4. R8 فعال چون codebase عاری از reflection زمان‌اجرا است.
5. نصب بروزرسانی فقط با `PackageInstaller` رسمی اندروید + اعتبارسنجی package / versionCode / امضا.

## ۱۲. محدودیت‌ها

- PBKDF2 با ۶۰۰k iteration روی دستگاه‌های خیلی ضعیف ممکن است unlock را تا حدود یک ثانیه طول بدهد (عمدی).
- رفتار recents/screenshot روی برخی OEMها فراتر از `FLAG_SECURE` تضمین کامل ندارد.
- رمز اصلی و عبارت عبور پشتیبان قابل بازیابی نیستند؛ مسئولیت نگه‌داری با کاربر است.
