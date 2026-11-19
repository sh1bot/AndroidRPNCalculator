#!/usr/bin/env bash
set -euo pipefail

package=dev.exactrpn
apk=dist/ExactRPN.apk

calculate() {
    local left=$1 right=$2 expected=$3
    adb shell am start -W -n "$package/.MainActivity"
    adb shell input text "$left"
    adb shell input keyevent KEYCODE_ENTER
    adb shell input text "$right"
    adb shell input text +
    for attempt in {1..12}; do
        if adb shell uiautomator dump /sdcard/release-smoke.xml >/dev/null \
            && adb shell cat /sdcard/release-smoke.xml | python3 -c '
import sys
import xml.etree.ElementTree as ET

nodes = ET.parse(sys.stdin).iter("node")
sys.exit(not any(
    node.get("package") == "dev.exactrpn"
    and node.get("text") == sys.argv[1]
    for node in nodes
))
' "$expected"; then
            return
        fi
    done
    adb shell cat /sdcard/release-smoke.xml
    adb logcat -d -b crash
    echo "Did not see calculation result $expected" >&2
    return 1
}

adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb install "$apk"
calculate 20 22 42
adb uninstall "$package"

if [[ -f dist/previous.apk ]]; then
    adb install dist/previous.apk
    calculate 8 9 17
    adb shell am force-stop "$package"
    adb install -r "$apk"
    calculate 12 11 23
fi
