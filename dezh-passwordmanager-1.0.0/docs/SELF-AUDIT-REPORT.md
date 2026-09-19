# گزارش خودبازرسی — شکار، اثبات و رفع باگ‌ها (نسخهٔ 1.0.0)

**تاریخ:** ۱۷ سپتامبر ۲۰۲۶ · **روش:** خودبازرسی سیستماتیک کد (منطق + ظاهر) با تکیه بر اسکیل‌های نصب‌شده (`ui-ux-pro-max` + `claude-android-ninja`)
**قاعدهٔ این دور:** هیچ باگی بدون **اثبات** فیکس نشد — برای هر مورد اول تستِ بازتولید نوشته شد، **اجرا و شکستش ثبت شد** (۴/۴ FAILED)، بعد فیکس و **سبز شدن** تأیید شد.
**خروجی:** `dezh-pasargad-release.apk` — ۳.۶۹MB، امضای شخصی، versionName همان **1.0.0**

---

## باگ‌های پیدا شده، اثبات‌شده و رفع‌شده (۴ مورد)

### #۱ — ویرایشِ رمزِ قوی، نوار قدرت قرمزِ «ضعیف» می‌گرفت
- **ریشه:** در `EntryEditViewModel` هنگام load رکورد برای ویرایش، رمز در فرم می‌نشست ولی `onPasswordChanged` صدا نمی‌خورد → `meterScore=0 / WEAK` می‌ماند.
- **اثبات:** `EditMeterLoadTest.editing a strong entry does not show a weak meter` → **FAILED قبل از fix** (band واقعی WEAK بود).
- **رفع:** پس از load، strengthMeter روی رمز بارگذاری‌شده اجرا و score/band ست می‌شود. تست حالا **سبز**.

### #۲ — همان باگ عددِ ۱۰ رقمی، این‌بار در **صفحهٔ پشتیبان**
- **ریشه:** `BackupScreen` هم `stringResource(R.string.entry_meter_label, meterLabel(band))` — یعنی شناسهٔ عددی منبع به‌جای رشته در `%1$s`. در دور قبل فقط نمونهٔ EntryEdit فیکس شده بود و این call-site از قلم افتاده بود (خودِ بازرسی این را پیدا کرد).
- **اثبات:** `BackupMeterLabelUiTest` → تایپ عبارت قوی → جستجوی متن «قدرت رمز: خیلی خوب» → **FAILED قبل از fix** (عدد خام نمایش داده می‌شد).
- **رفع:** `stringResource` تودرتو. تست سبز.

### #۳ — جستجوی بی‌نتیجه، پیام غلط «گاوصندوق هنوز خالی است» می‌داد
- **ریشه:** در `VaultListContent` شاخهٔ خالی فقط `favoritesOnly` را تفکیک می‌کرد؛ `query` نامخالی نادیده بود.
- **اثبات:** `SearchNoResultsUiTest` → state با query و بدون نتیجه → **FAILED قبل از fix** (پیام vault-empty رندر می‌شد).
- **رفع:** EmptyState سوم: «موردی یافت نشد / برای این جستجو نتیجه‌ای نبود…» با آیکون Search و بدون CTAٔ افزودن. +۴ رشته (fa/en، پاریتی). تست سبز.

### #۴ — گشودن از مسیر کیبورد (IME Done)، رمز را در فیلد جا می‌گذاشت
- **ریشه:** `onDone = { onSubmit(password) }` بدون پاک‌سازی؛ در تضاد با دکمه که پاک می‌کرد → رمز اصلی روی صفحه باقی می‌ماند (نکتهٔ امنیتی).
- **اثبات:** `UnlockImeClearTest` → تایپ + performImeAction → **FAILED قبل از fix** (متن فیلد باقی بود).
- **رفع:** یک مسیر مشترک `submitAndClear` برای هر دو (همچنین مسیر دکمه هم الان gate `canSubmit` دارد). تست سبز.

## بازرسی‌هایی که مشکلی پیدا نکردند (نمونه)
خروجی generator (تست‌های موجود) · جریان restore مرحله‌ای و header جلالی · auto-lock UI پس از حذف «هرگز» (choices + clamp) · تم brand-first و لیبل‌های accent · توازن strings ۲۰۷/۲۰۷ · بدون GlobalScope/runBlocking/رنگ hardcode در presentation.

## گیت‌های نهایی

| گیت | نتیجه |
|-----|--------|
| **سوئیت کامل** | **۱۷۹/۱۷۹** ✅ (۱۷۵ قبلی + ۴ تست اثبات/رگرسیون) |
| lint / detekt | ۰ / ۰ ✅ |
| `assembleRelease` (R8) | ✅ |
| امضا / verify | keystore شخصی + apksigner ✅ |
| versionName / versionCode | 1.0.0 / 1 ✅ (تغییر نکرد) |

## چرخهٔ اثبات (خلاصه)

```
proof:  EditMeterLoadTest FAILED · BackupMeterLabelUiTest FAILED ·
        SearchNoResultsUiTest FAILED · UnlockImeClearTest FAILED   ← باگ‌ها واقعی
fix:    EntryEditViewModel(init) · BackupScreen(label) ·
        VaultListContent(search empty) · UnlockScreen(submitAndClear)
post:   هر ۴ سبز + 179/179 کامل
```

**فایل:** `/home/user/dezh-pasargad/dezh-pasargad-release.apk` (۳.۶۹MB)
**محدودیت صادقانه:** تست روی دستگاه واقعی همچنان خارج از توان سندباکس است؛ تست‌های UI این دور با Robolectric (fa-rIR) اجرا شده‌اند.
