# گزارش فاز ۶ — امنیت رفتاری، Privacy، Accessibility و پولیش نهایی

**تاریخ:** ۱۶ سپتامبر ۲۰۲۶
**پروژه:** Dezh Pasargad
**پایه:** commit `dcbf67c` (پایان فاز ۵)
**کامیت این فاز:** انتهای همین گزارش

---

## ۱. Auto-lock قابل تنظیم + رویدادهای چرخهٔ عمر

### گزینه‌ها (DataStore، همان تنظیمات فاز ۵ با گسترش سیاستی)

| گزینه | مقدار | وضعیت |
|--------|-------|--------|
| بلافاصله هنگام خروج از برنامه | `0` دقیقه | ✅ قابل انتخاب |
| ۱ دقیقه | `1` | ✅ |
| ۵ دقیقه (پیش‌فرض) | `5` | ✅ |
| ۱۵ دقیقه | `15` | ✅ |
| ۳۰ دقیقه | `30` | ✅ |
| هرگز | — | ❌ **در UI فقط به‌صورت ردیف غیرفعال با برچسب «مجاز طبق سیاست امنیتی نیست»** |

- سیاست در کد هم fail-closed است: `updateTimeoutMinutes(-1)` (هرگز) به‌جای نادیده گرفته شدن، به «بلافاصله» برمی‌گردد — با تست واحد اثبات‌شده (`forbidden never value fails closed to immediate lock`).
- مقدارهای نامعتبر ذخیره‌شده (مثل `60` قدیمی) هنگام خواندن به نزدیک‌ترین گزینهٔ مجاز clamp می‌شوند.

### رویدادها

| رویداد | رفتار | پیاده‌سازی |
|---------|--------|-------------|
| App background | شروع تایمر طبق تنظیم | `ProcessLifecycleOwner` → `onAppBackgrounded` |
| Screen off | همان سیاست background | `BroadcastReceiver(ACTION_SCREEN_OFF)` با `RECEIVER_NOT_EXPORTED` → `onScreenOff()` |
| Activity recreation | هیچ secret از دست نمی‌رود و هیچ bypass ای رخ نمی‌دهد: session و قفل در `AppContainer` (Application-scoped) هستند؛ پس از recreation، navigation از `lockState` بازسازی می‌شود | معماری موجود + بررسی |
| Process recreation | برنامه همیشه قفل شروع می‌شود؛ هیچ plaintext ای روی دیسک نیست (فاز ۲/۳) | طراحی موجود |
| App restart | همان بالا؛ صفحهٔ Startup → Unlock | طراحی موجود |

تست‌های جدید `AutoLockControllerTest` (۵ مورد): قفل فوری با ۰ دقیقه، fail-closed با مقدار ممنوع، ۱ دقیقه دقیق، سیاست screen-off، و قفل لحظه‌ای screen-off با تنظیم «بلافاصله». (کل کلاس: ۹ تست سبز.)

### رفتار پس از lock

- **Sensitive content hide:** با قفل، `lockState` تغییر می‌کند و `DezhApp` بلافاصله back stack را به Unlock بازمی‌سازد؛ ViewModelهای صفحه‌های رمزنگاری‌شده (که plaintext در StateFlow دارند) نابود می‌شوند. هیچ snapshot UI ای از محتوای رمز باقی نمی‌ماند.
- **Authentication لازم:** مسیر Unlock (master password) — بدون تغییر.
- **Sensitive previews:** همهٔ صفحه‌های پس از Startup (شامل Unlock/Setup/کل گاوصندوق) با `FLAG_SECURE` از screenshot و پیش‌نمایش app switcher مستثنی می‌شوند — سیاست در `ui/SecureScreenPolicy.kt` جداشده و تست‌شده (`SecureScreenPolicyTest`).

## ۲. Clipboard

- **فقط با اقدام صریح:** دو سطح — «کپی رمز» در جزئیات رکورد و «کپی» در مولّد.
- **Feedback:** نشانگر «کپی شد — پس از مهلت تعیین‌شده پاک می‌شود».
- **Auto-clear:** هر دو سطح اکنون پس از تنظیم «پاک‌سازی کلیپ‌بورد» پاک می‌شوند (`setText("")`)؛ گزینهٔ «هرگز» فقط برای کلیپ‌بورد مجاز است (ریسک کمتر از auto-lock) و بدون مهلت هم feedback درست نشان داده می‌شود.
- **عدم log / عدم persistence:** کد اسکن شد — هیچ log یا ذخیره‌سازی مقدار کپی‌شده وجود ندارد.
- **بدون monitoring:** هیچ `OnPrimaryClipChangedListener` ای ثبت نشده (اسکن: صفر).

## ۳. جلوگیری از نشت (leakage)

| مسیر نشت | وضعیت |
|-----------|--------|
| Screenshots | `FLAG_SECURE` روی همهٔ صفحه‌های حساس (تست policy) |
| App switcher previews | همان `FLAG_SECURE` |
| Notifications | برنامه هیچ notification ای نمی‌سازد (اسکن) |
| Debug logs | صفر `Log.` / `println` در کل main sources (اسکن) |
| Exception messages | پیام‌های exception همه static/generic هستند؛ هیچ فیلد secret ای در message جاری نمی‌شود (اسکن BackupModels/BackupFileGateway) |
| Analytics | هیچ — کتابخانه/کد تحلیلی وجود ندارد (اسکن dependencies) |
| Crash reports | هیچ SDK گزارش خرابی نیست |

## ۴. Offline-first (بررسی Manifest)

- **`<uses-permission>`: صفر** — حتی INTERNET وجود ندارد. هیچ backend/cloud/sync/remote-auth/API ای در پروژه نیست؛ تنهاییِ شبکه‌ای در سطح manifest تضمین شده است.
- `allowBackup=false` + `dataExtractionRules` (فاز ۵) سر جای خود است؛ `supportsRtl=true`.

## ۵. Accessibility

- **TalkBack/semantic labels:** هر Icon با `contentDescription` («جستجو»، «اقدامات بیشتر»، «افزودن رکورد جدید»، checkbox علاقه‌مندی، ± حداقل‌های مولّد)؛ سرستون‌های بخش‌ها با `semantics { heading() }` در Settings/Theme/Security.
- **Minimum touch targets:** همهٔ کنترل‌ها M3 (IconButton/TextButton/Checkbox/ListItem) با هدف لمسی ۴۸dp داخلی.
- **Font scaling:** `LocalDensity(fontScale = settings.fontScale)` در ریشه؛ تنظیم ۴ سطحی کاربر.
- **Reduced motion:** پویانمایی‌ها با تنظیم «پویانمایی رابط» خاموش/روشن می‌شوند.
- **State فقط با رنگ نیست:** انتخاب‌ها با RadioButton/Checkmark متنی («انتخاب‌شده») و متن نقش‌ها منتقل می‌شوند.
- **Readable typography/contrast:** توکن‌های M3 فاز ۱ (جفت‌های light/dark با هدف کنتراست WCAG) بدون تغییر رنگ hardcoded.

## ۶. RTL Audit (فا/انگلیسی)

- Layout direction: فقط `start/end` (اسکن: صفر Alignment.Left/Right و absolutePadding) — شامل **Dialogها، فرم‌ها، منوها، validation، onboarding، empty states، settings و backup/restore** که همگی از همان کامپوننت‌های M3 ساخته شده‌اند.
- تست‌های UI با `qualifiers = "fa-rIR"` اجرا می‌شوند (رشته‌ها و چیدمان واقعاً RTL resolve می‌شوند)؛ `supportsRtl=true` فعال.
- Icons جهت‌دار استفاده نشده (فقط Search/MoreVert که جهت‌محور نیستند).

## ۷. Material 3 UX Audit → اصلاحات انجام‌شده توسط خودم

| مشکل یافته‌شده | اصلاح |
|------------------|--------|
| **سرستون شلوغ گاوصندوق** (۵ دکمهٔ متنی هم‌ردیف: جستجو/دسته‌ها/علاقه‌مندی‌ها/تولید/تنظیمات — سرریز روی صفحه‌های کوچک) | بازطراحی به **TopAppBar متریال ۳**: title + آیکن جستجو + منوی سرریز «اقدامات بیشتر» (دسته‌ها، علاقه‌مندی‌ها، تولید رمز، تنظیمات، چرخش تم) — با تست UI ناوبری (`VaultTopBarUiTest`) |
| **FAB بدون معنای TalkBack** («+» بی‌معنا خوانده می‌شد) | `contentDescription = "افزودن رکورد جدید"` + تست |
| **تکرار دکمهٔ چرخش تم** در سرستون لیست و منو | حذف از سرستون لیست؛ تنها در منوی سرریز |
| **نبود عنوان‌بخش برای TalkBack** در صفحه‌های تنظیمات | `heading()` semantics روی همهٔ section labelها |
| **کپی مولّد بدون پاک‌سازی خودکار** (برخلاف جزئیات رکورد) | auto-clear یکسان در هر دو سطح |

سایر بندهای audit (hierarchy/typography/spacing/color roles/shapes/elevation/adaptive/dark-light/loading-error-empty/destructive actions) از فازهای ۴–۵ برقرارند و دوباره اسکن شدند.

## ۸. گیت‌ها (به ترتیب الزامی)

| گیت | نتیجه |
|------|-------|
| BUILD (`assembleDebug`) | ✅ EXIT=0 |
| INSTRUMENTATION/UI TESTS | ✅ **۱۵۶/۱۵۶** (۱۴۴ قبلی + ۵ auto-lock + ۴ FLAG_SECURE policy + ۲ TopBar UI + ۱ تست جدید UI Robolectric با locale فارسی) — تست‌های instrumentation روی emulator در سندباکس ممکن نیست؛ معادل UI آنها با Robolectric Compose پوشش داده شده |
| ACCESSIBILITY REVIEW | ✅ اسکن + اصلاحات §۵ (descriptionها، headings، touch targets، font scale، reduced motion) |
| PRIVACY REVIEW | ✅ صفر permission، صفر analytics/tracking/ads/remote، صفر log، صفر clipboard monitoring |
| SECURITY REVIEW | ✅ FLAG_SECURE + ۵ رویداد قفل + fail-closed سیاست + clipboard hygiene + پیام‌های exception بدون راز |
| LINT (`lintDebug`) | ✅ ۰ خطا |
| STATIC ANALYSIS (detekt) | ✅ ۰ یافته |

## ۹. محدودیت‌های صادقانه

- «Never» برای auto-lock در UI نشان داده ولی غیرفعال است تا کاربر بداند چرا نیست؛ اگر روزی سیاست تغییر کند، فقط ثابت `AUTO_LOCK_NEVER_PERMITTED` و UI باید باز شود.
- تشخیص screen-off روی برخی لایه‌های سازگارسازی OEM می‌تواند با تأخیر broadcast بیاید؛ سیاست fail-closed در سطح background هم فعال است بنابراین پوشش دوگانه داریم.
- FLAG_SECURE رفتار app switcher را در همهٔ لانچرها تضمین می‌کند، ولی «عکس صفحه با دستگاه دیگر» را طبعاً نمی‌تواند بگیرد.
