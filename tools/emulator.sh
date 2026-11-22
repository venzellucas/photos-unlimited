#!/usr/bin/env bash
set -euo pipefail
# The dedicated test emulator (signed out, no Google account), on adb serial emulator-5560.
# Needs the Android SDK's emulator, avdmanager and the API 37 Google APIs image on PATH.
avd=photos-api37
avd_home="${ANDROID_AVD_HOME:-$HOME/.android/avd}"
if [[ ! -f "$avd_home/$avd.ini" ]]; then
  printf 'no\n' | avdmanager create avd --name "$avd" \
    --package 'system-images;android-37.0;google_apis;x86_64' --device pixel_9
  sed -i -e 's/^hw.lcd.width=.*/hw.lcd.width=720/' \
    -e 's/^hw.lcd.height=.*/hw.lcd.height=1280/' \
    -e 's/^hw.lcd.density=.*/hw.lcd.density=320/' \
    "$avd_home/$avd.avd/config.ini"
fi
exec emulator -avd "$avd" -port 5560 -no-window -no-audio -no-boot-anim \
  -no-snapshot -gpu swiftshader -feature -Vulkan,-HardwareDecoder \
  -cores 4 -memory 2048 -camera-back none -camera-front none
