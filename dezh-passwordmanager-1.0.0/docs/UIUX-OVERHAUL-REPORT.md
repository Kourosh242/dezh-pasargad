# گزارش بازطراحی UI/UX — دژ پاسارگاد (نسخه 1.0.0)

**تاریخ:** ۱۷ سپتامبر ۲۰۲۶
**روش:** نصب و فعال‌سازی اسکیل `ui-ux-pro-max` (۷۳ فایل در `.claude/skills/ui-ux-pro-max`) و اجرای کامل Workflow آن: تولید Design System → جستجوهای دامنه‌ای (ux/color/typography/stack jetpack-compose) → بازطراحی → چک‌لیست pre-delivery
**خروجی:** `dezh-pasargad-release.apk` — ۳.۷۰MB، امضای شخصی، نسخه همان **1.0.0** (طبق دستور؛ منتشر نشده)

---

## ۱. Design System تولیدشده توسط اسکیل

با `--design-system --persist` ثبت شد در `design-system/dezh-pasargad/MASTER.md` (منبع حقیقت بصری):

- **سبک:** Minimalism & Swiss — تمیز، ساده، حرفه‌ای (توصیهٔ اسکیل برای ابزارهای امنیتی)؛ light+dark هر دو پشتیبانی
- **رنگ‌ها:** پایهٔ سرمه‌ای امنیتی + سبز اعتماد؛ در عمل با پالت طلایی/پوست‌پیازی هویتی پروژه ادغام شد (حفظ هویت «دژ») و نقش‌های elevation جدید اضافه شد
- **افکت‌ها:** ترنزیشن‌های نرم ۱۸۰–۲۵۰ms، سلسله‌مراتب تایپ واضح، بدون تزئین اضافی
- **پرهیزها:** رنگ به‌عنوان تنها نشانگر حالت، تزئین افراطی، صفحهٔ خالی بی‌راهنما

**تایپوگرافی:** پیشنهاد Lexend/Source Sans 3 اسکیل فاقد گلیف فارسی بود؛ طبق کاتالوگ google-fonts خود اسکیل، **Vazirmatn** (متغیر، ۱۰۰–۹۰۰، OFL) تأیید و استفاده شد — استاندارد واقعی UI فارسی. لایسنس: `docs/OFL-Vazirmatn.txt`.

## ۲. چه چیزهایی عوض شد

### بنیاد (Theme Layer)
| مورد | قبل | بعد |
|------|-----|-----|
| فونت | Roboto پیش‌فرض (fallback سیستم برای فارسی) | **Vazirmatn variable** با ۴ وزن روی محور wght — همهٔ ۱۵ نقش M3 با line-height بهینهٔ فارسی، letterSpacing=0 برای خط متصل |
| Elevation | فقط surface/variant | ۵ نقش `surfaceContainer*` برای هر دو تم — عمق بدون سایه، مرز واضح روشن/تاریک |
| Shapes | پیش‌فرض M3 | مقیاس ۶/۱۰/۱۴/۱۸/۲۶dp — کارت‌های نرم‌تر با حس دژ |
| Motion | پراکنده | `LocalMotionEnabled` از تنظیمات پویانمایی کاربر — همهٔ انیمیشن‌های جدید به آن احترام می‌گذارند |

### صفحات
- **گاوصندوق (VaultList/Favorites/Search):** ردیف جدید — کل ردیف لمس‌پذیر (48dp+)، مونوگرام حرف اول در دایره، username · category، **قلب IconToggleButton به‌جای Checkbox**؛ FAB با آیکون `Add` و رنگ container؛ چیپ علاقه‌مندی/دسته‌ها در **LazyRow** (حذف سرریز افقی)؛ **EmptyState مشترک** با آیکون سپر/عنوان/توضیح/CTA «افزودن نخستین رمز»؛ `animateItem()` روی لیست و گرید؛ دکمهٔ مرتب‌سازی با آیکون
- **بازگشایی (Unlock):** لوگوی دژ طلایی ۱۱۲dp + **visibility toggle رمز** + پاک‌شدن toggle بعد از submit — paste مسدود نمی‌شود (قاعدهٔ accessible-authentication)
- **راه‌اندازی اولیه (Setup):** لوگوی برند + toggle نمایش روی هر دو فیلد + **نوار قدرت رنگی متحرک** (ضعیف=error، متوسط=tertiary، قوی=primary) با track رنگی
- **جزئیات رکورد:** مونوگرام کنار عنوان، دسته به‌صورت pill، اکشن‌های رمز با آیکون چشم/کپی (برچسب گفتاری کامل)، **قلب به‌جای Checkbox**، دکمهٔ حذف با رنگ error (تفکیک اقدام مخرب)
- **ویرایش/ایجاد:** toggle نمایش رمز کنار دکمهٔ تولید + نوار قدرت رنگی معنایی
- **دسته‌ها:** EmptyState با آیکون label + `animateItem`
- **Startup:** لوگوی برند + spinner
- **آیکون‌های وکتور جدید:** visibility، visibility-off، sort، copy، shield، label (مسیرهای رسمی Material، Apache-2.0) + سیلوئت دژ ۵۱۲px tintable

### پرهیزهای رعایت‌شده (طبق pro-rules)
بدون emoji به‌جای آیکون · فقط وکتور · یک خانواده/سبک آیکون در هر سطح · توکن‌های semantic صرف (صفر رنگ hardcode جدید) · برچسب گفتاری برای هر کنترل آیکونی · null برای آیکون‌های تزئینی · رنگ هرگز تنها نشانگر نیست (قلب شکل عوض می‌کند، نوار قدرت متن دارد) · 8dp rhythm

## ۳. Build / Test / Audit status

| گیت | نتیجه |
|-----|--------|
| `compileDebugKotlin` / `assembleDebug` | ✅ |
| **تست کامل** | **۱۶۵/۱۶۵** ✅ (۱ تست به‌روز شد: متن empty-state علاقه‌مندی‌ها) |
| `lintDebug` | ۰ خطا ✅ |
| `detekt` | ۰ یافته ✅ (PasswordSection از EntryDetail استخراج شد تا complexity ≤15 بماند) |
| `assembleRelease` (R8) | ✅ — ۳.۷۰MB (فونت +۲۴۱KB + دارایی‌های آیکون) |
| امضا | keystore شخصی، `apksigner verify` ✅ |
| dex نهایی | صفر ارجاع `android.util.Log` ✅ |
| versionName / permission | 1.0.0 / فقط مجوز signature سیستم ✅ |

## ۴. Limitation صادقانه

- رفتار runtime (فونت روی دستگاه واقعی، انیمیشن‌ها، font-scale بیشینه، landscape) در سندباکس قابل تست نیست — تست دستگاه همان REMAINING قبلی است.
- پیشنهاد پالت اسکیل (سرمه‌ای/سبز) عمداً با هویت طلایی دژ ادغام شد، نه جایگزینی — تصمیم طراحی مستندشده.
- Reduced-motion سیستم‌اندروید (animator scale) مستقیم در Compose در دسترس نیست؛ gate فعلی تنظیمات درون‌برنامه‌ای «پویانمایی» است.

## ۵. فایل‌های کلیدی

- اسکیل نصب‌شده: `.claude/skills/ui-ux-pro-max/` (commit شده تا بین sessionها بماند)
- منبع حقیقت بصری: `design-system/dezh-pasargad/MASTER.md`
- فونت: `app/src/main/res/font/vazirmatn.ttf` + `docs/OFL-Vazirmatn.txt`
- APK: `/home/user/dezh-pasargad/dezh-pasargad-release.apk` (۳.۷۰MB، امضای شخصی)
