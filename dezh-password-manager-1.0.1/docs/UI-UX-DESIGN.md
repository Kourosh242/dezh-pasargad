# طراحی UI/UX — دژ پاسارگاد (تک‌فایل مرجع طراحی)

> این فایل + `docs/UIUX-RESEARCH-1.0.1.md` (پژوهش موبایل) مرجع کامل طراحی است. پیاده‌سازی فعلی: `ui/theme/` + `presentation/*`.

---

## ۱) اصول

- **M3 واقعی، نه M3-نما:** tokenهای semantic، stateهای کامل، a11y الزامی.
- **فارسی/RTL از روز اول:** پیش‌فرض `values/` فارسی؛ RTL سیستم‌محور؛ اعداد/تاریخ فارسی‌دوست.
- **امنیت در UI:** هیچ secretای در navigation arguments؛ reveal رمز فقط با اقدام صریح کاربر.
- **کم‌گویی بصری:** ابزار بهره‌وری؛ Expressive فقط در لحظات هدفمند.

## ۲) Design Tokens

### پالت پاسارگاد (brand — وقتی dynamic color خاموش است)
| Role | Light | Dark |
|---|---|---|
| primary / onPrimary | `#7A5900` / `#FFFFFF` | `#EDC14C` / `#3F2E00` |
| primaryContainer / on | `#FFDF9E` / `#251A00` | `#5B4300` / `#FFDF9E` |
| secondary (برنز) / on | `#695F43` / `#FFFFFF` | `#D5C5A0` / `#393016` |
| secondaryContainer / on | `#F2E1BB` / `#231B04` | `#51462A` / `#F2E1BB` |
| tertiary (لاجورد) / on | `#3B638C` / `#FFFFFF` | `#92CCF9` / `#003352` |
| tertiaryContainer / on | `#C2E4FF` / `#001D33` | `#1F4A73` / `#C2E4FF` |
| error / on | `#BA1A1A` / `#FFFFFF` | `#FFB4AB` / `#690005` |
| errorContainer / on | `#FFDAD6` / `#410002` | `#93000A` / `#FFDAD6` |
| background / on | `#FFF8F0` / `#201B11` | `#1B1710` / `#EDE0D3` |
| surface / on | `#FFF8F0` / `#201B11` | `#1B1710` / `#EDE0D3` |
| surfaceVariant / on | `#EDE1CC` / `#4D4639` | `#4D4639` / `#D0C5B4` |
| outline | `#7F7667` | `#998F80` |

- **Dynamic color** (Material You) روی API 31+ روشن است؛ پالت برند fallback است.
- هر fill با جفت on* خودش؛ جداکنندهٔ تزئینی = `outlineVariant`، بوردر تعاملی = `outline`.

### Typography
فعلاً baseline M3 (`Typography()`). فاز آینده: فونت فارسی‌پسند (مثلاً Vazirmatn) برای headline/title، اعداد tabular برای timestampها. scale را فقط از `MaterialTheme.typography` بگیر.

### Shape / Spacing / Elevation
- shapes از `MaterialTheme.shapes` (small=badge/چیپ، medium=کارت/فیلد، large=sheet).
- spacing شبکهٔ 4dp؛ padding صفحه 16–24dp؛ فاصلهٔ آیتم‌ها 8–16dp.
- عمق با tonal elevation (`surfaceContainer`ها)؛ سایه فقط برای عناصر شناور واقعی.

## ۳) نقشهٔ Navigation و نگاشت صفحه‌ها

ریشهٔ backstack توسط `VaultLockState` کنترل می‌شود (امنیت = ناوبری):

| State | مقصد |
|---|---|
| Initializing | Startup |
| NotSetUp | Onboarding (Setup) |
| Locked | Unlock |
| Unlocked | Vault → (AddEntry / EntryDetails(entryId) → EditEntry(entryId)) |

آینده: Search، Generator، Categories، Favorites (فیلتر فعلی موجود)، Backup، Restore، Settings، Security، Theme، About — همگی در `DezhDestination` قرارداد شده‌اند.

## ۴) مشخصات صفحه‌های فعلی

### Startup (splash)
لوگو/نام + `CircularProgressIndicator` — فقط تا resolve شدن state. بدون تعامل.

### Setup (ساخت رمز اصلی)
- فیلدها: رمز اصلی / تکرار (PasswordVisualTransformation)
- بازخورد زنده: `LinearProgressIndicator` قدرت (Weak 0.25 / Fair 0.6 / Strong 1.0) + لیست خطاها (کوتاه‌بودن، دسته‌ها، رایج، تکرار) با رنگ `error`
- mismatch → `supportingText` خطا؛ دکمهٔ «ایجاد گاوصندوق» تا valid+mismatch-off غیرفعال
- submitting → progress داخل دکمه؛ پس از موفقیت ناوبری خودکار (state-driven)

### Unlock
- یک فیلد + دکمهٔ گشودن؛ خطاها generic (نادرست/آسیب‌داده) — بدون جزئیات رمزنگاری
- backoff: پیام شمارش‌معکوس با plurals (`تلاش مجدد تا n ثانیه دیگر`) و دکمه تا صفر شدن غیرفعال
- پس از submit، فیلد خالی می‌شود

### Vault List (خانهٔ گاوصندوق)
- عنوان + فیلد جستجو (in-memory پس از unlock) + `FilterChip` علاقه‌مندی‌ها + شمارندهٔ آیتم‌ها (plurals)
- ردیف دسته‌ها: FilterChip «همه» + دسته‌های distinct (DB query)
- لیست: `LazyColumn` + `ListItem` (title / username یا category / چک‌باکس علاقه‌مندی / «بازکردن») — key=id، maxLines=1 + ellipsis
- خالی: پیام راهنما + اشاره به FAB «+»؛ loading: `LinearProgressIndicator`
- FAB «+» فقط اقدام اصلی (افزودن)

### Entry Edit (افزودن/ویرایش)
- فیلدها: عنوان* / نام‌کاربری / ایمیل (Email keyboard) / رمز (masked) / یادداشت‌ها (چندخطی) / دسته + چک‌باکس علاقه‌مندی
- عنوان خالی → خطای inline «عنوان الزامی است»؛ ذخیره → progress؛ موفقیت → popBack؛ خطا → پیام generic
- دکمه‌ها: ذخیره (filled, weight 1f) / انصراف (outlined)

### Entry Detail
- title + category chip رنگ primary
- فیلدهای موجود (username/email/notes) — خالی‌ها رندر نمی‌شوند؛ جداکنندهٔ `HorizontalDivider`
- رمز: `••••••••` (Monospace) + دکمهٔ نمایش/پنهان‌کردن (reveal فقط با اقدام کاربر)
- چک‌باکس علاقه‌مندی؛ timestamps (created/updated) با labelSmall
- ویرایش (filled) / حذف (outlined) → `AlertDialog` تأیید → حذف → popBack

## ۵) ماتریس Stateها (همهٔ صفحه‌ها)

| State | نمایش |
|---|---|
| Loading | Linear/CircularProgressIndicator |
| Empty | پیام فارسی راهنما + اقدام (FAB) |
| Error | متن `error` color + مسیر ریکاوری (تلاش مجدد/انصراف) |
| Submitting | progress درون دکمه + غیرفعال‌سازی |
| Disabled | backoff، فرم نامعتبر |

## ۶) Accessibility (الزام انتشار)

- هدف لمسی ≥48dp (چک‌باکس/دکمه‌های M3 پیش‌فرض رعایت می‌کنند)
- کنتراست AA با جفت‌های on*؛ معنا هرگز فقط با رنگ
- contentDescription برای آیکون‌ها/اقدامات فقط-آیکونی (وقتی اضافه شدند)
- بزرگ‌نمایی فونت بدون شکستن layout (wrap/ellipsis کنترل‌شده)

## ۷) Anti-patterns (چک‌لیست پیش از merge)

- [ ] رنگ/اندازهٔ hardcode خارج از theme؟
- [ ] کارت/چیپ/تاب بی‌دلیل؟
- [ ] state جاافتاده (loading/empty/error)؟
- [ ] متن فقط en؟ (fa پیش‌فرض است)
- [ ] منطق کسب‌وکار داخل Composable؟ (باید صفر باشد)
- [ ] secret در آرگومان ناوبری یا متن روی صفحه‌ای که ربطی ندارد؟
