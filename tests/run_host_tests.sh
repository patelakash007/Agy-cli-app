#!/usr/bin/env bash
# run_host_tests.sh - Offline host regression tests for Agy-cli-app.
#
# Usage:
#   bash tests/run_host_tests.sh              # Python + Java host tests
#   bash tests/run_host_tests.sh --python-only
#   bash tests/run_host_tests.sh --java-only
#   bash tests/run_host_tests.sh --verbose
#
# No network, credentials, .agents files, or pre-existing build/ output are
# required. Test workspaces are created under build-test/.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BUILD_TEST_DIR="$PROJECT_ROOT/build-test"
PYTHON="${PYTHON:-python3}"
JAVA="${JAVA:-java}"
JAVAC="${JAVAC:-javac}"

RUN_PYTHON=1
RUN_JAVA=1
VERBOSE=0

usage() {
    sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'
}

for arg in "$@"; do
    case "$arg" in
        --python-only) RUN_JAVA=0 ;;
        --java-only) RUN_PYTHON=0 ;;
        --verbose|-v) VERBOSE=1 ;;
        --help|-h) usage; exit 0 ;;
        *) echo "Unknown argument: $arg" >&2; usage >&2; exit 2 ;;
    esac
done

mkdir -p "$BUILD_TEST_DIR"

PY_STATUS=0
JAVA_STATUS=0

if [ "$RUN_PYTHON" = "1" ]; then
    echo "== Python host tests =="
    if [ "$VERBOSE" = "1" ]; then
        (cd "$PROJECT_ROOT" && "$PYTHON" -m unittest discover -s tests/python -p 'test_*.py' -v)
    else
        (cd "$PROJECT_ROOT" && "$PYTHON" -m unittest discover -s tests/python -p 'test_*.py')
    fi
    PY_STATUS=$?
fi

if [ "$RUN_JAVA" = "1" ]; then
    echo
    echo "== Java host tests =="
    JAVA_BUILD="$BUILD_TEST_DIR/java"
    rm -rf "$JAVA_BUILD"
    mkdir -p "$JAVA_BUILD/classes" "$JAVA_BUILD/stubs/com/agy/runtime"

    cat > "$JAVA_BUILD/stubs/com/agy/runtime/RuntimeConfig.java" <<'EOF'
package com.agy.runtime;

/** Minimal host-test stub; production RuntimeConfig also reads Android resources. */
public final class RuntimeConfig {
    public static final int DEFAULT_HTTPS_PORT = 45157;

    private RuntimeConfig() {
    }
}
EOF

    "$JAVAC" -encoding UTF-8 -source 8 -target 8 -Xlint:-options \
        -d "$JAVA_BUILD/classes" \
        "$JAVA_BUILD/stubs/com/agy/runtime/RuntimeConfig.java" \
        "$PROJECT_ROOT/app/src/main/java/com/agy/core/CoreRuntimeState.java" \
        "$PROJECT_ROOT/app/src/main/java/com/agy/core/CoreRestartPolicy.java" \
        "$PROJECT_ROOT/app/src/main/java/com/agy/install/TarHeader.java" \
        "$PROJECT_ROOT/app/src/main/java/com/agy/log/LogRedactor.java" \
        "$PROJECT_ROOT/app/src/main/java/com/agy/util/ErrorText.java" \
        "$PROJECT_ROOT/app/src/main/java/com/agy/util/ShellQuote.java" \
        "$PROJECT_ROOT/tests/java/HostTestRunner.java"
    if [ $? -ne 0 ]; then
        echo "[ERROR] Java host test compilation failed" >&2
        JAVA_STATUS=1
    else
        "$JAVA" -cp "$JAVA_BUILD/classes" HostTestRunner
        JAVA_STATUS=$?
    fi
fi

echo
if [ "$PY_STATUS" -eq 0 ] && [ "$JAVA_STATUS" -eq 0 ]; then
    echo "Host test result: PASS"
    exit 0
fi

echo "Host test result: FAIL (python=$PY_STATUS java=$JAVA_STATUS)"
echo "Known regressions are expected until A02/A05/B02 are implemented; see PLAN.md."
exit 1
