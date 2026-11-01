#!/usr/bin/env bash
# Installiert Android-SDK und NDK für Builds auf der Jules-VM.
set -euo pipefail

export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
TOOLS_ZIP="commandlinetools-linux-13114758_latest.zip"

if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  tmp="$(mktemp -d)"
  curl -fsSL -o "$tmp/tools.zip" "https://dl.google.com/android/repository/$TOOLS_ZIP"
  unzip -q "$tmp/tools.zip" -d "$tmp"
  mv "$tmp/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -rf "$tmp"
fi

yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null || true
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" \
  "platform-tools" "platforms;android-37.0" "build-tools;37.0.0" \
  "ndk;29.0.14206865" "cmake;3.31.5"

echo "sdk.dir=$ANDROID_HOME" > local.properties
java -version
