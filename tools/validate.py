#!/usr/bin/env python3
"""Run only on this project's dedicated, unsigned-in emulator."""
import json
import re
from pathlib import Path
import subprocess
import time

import os

SDK = Path(os.environ['ANDROID_HOME'])
ADB = str(SDK / 'platform-tools/adb')
SERIAL = 'emulator-5560'  # tools/emulator.sh
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'build/validation'


def adb(*args, timeout=180):
    return subprocess.run([ADB, '-s', SERIAL, *args], text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                          timeout=timeout, check=True).stdout

OUT.mkdir(parents=True, exist_ok=True)
deadline = time.monotonic() + 300
while time.monotonic() < deadline:
    try:
        if adb('shell', 'getprop', 'sys.boot_completed', timeout=10).strip() == '1':
            break
    except (subprocess.SubprocessError, OSError):
        pass
    time.sleep(5)
else:
    raise RuntimeError('Dedicated emulator did not boot')

assert 'photos-api37' in adb('emu', 'avd', 'name')
# This dedicated, unsigned-in AVD alternates between debug and release signatures.
for package in ('app.photos.unlimited.test', 'app.photos.unlimited'):
    subprocess.run([ADB, '-s', SERIAL, 'uninstall', package],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=60)
report = {'android_api': adb('shell', 'getprop', 'ro.build.version.sdk').strip()}
report['install'] = adb('install', '-r', str(ROOT / 'app/build/outputs/apk/debug/app-debug.apk')).strip()
report['test_install'] = adb('install', '-r', str(ROOT / 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')).strip()
# LiveReclaimProbe is for the signed-in reference AVD only.
result = adb('shell', 'am', 'instrument', '-w', '-e', 'notClass', 'app.photos.unlimited.LiveReclaimProbe',
             'app.photos.unlimited.test/androidx.test.runner.AndroidJUnitRunner', timeout=240)
(OUT / 'instrumentation.txt').write_text(result)
count = re.search(r'OK \((\d+) tests\)', result)
report['instrumentation_count'] = int(count.group(1)) if count else 0
report['instrumentation_passed'] = report['instrumentation_count'] >= 28 and 'FAILURES' not in result
for theme in ('light', 'dark'):
    adb('pull', '/sdcard/Android/data/app.photos.unlimited/files/profile-' + theme + '.png', str(OUT / ('profile-' + theme + '.png')))
report['launch'] = adb('shell', 'am', 'start', '-W', '-n', 'app.photos.unlimited/.MainActivity')
time.sleep(3)
subprocess.run([ADB, '-s', SERIAL, 'exec-out', 'screencap', '-p'],
               stdout=(OUT / 'home.png').open('wb'), check=True)
adb('shell', 'uiautomator', 'dump', '/sdcard/photos-ui.xml')
adb('pull', '/sdcard/photos-ui.xml', str(OUT / 'home.xml'))
report['ui_visible'] = 'Photos Unlimited' in (OUT / 'home.xml').read_text()
(OUT / 'validation.json').write_text(json.dumps(report, indent=2))
print(json.dumps(report, indent=2))
if not report['instrumentation_passed'] or not report['ui_visible']:
    raise SystemExit(1)
