import com.agy.core.CoreRestartPolicy;
import com.agy.core.CoreRuntimeState;
import com.agy.install.TarHeader;
import com.agy.log.LogRedactor;
import com.agy.util.ErrorText;
import com.agy.util.ShellQuote;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Dependency-light host test runner for pure policy/parser/state classes.
 *
 * Compiled by tests/run_host_tests.sh with a small RuntimeConfig stub so the
 * runner does not need Android framework classes. Each failure names the PLAN
 * finding it tracks.
 */
public final class HostTestRunner {

    private interface TestCase {
        void run() throws Exception;
    }

    private static int passed;
    private static int failed;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        // R04: session URL validation must check the trusted origin, not just shape.
        test("official session URL accepted", "R04", () ->
                assertTrue(CoreRuntimeState.isValidSessionUrl("https://antigravity.google.com/r/abc123"),
                        "official session URL was rejected"));
        test("official URL with query accepted", "R04", () ->
                assertTrue(CoreRuntimeState.isValidSessionUrl("https://antigravity.google.com/r/abc123?foo=bar"),
                        "official session URL with query was rejected"));
        test("attacker host rejected", "R04", () ->
                assertFalse(CoreRuntimeState.isValidSessionUrl("https://attacker.example/r/fake"),
                        "attacker host was accepted"));
        test("HTTP rejected", "R04", () ->
                assertFalse(CoreRuntimeState.isValidSessionUrl("http://antigravity.google.com/r/abc123"),
                        "HTTP session URL was accepted"));
        test("suffix host rejected", "R04", () ->
                assertFalse(CoreRuntimeState.isValidSessionUrl("https://antigravity.google.com.attacker.example/r/fake"),
                        "suffix host was accepted"));
        test("userinfo rejected", "R04", () ->
                assertFalse(CoreRuntimeState.isValidSessionUrl("https://user:pass@antigravity.google.com/r/abc"),
                        "userinfo URL was accepted"));
        test("auth path rejected", "R04", () ->
                assertFalse(CoreRuntimeState.isValidSessionUrl("https://antigravity.google.com/o/oauth2/r/abc"),
                        "auth path was accepted"));
        test("non-session path rejected", "R04", () ->
                assertFalse(CoreRuntimeState.isValidSessionUrl("https://antigravity.google.com/foo"),
                        "non-session path was accepted"));
        test("null and empty rejected", "R04", () -> {
            assertFalse(CoreRuntimeState.isValidSessionUrl(null), "null was accepted");
            assertFalse(CoreRuntimeState.isValidSessionUrl("   "), "blank was accepted");
        });

        // A01 smoke coverage for other pure classes used by the host.
        test("restart backoff is bounded", "A01", () -> {
            assertEquals(1000L, CoreRestartPolicy.delayFor(1), "attempt 1");
            assertEquals(2000L, CoreRestartPolicy.delayFor(2), "attempt 2");
            assertEquals(30000L, CoreRestartPolicy.delayFor(6), "attempt 6");
            assertEquals(30000L, CoreRestartPolicy.delayFor(100), "attempt 100");
        });
        test("tar octal parser accepts valid fields", "A01", () -> {
            byte[] block = new byte[512];
            byte[] value = "0000644".getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(value, 0, block, 100, value.length);
            assertEquals(420L, TarHeader.readOctal(block, 100, 8), "octal 644");
        });
        test("log redactor removes bearer tokens", "A01", () -> {
            String redacted = LogRedactor.redact("Authorization: Bearer abc123");
            assertTrue(redacted.contains("[REDACTED]"), "bearer token was not redacted");
            assertFalse(redacted.contains("abc123"), "bearer token leaked");
        });
        test("shell quote handles spaces and quotes", "A01", () -> {
            assertEquals("abc", ShellQuote.quote("abc"), "safe value");
            assertEquals("'a b'", ShellQuote.quote("a b"), "space value");
            assertEquals("'a'\\''b'", ShellQuote.quote("a'b"), "quote value");
        });
        test("error text falls back to class name", "A01", () -> {
            assertEquals("Exception", ErrorText.describe(new Exception()), "empty message");
            assertEquals("boom", ErrorText.describe(new Exception("boom")), "message");
        });

        System.out.println();
        System.out.printf("Java host tests: %d passed, %d failed%n", passed, failed);
        if (failed > 0) {
            System.out.println("Failures:");
            for (String failure : failures) {
                System.out.println("  - " + failure);
            }
            System.exit(1);
        }
    }

    private static void test(String name, String task, TestCase testCase) {
        try {
            testCase.run();
            passed++;
            System.out.println("[PASS] " + task + " " + name);
        } catch (Throwable throwable) {
            failed++;
            String message = throwable.getMessage();
            String detail = task + " " + name + (message == null ? "" : ": " + message);
            failures.add(detail);
            System.out.println("[FAIL] " + detail);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    private static void assertEquals(long expected, long actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " (expected " + expected + ", got " + actual + ")");
        }
    }

    private static void assertEquals(String expected, String actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " (expected " + expected + ", got " + actual + ")");
        }
    }
}
