#!/bin/bash
# Capture the screenshot set for screenshots/ with Robolectric and optimise
# it the way the set was made: quantise to 32 colours (pngquant), then
# zopflipng.
#
# ScreenshotCapture.java (beside this script) drives the app through the
# set's scenes at its native geometry — portrait 420x840dp and landscape
# 840x420dp at xxhdpi — under Robolectric's native graphics and writes raw
# PNGs. This script compiles it, runs it via the bundled TestRunner, and
# optimises the results into screenshots/.
#
# The script does not build the app. The caller provides, via the
# environment:
#   JAVA_HOME        JDK 21 (Robolectric's SDK 36 runtime is Java 21
#                    bytecode)
#   ANDROID_JAR      android.jar for SDK 36
#   APP_CLASSES      directory with the app's compiled classes
#   TEST_CLASSES     directory with the compiled androidTest classes
#   TEST_CONFIG      directory put on the classpath for the test manifest
#                    and resources Robolectric loads
#   ANDROIDX_JARS    colon-separated list of androidx classes.jar files
#   ROBOLECTRIC_DEPS directory holding the Robolectric dependency jars
#   ANDROID_ALL_DIR  directory holding Robolectric's android-all jars
#                    (used with robolectric.offline=true)
# Optional:
#   SCREENSHOTS_DIR  destination for the optimised PNGs (default:
#                    <repo>/screenshots)
#   RAW_DIR          raw capture directory (default: <repo>/build/screenshots-raw)
#   TOOLS_CLASSES    where the capture classes are compiled (default:
#                    <repo>/build/screenshot-tools)
# pngquant and zopflipng must be on PATH for the optimisation step.
set -euo pipefail

REPO=$(cd "$(dirname "$0")/../.." && pwd)
SCREENSHOTS_DIR=${SCREENSHOTS_DIR:-$REPO/screenshots}
RAW_DIR=${RAW_DIR:-$REPO/build/screenshots-raw}
TOOLS_CLASSES=${TOOLS_CLASSES:-$REPO/build/screenshot-tools}
DEPS=$(ls "$ROBOLECTRIC_DEPS"/*.jar | tr '\n' ':')

mkdir -p "$RAW_DIR" "$SCREENSHOTS_DIR" "$TOOLS_CLASSES"

echo "== compile capture classes =="
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn \
    -cp "$ANDROID_JAR:$APP_CLASSES:$TEST_CLASSES:$ANDROIDX_JARS:$DEPS" \
    -d "$TOOLS_CLASSES" \
    "$REPO/tools/screenshots/ScreenshotCapture.java" \
    "$REPO/tools/screenshots/TestRunner.java"

echo "== shoot =="
rm -f "$RAW_DIR"/*.png
"$JAVA_HOME/bin/java" -Xmx1g -Dfile.encoding=UTF-8 \
    -Drobolectric.offline=true -Drobolectric.dependency.dir="$ANDROID_ALL_DIR" \
    -Dscreenshot.dir="$RAW_DIR" \
    --add-opens java.base/java.lang=ALL-UNNAMED \
    --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
    --add-opens java.base/java.io=ALL-UNNAMED \
    --add-opens java.base/java.nio=ALL-UNNAMED \
    --add-opens java.base/java.util=ALL-UNNAMED \
    --add-opens java.base/java.util.regex=ALL-UNNAMED \
    --add-opens java.base/java.text=ALL-UNNAMED \
    --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
    --add-opens java.desktop/java.awt.font=ALL-UNNAMED \
    -cp "$TOOLS_CLASSES:$TEST_CLASSES:$APP_CLASSES:$TEST_CONFIG:$DEPS$ANDROIDX_JARS:$ANDROID_JAR" \
    dev.exactrpn.TestRunner dev.exactrpn.ScreenshotCapture

echo "== optimise (32 colours + zopflipng) =="
for raw in "$RAW_DIR"/*.png; do
    name=$(basename "$raw")
    tmp="$SCREENSHOTS_DIR/.tmp-$name"
    pngquant 32 --force --output "$tmp" "$raw"
    zopflipng -y "$tmp" "$SCREENSHOTS_DIR/$name" >/dev/null
    rm -f "$tmp"
    printf '%-36s raw %8d  final %8d\n' "$name" \
        "$(stat -c%s "$raw")" "$(stat -c%s "$SCREENSHOTS_DIR/$name")"
done
echo "done: $SCREENSHOTS_DIR"
