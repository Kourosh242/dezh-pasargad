# گزارش دور «نصب درون‌اپی APK با PackageInstaller رسمی»

وضعیت گیت: **سبز کامل** — تست ۲۵۹/۲۵۹ (۴۹ کلاس)، detekt ۰، lint سبز، APK امضاشده با همان کلید.

---

## ۱) خلاصه

پیش از این دور، «بررسی بروزرسانی» فقط تگ جدید را از GitHub Releases می‌خواند و کاربر باید دستی به مرورگر می‌رفت و APK را دانلود و نصب می‌کرد. در این دور مسیر رسمی اندروید برای نصب درون‌اپی پیاده شد:

**بررسی نسخه ← دانلود APK درون اپ ← نمایش اطلاعات APK ← (حداکثر یک‌بار) گیت اجازهٔ «نصب برنامه‌های ناشناس» ← Session رسمی `PackageInstaller` ← پنجرهٔ تأیید خود سیستم ← اعلام نتیجه**

ممنوعیت‌های رعایت‌شده: هیچ تماسی با Google Play / `market://` وجود ندارد؛ هیچ bypass امنیتی (Play Protect یا غیره) نوشته نشده؛ هیچ silent install وجود ندارد — پنجرهٔ تأیید رسمی سیستم تنها دروازهٔ نصب است.

## ۲) تصمیم‌های طراحی

| موضوع | تصمیم | دلیل |
|---|---|---|
| API نصب | `PackageInstaller.Session` با `MODE_FULL_INSTALL` | API رسمی؛ minSdk 29 پس هیچ مسیر deprecated لازم نشد |
| FileProvider | **لازم نشد** | `session.openWrite()` مستقیم `InputStream` می‌گیرد و APK از cache خصوصی اپ استریم می‌شود؛ هرگز `file://` به هیچ اکسترانتی داده نمی‌شود |
| اندازهٔ session | multi-write آماده | `install(apkFiles: List<File>)` — هر آرشیو یک entry از همان session؛ آیندهٔ split APK بدون تغییر شکل API |
| منبع APK | فقط `assets[].browser_download_url` از release JSON خودِ پروژه + allowlist هاست (github.com / githubusercontent.com) فقط HTTPS | جلوگیری از URL جعلی/redirect |
| checksum | `sha256:` در صورت انتشار کنار release (فیلد `digest`) — اختیاری | GitHub Releases API هش پیش‌فرض نمی‌دهد؛ سقف حجم ۲۵۶MB + کانال GCM-شده + بررسی امضا پوشش می‌دهند |
| اعتبارسنجی پیش از نصب | `PackageManager.getPackageArchiveInfo` + `SigningInfo` (API 28+) | بررسی package name / versionCode / SHA-256 کلید امضا |
| گیت اجازه | `canRequestPackageInstalls()` + `ACTION_MANAGE_UNKNOWN_APP_SOURCES` با `package:com.pasargad.dezh` | دقیقاً صفحهٔ تنظیمات همین اپ؛ پس از برگشت re-check؛ فقط یک دور |
| نتیجهٔ session | receiver هدفمند + `PendingIntent` با `FLAG_MUTABLE` | اکشن رسمی `com.android.packageinstaller.ACTION_INSTALL_COMPLETE`؛ SUCCESS / PENDING_USER_ACTION / FAILURE_ABORTED / FAILURE_BLOCKED / FAILURE همه نقشه‌برداری شده‌اند |
| Play Protect | بدون هیچ مداخله؛ `FAILURE_BLOCKED` فقط «اعلام» می‌شود | bypass ممنوع (تأکید صریح کاربر) |

## ۳) فایل‌های تغییرکرده / جدید

### جدید — دامنه
- `domain/update/ApkUpdateModels.kt` — `ReleaseApkAsset`، `ApkArchiveInfo`، `CurrentAppInfo`، `ApkInspection`، `Verdict` و شیء خالص `SelfUpdatePolicy` (تنها APKِ «همین package، سختگیرانه جدیدتر، با همان کلید» مجاز است).
- `domain/update/ApkAssetFetcher` (در همان فایل) — کانالِ همراهِ asset.

### جدید — داده
- `data/update/ApkUpdateGateways.kt` — اینترفیس‌های `ApkDownloadGateway`، `ApkVerificationGateway`، `ApkInstallGateway` و `InstallOutcome` (تست‌پذیری با fake، مطابق سبک SettingsRepository).
- `data/update/HttpApkDownloader.kt` — دانلود استریمی به `cacheDir/updates` (مسیر هرگز از ورودی نمی‌آید)، allowlist هاست، سقف حجم هم در هدر و هم در جریان خواندن (ضد path traversal/فایل جعلی)، پاک‌سازی فایل ناقص در هر مسیر خطا، تایم‌اوت، یک فایل همزمان.
- `data/update/PackageApkVerifier.kt` — خواندن آرشیو با `PackageManagerArchiveReader` و اجرای `SelfUpdatePolicy` روی اطلاعات اپ در حال اجرا.
- `data/update/PackageApkInstaller.kt` — gateway رسمی PackageInstaller: ساخت session، استریم هر فایل + `fsync`، `commit` با PendingIntent وضعیت، receiver هدفمند؛ تابع خالص `parseInstallOutcome` برای نقشهٔ status ها.
- `data/update/UpdateDownloadGuard.kt` — گاردهای خالص: allowlist URL، سانیتایز نام فایل (ASCII)، مقایسهٔ digest.

### جدید — presentation
- `presentation/update/UpdateInstallStepSection.kt` — رندر همهٔ گام‌های نصب: نوار پیشرفت دانلود (درصد زنده)، «بررسی فایل»، کارت «اطلاعات فایل نصبی» (نام/حجم/versionCode/بسته + جملهٔ تأیید)، کارت گیت اجازه، انتظار از پنجرهٔ رسمی، و همهٔ نتایج (موفق/لغو/مسدود/ناموفق/رد سیاست).
- `presentation/update/UpdateFormatters.kt` — `formatBytes`.

### تغییر
- `presentation/update/UpdateViewModel.kt` — ماشین حالت کامل خط لوله: `check` حالا asset را هم می‌آورد؛ `downloadUpdate` (با درصد زنده) → `inspect` → `ReadyToInstall` → `proceedToInstall` (شمارش دقیقاً یک دور Settings) → `onHostResumed` (ادامهٔ خودکار پس از اجازه / توقف قاطع پس از رد) → `beginInstall` → `onInstallOutcome`. `reopenInstallSettings` فقط intent را دوباره می‌فرستد و دورِ تنظیمات را مصرف نمی‌کند. `InstallAborted` خلاصهٔ APK را نگه می‌دارد تا بدون دانلود دوباره بتوان تلاش کرد.
- `presentation/update/UpdateScreen.kt` — حذف مسیر مرورگر از CTA اصلی؛ اتصال side-effect ها (بازکردن تنظیمات/پنجرهٔ تأیید با `FLAG_ACTIVITY_NEW_TASK`)؛ observer چرخهٔ عمر برای `onHostResumed`؛ لینک ثانویهٔ «مشاهدهٔ صفحهٔ انتشار» (فقط GitHub، هرگز Play).
- `domain/update/UpdateChecker.kt` — پارامتر اختیاری `assetFetcher` (additive؛ رفتار قبلی حفظ شد).
- `data/update/GithubReleaseFetcher.kt` — پیاده‌سازی `ApkAssetFetcher` + `parseApkAsset` (ترجیح `dezh-pasargad-release.apk`، فال‌بک اولین `.apk`، parse اختیاری `sha256:`).
- `di/AppContainer.kt` — سیم‌کشی downloader/verifier/installer + اتصال asset fetcher به چکر.
- `navigation/DezhApp.kt` — ورود UpdateCheck با سازندهٔ جدید.
- `AndroidManifest.xml` — `<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />` (فقط همین؛ هیچ مجوز یا trick دیگری).
- `res/values/strings.xml` + `res/values-en/strings.xml` — ۲۹ رشتهٔ جدید (فارسی + انگلیسی).

## ۴) سناریوهای تست (a–f) — همهٔ پوشش‌ها سبز

| # | سناریو | تست | نتیجه |
|---|---|---|---|
| a | نصب جدید (download→info→session→pending→success) | `UpdateViewModelInstallTest` + `UpdateScreenUiTest` | ✓ فایلِ همان APK به session داده شد؛ side-effect intent رسمی |
| b | آپدیت روی نسخهٔ نصب‌شده (policy) | `SelfUpdatePolicyTest` (۸ حالت) + ردِ WrongPackage/NotNewer/Signature در VM | ✓ هر mismatch قبل از installer رد و فایل پاک می‌شود |
| c | APK نامعتبر | خوانده‌نشدن آرشیو → `Unreadable`؛ خطای دانلود/digest → `InstallFailed` با پیام | ✓ |
| d | مدیریت permission | یک دور Settings + ادامهٔ خودکار پس از اجازه؛ رد → توقف قاطع بدون دور دوم | ✓ |
| e | بدون فراخوانی Play | گارد allowlist (۱۱ حالت) + «هر intent هدف system با data «package:com.pasargad.dezh»»، نه market/play/VIEW | ✓ |
| f | عدم مقابله با Play Protect | `InstallBlocked` فقط اعلام می‌شود؛ تلاش مجدد پنهان انجام نمی‌شود | ✓ |

به‌علاوه: `ReleaseAssetParsingTest` (۷)، `UpdateDownloadGuardTest` (۸)، `InstallOutcomeMappingTest` (۷)، `UpdateCheckerAssetTest` (۵)، تست درصد زندهٔ دانلود، تست retry-بدون-دانلودِ مجدد پس از abort، و تست رشتهٔ فارسی کارت اطلاعات در Robolectric.

## ۵) گیت‌های نهایی

- تست: **۲۵۹/۲۵۹** (۴۹ کلاس، یک اجرا، `workers.max=2`)
- detekt: **۰** issue
- lint: **۰** error (۲۹ ترجمهٔ انگلیسی جدید اضافه شد)
- APK: `dezh-pasargad-release.apk` — **۳,۹۸۸,۸۱۹ B**، versionName **1.0.0**، versionCode **1**، minSdk 29 / target 37
- cert SHA-256: `cbb7b139d024000bc53ff900bc5d46ebcd017b594ab4271e975ebc21e898ce6b` (همان keystore شخصی)
- مجوزها: `INTERNET` + `REQUEST_INSTALL_PACKAGES` (و permission خودکار androidx برای receiver غیرصادره)

## ۶) یادداشت انتشار (برای فعال‌شدن مسیر جدید)

Release بعدی باید: versionCode **بالاتر** (مثلاً 2 در versionName 1.0.1)، تگ بالاتر، و در assets فایل `dezh-pasargad-release.apk` با **همان keystore**. اگر خواستید checksum منتشر کنید، فیلد `digest` asset را به شکل `sha256:<hex>` بگذارید — اپ آن را راستی‌آزمایی می‌کند. کاربران 1.0.0 با «بررسی بروزرسانی → دانلود و نصب در همین اپ» مستقیم به‌روز می‌شوند؛ اندروید یک‌بار اجازهٔ «نصب برنامه‌های ناشناس» را برای همین اپ می‌پرسد و سپس پنجرهٔ رسمی تأیید نصب نمایش داده می‌شود.

## ۷) محدودیت‌های شفاف

- هیچ ادعای امنیتی مطلقی وجود ندارد: play Protect همچنان حق مسدودکردن هر sideload را دارد (این عمدی است و دور زده نشد).
- پس از SUCCESS، سیستم معمولاً process اپ را برای جایگزینی می‌کشد؛ پیام «نصب با موفقیت انجام شد» در صورت زنده‌ماندن process نمایش داده می‌شود.
- split APK هم‌اکنون در شکل API پشتیبانی می‌شود (multi-write در یک session) اما کانال انتشار فعلی تک‌APK است.
