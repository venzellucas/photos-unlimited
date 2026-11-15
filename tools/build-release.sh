#!/usr/bin/env bash
set -euo pipefail
: "${PHOTOS_KEYSTORE:?Set PHOTOS_KEYSTORE to your external signing keystore}"
: "${PHOTOS_SIGN_PASSWORD:?Supply PHOTOS_SIGN_PASSWORD securely in the environment}"
[[ -f "$PHOTOS_KEYSTORE" ]] || { echo 'Signing keystore not found.' >&2; exit 1; }
./gradlew --no-daemon --console=plain --max-workers=2 :app:assembleRelease :app:lintRelease
