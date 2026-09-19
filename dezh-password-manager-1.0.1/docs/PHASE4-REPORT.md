# گزارش فاز ۴ — UI کامل، Navigation 3، Material 3 و RTL واقعی

**تاریخ:** ۱۶ سپتامبر ۲۰۲۶
**پروژه:** Dezh Pasargad (مدیریت‌کنندهٔ رمز عبور آفلاین)
**پایه:** commit `2104783` (پایان فاز ۳ + تمیزکاری)

---

## ۱. صفحات تکمیل‌شده (screens completed)

هر ۱۲ نقش صفحه‌ای تعریف‌شده در طراحی (`docs/UI-UX-DESIGN.md`) پیاده‌سازی شد:

| # | نقش صفحه | فایل اجراکننده | وضعیت |
|---|-----------|----------------|--------|
| ۱ | Splash / Startup | `presentation/startup/StartupScreen` | ✅ |
| ۲ | First-time setup | `presentation/setup/SetupScreen` | ✅ |
| ۳ | Master password (ساخت/تغییر) | `presentation/setup/SetupScreen` | ✅ |
| ۴ | Unlock | `presentation/unlock/UnlockScreen` | ✅ |
| ۵ | Main Vault | `presentation/vault/VaultListScreen` (بازنویسی فاز ۴) | ✅ |
| ۶ | Search | `presentation/search/SearchScreen` + `SearchViewModel` (جدید) | ✅ |
| ۷ | Add Password | `presentation/vault/EntryEditScreen` (حالت ایجاد) | ✅ |
| ۸ | Edit Password | `presentation/vault/EntryEditScreen` (حالت ویرایش) | ✅ |
| ۹ | Password Details | `presentation/vault/EntryDetailScreen` (reveal/hide با کنترل leakage) | ✅ |
| ۱۰ | Password Generator | `presentation/generator/GeneratorScreen` + `GeneratorViewModel` (جدید) | ✅ |
| ۱۱ | Categories | `presentation/categories/CategoriesScreen` + `CategoriesViewModel` (جدید) | ✅ |
| ۱۲ | Favorites | `presentation/vault/FavoritesScreen` (جدید) | ✅ |

- `VaultListContent` به‌عنوان رندر مشترکِ Vault / Search / Favorites استخراج شد تا رفتار لیست، حالت‌ها و adaptive بودن در سه صفحه یکسان بماند.
- هر صفحه هر چهار حالت **loading / success / empty / error** را دارد (error برای خرابی ذخیره‌سازی و اعتبارسنجی فرم؛ confirmation برای حذف در Details).
- در EntryEdit، «Strength Meter» تقریبی به‌صورت live به فیلد رمز اضافه شد و دکمهٔ «تولید» رمز امن درج می‌کند.

## ۲. وضعیت ناوبری (navigation status)

- موتور ناوبری: **Navigation 3** (`androidx.navigation3`) با `rememberNavBackStack` و `entry<T>` تایپ‌محور — همان معماری فاز ۳ که اکنون مقصدهای جدید را هم پوشش می‌دهد.
- مقصدهای اضافه‌شده در `DezhDestination.kt`: `Search`، `Generator`، `Categories`، `Favorites` و تبدیل `Vault` به `data class` با دو آرگومان اختیاری:
  - `category: String?` — فیلتر اولیهٔ دسته (از صفحهٔ Categories).
  - `favoritesOnly: Boolean` — ورود به حالت علاقه‌مندی‌ها.
- **هیچ secret ای از route arguments عبور نمی‌کند**: id رکورد و نام دسته (متادیتای ذخیره‌شده به‌صورت ستونی طبق طراحی فاز ۳) تنها داده‌های قابل انتقال بین مقصدها هستند. رمزهای عبور فقط در `VaultSession` (حافظهٔ session باز) زندگی می‌کنند.
- پارامترهای عمومی `AppContainer` (generator، strength meter، themeModeController) بیرون از Composable‌ها ساخته و تزریق می‌شوند؛ ViewModelها با `viewModel { ... }` ساخته می‌شوند.

## ۳. وضعیت جستجو/فیلتر/مرتب‌سازی (search/filter/sort status)

- **جستجو:** کاملاً آفلاین و in-memory روی محتوای رمزگشایی‌شدهٔ session باز (`title` / `username` / `email` / `notes`) — بدون هیچ index پایدار plaintext روی دیسک (ادامهٔ قرارداد فاز ۳). صفحهٔ Search با `SearchBar` متریال ۳ و debounce-aware flow (`flatMapLatest`).
- **فیلترها:** برچسب دسته (chips) + «فقط علاقه‌مندی‌ها» (chip) + پیش‌فرض از آرگومان ناوبری.
- **مرتب‌سازی:** `VaultSortOption` با ۵ گزینه — `NAME` / `CREATED_NEWEST` / `UPDATED_NEWEST` / `CATEGORY` / `FAVORITES_FIRST` — به امضای `observeEntries(repo/use case)` اضافه شد و در `RoomVaultEntryRepository` به‌صورت in-memory مرتب می‌شود (tie-break با `updatedAt` نزولی). منوی مرتب‌سازی در سرستون لیست.

## ۴. وضعیت مولّد رمز (generator status)

- ماژول `generator/PasswordGenerator`:
  - منبع تصادف: `java.security.SecureRandom`.
  - طول قابل‌تنظیم ۸ تا ۶۴ (پیش‌فرض ۱۶).
  - چهار دستهٔ نویسه: بزرگ/کوچک/رقم/نماد با سوییچ روشن/خاموش.
  - **حداقل تعداد برای هر دسته** (`minUppercase` … `minSymbols`) با اعتبارسنجی `sum(min) ≤ length`.
  - گزینهٔ «حذف نویسه‌های گیج‌کننده» (`O0oIl1`).
  - جای‌گذاری اولیهٔ حداقل‌ها + پرکردن باقی از pool ترکیبی + **Fisher–Yates با همان SecureRandom**.
- **قانون نشت:** هیچ مقدار تولیدی log نمی‌شود؛ کپی روی کلیپ‌بورد فقط با اقدام صریح کاربر و نشانگر موقت «کپی شد». این مورد با اسکن خودکار کد (`Log.` / `println`) در ماژول generator/meter تأیید شد.
- UI مولّد: اسلایدر طول، سوییچ‌ها، کنترل ± برای حداقل‌ها، دکمه‌های تولید مجدد/کپی. دکمهٔ «تولید» در EntryEdit همان موتور را با پیش‌فرض‌ها به کار می‌برد.
- Strength Meter (`domain/PasswordStrengthMeter`): امتیاز ۰–۱۰۰ با طول، تنوع دسته، تکرار، توالی و ساختارهای رایج؛ سه باند WEAK/FAIR/STRONG. صراحتاً یک برآورد تقریبی UI است، نه entropy analyzer، و در UI هم با برچسب «برآورد تقریبی قدرت» معرفی شده است.

## ۵. وضعیت RTL (RTL status)

- رابط کاملاً فارسی است؛ رشته‌های انگلیسی فقط در `values-en` برای سازگاری ابزارها حفظ شده‌اند.
- RTL واقعی نه صرفاً ترجمه: همهٔ فاصله‌ها و ترازها با معناهای **start/end** نوشته شده‌اند (`horizontal = 16.dp`، `Arrangement`، `trailingContent` در `ListItem`)؛ هیچ استفاده‌ای از `Alignment.Left/Right`، `AbsoluteAlignment` یا `absolutePadding` در کد UI وجود ندارد (اسکن شد). `LazyColumn/LazyVerticalGrid/ListItem/Checkbox` به‌صورت بومی mirror می‌شوند.
- تست‌های Compose Robolectric با `qualifiers = "fa-rIR"` اجرا می‌شوند تا رشته‌های فارسی و مسیر RTL واقعاً resolve و assert شوند.

## ۶. وضعیت تم (theme status)

- `settings/ThemeModeController` (جدید): سه حالت **SYSTEM / LIGHT / DARK**، ذخیرهٔ انتخاب با `SharedPreferences` (کلید `dezh_theme_mode`، بدون دادهٔ حساس) و دکمهٔ چرخهٔ تم در سرستون لیست.
- `MainActivity`: با `calculateWindowSizeClass` عرض پنجره را استخراج می‌کند و `darkTheme` را از حالت انتخابی + `isSystemInDarkTheme()` محاسبه و به `DezhTheme` می‌دهد.
- **adaptive:** `VaultListContent` در عرض `Compact` تک‌ستونی و در `≥ Medium` (تبلت/پنجرهٔ قابل‌تغییر) دوستونی (`LazyVerticalGrid`).
- تم و رنگ: تمام رنگ‌ها از `MaterialTheme.colorScheme` و شکل‌ها از `MaterialTheme.shapes` — هیچ رنگ hardcoded در کد فاز ۴ وجود ندارد (اسکن شد)؛ بدون کارت بیش‌ازحد rounded، بدون gradient تزئینی و بدون انیمیشن تزئینی.

## ۷. نتیجهٔ build و تست‌ها (test/build result)

گیت‌ها به همان ترتیب الزامی اجرا شدند:

| گیت | نتیجه |
|------|-------|
| BUILD (`assembleDebug` + compileهای میانی) | ✅ EXIT=0 |
| UNIT TESTS (`testDebugUnitTest`) | ✅ **۱۰۶/۱۰۶ سبز** (۸۸ فاز ۳ + ۸ مولّد + ۶ سنجشگر قدرت + ۱ مرتب‌سازی + ۳ تست Compose UI) |
| UI TESTS (Robolectric Compose، fa locale) | ✅ `GeneratorScreenUiTest`، `VaultListContentUiTest` (رندر ردیف‌ها + حالت خالی علاقه‌مندی‌ها) |
| RTL REVIEW | ✅ اسکن: فقط start/end semantics؛ اجرای تست‌های UI با locale `fa-rIR` |
| DARK/LIGHT REVIEW | ✅ اسکن: صفر رنگ hardcoded؛ همه از colorScheme |
| ACCESSIBILITY REVIEW | ✅ `contentDescription` برای checkbox علاقه‌مندی و دکمه‌های ± حداقل‌ها؛ سایر کنترل‌ها متنی هستند |
| LINT (`lintDebug`) + detekt | ✅ lint: ۰ خطا / ۲ اطلاع‌رسانی (موجودیت Gradle 9.7.1 به‌عنوان نسخهٔ جدیدتر — پین 9.6.0 عمدی؛ اطلاع از A12+-only بودن `dataExtractionRules` در حالی که `allowBackup=false` برای API<31 عمداً حفظ شده) / detekt: ۰ یافته |

- تست‌های جدید در این فاز: `PasswordGeneratorTest` (۸)، `PasswordStrengthMeterTest` (۶)، مرتب‌سازی در `RoomVaultEntryRepositoryTest` (۱)، `GeneratorScreenUiTest` (۱)، `VaultListContentUiTest` (۲).
- یادداشت اجرا: به‌دلیل محدودیت حافظهٔ سندباکس، سوئیت کامل گاهی daemon را از کار می‌اندازد؛ با الگوی «daemon تازه + اجرای مرحله‌ای» هر دو نیمه و سپس اجرای کامل ۱۰۶/۱۰۶ سبز شد.

## ۸. امنیت و محدودیت‌های حفظ‌شده

- رمزهای تولیدی و فیلدهای credential فقط در `StateFlow` حافظه زندگی می‌کنند؛ در این فاز هیچ مسیر log/write جدیدی به‌صورت خودکار تعریف نشد.
- `data_extraction_rules.xml` اضافه شد تا در اندروید ۱۲+ نیز هیچ داده‌ای (DB، sharedpref، فایل) شامل backup/انتقال دستگاه نشود — هم‌راستا با طراحی امنیتی فاز ۲.
- هیچ ادعایی دربارهٔ «امنیت ۱۰۰٪» وجود ندارد؛ Strength Meter یک برآورد اووریستیک UI است.
