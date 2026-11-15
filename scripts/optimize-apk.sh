#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 INPUT.apk OUTPUT.apk" >&2
  exit 2
fi

tools="${ANDROID_HOME:?ANDROID_HOME is not set}/build-tools/35.0.0"
temporary=$(mktemp "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/exactrpn-optimized.XXXXXX.apk")
trap 'rm -f "$temporary"' EXIT

"$tools/aapt2" optimize \
  --collapse-resource-names \
  --deduplicate-entry-values \
  -o "$temporary" "$1"
"$tools/zipalign" -f 4 "$temporary" "$2"
