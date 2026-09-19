# گزارش بازطراحی UI/UX — دژ پاسارگاد 1.0.0

**Commit پایه:** `cd802f3` (خودبازرسی دور ۲) → **این دور:** بازطراحی عمیق UI/UX با پژوهش وب + اسکیل‌ها
**نسخه:** همان 1.0.0 (versionCode 1) — بدون تغییر نسخه
**APK:** `dezh-pasargad-release.apk` — 3,898,539 بایت (~3.72MB) — امضاشده با `dezh-pasargad-personal.keystore` (همان SHA-256 گواهی دور قبل: `cbb7b139…e898ce6b`) — verify ✓

## ۱. پژوهش (وب‌سرچ + اسکیل)
- **الگوهای password managerهای مرجع** (1Password / Bitwarden / Dashlane): vault خلوت با هویت بصری فوری برای هر آیتم (آواتار/آیکون دسته‌ای)، نشانگرهای امنیتی بصری (trust badge، متر قدرت)، feedback میکرو برای کپی/کمتر نمایش دادن اسرار، onboarding آرام و اعتمادساز.
- **Material 3 Expressive** (اسکیل `m3-expressive` نصب‌شده در `.claude/skills/`): اسپرینگ‌های کانونی (bouncy/snappy/gentle/critical)، تایپوگرافی وزن‌دار، tonal pairs از رنگ‌های container، اشتباهات رایج پرهیز شد (tween برای spatial ممنوع — فقط فنر؛ graphicsLayer به‌جای Modifier.scale؛ reduced-motion gate؛ کلید stable در لیست‌ها).
- اسکیل‌های نصب‌شده: `ui-ux-pro-max`، `claude-android-ninja`، `m3-expressive`.

## ۲. فایل‌های جدید
| فایل | نقش |
|---|---|
| `ui/theme/DezhMotion.kt` | توکن‌های حرکت: `bouncy(0.4/400)`, `snappy(0.75/1000)`, `gentle(0.6/200)`, `critical(1.0/800)` + `springBouncy/Snappy/Critical<T>` |
| `ui/components/Visuals.kt` | `StaggerIn` (ورود پله‌ای آیتم‌ها: 40ms × index، cap 320، fade 220 + اسلاید فنری) + `rememberCategoryAvatarStyle` (زوج تونال + آیکون per دسته) |
| `ui/components/StrengthRing.kt` | حلقهٔ قدرت 270° (شروع 135°، stroke 10٪ قطر، StrokeCap.Round، sweepGradient primary→tertiary→primary، snap بدون reduced-motion) |

## ۳. تغییرات صفحه‌به‌صفحه
- **VaultList:** `VaultHeroCard` جدید — گرادیان primaryContainer→secondaryContainer، واترمارک قلعه (96dp، آلفا 0.9)، trust chip («رمزنگاری‌شده و فقط روی همین دستگاه» + آیکون سپر)؛ هر ردیف → Card با `surfaceContainerLow`، آواتار دسته‌ای 44dp، قلب علاقه‌مندی با فنر bouncy، `pressScale`؛ `StaggerIn` روی هر دو شاخهٔ grid/linear.
- **Generator:** حلقهٔ قدرت + لیبل کنار رمز تولیدشده (`meterScore` 0..100 از `PasswordStrengthMeter`؛ <28 ضعیف / <50 قابل‌قبول / ≥50 خیلی‌خوب — هم‌باند با domain)؛ دکمه‌ها weight وزن‌دار + pressScale.
- **EntryDetail:** بخش رمز → Card با reveal انیمیشنی (`AnimatedContent` slide+fade) و آیکون‌های show/hide و copy با pressScale؛ username/email کارت‌بندی‌شده.
- **Unlock:** پس‌زمینهٔ گرادیان عمودی (background→surfaceContainerLow) + دیسک لوگو (148dp primaryContainer دایره‌ای + قلعه 112dp).
- **Setup:** همان دیسک لوگو (124dp/96dp) برای هم‌خوانی برند.

## ۴. دروازه‌های کیفیت
- **compile** سبز (compileDebugKotlin) — چند نوبت فیکس (نام فایل `Motion.kt`→`DezhMotion.kt` برای `MatchingDeclarationName`، MagicNumber→constant، `CyclomaticComplexMethod` → جداسازی `familyFor`/`styleFor`).
- **تست‌ها: 181/181 سبز** (دستهٔ C: 151 + دستهٔ AB: 30؛ هر دو باند پس از تغییرات UI دوباره اجرا شد).
- **detekt: صفر مورد** • **lintDebug: سبز**.
- **assembleRelease + R8** سبز (6m16s) → امضا + apksigner verify ✓.
- نکتهٔ محیطی: یک بار daemon OOM (دستهٔ تست بزرگ + بازسازی کش Robolectric) → سندباکس ~۸ دقیقه قفل شد؛ ادامه با دسته‌های ≤۳ پکیج و `--stop` بین runها.

## ۵. قواعد رعایت‌شده
- همهٔ حرکت‌ها gated با `LocalMotionEnabled` (reduced-motion) • بدون business logic در Composable • فنر برای spatial، tween فقط fade • بدون تغییر نسخه • بدون secret در navigation • بدون ادعای امنیتی مطلق.
