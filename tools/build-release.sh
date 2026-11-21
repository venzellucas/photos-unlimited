#!/usr/bin/env bash
set -euo pipefail
: "${PHOTOS_KEYSTORE:?Set PHOTOS_KEYSTORE to your external signing keystore}"
: "${PHOTOS_SIGN_PASSWORD:?Supply PHOTOS_SIGN_PASSWORD securely in the environment}"
[[ -f "$PHOTOS_KEYSTORE" ]] || { echo 'Signing keystore not found.' >&2; exit 1; }
# Release contract: the tag passed in must equal "v" + versionName.
: "${RELEASE_TAG:?Set RELEASE_TAG, for example RELEASE_TAG=v1.1.0}"
: "${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}"
version=$(sed -n "s/.*versionName '\(.*\)'.*/\1/p" app/build.gradle)
if [[ "$RELEASE_TAG" != "v$version" ]]; then
  echo "RELEASE_TAG $RELEASE_TAG does not match versionName $version" >&2
  exit 1
fi
./gradlew --no-daemon --console=plain --max-workers=2 :app:assembleRelease :app:lintRelease
mkdir -p "dist/$version"
cp app/build/outputs/apk/release/app-release.apk "dist/$version/Photos-Unlimited.apk"
(cd "dist/$version" && sha256sum Photos-Unlimited.apk > SHA256SUMS.txt)
"$ANDROID_HOME/build-tools/37.0.0/apksigner" verify --print-certs "dist/$version/Photos-Unlimited.apk"
