#!/bin/bash
set -euo pipefail

ADB="${ADB:-adb}"
APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"

if ! $ADB devices | grep -qw device$; then
  echo "FEHLER: Kein Gerät per adb erreichbar."
  exit 1
fi

echo "Installing $APK..."
if ! $ADB install -r "$APK"; then
  echo "WARNUNG: Falls INSTALL_FAILED_UPDATE_INCOMPATIBLE aufgetreten ist, verwenden Sie einen anderen Signing-Key als der, mit dem die App zuvor installiert wurde."
  echo "BITTE DIE APP NIEMALS DEINSTALLIEREN, um den Fehler zu beheben! Eine Deinstallation löscht importierte Modelle und Vault-Zugriffsrechte."
  exit 1
fi

echo "Starting MainActivity..."
$ADB shell am start -n dev.s10e.companion/.MainActivity
