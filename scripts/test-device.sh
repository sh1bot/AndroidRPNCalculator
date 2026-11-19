#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
reports=app/build/reports/device-tests
mkdir -p "$reports"
status=0
# The credential-changing test gets its own invocation after all ordinary tests.
# Even a failed emulator unlock cannot contaminate their clipboard or persistence checks.
gradle --no-daemon --console=plain :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.notAnnotation=androidx.test.filters.LargeTest,dev.exactrpn.CancellationRecovery || status=$?
cp -R app/build/reports/androidTests/connected "$reports/ordinary"
adb logcat -d -s GlyphProbe:I MenuTest:E CalculatorWorker:W "*:S"
adb logcat -d -b crash
# Each recovery attempt runs in a fresh instrumentation invocation. Keep every report.
results=app/build/outputs/androidTest-results/connected
html=app/build/reports/androidTests/connected
for attempt in {1..4}; do
    rm -rf "$results" "$html"
    recovery_status=0
    gradle --no-daemon --console=plain :app:connectedDebugAndroidTest \
      '-Pandroid.testInstrumentationRunnerArguments.class=dev.exactrpn.CancellationTest#cancelStopsWorkerAndPreservesCheckpoint' || recovery_status=$?
    attempt_report="$reports/recovery-$attempt"
    mkdir -p "$attempt_report"
    if [[ -d "$html" ]]; then cp -R "$html" "$attempt_report/html"; fi
    if [[ -d "$results" ]]; then cp -R "$results" "$attempt_report/xml"; fi
    if (( recovery_status == 0 )); then
        if (( attempt > 1 )); then
            echo "::warning::Flaky cancellation recovery passed on attempt $attempt"
        fi
        break
    fi
    if (( attempt == 4 )) || ! python3 - "$results" <<'PYTHON'
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

cases = [
    case
    for report in Path(sys.argv[1]).rglob("TEST-*.xml")
    for case in ET.parse(report).iter("testcase")
]
if len(cases) != 1:
    sys.exit(1)
case = cases[0]
failure = case.find("failure")
watchdog = "Recovery worker startup watchdog expired"
sys.exit(0 if (
    case.get("classname") == "dev.exactrpn.CancellationTest"
    and case.get("name") == "cancelStopsWorkerAndPreservesCheckpoint"
    and failure is not None
    and (
        watchdog in failure.get("message", "")
        or watchdog in "".join(failure.itertext())
    )
) else 1)
PYTHON
    then
        status=$recovery_status
        break
    fi
    echo "::warning::Cancellation recovery startup watchdog expired on attempt $attempt. Retrying."
done

gradle --no-daemon --console=plain :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.exactrpn.LockScreenTest#secureLockThenUnlockKeepsTheSameTemporarySession || status=$?
cp -R app/build/reports/androidTests/connected "$reports/secure-lock"
adb logcat -d -s LockTest:E "*:S"
adb logcat -d -b crash
exit "$status"
