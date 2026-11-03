#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p engine/build/manual
mapfile -t sources < <(find engine/src -name '*.java' -print)
java scripts/Compile.java -source 17 -target 17 -d engine/build/manual "${sources[@]}"
java -cp engine/build/manual dev.exactrpn.Regression
