#!/bin/bash
set -euo pipefail

git submodule update --init --recursive
bash scripts/build-llama.sh

./gradlew --no-daemon testDebugUnitTest assembleRelease

if [[ -z "${S10E_KEYSTORE:-}" ]] || [[ -z "${S10E_KEYSTORE_PASSWORD:-}" ]] || [[ -z "${S10E_KEY_ALIAS:-}" ]]; then
  echo "WARNUNG: S10E_KEYSTORE, S10E_KEYSTORE_PASSWORD oder S10E_KEY_ALIAS fehlen. Release-Build wird mit dem Debug-Key signiert."
fi
