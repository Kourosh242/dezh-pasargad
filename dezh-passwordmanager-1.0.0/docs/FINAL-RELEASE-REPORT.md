# گزارش نهایی انتشار — دژ پاسارگاد (Release 1.0.0)

**تاریخ:** ۱۶ سپتامبر ۲۰۲۶
**شرط ورود:** فاز ۷ پایدار (۱۶۴/۱۶۴) — تأیید شد
**خروجی نهایی:** `dezh-pasargad-release.apk` (امضای شخصی، R8، ۳.۷۰MB — پس از بازطراحی UI/UX با فونت Vazirmatn؛ گزارش جداگانه: `docs/UIUX-OVERHAUL-REPORT.md`)

---

## ۱. چه چیزهایی پیاده‌سازی شد

**در طول ۷ فاز:** معماری تک‌ماژول MVVM با Navigation 3 و Material 3؛ گاوصندوق Room با payload رمزنگاری‌شدهٔ هر رکورد (AES-256-GCM با DEK نشست)؛ جستجو/فیلتر/مرتب‌سازی in-memory پس از unlock؛ مولّد رمز SecureRandom با حداقل هر دسته؛ سنجشگر قدرت تقریبی؛ **پشتیبان رمزگذاری‌شدهٔ versioned** (PBKDF2 600k + AES-GCM + AAD) با بازیابی امن مرحله‌ای (ادغام/جایگزینی/انصراف با تأیید صریح)؛ تنظیمات کامل روی DataStore (تم، accent، font scale، صفحهٔ شروع، دستهٔ پیش‌فرض، timeoutهای کلیپ‌بورد و قفل خودکار با ممنوعیت «هرگز»، پیش‌فرض‌های مولّد، تأییدیه‌ها، پویانمایی، بازنشانی)؛ auto-lock روی background/screen-off/restart؛ FLAG_SECURE روی همهٔ صفحه‌های حساس؛ پاک‌سازی خودکار کلیپ‌بورد؛ RTL کامل فارسی؛ صفر permission؛ مستندات کامل (`docs/ARCHITECTURE.md` + ۸ گزارش فاز).

**در همین فاز (نهایی‌سازی):**
- Clean build کامل + rebuild بدون cache (`--rerun-tasks`) با R8.
- **versionName → 1.0.0** (قبلاً `0.2.0-phase2` بود).
- **امضای APK** با keystore QA خارج از مخزن (`/home/user/keys/dezh-qa.keystore`، رمز `dezh-qa-1404` — فقط برای تست؛ پیش از توزیع عمومی با keystore خودتان جایگزین کنید) و verify با `apksigner` — معتبر.
- **سخت‌سازی R8 جدید:** حذف کامل فراخوانی‌های `android.util.Log` از dex نهایی (قانون `assumenosideeffects`) — اسکن dex پس از build: **صفر ارجاع Log** (قبلاً ۴۱ ارجاع کتابخانه‌ای مثل profileinstaller بود).
- **Smoke test نهایی end-to-end** (`FinalReleaseSmokeTest`): کل سفر کاربر در یک زنجیرهٔ واقعی با رمزنگاری واقعی — setup → رمز غلط (رد + backoff) → unlock → ساخت/خواندن/ویرایش/حذف/علاقه‌مندی/دسته → جستجو/فیلتر/مرتب‌سازی → محدودیت‌های مولّد (۲۰ نمونه + ردّ پیکربندی نامعتبر) → export پشتیبان (بدون plaintext در فایل) → ردّ عبارت عبور غلط → ردّ پشتیبان خراب (INTEGRITY_FAILURE) → جایگزینی کامل → **ماندگاری پس از restart واقعی** (session تازهٔ قفل + بازگشایی با رمز + سالم بودن همهٔ رکوردها) → fail-closed نشست قفل. سبز.
- هر ۹ سناریوی چک‌لیستِ کاربر که روی JVM قابل اجراست روی همین زنجیره + ۱۶۵ تست موجود پوشش داده شد.

## ۲. Build status — PASS

| گام | نتیجه |
|-----|--------|
| `clean` | EXIT=0 |
| `assembleRelease` (R8 minify + shrinkResources، بدون cache) | EXIT=0، `minifyReleaseWithR8` اجرا شد |
| خطای build حل‌نشده | **صفر** |
| اندازهٔ نهایی | **۳.۵۰MB** (افزایش ~۰.۴MB به‌دلیل آیکون اختصاصی در همهٔ تراکم‌ها) |
| versionName / versionCode / package | 1.0.0 / 1 / `com.pasargad.dezh` |

## ۳. Test status — PASS (با دو مورد NOT-TESTED صادقانه)

- **۱۶۵/۱۶۵ تست** سبز (۱۶۴ فاز ۷ + ۱ smoke test جامع جدید؛ خود smoke شامل ده‌ها assert است).
- Lint: ۰ خطا · detekt: ۰ یافته.
- ⚠️ **NOT TESTED (نمی‌توان در سندباکس انجام داد):** (۱) نصب واقعی APK و launch روی دستگاه/شبیه‌ساز — هیچ adb device متصلی وجود ندارد (بررسی شد: `adb devices` خالی)؛ (۲) رفتار runtime R8 روی سخت‌افزار واقعی. به‌جای آن، verify ساختاری کامل انجام شد (بخش security) و معادل عملکردیِ تمام مسیرها در JVM با crypto واقعی (همان کلاس‌هایی که در APK هستند) تست شد.

## ۴. وضعیت Security audit — PASS

| کنترل | روش بررسی | نتیجه |
|--------|-----------|--------|
| رمزنگاری at-rest | payload رمز per-record + تست‌های byte-scan | PASS |
| master password ذخیره نمی‌شود | فقط wrap/KDF روی فایل؛ تست non-persistence | PASS |
| لاگ حساس | صفر در سورس + **صفر ارجاع Log در dex نهایی** (اسکن dexdump) | PASS |
| پشتیبان رمزشده | AES-GCM + checksum + AAD؛ تست round-trip و tamper | PASS |
| پشتیبان خراب رد می‌شود | smoke test → INTEGRITY_FAILURE | PASS |
| عبارت عبور غلط پشتیبان رد می‌شود | smoke test → WRONG_PASSWORD | PASS |
| Screenshot/recents | FLAG_SECURE روی همهٔ صفحات حساس (policy تست‌شده) — **runtime رفتار روی دستگاه NOT TESTED** | PASS (پیاده‌سازی) / NOT TESTED (رفتار روی دستگاه) |
| INTERNET permission | aapt2 badging: **ندارد** | PASS |
| Analytics/Ads/Cloud | صفر وابستگی؛ صفر endpoint در کد (URLهای یافته در dex فقط لینک‌های مستندات issuetracker/youtrack کتابخانه‌ها — هرگز fetch نمی‌شوند) | PASS |
| Exported components | فقط MainActivity (LAUNCHER، عمدی) + ProfileInstallReceiver (پیش‌فرض کتابخانهٔ profileinstaller؛ فقط actionهای نصب پروفایل، بدون دسترسی به داده) | PASS (ارزیابی‌شده) |
| URI/file security | بدون FileProvider/grant ماندگار؛ SAF فقط با pick کاربر | PASS |
| مجوز تزریق‌شده | `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` با protectionLevel=**signature** (تزریق AndroidX برای گیرندهٔ screen-off؛ خصوصیِ اپ) | PASS (ارزیابی‌شده) |
| java.util.Random | ۱۵ ارجاع فقط از kotlin-stdlib/کتابخانه‌ها (کلاس‌های obfuscated)؛ **هیچ مسیر راز ما از آن استفاده نمی‌کند** (همهٔ crypto = SecureRandom) | PASS |
| R8 | build موفق + قوانین حداقلی + strip کامل Log | PASS |

## ۵. مسیر دقیق APK

- **فایل نهایی تحویلی:** `/home/user/dezh-pasargad/dezh-pasargad-release.apk` (امضاشده با **keystore شخصی**؛ قابل نصب مستقیم)
- خروجی خام unsigned: `dezh-pasargad/app/build/outputs/apk/release/dezh-pasargad-release-unsigned.apk`
- امضای SHA-256 گواهی شخصی: `CB:B7:B1:39:D0:24:00:0B:C5:3F:F9:00:BC:5D:46:EB:CD:01:7B:59:4A:B4:27:1E:97:5E:BC:21:E8:98:CE:6B` (RSA-4096، اعتبار تا ۲۰۵۶؛ اعتبارنامه‌ها در `~/keys/` خارج از مخزن؛ سیاست نگهداری: `~/keys/KEYS-POLICY.md`)
- **آیکون اختصاصی:** نماد دژ پاسارگاد (تولیدشده) — adaptive icon (fg/bg/monochrome) + legacy round، همهٔ تراکم‌ها؛ verify شده در APK (بدون shrink حذف نشده)

## ۶. Limitation واقعی باقی‌مانده

1. **نصب/launch/smoke روی دستگاه واقعی انجام نشده** — سندباکس هیچ دستگاه/emulator متصلی ندارد (این تنها مورد از چک‌لیست است که قابل اجرا نبود و طبق قانون شما PASS اعلام نمی‌شود). APK امضاشده آمادهٔ نصب دستی است؛ پس از نصب، همان مسیرهایی که در smoke test JVM اجرا شدند باید روی دستگاه هم ورقی شوند.
2. نسخهٔ ۱.۰.۰ نهایی با keystore شخصی امضا شد؛ **آپدیت نصب‌روی از buildهای QA قبلی ممکن نیست** (امضای متفاوت → نصب مجدد لازم؛ برای کاربر نهایی بی‌اثر).
3. تست میدانی رفتار FLAG_SECURE و auto-lock روی OEMهای مختلف (لایه‌های سازگارسازی) ممکن نیست.

## ۷. چک‌لیست نهایی اعلام completion

**PASS (تست‌شدهٔ واقعی):** zero build errors · release build · APK exists (ساختاری verify شده: badging/manifest/dex/signature) · setup · unlock · wrong-password rejected · vault CRUD · persistence پس از restart · encryption at rest · بدون master password ذخیره · بدون log حساس (شامل dex) · backup works · restore works · corrupted rejected · wrong backup password rejected · auto-lock (واحد + زنجیره) · clipboard protection (پیاده‌سازی + تست) · بدون INTERNET/analytics/ads/cloud (aapt2 + dex) · RTL/Persian (تست‌های fa locale) · light/dark/font-scale (تست + پیاده‌سازی توکنی) · a11y checks · migrations valid · R8 release works.

**NOT TESTED (صادقانه):** install/launch روی دستگاه · runtime رفتار FLAG_SECURE/clipboard روی دستگاه · R8 smoke روی سخت‌افزار.

**جمع‌بندی:** پروژه در وضعیت **قابل انتشار داخلی (sideload)** است؛ تنها گام انسانیِ باقی‌مانده، نصب و اجرای ۵ دقیقه‌ای روی یک دستگاه واقعی برای تکمیل دو مورد NOT TESTED است.
