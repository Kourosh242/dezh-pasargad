# دژ پاسارگاد — سورس اندروید

ماژول Gradle اپ **دژ پاسارگاد** (نسخهٔ 1.0.1). مستندات کاربری و راهنمای کامل در [README ریشهٔ مخزن](../README.md) است.

## پیش‌نیاز

- JDK 17
- Android SDK (پلتفرم `android-37.0` و build-tools 36.0.0)

```bash
# راه‌اندازی اختیاری روی ماشین تازه
scripts/setup-dev-env.sh
source "$HOME/.cache/env/env.sh"
echo "sdk.dir=$ANDROID_HOME" > local.properties

./gradlew :app:testDebugUnitTest
./gradlew :app:detekt :app:lintDebug
./gradlew :app:assembleRelease
```

خروجی release بدون امضا است؛ امضا را با keystore شخصی خودتان **خارج از مخزن** انجام دهید. فایل‌های `*.jks` / `*.keystore` / `local.properties` نباید commit شوند.

## ساختار

```
app/src/main/java/com/pasargad/dezh/
  navigation/ presentation/ domain/ data/
  security/ cryptography/ backup/ generator/ settings/ di/ ui/
art/          آیکون و اسکرین‌شات‌ها
docs/         معماری فنی
licenses/     پروانهٔ فونت وزیرمتن (OFL)
```

جزئیات لایه‌ها و مدل امنیتی: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
