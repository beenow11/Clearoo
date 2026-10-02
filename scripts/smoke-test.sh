#!/usr/bin/env bash
# End-to-end smoke test of the minified (R8) build on an emulator. Run by CI.
set -euo pipefail
cd "$(dirname "$0")/.."
APK=app/build/outputs/apk/qa/app-qa.apk
PKG=com.clearoo.app
UI="python3 scripts/ui.py"
mkdir -p smoke media

echo "== Sample gallery"
for i in $(seq 1 14); do
  ffmpeg -loglevel error -y -f lavfi -i "testsrc2=size=720x1280:rate=1" -vf "hue=h=$((i * 25))" -frames:v 1 "media/IMG_$i.jpg"
done
for i in 1 2 3; do
  ffmpeg -loglevel error -y -f lavfi -i "smptebars=size=720x1280:rate=1" -frames:v 1 "media/Screenshot_2024010${i}.png"
done
ffmpeg -loglevel error -y -f lavfi -i "testsrc2=size=720x1280:rate=30" -t 3 -pix_fmt yuv420p "media/VID_1.mp4"
adb shell mkdir -p /sdcard/DCIM/Camera /sdcard/Pictures/Screenshots
adb push media/IMG_*.jpg media/VID_1.mp4 /sdcard/DCIM/Camera/ >/dev/null
adb push media/Screenshot_*.png /sdcard/Pictures/Screenshots/ >/dev/null
adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null || true
sleep 3
echo "MediaStore images: $(adb shell content query --uri content://media/external/images/media --projection _id | grep -c Row || true)"

echo "== Install and grant permissions"
adb install -r "$APK"
for p in READ_MEDIA_IMAGES READ_MEDIA_VIDEO POST_NOTIFICATIONS; do
  adb shell pm grant "$PKG" "android.permission.$p" || true
done
adb logcat -c

echo "== Onboarding"
adb shell am start -W -n "$PKG/.MainActivity" >/dev/null
$UI shot 01-onboarding
$UI tap "Hi Roo"
$UI tap "Got it"
$UI tap "Allow access"
$UI tap "Remind me at"
$UI expect "Start swiping"
$UI shot 02-home

echo "== Swipe"
read -r W H < <(adb shell wm size | grep -o '[0-9]*x[0-9]*' | tail -1 | tr x ' ')
# 450ms drags: the software emulator drops most touch points of very fast flicks (a real
# touchscreen doesn't), so a quicker gesture would test the emulator, not the app.
swipe() {
  adb logcat -c
  adb shell input swipe $((W / 2)) $((H / 2)) "$1" $((H / 2)) 450
  sleep 1.5
  adb logcat -d -s ClearooSwipe:D | grep ClearooSwipe || echo "  (no drag seen)"
}
$UI tap "Start swiping"
$UI expect "Quick mix"
$UI shot 03-deck
echo "-- diagnostics: card positions before and after one keep-flick"
$UI cards
adb shell input swipe $((W / 2)) $((H / 2)) $((W * 9 / 10)) $((H / 2)) 180
sleep 2
$UI cards
adb logcat -d -s ClearooSwipe:D | tail -20 || true
# The bin count is checked after every swipe.
swipe $((W / 10));     $UI expect "🗑️ 1"   # delete
swipe $((W / 10));     $UI expect "🗑️ 2"   # delete
swipe $((W * 9 / 10)); $UI expect "🗑️ 2"   # keep
swipe $((W / 10));     $UI expect "🗑️ 3"   # delete
swipe $((W * 9 / 10)); $UI expect "🗑️ 3"   # keep
$UI shot 04-after-swipes

echo "== The bin survives the app being killed"
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/.MainActivity" >/dev/null
$UI tap "Start swiping"
$UI expect "🗑️ 3"

echo "== Empty the bin"
$UI tap "🗑️ 3"
$UI expect "Move 3 to trash"
$UI shot 05-bin
$UI tap "Move 3 to trash"
$UI tap "Allow"                  # Android's own confirmation dialog
$UI expect "freed"
$UI shot 06-celebration
$UI tap "Done for today"
$UI expect "Today's clean-up"
$UI shot 07-home-after

echo "== Stats survive an app update"
adb install -r "$APK"
adb shell am start -W -n "$PKG/.MainActivity" >/dev/null
$UI expect "Today's clean-up"    # still onboarded, not back at the intro
$UI expect "2 more deletes keep your streak"
$UI shot 08-after-update

echo "== Wardrobe and settings"
$UI tap "👕"
$UI expect "Roo's wardrobe"
$UI shot 09-wardrobe
adb shell input keyevent KEYCODE_BACK; sleep 1
$UI tap "Settings"
$UI expect "Reminder time"
$UI shot 10-settings
$UI tap "Preview reminder"
adb shell input keyevent KEYCODE_BACK; sleep 1

echo "== Smart deck"
adb shell input swipe $((W / 2)) $((H * 3 / 4)) $((W / 2)) $((H / 4)) 300; sleep 1
$UI tap "Screenshots"
$UI expect "Screenshots ·"
$UI shot 11-screenshots-deck
adb shell input keyevent KEYCODE_BACK; sleep 1

echo "== Random stress test"
adb shell monkey -p "$PKG" --pct-syskeys 0 --throttle 40 -s 42 -v 3000 > smoke/monkey.txt 2>&1 || true
tail -3 smoke/monkey.txt

echo "== Crash check"
adb logcat -d > smoke/logcat.txt
if grep -E "FATAL EXCEPTION|ANR in $PKG|CRASH: $PKG" smoke/logcat.txt smoke/monkey.txt; then
  grep -A 30 "FATAL EXCEPTION" smoke/logcat.txt | head -60 || true
  echo "FAIL: crash or ANR"
  exit 1
fi
echo "PASS: no crashes"
