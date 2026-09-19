#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# دژ پاسارگاد — راه‌اندازی محیط build (ماشین تازه / CI)
# JDK 17 (Temurin) + Android SDK (platform android-37.0) + Gradle از Wrapper
# ---------------------------------------------------------------------------
set -euo pipefail

ENV_DIR="${DEZH_ENV_DIR:-$HOME/.cache/env}"
SDK="$ENV_DIR/android-sdk"

mkdir -p "$ENV_DIR"

# --- JDK 17 -----------------------------------------------------------------
if [ ! -x "$ENV_DIR/jdk17/bin/java" ]; then
  echo ">> Installing Temurin JDK 17 ..."
  curl -fsSL -o "$ENV_DIR/jdk17.tar.gz" \
    "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
  mkdir -p "$ENV_DIR/jdk17"
  tar -xzf "$ENV_DIR/jdk17.tar.gz" -C "$ENV_DIR/jdk17" --strip-components=1
  rm -f "$ENV_DIR/jdk17.tar.gz"
fi

# --- Android cmdline-tools ----------------------------------------------------
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo ">> Installing Android cmdline-tools ..."
  curl -fsSL -o "$ENV_DIR/clt.zip" \
    "https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip" ||
    curl -fsSL -o "$ENV_DIR/clt.zip" \
      "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  mkdir -p "$SDK/cmdline-tools"
  unzip -q "$ENV_DIR/clt.zip" -d "$SDK/cmdline-tools"
  rm -f "$ENV_DIR/clt.zip"
  mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
fi

export JAVA_HOME="$ENV_DIR/jdk17"
export ANDROID_HOME="$SDK"
export PATH="$JAVA_HOME/bin:$SDK/cmdline-tools/latest/bin:$PATH"

# --- SDK packages -------------------------------------------------------------
# توجه: پلتفرم API 37 با شناسهٔ «android-37.0» منتشر می‌شود (نه android-37).
# نکته: در حالت `set -o pipefail`، پایان کار sdkmanager سیگنال SIGPIPE را به `yes`
# می‌دهد (exit 141)؛ به همین دلیل خطا را اینجا می‌پذیریم.
yes | sdkmanager --licenses >/dev/null 2>&1 || true
sdkmanager "platform-tools" "platforms;android-37.0" "platforms;android-36" "build-tools;36.0.0"

# --- env.sh برای sessionهای بعدی ---------------------------------------------
cat > "$ENV_DIR/env.sh" <<EOF
export JAVA_HOME=$ENV_DIR/jdk17
export ANDROID_HOME=$SDK
export GRADLE_USER_HOME=\${DEZH_GRADLE_HOME:-$ENV_DIR/gradle-home}
export PATH="\$JAVA_HOME/bin:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$PATH"
EOF

echo ">> Done. source $ENV_DIR/env.sh سپس: ./gradlew :app:assembleDebug"
