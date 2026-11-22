#!/usr/bin/env python3
"""Validate the signed update on the dedicated unsigned-in emulator only."""
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

import os

SDK=Path(os.environ['ANDROID_HOME'])
ADB=str(SDK/'platform-tools/adb')
SERIAL='emulator-5560'  # tools/emulator.sh
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'build/validation'
VERSION=re.search(r"versionName '([^']+)'",(ROOT/'app/build.gradle').read_text()).group(1)
# The release built by tools/build-release.sh, and the previous signed release to upgrade from.
APK=ROOT/'dist'/VERSION/'Photos-Unlimited.apk'
OLD=Path(os.environ['PREVIOUS_APK'])
SIGNER=str(SDK/'build-tools/37.0.0/apksigner')

def adb(*args,check=True):
    return subprocess.run([ADB,'-s',SERIAL,*args],text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=180,check=check).stdout

def dump(name='current'):
    remote='/sdcard/photos-release-ui.xml'
    adb('shell','uiautomator','dump',remote)
    target=OUT/(name+'.xml')
    adb('pull',remote,str(target))
    return list(ET.parse(target).getroot().iter('node'))

def tap(label,contains=False):
    matches=[]
    size=adb('shell','wm','size')
    width,height=map(int,re.findall(r'(\d+)x(\d+)',size)[-1])
    for _ in range(8):
        nodes=dump()
        matches=[n for n in nodes if (label in n.get('text','') if contains else n.get('text')==label)]
        if matches: break
        adb('shell','input','swipe',str(width//2),str(int(height*.78)),str(width//2),str(int(height*.3)),'300')
    if not matches: raise AssertionError('Missing control: '+label)
    node=matches[-1]
    x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
    assert x2>x1 and y2>y1,label
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
    time.sleep(.6)

def screenshot(name):
    time.sleep(.5)
    with (OUT/(name+'.png')).open('wb') as f:
        subprocess.run([ADB,'-s',SERIAL,'exec-out','screencap','-p'],stdout=f,check=True)
    dump(name)

def certificate(path):
    text=subprocess.check_output([SIGNER,'verify','--print-certs',str(path)],text=True)
    return re.search(r'certificate SHA-256 digest: ([0-9a-fA-F]+)',text).group(1)

assert 'photos-api37' in adb('emu','avd','name')
report=json.loads((OUT/'validation.json').read_text())
assert report['instrumentation_passed']
assert OLD.is_file(),'Previous signed release is required for the upgrade check'
assert certificate(OLD)==certificate(APK),'Signing certificate changed'
report['signing_certificate_preserved']=True
report['apk_sha256']=hashlib.sha256(APK.read_bytes()).hexdigest()
report['apk_bytes']=APK.stat().st_size
for package in ('app.photos.unlimited.test','app.photos.unlimited'):
    adb('uninstall',package,check=False)
adb('shell','settings','put','system','font_scale','1.0')
adb('shell','cmd','uimode','night','no')
adb('install',str(OLD))
adb('shell','am','start','-W','-n','app.photos.unlimited/.MainActivity')
time.sleep(1)
# Change preferences in the previous signed Material release, then update in place.
time.sleep(2)
tap('Settings')
tap('Unmetered networks only',contains=True)
tap('Only while charging',contains=True)
report['release_upgrade_install']=adb('install','-r',str(APK)).strip()
adb('shell','am','force-stop','app.photos.unlimited')
report['release_launch']=adb('shell','am','start','-W','-n','app.photos.unlimited/.MainActivity')
# Android can briefly keep its package-update overlay above the launched app.
for _ in range(10):
    if any(n.get('text')=='Photos Unlimited' for n in dump()):
        break
    time.sleep(1)
else:
    raise AssertionError('Updated app did not become visible')
report['release_not_debuggable']='DEBUGGABLE' not in adb('shell','dumpsys','package','app.photos.unlimited')
screenshot('release-home-light')
tap('Settings')
nodes=dump('release-settings-light')
unmetered=next(n for n in nodes if 'Unmetered networks only' in n.get('text','') and n.get('checkable')=='true')
charging=next(n for n in nodes if n.get('text')=='Only while charging' and n.get('checkable')=='true')
assert unmetered.get('checked')=='false' and charging.get('checked')=='true'
report['upgrade_preferences_preserved']=True
# Restore typical constraints on the test AVD; there is no Google account.
tap('Unmetered networks only',contains=True)
tap('Only while charging')
tap('Follow system')
tap('Dark')
time.sleep(1)
screenshot('release-settings-dark')
tap('Backup')
screenshot('release-home-dark')
tap('Activity')
screenshot('release-activity-dark')
tap('Storage')
screenshot('release-storage-dark')
report['storage_screen_visible']=any('Google Photos inventory' in n.get('text','') for n in dump())
# Large-font and narrow-width inspection is isolated to this disposable AVD.
adb('shell','settings','put','system','font_scale','2.0')
adb('shell','am','force-stop','app.photos.unlimited')
adb('shell','am','start','-W','-n','app.photos.unlimited/.MainActivity')
screenshot('release-large-font')
adb('shell','settings','put','system','font_scale','1.0')
adb('shell','am','force-stop','app.photos.unlimited')
adb('shell','am','start','-W','-n','app.photos.unlimited/.MainActivity')
tap('Connect Google account')
time.sleep(5)
signin=dump('signin-page')
texts=[n.get('text','') for n in signin]
report['signin_screen_opens']=any('Google sign-in' in t for t in texts)
report['browser_fallback_visible']='Use browser setup token' in texts
report['phone_account_button_visible']='Test account on this phone' in texts
tap('Test account on this phone')
probe=dump('account-probe')
texts=[n.get('text','') for n in probe]
report['account_probe_visible']='Test Photos access' in texts and 'Test basic Google access' in texts
report['live_account_authentication_tested']=False
report['live_upload_or_quota_tested']=False
report['live_inventory_tested']=False
report['native_theme_screenshots_captured']=True
(OUT/'release-validation.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
assert report['release_not_debuggable'] and report['storage_screen_visible']
assert report['signin_screen_opens'] and report['browser_fallback_visible'] and report['account_probe_visible']
