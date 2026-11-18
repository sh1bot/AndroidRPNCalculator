#!/usr/bin/env bash
set -euo pipefail
gradle --no-daemon --console=plain :app:installDebug :app:installDebugAndroidTest
reports=app/build/reports/device-tests
mkdir -p "$reports"
for attempt in {1..12}; do
    budget=5000
    if (( attempt % 2 == 0 )); then
        budget=12000
    fi
    echo "Attempt $attempt, startup budget ${budget} ms"
    adb logcat -c
    adb shell am instrument -w -r \
        -e class 'dev.exactrpn.CancellationTest#cancelStopsWorkerAndPreservesCheckpoint' \
        -e startupBudgetMs "$budget" \
        dev.exactrpn.test/androidx.test.runner.AndroidJUnitRunner \
        | tee "$reports/attempt-$attempt-result.txt"
    adb logcat -d -v threadtime > "$reports/attempt-$attempt-logcat.txt"
    awk '/WorkerStartup/' "$reports/attempt-$attempt-logcat.txt"
done
