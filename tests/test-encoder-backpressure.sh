#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
test_classes="$(mktemp -d)"
trap 'rm -rf "$test_classes"' EXIT
javac -d "$test_classes" src/client/java/com/whaltermc/EncoderBackpressure.java tests/EncoderBackpressureTest.java
java -cp "$test_classes" EncoderBackpressureTest
