# دور اصلاح UI بر پایهٔ رقبا + قابلیت «حفاظت از عکاسی» — دژ پاسارگاد 1.0.0

**Commit پایه:** `829d28f` → **این دور:** `WIP: 32a9e46` + این commit
**نسخه:** همان 1.0.0 (versionCode 1)

## ۱. پژوهش بصری رقبا (تصاویر واقعی بررسی شد)
- **Bitwarden Android (گاوصندوق خالی):** تیتر بزرگ ساده، **هیچ بنر/کارت خوش‌آمدی وجود ندارد**، خالی‌بودن = ایلاستریشن + یک خط توضیح + یک دکمهٔ قرصی «New login»؛ FAB شناور؛ bottom-nav چهارگزینه‌ای.
- **Bitwarden (لیست پر):** ردیف‌های تخت با آواتار واقعی سرویس + عنوان + زیرعنوان؛ بخش‌بندی Favorites/Types با شمارنده؛ بدون هیچ کارت اضافی بالای لیست.
- **1Password Android:** دیالوگ‌ها و شیت‌های گرد، ردیف‌های دوخطی تمیز.
- **Proton Pass (تنظیمات):** کارت‌های گروهی با لیبل کم‌رنگ + مقدار + کلید، چیپ‌های آمار قرصی، سلسله‌مراتب بخش‌ها با سرتیتر کوچک.
- **نتیجهٔ طراحی:** بنر «گاوصندوق N ایتم رمزنگاری‌شده» (VaultHeroCard) الگوی هیچ‌کدام از رقبا نبود و حذف شد؛ شمارندهٔ آیتم‌ها در یک ردیف ظریف کنار مرتب‌سازی می‌ماند (الگوی Bitwarden «Vault: All»).

## ۲. قابلیت جدید: «حفاظت از عکاسی» (Screenshot protection)
- **تنظیمات ← تنظیمات امنیتی ← حفاظت از عکاسی** (پیش‌فرض: **روشن**).
- روشن = FLAG_SECURE روی همهٔ صفحات دارای اطلاعات (اسکرین‌شات و پیش‌نمایش app-switcher مسدود).
- خاموش = کاربر می‌تواند از صفحه عکس بگیرد (همان درخواست کاربر).
- پیاده‌سازی:
  - `DezhSettings.screenshotProtectionEnabled` + نگاشت DataStore (`screenshot_protection_enabled`)
  - `SecureScreenPolicy.requiresSecureFlag(lockState, screenshotProtectionEnabled)` — سیاست واحد و تست‌پذیر
  - `DezhApp`: اثر FLAG_SECURE به toggle واکنش داده و در هر دو جهت add/clear می‌کند
  - `SettingsViewModel.onScreenshotProtectionChanged` + ردیف سوییچ با متن توضیحی (ردیف کلیک‌پذیر + Switch)
  - strings: `settings_screenshot_protection` / `_desc` (فا/ان)

## ۳. اصلاحات UI
- **حذف کامل VaultHeroCard** و رشته‌هایش — صفحهٔ گاوصندوق حالا مثل Bitwarden: تیتر، ردیف ظریف شمارش/مرتب‌سازی، چیپ دسته‌ها، لیست کارتی، FAB.
- **EmptyState بازطراحی شد** (الگوی خالی‌بودن Bitwarden): دیسک گرادیانی 112dp + تیتر titleLarge نیم‌ضخیم + متن تک‌خطی + دکمهٔ قرصی CTA.
- فاصلهٔ ردیف‌های لیست خطی 2dp→8dp برای ریتم بصری هم‌اندازه با گرید.

## ۴. دروازه‌های کیفیت (همه اجرا شد)
- compile سبز • **تست‌ها 184/184 سبز** (181 قبلی + ۲ تست سیاست جدید + ۱ تست UI toggle + توسعهٔ roundtrip DataStore)
- **detekt: 0** • **lintDebug: سبز**
- **assembleRelease + R8** سبز → امضا با `dezh-pasargad-personal.keystore` (SHA-256 همان `cbb7b139…`) → `apksigner verify` ✓
- راستی‌آزمایی محتوای APK: `settings_screenshot_protection` در 리سورس‌ها هست (فا+ان)، `hero_secured_badge` حذف شده.

## ۵. حادثهٔ محیطی (شفاف‌سازی)
- سندباکس بین دو نوبت `.cache` (JDK/SDK) را کاملاً پاک کرد؛ بازسازی کامل در `/home/user/envbuild` انجام شد (JDK 17.0.20.1 + cmdline-tools 15859902 + platforms;android-37.0 + build-tools;36.0.0).
- دو نکتهٔ تازه: (الف) نام پلتفرم `android-37` به `android-37.0` تغییر کرده؛ (ب) پرشدن tmpfs سقف‌دار `/tmp` (۱GB) باعث خروج بی‌صدای gradle می‌شد — با `_JAVA_OPTIONS=-Djava.io.tmpdir=...` و آزادسازی `/tmp` حل شد؛ دو بار OOM-kernel هم رخ داد که با `--no-daemon` و پاکسازی مدیریت شد.
