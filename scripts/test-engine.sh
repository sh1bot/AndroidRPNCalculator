#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
rm -rf engine/build/manual
mkdir -p engine/build/manual engine/build/test-tools
junit=engine/build/test-tools/junit-platform-console-standalone-1.11.4.jar
if [[ ! -f "$junit" ]]; then
    curl --fail --silent --show-error --location https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar -o "$junit.tmp"
    mv "$junit.tmp" "$junit"
fi
mapfile -t sources < <(find engine/src test-support/src -name '*.java' -print)
java scripts/Compile.java -source 17 -target 17 -cp "$junit" -d engine/build/manual "${sources[@]}"
java -jar "$junit" execute --class-path engine/build/manual --scan-class-path --fail-if-no-tests
