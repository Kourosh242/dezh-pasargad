# دژ پاسارگاد (Dezh-e Pasargad)

مدیر رمز و دادهٔ امن فارسی‌محور — Android · Kotlin · Jetpack Compose · Material 3 · Navigation 3 · Room · Android Keystore.

## وضعیت فعلی

**🎉 نسخهٔ 1.0.0 — Release APK:** `dezh-pasargad-release.apk` (امضای شخصی، R8، ۳.۶۹MB، آیکون اختصاصی دژ، فونت Vazirmatn، بدون هیچ permission) · گزارش انتشار: [`docs/FINAL-RELEASE-REPORT.md`](docs/FINAL-RELEASE-REPORT.md) · گزارش بازطراحی UI/UX: [`docs/UIUX-OVERHAUL-REPORT.md`](docs/UIUX-OVERHAUL-REPORT.md) · گزارش رفع اشکالات ۱.۰.۰: [`docs/BUGFIX-1.0.0-REPORT.md`](docs/BUGFIX-1.0.0-REPORT.md) · خودبازرسی دور ۱ (۴ باگ): [`docs/SELF-AUDIT-REPORT.md`](docs/SELF-AUDIT-REPORT.md) · دور ۲ (کلیپ‌بورد امنیتی + نوار پشتیبان): [`docs/SELF-AUDIT-2-REPORT.md`](docs/SELF-AUDIT-2-REPORT.md)

**فاز ۷ (QA و Audit جامع) تکمیل شد** — ۱۶۴/۱۶۴ تست، R8 release فعال (APK ۳.۱۰MB)، clean build، security/performance/a11y review. مستندات فنی کامل: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) · گزارش QA: [`docs/PHASE7-QA-REPORT.md`](docs/PHASE7-QA-REPORT.md)

**فاز ۳ تکمیل شد** — Vault CRUD روی Room با **encrypted record payload** (AES-256-GCM با DEK نشست)، schema export، migration strategy و ۸۸/۸۸ تست. گزارش‌ها: [`docs/PHASE3-REPORT.md`](docs/PHASE3-REPORT.md) · [`docs/PHASE2-REPORT.md`](docs/PHASE2-REPORT.md) · [`docs/PHASE1-REPORT.md`](docs/PHASE1-REPORT.md)

| مؤلفه | نسخه |
|---|---|
| AGP | 9.4.0 (Built-in Kotlin) |
| Gradle | 9.6.0 |
| JDK | 17 |
| Kotlin | 2.4.20 |
| KSP | 2.3.12 |
| Compose BOM | 2026.08.00 (Material 3) |
| Navigation 3 | 1.1.7 |
| Room (catalog) | 2.8.5 — ارتقا به Room 3 stable پس از انتشار |
| Robolectric | 4.17 (تست‌های DB روی SDK 35) |
| compile/target/min SDK | 37 / 37 / 29 |
| detekt | 1.23.8 |

## Build & Quality

```bash
./gradlew :app:assembleDebug     # خروجی: app/build/outputs/apk/debug/dezh-pasargad-debug.apk
./gradlew :app:testDebugUnitTest # 88 test (شامل DB tests با Room واقعی/Robolectric)
./gradlew :app:detekt            # static analysis
./gradlew :app:lintDebug         # دروازهٔ کیفیت Lint
./gradlew :app:compileDebugAndroidTestKotlin  # gate تست‌های UI (اجرا با emulator)
./gradlew :app:assembleRelease   # فعلاً unsigned؛ signing در فاز hardening
```

نیازمندی: JDK 17 و Android SDK با `platforms;android-37.0` + `build-tools;36.0.0` (اسکریپت کمکی: `scripts/setup-dev-env.sh`).

## ساختار

تک‌ماژول (`:app`) با لایه‌بندی package-oriented: `presentation → navigation/ui`، و پکیج‌های آمادهٔ `domain` / `data` / `security` / `cryptography` / `backup` / `settings` / `generator` / `shared`. قرارداد کامل مسیرهای Navigation 3 در `navigation/DezhDestination.kt`.

## مستندات

- `docs/AGENT-SKILLS.md` — پک یکپارچهٔ Skillها (Android coding + Material 3) در یک فایل
- `docs/UI-UX-DESIGN.md` — مرجع کامل طراحی UI/UX (tokens، صفحه‌ها، stateها، a11y)
- `docs/PHASE1-REPORT.md` · `docs/PHASE2-REPORT.md` · `docs/PHASE2-SECURITY-DESIGN.md` · `docs/PHASE3-REPORT.md` · `docs/PHASE4-REPORT.md` · `docs/PHASE5-REPORT.md` · `docs/PHASE6-REPORT.md` · `docs/PHASE7-QA-REPORT.md`
- `docs/ARCHITECTURE.md` — مستندات فنی جامع (معماری، امنیت، crypto، schema، backup، build، تست)
- [گزارش بازطراحی UI/UX](docs/UI-REDESIGN-REPORT.md) — بازطراحی M3 Expressive (1.0.0)
- [دور اصلاح UI بر پایهٔ رقبا + حفاظت از عکاسی](docs/COMPETITIVE-UI-PASS.md) (1.0.0)
