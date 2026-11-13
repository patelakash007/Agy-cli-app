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
#
# A run only reports PASS when at least one suite actually executed and every
# suite reported at least one case; contradictory selections and empty discovery
# fail loudly (R29/A06).
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BUILD_TEST_DIR="$PROJECT_ROOT/build-test"
PYTHON="${PYTHON:-python3}"
JAVA="${JAVA:-java}"
JAVAC="${JAVAC:-javac}"

RUN_PYTHON=1
RUN_JAVA=1
SAW_PYTHON=0
SAW_JAVA=0
VERBOSE=0

usage() {
    sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'
}

for arg in "$@"; do
    case "$arg" in
        --python-only)
            RUN_JAVA=0
            SAW_PYTHON=1
            ;;
        --java-only)
            RUN_PYTHON=0
            SAW_JAVA=1
            ;;
        --verbose|-v) VERBOSE=1 ;;
        --help|-h) usage; exit 0 ;;
        *) echo "Unknown argument: $arg" >&2; usage >&2; exit 2 ;;
    esac
done

if [ "$SAW_PYTHON" = "1" ] && [ "$SAW_JAVA" = "1" ]; then
    echo "[ERROR] --python-only and --java-only cannot be combined (no suite would run)" >&2
    exit 2
fi

mkdir -p "$BUILD_TEST_DIR"

PY_STATUS=0
JAVA_STATUS=0
PY_CASES=0
JAVA_CASES=0
SUITES_RUN=0

if [ "$RUN_PYTHON" = "1" ]; then
    SUITES_RUN=$((SUITES_RUN + 1))
    echo "== Python host tests =="
    PY_LOG="$BUILD_TEST_DIR/python-tests.log"
    if [ "$VERBOSE" = "1" ]; then
        (cd "$PROJECT_ROOT" && "$PYTHON" -m unittest discover -s tests/python -p 'test_*.py' -v) 2>&1 | tee "$PY_LOG"
    else
        (cd "$PROJECT_ROOT" && "$PYTHON" -m unittest discover -s tests/python -p 'test_*.py') 2>&1 | tee "$PY_LOG"
    fi
    PY_STATUS=${PIPESTATUS[0]}
    PY_CASES=$(sed -n 's/^Ran \([0-9][0-9]*\) tests\?.*/\1/p' "$PY_LOG" | tail -1)
    PY_CASES=${PY_CASES:-0}
    echo "[*] Python cases executed: $PY_CASES"
fi

if [ "$RUN_JAVA" = "1" ]; then
    SUITES_RUN=$((SUITES_RUN + 1))
    echo
    echo "== Java host tests =="
    JAVA_BUILD="$BUILD_TEST_DIR/java"
    rm -rf "$JAVA_BUILD"
    mkdir -p "$JAVA_BUILD/classes" "$JAVA_BUILD/stubs/com/agy/runtime" "$JAVA_BUILD/stubs/com/agy/util"

    cat > "$JAVA_BUILD/stubs/com/agy/runtime/RuntimeConfig.java" <<'EOF'
package com.agy.runtime;

/** Minimal host-test stub; production RuntimeConfig also reads Android resources. */
public final class RuntimeConfig {
    public static final int DEFAULT_HTTPS_PORT = 45157;

    private RuntimeConfig() {
    }
}
EOF

    cat > "$JAVA_BUILD/stubs/com/agy/util/FileModeUtil.java" <<'EOF'
package com.agy.util;

import java.io.File;

/** Host-test stub: android.system.Os is unavailable outside Android. */
public final class FileModeUtil {
    private FileModeUtil() {
    }

    public static void copyPermissions(File src, File dst) {
        if (src.exists()) {
            dst.setReadable(src.canRead(), false);
            dst.setWritable(src.canWrite(), false);
            dst.setExecutable(src.canExecute(), false);
        }
    }
}
EOF

    "$JAVAC" -encoding UTF-8 -source 8 -target 8 -Xlint:-options \
        -d "$JAVA_BUILD/classes" \
        "$JAVA_BUILD/stubs/com/agy/runtime/RuntimeConfig.java" \
        "$JAVA_BUILD/stubs/com/agy/util/FileModeUtil.java" \
        "$PROJECT_ROOT/app/src/main/java/com/agy/core/CoreRuntimeState.java" \
        "$PROJECT_ROOT/app/src/main/java/com/agy/core/CoreRestartPolicy.java" \
        "$PROJECT_ROOT/app/src/main/java/com/agy/core/ProcessScope.java" \
        "$PROJECT_ROOT/app/src/main/java/com/agy/install/TarHeader.java" \
        "$PROJECT_ROOT/app/src/main/java/com/agy/install/TarPolicy.java" \
        "$PROJECT_ROOT/app/src/main/java/com/agy/install/TarPath.java" \
        "$PROJECT_ROOT/app/src/main/java/com/agy/runtime/CliInstallPolicy.java" \
        "$PROJECT_ROOT/app/src/main/java/com/agy/runtime/CliInstallMetadata.java" \
        "$PROJECT_ROOT/app/src/main/java/com/agy/util/AtomicFileWriter.java" \
        "$PROJECT_ROOT/app/src/main/java/com/agy/util/IoUtil.java" \
        "$PROJECT_ROOT/app/src/main/java/com/agy/util/PathSafety.java" \
        "$PROJECT_ROOT/app/src/main/java/com/agy/util/SharePathPolicy.java" \
        "$PROJECT_ROOT/app/src/main/java/com/agy/log/LogRedactor.java" \
        "$PROJECT_ROOT/app/src/main/java/com/agy/util/ErrorText.java" \
        "$PROJECT_ROOT/app/src/main/java/com/agy/util/ShellQuote.java" \
        "$PROJECT_ROOT/tests/java/HostTestRunner.java"
    if [ $? -ne 0 ]; then
        echo "[ERROR] Java host test compilation failed" >&2
        JAVA_STATUS=1
    else
        JAVA_LOG="$BUILD_TEST_DIR/java-tests.log"
        "$JAVA" -cp "$JAVA_BUILD/classes" HostTestRunner 2>&1 | tee "$JAVA_LOG"
        JAVA_STATUS=${PIPESTATUS[0]}
        JAVA_CASES=$(sed -n 's/^Java host tests: \([0-9][0-9]*\) passed,.*/\1/p' "$JAVA_LOG")
        JAVA_CASES=${JAVA_CASES:-0}
        echo "[*] Java cases passed: $JAVA_CASES"
    fi
fi

echo
if [ "$SUITES_RUN" -eq 0 ]; then
    echo "[ERROR] No test suite was selected." >&2
    exit 2
fi
if [ "$RUN_PYTHON" = "1" ] && [ "$PY_CASES" -eq 0 ]; then
    echo "[ERROR] Python suite reported zero executed cases (discovery failed?)." >&2
    PY_STATUS=1
fi
if [ "$RUN_JAVA" = "1" ] && [ "$JAVA_STATUS" -eq 0 ] && [ "$JAVA_CASES" -eq 0 ]; then
    echo "[ERROR] Java suite reported zero executed cases (discovery failed?)." >&2
    JAVA_STATUS=1
fi

if [ "$PY_STATUS" -eq 0 ] && [ "$JAVA_STATUS" -eq 0 ]; then
    echo "Host test result: PASS (suites=$SUITES_RUN python=$PY_CASES java=$JAVA_CASES)"
    exit 0
fi

echo "Host test result: FAIL (python=$PY_STATUS java=$JAVA_STATUS)"
echo "Fix the failing cases; do not suppress them. See PLAN.md for current task status."
exit 1
