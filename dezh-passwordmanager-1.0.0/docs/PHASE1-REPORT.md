# گزارش فاز ۱ — دژ پاسارگاد (Dezh-e Pasargad)

> تاریخ: 2026-09-16 · وضعیت گیت فاز ۱: **سبز — project skeleton واقعاً build می‌شود**
> هیچ قابلیت محصولی (vault، رمزنگاری، دیتابیس) در این فاز پیاده‌سازی نشده است — طبق تعریف فاز.

---

## ۱) Environment Report

| مورد | مقدار |
|---|---|
| سیستم‌عامل | Debian GNU/Linux 13 (trixie) — kernel `6.1.158+ x86_64` |
| سخت‌افزار سندباکس | 2 vCPU، ~1.9GB RAM، ~19GB فضای آزاد |
| Agent/Coding Environment | **Arena.ai Agent Mode** روی سندباکس E2B (شناسه sandbox: `E2B_SANDBOX=true`) — Claude Code CLI نصب نیست و `~/.claude` وجود ندارد |
| محل صحیح نصب Skillها | چون Agent فعلی فهرست skill سراسری ندارد، Skillها به‌صورت **پروژه‌محور** در `dezh-pasargad/.claude/skills/` نصب شدند (کنوانسیون Agent Skills/‎Claude Code در سطح پروژه) تا هر Agent سازگاری بتواند از آن‌ها استفاده کند و همراه repository بمانند |
| JDK | **Temurin OpenJDK 17.0.20.1** (قبل از آن فقط OpenJDK 11 سیستم موجود بود؛ Debian 13 در apt فقط openjdk-21 دارد → JDK 17 از Adoptium نصب شد تا دقیقاً baseline رعایت شود) |
| Android SDK | cmdline-tools + platform-tools + `platforms;android-36` و `platforms;android-37.0` + `build-tools;36.0.0` — نصب در `/home/user/.cache/env/android-sdk` |
| Gradle | **9.6.0** از طریق Wrapper استاندارد (`gradle/wrapper/gradle-wrapper.properties`) |
| شبکه | دسترسی به dl.google.com، repo1.maven.org، services.gradle.org و GitHub تأیید شد |
| npm / npx | **استفاده نشد** (طبق قوانین پروژه؛ اجباری هم نبود) |

نکات محیطی مهم:
- مسیر `/opt` در این سندباکس اجازهٔ نوشتن ندارد و `/tmp` یک tmpfs ۹۹۳ مگابایتی است → کل Toolchain در `/home/user/.cache/env` نصب شد (خارج از snapshot، داخل فایل‌سیستم بزرگ). برای بازیابی محیط در فازهای بعد: `scripts/setup-dev-env.sh`.
- `local.properties` به مسیر SDK سندباکس اشاره می‌کند و در `.gitignore` است.

---

## ۲) Skills Status

هر دو مخزن با `git clone --depth 1` دریافت و SKILL.md آن‌ها خوانده شد:

| Skill | محل نصب | محتوا (خلاصه) |
|---|---|---|
| `claude-android-ninja` (Drjacky) | `.claude/skills/claude-android-ninja/` | راهنمای ساخت اپ Android با Kotlin/Compose/MVVM/Nav3/Room 3 + قوانین سخت‌گیرانهٔ لایه‌ها + template نسخه‌ها (`assets/libs.versions.toml.template`) |
| `material-design-3-ui-skill` (skydashnet) | `.claude/skills/material-design-3-ui/` | سیستم طراحی M3 به‌عنوان زنجیرهٔ token→component→state + referenceهای progressive-disclosure |

Referenceهایی که طبق قانون «فقط معماری و build» مطالعه شد:
- `references/architecture.md` — قوانین لایه‌ها و جریان وابستگی (مبنای لایه‌بندی ما)
- `references/gradle-setup.md` (بخش‌های Built-in Kotlin / AGP 9 / KSP) — نکات کلیدی: حذف `org.jetbrains.kotlin.android`، الزام KSP ≥ 2.3.10 روی AGP 9، تنظیم compilerOptions از طریق `tasks.withType<KotlinCompile>`
- `references/android-navigation-quick.md` — الگوی رسمی Nav3 (‌`NavKey` + `rememberNavBackStack` + `NavDisplay` + `entryProvider`)
- `references/workflows.md` + `INDEX.md` — مسیر bootstrap پروژهٔ نو
- `assets/libs.versions.toml.template` و `assets/settings.gradle.kts.template` — صرفاً به‌عنوان مرجع تطبیق نسخه‌ها (جایگزین catalog پروژه نشد — مطابق قانون خود skill)

سایر referenceها (security، i18n، performance و …) عمداً باز نشده‌اند تا در فازهای مربوطه خوانده شوند.

---

## ۳) Toolchain Compatibility Report

روش‌شناسی: استعلام مستقیم از **Google Maven (`dl.google.com/android/maven2`)**، **Maven Central (`repo1.maven.org`)**، **`services.gradle.org`** و **release notes رسمی AGP 9.4.0** در developer.android.com — نه حدس.

### نتیجهٔ تطبیق با baseline

| مؤلفه | baseline | وضعیت | نسخهٔ نهایی | دلیل |
|---|---|---|---|---|
| AGP | 9.4.0 | ✅ موجود و stable | **9.4.0** | آخرین stable در Google Maven؛ max API = 37 |
| Gradle | 9.6.0 | ✅ | **9.6.0** | حداقل و پیش‌فرضِ رسمی AGP 9.4 (9.7.1 هم موجود است ولی به baseline وفادار ماندیم) |
| JDK | 17 | ✅ | **17 (Temurin 17.0.20.1)** | حداقل/پیش‌فرض رسمی AGP 9.4 |
| Kotlin | 2.4.20 | ✅ | **2.4.20** | آخرین stable در Maven Central؛ با **Built-in Kotlin** AGP 9 (پلاگین `org.jetbrains.kotlin.android` برای AGP ≥ 9.0 deprecated است و اعمال نشد) |
| KSP | (الزامی) | ✅ | **2.3.12** | خط KSP2؛ از KSP 2.3.0 نسخهٔ KSP از Kotlin مستقل شده؛ حداقل 2.3.10 برای سازگاری R-class با Built-in Kotlin AGP 9 |
| Compile/Target SDK | 37 | ✅ | **37** | پلتفرم با شناسهٔ جدید `platforms;android-37.0` نصب شد (خانوادهٔ 37.0/37.1/37.2) |
| Min SDK | 29 | ✅ | **29** | — |
| Compose BOM | 2026.08.00 | ✅ | **2026.08.00** | → material3 1.4.0، ui 1.12.0 (نسخهٔ جدیدتر 2026.09.00 موجود است؛ به baseline وفادار ماندیم) |
| Navigation 3 | 1.1.7 | ✅ | **1.1.7** | آخرین stable (1.2 هنوز فقط alpha/beta/rc است — استفاده نشد) |
| Room | 3.0.3 | ❌ **موجود نیست** | **2.8.5** (فقط در catalog) | artifactهای `3.0` و `3.0-rc01` روی Google Maven در metadata فهرست شده‌اند اما واقعاً منتشر نشده‌اند (POM/module → HTTP 404). آخرین stable واقعی = 2.8.5. طبق قانون «جدیدترین STABLE سازگار» 2.8.5 پین شد؛ ارتقا به Room 3 stable بلافاصله پس از انتشار رسمی در فاز ۲ انجام می‌شود |
| Lifecycle | — | ✅ | **2.11.0** (viewmodel-compose, runtime-compose) | آخرین stable |
| Activity Compose | — | ✅ | **1.13.0** | آخرین stable |
| Core KTX | — | ✅ | **1.19.0** | آخرین stable |
| Coroutines | — | ✅ | **1.11.0** (android) | آخرین stable |
| kotlinx-serialization-core | — | ✅ | **1.11.0** | برای کلیدهای type-safe در Nav3 |
| Material 3 | — | ✅ | **1.4.0** (از BOM) | — |
| Hilt | «فقط در صورت نیاز واقعی» | ⏸️ **فاز ۱: اعمال نشد** | — | اسکلت تک‌ماژولی با یک صفحه نیازی به DI framework ندارد؛ تصمیم در فاز ۲ با معیار «تعداد Repository/DAO/UseCase» دوباره ارزیابی می‌شود |

نتیجهٔ تجربی (خروجی واقعی `:app:dependencies`): `compose-bom:2026.08.00`، `material3-android:1.4.0`، `ui-android:1.12.0`، `navigation3-runtime/ui-android:1.1.7`، `lifecycle-viewmodel-compose-android:2.11.0`، `core-ktx:1.19.0`، `activity-compose:1.13.0`، `kotlinx-coroutines-android:1.11.0`، `kotlin-stdlib:2.4.20` — سازگاری **با build موفق اثبات شد**.

---

## ۴) Architecture Decision

**Compose-first، تک‌ماژول با لایه‌بندی package-oriented** (طبق قانون «multi-module complexity غیرضروری ممنوع»؛ در آینده در صورت نیاز، استخراج core/* بدون تغییر قوانین لایه‌ها ممکن است):

```
Presentation (Compose Screen — بدون business logic)
        ↓
ViewModel (StateFlow + Coroutines)
        ↓
Use Cases / Domain (interfaceهای Repository اینجا تعریف می‌شوند)
        ↓
Repository (تنها مرز داده)
        ↓
Data (Room 3 + Encrypted Local Storage)
```

تصمیم‌های قطعی‌شده:
- **UI**: Jetpack Compose + Material 3 (semantic colorScheme roles) — بدون XML UI، بدون ViewBinding، بدون Fragment-based screen
- **ناوبری**: Navigation 3 stable 1.1.7 با کلیدهای type-safe (`@Serializable` + `NavKey`) — قرارداد کامل ۱۷ مسیر آینده در `DezhDestinations.kt` تعریف شد و فقط `Startup` سیم‌کشی شده است
- **قانون امنیتی ناوبری**: هیچ secret/دادهٔ حساسی از طریق navigation arguments منتقل نمی‌شود (در KDoc قرارداد ثبت شد)
- **حالت**: StateFlow + Coroutines؛ ViewModel لایهٔ تنها دارندهٔ منطق presentation
- **امنیت**: Android Keystore + رمزنگاری فقط در لایهٔ `security`/`cryptography` (فازهای بعد) — در UI مطلقاً نه
- **RTL/فارسی**: از روز اول — `android:supportsRtl="true"`، زبان پیش‌فرض resourceها فارسی (`values/`) و انگلیسی در `values-en/`؛ RTL با سیستم محور است نه force شده
- **پشتیبان‌گیری**: `allowBackup="false"` به‌صورت پیش‌فرض امنیتی تا وقتی backup رمزنگاری‌شدهٔ خودمان آماده شود
- **MainActivity بسیار کوچک** (~۲۰ خط): فقط `enableEdgeToEdge + setContent { DezhTheme { DezhApp() } }`
- **Gradle**: version catalog (`gradle/libs.versions.toml`)، configuration-cache، build-cache؛ KSP اعمال شده (آمادهٔ Room در فاز ۲)

---

## ۵) Package Structure (واقعاً ساخته‌شده)

```
com.pasargad.dezh
├── MainActivity.kt              ← نقطهٔ ورود (حداقلی)
├── navigation/                  ← DezhApp.kt (NavDisplay) + DezhDestinations.kt (۱۷ مسیر)
├── presentation/
│   └── startup/                 ← StartupScreen.kt (صفحهٔ verify فاز ۱ + Preview روشن/تاریک)
├── ui/theme/                    ← Color.kt (پالت پاسارگاد: طلایی/لاجوردی) + Theme.kt + Type.kt
├── domain/                      ← (فاز بعد — Use Cases, Repository interfaces)
├── data/                        ← (فاز بعد — Room 3, Repository impl)
├── security/                    ← (فاز بعد — Android Keystore, master key)
├── cryptography/                ← (فاز بعد — encrypt/decrypt)
├── backup/                      ← (فاز بعد — backup/restore رمزنگاری‌شده)
├── settings/                    ← (فاز بعد)
├── generator/                   ← (فاز بعد — password generator)
└── shared/                      ← (فاز بعد — utilities مشترک)
```

(پوشه‌های خالی با `.gitkeep` حفظ شده‌اند.)

---

## ۶) Initial Build Status — دستورها، خطاها و رفع آن‌ها

| # | فرمان | نتیجه |
|---|---|---|
| ۱ | `./gradlew :app:assembleDebug` (بار اول) | ❌ کرنل daemon را کشت — `dmesg`: `Out of memory: Killed process (java)` — علت ریشه‌ای: heap 1400m در محیط ~1.9GB RAM (سهم آزاد واقعی ~1GB) |
| ۲ | همان فرمان با heap 640m | ❌ دوباره OOM در حین دانلود/لود pluginها (شواهد dmesg مجدد) |
| ۳ | پس از آزادسازی daemon زنده‌مانده + JVM فشرده (heap 640m، metaspace 288m، SerialGC، `kotlin.compiler.execution.strategy=in-process`) | ✅ **BUILD SUCCESSFUL in 1m 50s** → `dezh-pasargad-debug.apk` |
| ۴ | `./gradlew :app:lintDebug` | ❌ `OutOfMemoryError: Metaspace` در `lintAnalyzeDebug` (تحلیل Lint داخل daemon اجرا می‌شود و class-loading سنگینی دارد) |
| ۵ | پس از جابه‌جایی بودجهٔ JVM (heap 768m / metaspace 512m) و غیرفعال‌سازی `lintVitalRelease` تکراری (`checkReleaseBuilds=false`؛ دروازهٔ اصلی همان `lintDebug` است) | ✅ **LINT: BUILD SUCCESSFUL** — **0 errors, 3 warning** (`AndroidGradlePluginVersion`، `GradleDependency`، `DataExtractionRules` — همگی اطلاع‌رسانی/بهترین‌رویه، عمداً باز گذاشته شده‌اند: پیشنهاد نسخهٔ جدیدتر که به عمد رد شده و یادآور dataExtractionRules برای فاز امنیت) |
| ۶ | `./gradlew :app:assembleRelease` | ✅ **BUILD SUCCESSFUL** → `dezh-pasargad-release-unsigned.apk` |
| ۷ | `./gradlew :app:assembleDebug` (تأیید مجدد وضعیت نهایی) | ✅ BUILD SUCCESSFUL (37 task, up-to-date) |

**Artifactهای تولیدشده:**
- `app/build/outputs/apk/debug/dezh-pasargad-debug.apk` (~30MB)
- `app/build/outputs/apk/release/dezh-pasargad-release-unsigned.apk` (~23MB)
- `app/build/reports/lint-results-debug.html` + `.sarif`

توضیح نام release APK: به لطف `base { archivesName.set("dezh-pasargad") }` خروجی الان `dezh-pasargad-release-unsigned.apk` است و پس از افزودن signing config در فاز hardening دقیقاً `dezh-pasargad-release.apk` خواهد شد.

تنظیمات حافظهٔ فعلی (`gradle.properties`) عمداً برای سندباکس ۲GB فشرده شده است؛ روی ماشین توسعهٔ معمول می‌توان به `-Xmx2g -XX:MaxMetaspaceSize=768m` افزایش داد (هیچ تأثیری روی صحت build ندارد).

---

## ۷) Known Risks

1. **Room 3 هنوز منتشر نشده** — catalog روی 2.8.5 پین است؛ فاز ۲ باید ابتدا انتشار stable Room 3 را چک و در صورت وجود ارتقا دهد (migration Room 2→3 در referenceهای skill موجود است). برای همین فاز ۱ هیچ کد دیتابیسی نوشته نشد.
2. **Release APK فعلاً unsigned است** — signing config + R8/minify + سخت‌سازی proguard در فاز hardening.
3. **آیکون لانچر موقتی** — از `@android:drawable/sym_def_app_icon` استفاده شده تا lint تمیز بماند؛ آیکون adaptive برند در فاز UI ساخته می‌شود.
4. **سقف RAM سندباکس (~1.9GB)** — ریسک OOM در taskهای سنگین (lint/R8 آینده) وجود دارد؛ تنظیمات فعلی تست شده، اما اگر فازهای بعد به R8/full-lint خوردند ممکن است نیاز به `org.gradle.workers.max=1` یا اجرای جداگانهٔ taskها باشد.
5. **`dataExtractionRules`** (هشدار lint) — برای API 31+ باید صراحتاً تعریف شود؛ در فاز امنیت/بکاپ همراه سیاست backup رمزنگاری‌شده تعیین تکلیف می‌شود.
6. **Hilt به تعویق افتاد** — اگر در فاز ۲ تعداد وابستگی‌های تزریقی زیاد شود، افزودن Hilt (با KSP) هزینهٔ بازسازی کمی خواهد داشت؛ تصمیم با معیار «نیاز واقعی» گرفته می‌شود.
7. **نسخه‌های جدیدتر موجود ولی عمداً رد شده** — Gradle 9.7.1 و Compose BOM 2026.09.00 و lifecycle/activity جدیدترها؛ طبق قانون baseline، بدون درcompatibility واقعی ارتقا نمی‌دهیم (در فازهای بعد با چک مجدد matrix ارتقا خواهیم داد).
8. **پلتفرم SDK با شناسهٔ minor جدید** (`android-37.0` به‌جای `android-37`) — در CI/محیط‌های دیگر باید همین شناسه نصب شود (در `scripts/setup-dev-env.sh` ثبت شده).

---

## ۸) چک‌لیست خروجی فاز ۱

- [x] پروژهٔ skeleton ساخته شد و **واقعاً build می‌شود** (debug + release)
- [x] `settings.gradle.kts` / root `build.gradle.kts` / `app/build.gradle.kts` تکمیل
- [x] `gradle/libs.versions.toml` (version catalog کامل + یادداشت Room)
- [x] Compose + Material 3 فعال (`buildFeatures.compose` + پلاگین compose)
- [x] ساختار packageها (presentation/domain/data/security/cryptography/backup/settings/generator/shared/navigation/ui)
- [x] Navigation 3 آماده: `DezhApp` + قرارداد ۱۷ مسیر (فقط Startup سیم‌کشی شده)
- [x] صفحهٔ verify ساده (StartupScreen — بدون هیچ منطق کسب‌وکار)
- [x] اولین build موفق + re-verify
- [x] Lint اولیه: 0 errors / 3 warning مستندشده
- [x] Skillها دریافت، مطالعه (فقط معماری/build) و در `.claude/skills/` نصب شدند
- [x] بدون npm/npx · بدون XML UI · بدون Fragment · بدون secret در navigation
