# Agent Skills — پک یکپارچهٔ دژ پاسارگاد (تک‌فایل)

> این فایل جایگزین clone کامل دو مخزن skill است (حذف‌شده برای تمیزکاری). منابع اصلی:
> - `claude-android-ninja` (DrJacky, Apache-2.0) — skill کدنویسی Android
> - `material-design-3-ui-skill` (skydashnet, v1.1.0) — skill طراحی M3
> بازگرداندن نسخهٔ کامل: `git clone --depth 1 https://github.com/Drjacky/claude-android-ninja.git` و `https://github.com/skydashnet/material-design-3-ui-skill.git`

---

# بخش ۱ — Android Coding Skill (claude-android-ninja)

**پوشش:** ساخت/mهاجرت اپ با Kotlin، Compose، MVVM، Room (KSP, Flow/suspend DAOs)، Navigation3، Gradle، version-catalog. **نیست:** iOS، Flutter، RN، backend.

**Context ladder:** همین فایل → quick rules بخش ۲ → فقط بخش موردنیاز reference کامل.

## Quick Reference (مسیریابی وظایف)

| وظیفه | قاعدهٔ خلاصه (کامل در repo مرجع) |
|---|---|
| پروژهٔ نو / bootstrap | قالب settings.gradle.kts + libs.versions.toml + convention plugins؛ در این پروژه: تک‌ماژول طبق فاز ۱ |
| version catalog / آپدیت وابستگی | فقط آخرین **STABLE**؛ alpha/beta/RC ممنوع مگر ضرورت فنی؛ دلیل تغییر ثبت شود |
| لایه‌ها / DI / Repository | ۴ لایه Presentation/Domain/Data/UI؛ جریان وابستگی یک‌طرفه؛ offline-first: Room منبع حقیقت |
| Compose patterns | Screen + ViewModel + UiState؛ StateFlow؛ Channel برای one-shot events |
| Navigation3 | type-safe `NavKey` + `rememberNavBackStack` + `NavDisplay` + `entryProvider` |
| Theming M3 | فقط semantic color roles (بدون رنگ hardcode)؛ جفت fill/on* کامل؛ dynamic color API31+ |
| Coroutines | StateFlow برای state؛ Dispatchers صریح؛ KDF/IO خارج از Main |
| Gradle / R8 / performance | بعد از هر تغییر toolchain/ماژول: `./gradlew help` سپس `:app:assembleDebug` |
| Testing | Turbine برای Flow؛ Fakes به‌جای MockK زائد؛ Compose UI test برای مسیرهای بحرانی |
| i18n / RTL | strings در resourceها؛ RTL سیستم‌محور؛ plurals واقعی |
| Security | Play Integrity، رمزنگاری، biometrics → فقط APIهای رسمی؛ secret در log ممنوع |
| Migration (Room 2→3، API 37، Nav2→3) | مرحله‌به‌مرحله + build بین هر گام |

## قواعد سخت (استخراج بخش‌های کلیدی referenceها — تجربه‌شده در فازهای ۱–۳)

- **Built-in Kotlin (AGP 9+):** `org.jetbrains.kotlin.android` اعمال **نکن** (deprecated)؛ فقط `org.jetbrains.kotlin.plugin.compose` هم‌نسخه با Kotlin؛ `KotlinAndroidProjectExtension` ثبت نمی‌شود → تنظیمات از `tasks.withType<KotlinCompile>().configureEach { compilerOptions { … } }`.
- **KSP:** خط KSP2 مستقل از نسخهٔ Kotlin (از 2.3.0)؛ روی AGP 9 حداقل 2.3.10؛ kapt ممنوع.
- **Gradle:** AGP 9.4 ↔ Gradle 9.6.0 ↔ JDK 17 ↔ Build Tools 36.0.0 ↔ max API 37.
- **Navigation3 invariants:** secret در navigation arguments ممنوع؛ `sceneStrategies` (نه `sceneStrategy` deprecated)؛ Nav3 1.2-alpha روی پین stable ممنوع؛ predictive back پیرو back stack.
- **Architecture:** UI هیچ business logic ندارد؛ ViewModel تنها دارندهٔ state صفحه؛ Repository تنها مرز داده؛ dependency direction یک‌طرفه.
- **Room:** DAO فقط `suspend`/`Flow`؛ schema export + migration نسخه‌دار؛ بدون destructive fallback.
- **Verification ritual:** پس از هر تغییر build: `assembleDebug` + `testDebugUnitTest` + `lintDebug` + `detekt` (زنجیرهٔ رسمی این پروژه).

---

# بخش ۲ — Material Design 3 UI/UX Skill

**هدف:** رابط‌هایی که *مانند* M3 رفتار می‌کنند، نه فقط شبیه Google به‌نظر برسند.
زنجیره: `user goal → information architecture → hierarchy → adaptive layout → semantic tokens → components → states → interaction → motion → accessibility`.

## MUST
- شروع از وظیفه/محتوا/سلسله‌مراتب، نه استایل
- فقط semantic design tokens (نه رنگ/اندازهٔ پراکنده)
- انتخاب component بر اساس purpose/behavior، نه ظاهر
- پوشش stateها: default/pressed/focus/selected/disabled/loading/error/empty/success
- طراحی برای window واقعی (نه device فرضی)؛ respect سیستم‌بار/IME/cutout
- تفکیک اقدام primary/secondary/destructive

## MUST NOT
- «Materialize» فقط با گوشهٔ گرد و رنگ پاستلی
- رنگ hardcode به‌جای role
- انتقال معنا فقط با رنگ
- کارت دور همه‌چیز؛ ماکزیمم radius روی همه‌چیز
- FAB برای اقدام مخرب/جزئی؛ chip به‌جای دکمهٔ اصلی؛ tab برای مقصدهای بی‌ربط
- قربانی‌کردن accessibility برای ترکیب بصری؛ کشیدن layout موبایل روی صفحهٔ بزرگ

## Workflow (۱۰ گام)
1. درک محصول (goal/action/stateها/windowها) → 2. IA (گروه‌بندی، حذف دوباره‌کاری) → 3. ساختار adaptive (list-detail/pane) → 4. theme semantically (tokens→roles→component tokens) → 5. انتخاب component (semantic purpose، emphasis، immediate vs transactional) → 6. states و feedback (خطا باید مسیر ریکاوری داشته باشد) → 7. accessibility (الزام انتشار، نه polish نهایی) → 8. motion فقط برای توضیح تغییر state → 9. Expressive محدود و هدفمند → 10. audit ضد الگوها.

## Self-audit (امتیاز ۰/۱/۲ per مورد)
Task clarity · Information hierarchy · Component semantics · Token discipline · Adaptive behavior · States & feedback · Accessibility · Expressive restraint — **هر ۰ در semantics/states/accessibility = تأییدنشده.**

## Handoff semantically (نمونهٔ رسمی)
`screen: surface · primary text: onSurface · secondary: onSurfaceVariant · CTA: filled button (primary/onPrimary) · secondary CTA: outlined · section: surfaceContainer · separator: outlineVariant · error: errorContainer/onErrorContainer`

## Compose (Android)
`androidx.compose.material3` · APIهای stable · roleها به‌جای رنگ خام · edge-to-edge و insets به‌عنوان concern لایهٔ layout.

---

# بخش ۳ — نگاشت قواعد به پروژهٔ دژ پاسارگاد (اجباری‌های local)

| قاعده | محل اجرا در repo |
|---|---|
| معماری لایه‌ای + ممنوعیت logic در Composable | `presentation/*` فقط UI؛ `domain/` UseCaseها؛ `data/` repos — بررسی خودکار در Security Review هر فاز |
| رمزنگاری | `cryptography/` (AES-256-GCM, PBKDF2, key wrap) + `security/` (Keystore, session, auto-lock) — سند: `docs/PHASE2-SECURITY-DESIGN.md` |
| فرمت داده | DPVG (payload رمز) / DPKW (keywrap) — versioned؛ تغییر = نسخهٔ جدید + Migration |
| Room | `data/vault/` — schema در `app/schemas`؛ `DatabaseMigrations.ALL`؛ بدون fallback مخرب |
| Theming | `ui/theme/` — پالت پاسارگاد (بخش ۲)؛ فقط `MaterialTheme.colorScheme` roles |
| Strings/fa-RTL | پیش‌فرض `values/` فارسی، `values-en/` انگلیسی؛ plurals واقعی؛ `supportsRtl` |
| کیفیت | `config/detekt/detekt.yml` + lint (abortOnError) + تست‌ها (`app/src/test`, `app/src/androidTest`) |
| محیط build | `scripts/setup-dev-env.sh` (JDK17 + SDK `platforms;android-37.0` + build-tools 36.0.0) |
| گزارش‌های فازها | `docs/PHASE{1,2,3}-REPORT.md` |

**پایان — هر Agent توسعه‌دهندهٔ این repo باید همین فایل + گزارش‌های فاز را baseline بداند.**
