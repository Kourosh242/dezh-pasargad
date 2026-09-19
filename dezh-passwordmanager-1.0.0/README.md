# دژ پاسارگاد (Dezh-e Pasargad)

گاوصندوق رمز **آفلاین** و فارسی‌محور برای اندروید — رمزنگاری واقعی، بدون تبلیغ، بدون ردیابی.
Android · Kotlin · Jetpack Compose (Material 3) · Navigation 3 · Room · Android Keystore.

> **نسخهٔ 1.0.0** · ۲۰۹ تست سبز · detekt ۰ · lint سبز · release امضاشدهٔ R8 (~3.8MB)

## ویژگی‌ها

- 🔐 **رمزنگاری چندلایه:** PBKDF2-HMAC-SHA256 (۶۰۰k) → کلید دادهٔ تصادفی (DEK) → AES-256-GCM برای هر رکورد؛ کلید session فقط در RAM و با قفل صفر می‌شود (fail-closed)
- 🗄️ **گاوصندوق کامل:** رکوردها با عنوان/نام کاربری/ایمیل/رمز/یادداشت، دسته‌ها، علاقه‌مندی‌ها، جستجوی زنده (رمزگشایی در حافظه)، مرتب‌سازی — دیتابیس هیچ plaintext ای نگه نمی‌دارد
- 💾 **پشتیبان‌گیری رمزگذاری‌شده:** خروجی/ورودی از طریق SAF با envelope نسخه‌دار (AAD + checksum)؛ تشخیص جداگانهٔ رمز اشتباه از فایل خراب؛ restore با preview تغییرات و بدون overwrite خاموش
- 🎲 **رمزساز:** SecureRandom با قید طول/کلاس/حداقل + حلقهٔ قدرت زنده (entropy-محور، NIST 800-63B)
- 🔒 **قفل هوشمند:** auto-lock قابل تنظیم (بدون «هرگز» — fail-closed)، throttling نمایی تلاش نادرست، محافظت از پیش‌نمایش app-switcher (قابل خاموش‌کردن توسط کاربر: «حفاظت از عکاسی»)
- 🔄 **بررسی بروزرسانی + نصب درون‌اپی:** دستی و شفاف — خواندن آخرین Release از GitHub، دانلود APK درون اپ، اعتبارسنجی (package/versionCode/امضا) و نصب با `PackageInstaller` رسمی و پنجرهٔ تأیید خود اندروید (بدون هیچ تعامل با Google Play)
- 🌙 تم روشن/تیره/سیستم + ۵ اکسنت + مقیاس فونت · تقویم جلالی · RTL کامل · فونت Vazirmatn
- ♿ motion gated (reduced-motion) · لیبل‌های a11y فارسی · اهداف لمسی ۴۸dp

## معماری و امنیت

تک‌ماژول، لایه‌بندی‌شده: `presentation → domain → data/security/cryptography` — DI دستی (`AppContainer`)، بدون Hilt، بدون XML UI، بدون Fragment. طراحی کامل رمزنگاری: [`docs/PHASE2-SECURITY-DESIGN.md`](docs/PHASE2-SECURITY-DESIGN.md) · مستندات جامع: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) · ممیزی کامل نهایی: [`docs/FULL-AUDIT-REPORT.md`](docs/FULL-AUDIT-REPORT.md)

## بیلد

```bash
scripts/setup-dev-env.sh          # JDK17 + Android SDK (platforms;android-37.0 + build-tools;36.0.0)
source env.sh && echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:testDebugUnitTest  # 209 تست / 43 کلاس
./gradlew :app:detekt && ./gradlew :app:lintDebug
./gradlew :app:assembleRelease    # unsigned — امضا بیرون از بیلد با keystore شخصی
```

keystore عمداً خارج از این ریپوست و نباید داخل آن قرار بگیرد. برای بازبینی/توسعه با ابزارهای هوشمند، [`CLAUDE.md`](CLAUDE.md) را بخوانید.

## ساختار مخزن

```
app/src/main/java/com/pasargad/dezh/
  navigation/ presentation/ domain/ data/ security/ cryptography/ backup/ generator/ settings/ di/ ui/
docs/       گزارش کامل فازها، معماری، امنیت و خودبازرسی‌ها
scripts/    راه‌اندازی محیط بیلد
```

## مستندات

- [`CHANGELOG.md`](CHANGELOG.md) — تاریخچهٔ تغییرات
- [`CLAUDE.md`](CLAUDE.md) — راهنمای رویکرد برای ایجنت‌های کدنویس
- فازهای ساخت: `docs/PHASE1…7` · انتشار: [`docs/FINAL-RELEASE-REPORT.md`](docs/FINAL-RELEASE-REPORT.md)
- خودبازرسی‌ها: [`docs/SELF-AUDIT-REPORT.md`](docs/SELF-AUDIT-REPORT.md) · [`docs/SELF-AUDIT-2-REPORT.md`](docs/SELF-AUDIT-2-REPORT.md) · [`docs/FULL-AUDIT-REPORT.md`](docs/FULL-AUDIT-REPORT.md)
- دورهای UI: [بازطراحی M3](docs/UI-REDESIGN-REPORT.md) · [الگوی رقبا + حفاظت از عکاسی](docs/COMPETITIVE-UI-PASS.md) · [ناوبری پایین](docs/BOTTOMNAV-UI-PASS.md) · [رفع باگ بحرانی](docs/CRASHFIX-NAV-POLISH.md) · [زنده‌سازی](docs/ALIVE-UI-PASS.md)

## گردش انتشار

هر نسخه در GitHub Releases با **تگ بالاتر** (مثلاً `1.0.1`) منتشر می‌شود؛ کاربران نسخه‌های قبلی از داخل اپ («بررسی بروزرسانی») آن را می‌بینند و به صفحهٔ همان تگ هدایت می‌شوند. امضای همهٔ نسخه‌ها با یک keystore ثابت است (آپدیت درجا ممکن می‌ماند).
