#!/usr/bin/env bash
# Rebuild Ascend. Usage: ./build.sh [debug|release]
set -euo pipefail

MODE="${1:-debug}"

if [ -z "${ANDROID_HOME:-}" ]; then
    export ANDROID_HOME=/opt/android-sdk
fi

GRADLE_BIN="${GRADLE_BIN:-/opt/gradle/gradle-9.8.1/bin/gradle}"
if [ ! -x "$GRADLE_BIN" ]; then
    GRADLE_BIN="$(command -v gradle)"
fi

cd "$(dirname "$0")"

case "$MODE" in
    debug)   TASK=":app:assembleDebug" ;;
    release) TASK=":app:assembleRelease" ;;
    *) echo "usage: $0 [debug|release]" >&2; exit 2 ;;
esac

echo "ANDROID_HOME=$ANDROID_HOME"
"$GRADLE_BIN" "$TASK" --no-daemon --console=plain

OUT="app/build/outputs/apk/$MODE"
APK=$(find "$OUT" -name '*.apk' | head -1)
if [ -n "$APK" ]; then
    cp "$APK" "./Ascend-$MODE.apk"
    echo
    echo "APK: $(realpath "./Ascend-$MODE.apk")  ($(du -h "./Ascend-$MODE.apk" | cut -f1))"
    echo "Install with: adb install -r ./Ascend-$MODE.apk"
fi
