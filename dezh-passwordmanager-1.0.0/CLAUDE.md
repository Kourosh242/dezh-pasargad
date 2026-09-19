# CLAUDE.md — راهنمای رویکرد برای دستیارهای کدنویس (Claude Code / Claude و ابزارهای مشابه)

این فایل نقطهٔ ورود هر بازبین یا ایجنت هوشمند است. پیش از هر تغییر، این سند و
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) را بخوانید.

## ۱. شناسنامهٔ پروژه

- **اپ:** دژ پاسارگاد (Dezh-e Pasargad) — گاوصندوق رمز آفلاینِ فارسی‌محور برای اندروید
- **نسخه:** `1.0.0` (versionCode 1) — تغییر نسخه فقط با دستور صریح مالک پروژه
- **پشته:** Kotlin 2.4.20 · AGP 9.4.0 · Gradle 9.6.0 · JDK 17 · Compose BOM 2026.09.00 (Material 3) · Navigation 3 1.1.7 · Room 2.8.5 (KSP) · DataStore · Robolectric 4.17 · detekt 1.23.8
- **SDK:** compile/target 37 (نصب با شناسهٔ `platforms;android-37.0` — «نقطه‌دار»!) · minSdk 29 · build-tools 36.0.0
- **زبان UI:** فارسی (RTL-first) + انگلیسی (`values-en`)

## ۲. راه‌اندازی و کامندها

```bash
scripts/setup-dev-env.sh          # JDK17 + cmdline-tools + SDK (یک‌بار)؛ env.sh می‌سازد
source env.sh                      # از مسیری که اسکریپت گفت
echo "sdk.dir=/path/to/sdk" > local.properties   # commit نمی‌شود (.gitignore)
./gradlew :app:testDebugUnitTest   # ~209 تست / 43 کلاس — دروازهٔ اصلی
./gradlew :app:detekt              # باید دقیقاً ۰ یافته بماند
./gradlew :app:lintDebug           # باید سبز بماند
./gradlew :app:assembleRelease     # خروجی unsigned → امضا بیرون از بیلد
```

**امضای Release:** فایل keystore عمداً **خارج از این ریپو** است و باید همین‌طور بماند.
امضا با `apksigner` (build-tools 36.0.0) انجام می‌شود؛ هرگز keystore/رمز را به ریپو،
build.gradle یا لاگ اضافه نکنید. SHA-256 گواهی نسخه‌های منتشرشده: `cbb7b139…e898ce6b`.

**تله‌های محیطی شناخته‌شده:** حافظهٔ ~2GB → در fail/OOM: `./gradlew --stop`، `pkill -9 java`،
دسته‌های کوچک تست، و `_JAVA_OPTIONS=-Djava.io.tmpdir=<مسیر دیسک>` (tmpfs سقف‌دار باعث
خروج بی‌صدای gradle می‌شود). خطاهای موقت شبکه هنگام دانلود gradle/SDK طبیعی است؛ تلاش مجدد.

## ۳. معماری (تک‌ماژول `:app`)

```
navigation/   DezhApp (NavDisplay + FLAG_SECURE policy) · DezhDestination (Serializable NavKey)
presentation/ هر صفحه = Screen Composable + ViewModel (StateFlow) — بدون XML/Fragment/ViewBinding
domain/       UseCaseها + مدل‌ها + PasswordStrengthMeter/Validator + util/JalaliDate + update/UpdateChecker
data/         Room (VaultEntryDao/Entity/Repository + Migrations) · DataStore settings · backup gateway (SAF)
security/     VaultSession (کلید DEK فقط در RAM) · FileVaultSecurityRepository · KeystoreGateway · AutoLock · LoginAttemptPolicy
cryptography/ AesGcmCipher · KeyWrapper (PBKDF2→AES-GCM wrap) · EncryptionContainer/KeyWrapContainer (فرمت‌های نسخه‌دار DPVG/DPKW)
backup/       BackupCodec (envelope رمز + AAD + checksum) · RestorePlanner · BackupManager
generator/    PasswordGenerator (SecureRandom + Fisher–Yates)
settings/     DezhSettings + DataStoreSettingsRepository + ThemeModeController
di/           AppContainer — DI دستی؛ Hilt ممنوع مگر دلیل واقعی
ui/           theme (DezhMotion/برند طلایی) + components (DezhBottomNav، EmptyState، StrengthRing، Visuals)
```

## ۴. قواعد امنیتی تغییرناپذیر (هر PR/تغییری باید آن‌ها را حفظ کند)

1. **fail-closed:** هر شکست → قفل/خطا. «هرگز» برای auto-lock وجود ندارد.
2. **plaintext فقط در حافظهٔ session:** payload دیتابیس رمز است؛ ایندکس جستجوی plaintext پایدار ممنوع؛ کلیدها هنگام قفل صفر می‌شوند.
3. **SharedPreferences ممنوع** (فقط DataStore). ورودی/خروجی فایل فقط SAF؛ restore هرگز silently overwrite نمی‌کند.
4. **مجوزها:** فقط `INTERNET` — منحصراً برای «بررسی بروزرسانی» (GitHub Releases API). بقیهٔ اپ کاملاً آفلاین است؛ مجوز جدید ممنوع.
5. **هیچ secret از navigation args رد نمی‌شود** (فقط idهای UUID).
6. **business logic / crypto / DB در Composable ممنوع** — همه از ViewModel/UseCase.
7. MainActivity کوچک می‌ماند؛ multi-module اضافه ممنوع.

## ۵. کنوانسیون‌ها و گیت‌های کیفیت

- **زبان:** مستندات/گزارش‌ها فارسی؛ کد، identifier، Gradle و پیام commit انگلیسی.
- **detekt با پیکربندی سخت‌گیرانه** (`config/detekt/detekt.yml`): CyclomaticComplexMethod ≤ 15، LongMethod ≤ 60، MagicNumber و MatchingDeclarationName و… — هر تغییر باید با **صفر یافته** تمام شود.
- **فرهنگ TDD:** هر باگ اول با تست قطعی **قرمز** اثبات می‌شود، بعد فیکس، بعد سبز. «تست‌نشده ≠ PASS».
- الگوی تست قطعی زمان‌محور: `suspendDelay` تزریقی + گیت‌ها (نمونه: `selfaudit/GeneratorClipboardAutoClearTest` و `EntryDetailClipboardAutoClearTest`).
- Robolectric: `@Config(sdk=[35], qualifiers="fa-rIR-w411dp-h891dp")` + `@GraphicsMode(NATIVE)`.
- گیت هر دور: `testDebugUnitTest (تمام کلاس‌ها) + detekt + lintDebug + assembleRelease + verify امضا`.

## ۶. نقشهٔ مستندات (`docs/`)

- معماری جامع: `ARCHITECTURE.md` · طراحی رمزنگاری: `PHASE2-SECURITY-DESIGN.md`
- طراحی UI/UX: `UI-UX-DESIGN.md` + گزارش‌های دورهای UI (UI-REDESIGN / COMPETITIVE-UI-PASS / BOTTOMNAV-UI-PASS / CRASHFIX-NAV-POLISH / ALIVE-UI-PASS)
- فازهای ساخت: `PHASE1…7` · انتشار اولیه: `FINAL-RELEASE-REPORT.md`
- خودبازرسی‌ها: `SELF-AUDIT-REPORT.md` · `SELF-AUDIT-2-REPORT.md` · `FULL-AUDIT-REPORT.md`
- تغییرات نسخه‌ها: `../CHANGELOG.md`

## ۷. قابلیت بررسی بروزرسانی (نکته برای بازبین‌ها)

اپ در «تنظیمات امنیتی ← بررسی بروزرسانی» یک GET زنده به
`api.github.com/repos/Kourosh242/dezh-pasargad/releases/latest` می‌زند، `tag_name` را
با versionName مقایسهٔ عددی می‌کند (کلاس خالص `SemanticVersion`) و در صورت نسخهٔ جدیدتر،
صفحهٔ همان Release را در مرورگر باز می‌کند (دانلود APK توسط کاربر). بدون کش، بدون
dependency شبکه‌ای جدید (HttpURLConnection + org.json)، بدون ارسال هیچ داده‌ای.
EOF
