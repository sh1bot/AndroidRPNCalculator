#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
reports=app/build/reports/device-tests
mkdir -p "$reports"
# API 26 supports inline Binder payloads but must reject shared-memory-sized transfers.
if [[ "${1:-}" == "26" ]]; then
    gradle --no-daemon --console=plain :app:connectedDebugAndroidTest \
      -Pandroid.testInstrumentationRunnerArguments.class=dev.exactrpn.MemoryTransportTest
    cp -R app/build/reports/androidTests/connected "$reports/transport"
    exit 0
fi
status=0
# The credential-changing test gets its own invocation after all ordinary tests.
# Even a failed emulator unlock cannot contaminate their clipboard or persistence checks.
gradle --no-daemon --console=plain :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.notAnnotation=androidx.test.filters.LargeTest || status=$?
cp -R app/build/reports/androidTests/connected "$reports/ordinary"
adb logcat -d -s GlyphProbe:I "*:S"
gradle --no-daemon --console=plain :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.exactrpn.LockScreenTest#secureLockThenUnlockKeepsTheSameTemporarySession || status=$?
cp -R app/build/reports/androidTests/connected "$reports/secure-lock"
exit "$status"
