#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Supply LWJGL core, OpenGL, GLFW and platform native jars; run under Xvfb if headless.
: "${LWJGL_TEST_CLASSPATH:?Set LWJGL_TEST_CLASSPATH to the LWJGL test dependencies}"
test_classes="$(mktemp -d)"
trap 'rm -rf "$test_classes"' EXIT
javac -cp "$LWJGL_TEST_CLASSPATH" -d "$test_classes" \
    src/client/java/com/whaltermc/DirectFrameReadback.java tests/DirectFrameReadbackTest.java
java -cp "$test_classes:$LWJGL_TEST_CLASSPATH" DirectFrameReadbackTest
