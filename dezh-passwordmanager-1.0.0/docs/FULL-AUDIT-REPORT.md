# ممیزی کامل سورس (دور ۳) — دژ پاسارگاد 1.0.0

**دامنه:** کل سورس main (لایه‌به‌لایه، خواندن خط‌به‌خط) + اجرای مجدد تمام گیت‌ها.

## ۱. باگ یافت‌شده و رفع‌شده (TDD: قرمز → سبز)
**کلاپ‌بورد صفحهٔ جزئیات — تایمر پاک‌سازی با کپی دوباره ریست نمی‌شد** (همان کلاس باگ دور ۲ در مولّد):
- سناریو: کپی در t=0 → تایمر ۶۰s؛ کپی دوباره در t=30 → تایمر قدیمی باقی می‌ماند → در t=60 مقدارِ تازه (۳۰s عمر) پاک می‌شد؛ پنجرهٔ کامل خودش رعایت نمی‌شد.
- اثبات: `EntryDetailClipboardAutoClearTest` با گیت‌های delay قطعی — روی کد قدیمی FAILED (گیتِ کهنه، کلیپ‌بوردِ مقدارِ تازه را پاک کرد)، بعد فیکس، سبز.
- فیکس: شمارندهٔ یکنواخت `passwordCopyGeneration` به‌عنوان کلید LaunchedEffect — هر کپی، تایمر کهنه را لغو و پنجرهٔ کامل تازه می‌سازد؛ `suspendDelay` تزریقی مثل مولّد؛ پارامتر پیش‌فرض = delay واقعی (تماس‌های موجود دست‌نخورده).

## ۲. نواحی بازبینی‌شده و تأییدشده (بدون یافته)
| لایه | فایل‌ها | نتیجه |
|---|---|---|
| رمزنگاری | AesGcmCipher (nonce تصادفی per-encrypt، tag 128، خرابی همه = AuthenticationFailed)، KeyWrapper (DEK تصادفی، صفرکردن KEK در finally، salt تازه)، Pbkdf2 (600k OWASP، clearPassword)، EncryptionContainer/KeyWrapContainer (کامل چک‌شده: magic/version/bounds/بازهٔ iterations) | ✓ |
| امنیت | VaultSession (قفل = صفرکردن کلید، fail-closed)، FileVaultSecurityRepository (setup دست‌نخورده→wipeAll، tamper-check پارامترهای wrap با meta محافظت‌شدهٔ Keystore، backoff)، SecurityMetaCodec، AndroidKeystoreGateway (non-exportable، IV تصادفی، خرابی→MetaCorrupted) | ✓ |
| بکاپ | BackupCodec (AAD-bound header، checksum، version gate، wrong-pass جدا از corruption)، RestorePlanner (merge صرفاً جدیدتر؛ replace با گارد id تکراری)، SafBackupFileGateway (سقف 64MB، classبندی خطا، بدون اعتماد به نام فایل)؛ خروجی با CREATE سند (بدون overwrite خاموش) | ✓ |
| داده | RoomVaultEntryRepository (+محافظت containment نوبت قبل، fail-closed حفظ)، DataStore (clamp همهٔ مقادیر، enum سخت‌گیر) | ✓ |
| دامنه | PasswordGenerator (Fisher–Yates با SecureRandom، امکان‌سنجی minها، گارد pool خالی)، PasswordStrengthMeter/Validator (باندهای 28/50 هم‌خوان با UI)، JalaliDate (golden) | ✓ |
| presentation | همهٔ ViewModelها (StateFlow، بدون منطق در Composable)، UpdateViewModel (گارد دوباره‌فشردن)، Generator (wipe دور ۲ سالم) | ✓ |
| اپ | DezhApp (بازسازی backstack از lockState؛ FLAG_SECURE؛ بدون secret در navigation)، DezhApplication (breadcrumb) | ✓ |
| بهداشت کد | بدون `!!`، بدون GlobalScope، بدون runBlocking در main، بدون TODO/FIXME | ✓ |

نکتهٔ جزئی (غیرباگ، بدون تغییر برای حداقل‌سازی diff): فیلد `backgroundedAtMs` در AutoLockController فقط نوشته می‌شود؛ خوانده نمی‌شود.

## ۳. گیت‌های نهایی (همه از صفر اجرا شد)
- **تست: 209/209 سبز** در یک اجرای کامل (۴۳ کلاس) — شامل تست اثباتی جدید
- detekt: **۰** • lint: **سبز** • compile سبز
- assembleRelease + R8 ✓ → امضای همان keystore (SHA-256 `cbb7b139…`) → apksigner verify ✓ → **1.0.0**
