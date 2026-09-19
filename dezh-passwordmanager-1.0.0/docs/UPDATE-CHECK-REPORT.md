# قابلیت «بررسی بروزرسانی» — دژ پاسارگاد 1.0.0

**Release channel:** `github.com/Kourosh242/dezh-pasargad` (تگ‌های Releases)
**نسخه:** همان 1.0.0 • APK: 3,923,115 بایت — امضا با همان keystore (SHA-256 `cbb7b139…` تأیید شد)

## ۱. رفتار کاربر-محوْر (دقیقاً طبق درخواست)
1. کاربر در **منوی سه‌نقطهٔ گاوصندوق** گزینهٔ «**بررسی بروزرسانی**» را می‌زند.
2. صفحهٔ بررسی باز می‌شود: لوگوی برند + «نسخهٔ فعلی: 1.0.0» + دکمهٔ «بررسی کن».
3. با لمس، اپ یک GET تازه به `https://api.github.com/repos/Kourosh242/dezh-pasargad/releases/latest` می‌زند (بدون کش — هر بررسی = درخواست زنده).
4. تگِ آخرین Release خوانده می‌شود (`tag_name`) و با نسخهٔ در حال اجرا مقایسهٔ عددی می‌شود (1.0.1 > 1.0.0 → آپدیت؛ برابر یا کمتر → «شما جدیدترین نسخه را دارید»).
5. اگر نسخهٔ جدید باشد: کارت «به‌روزرسانی جدید موجود است — نسخهٔ X منتشر شده» + دکمهٔ «**همین حالا به‌روزرسانی کن**» → صفحهٔ همان تگ (`html_url` پاسخ گیت‌هاب، مثلاً `.../releases/tag/1.0.1`) در **مرورگر پیش‌فرض (کروم)** باز می‌شود و دانلود APK از همان‌جا انجام می‌شود.
6. خطای شبکه → پیام «بررسی ناموفق بود» + «تلاش دوباره». هیچ crashی ممکن نیست.

## ۲. معماری (بدون وابستگی جدید)
| لایه | فایل | نقش |
|---|---|---|
| domain | `domain/update/UpdateChecker.kt` | `SemanticVersion.isNewer` (مقایسهٔ عددی نقطه‌ای + tolerate `v` پیشوند)، `UpdateCheckResult`، `UpdateChecker` (هر check = fetch تازه) |
| data | `data/update/GithubReleaseFetcher.kt` | `HttpURLConnection` + `org.json` پلتفرم (صفر dependency جدید)، timeout 10s، HTTPS فقط، parse مستقل و تست‌پذیر |
| data | `data/update/PackageAppVersionProvider.kt` | versionName از PackageManager (تک منبع حقیقت؛ بدون BuildConfig) |
| presentation | `presentation/update/UpdateViewModel.kt` + `UpdateScreen.kt` | وضعیت‌ها: Idle/Checking/UpToDate/Available/Failed + کارت CTA با pressScale |
| navigation | `DezhDestination.UpdateCheck` + entry در `DezhApp` | باز کردن URL با `Intent.ACTION_VIEW` |
| تزریق | `AppContainer.updateChecker` | DI دستی مطابق قواعد پروژه |

## ۳. تغییر امنیتی مهم (شفاف‌سازی)
مجوز **`android.permission.INTERNET`** اضافه شد — اولین مجوز شبکهٔ این اپ:
- فقط برای همین قابلیت دستی؛ گاوصندوق، رمزها و همهٔ داده‌ها همان‌طور آفلاین و محلی می‌مانند.
- تنها مقصد: `api.github.com` (HTTPS) هنگام لمس دکمه؛ هیچ داده‌ای از رمزها/گاوصندوق ارسال نمی‌شود.
- بدون آن این قابلیت اساساً ناممکن بود (سیستم‌عامل اجازهٔ هیچ سوکتی نمی‌دهد).

## ۴. تست‌ها (۱۹ تست جدید — همهٔ تعاملات fake و بدون شبکه)
- `SemanticVersionTest` (۸): بالاتر/برابر/کمتر/عددی‌نه‌لغوی (1.10>1.9)/پیشوند v/بخش‌های ناقص/پسوند -beta
- `UpdateCheckerTest` (۵): Available/UpToDate/Failed + **هر بررسی = درخواست تازه (شمارندهٔ فراخوانی)**
- `GithubReleaseFetcherTest` (۴): parse تگ/URL، fallback صفحهٔ releases، تگ ناموجود → خطا، JSON خراب → خطا
- `UpdateScreenUiTest` (۲): نمایش «نسخهٔ فعلی: 1.0.0» → بررسی → دکمهٔ آپدیت → URL صحیح به مرورگر تحویل شد؛ حالت up-to-date
- جمع سوئیت: **۲۰۷/۲۰۷ سبز** • detekt: ۰ • lint: سبز • assembleRelease+R8 ✓ • verify ✓

## ۵. گردش کار انتشار برای شما
هر نسخهٔ جدید: APK را در Releases گیت‌هاب با **تگ جدیدتر** (مثلاً `1.0.1`) منتشر کنید؛ کاربرانِ 1.0.0 با «بررسی بروزرسانی» آن را می‌بینند.
