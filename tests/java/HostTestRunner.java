import com.agy.core.CoreRestartPolicy;
import com.agy.core.CoreRuntimeState;
import com.agy.core.ProcessScope;
import com.agy.install.TarHeader;
import com.agy.install.TarPath;
import com.agy.install.TarPolicy;
import com.agy.log.LogRedactor;
import com.agy.runtime.CliInstallMetadata;
import com.agy.runtime.CliInstallPolicy;
import com.agy.util.AtomicFileWriter;
import com.agy.util.ErrorText;
import com.agy.util.SharePathPolicy;
import com.agy.util.ShellQuote;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

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

    private static final String ARTIFACT_URL = "https://storage.googleapis.com/antigravity-public/"
            + "antigravity-cli/1.5.3-6604829099950080/android-arm/cli_android_arm64.tar.gz";
    private static final String SHA512 = "be0bcc8b73c0afa42f9e7ab681846b968627a8b6d3a1294d"
            + "490c43146165f22f64feaf39a832811339918e18ec763739952dc796a55946cccd79aaaec759d0e5";

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
        test("encoded slash rejected", "R04", () ->
                assertFalse(CoreRuntimeState.isValidSessionUrl("https://antigravity.google.com/r/abc%2Fdef"),
                        "encoded slash was accepted"));
        test("extra path segment rejected", "R04", () ->
                assertFalse(CoreRuntimeState.isValidSessionUrl("https://antigravity.google.com/r/abc/def"),
                        "extra path segment was accepted"));
        test("non-default port rejected", "R04", () ->
                assertFalse(CoreRuntimeState.isValidSessionUrl("https://antigravity.google.com:8443/r/abc"),
                        "non-default port was accepted"));
        test("trailing-dot host rejected", "R04", () ->
                assertFalse(CoreRuntimeState.isValidSessionUrl("https://antigravity.google.com./r/abc"),
                        "trailing-dot host was accepted"));
        test("control character rejected", "R04", () ->
                assertFalse(CoreRuntimeState.isValidSessionUrl("https://antigravity.google.com/r/a\u0007bc"),
                        "control character was accepted"));
        test("fragment and query preserved", "R04", () ->
                assertTrue(CoreRuntimeState.isValidSessionUrl(
                                "https://antigravity.google.com/r/abc-123?foo=bar&x=%20#frag"),
                        "official URL with query/fragment was rejected"));

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

        // B05/R09: header checksum, numeric, type, path and link policy.
        test("valid tar entry parses", "R09", () -> {
            TarPolicy.Entry entry = TarPolicy.parse(tarBlock("bin/agy", 5, '0', ""));
            assertEquals("bin/agy", entry.name, "entry name");
            assertEquals(5L, entry.size, "entry size");
            assertEquals('0', entry.type, "entry type");
        });
        test("tar checksum mismatch rejected", "R09", () -> {
            byte[] block = tarBlock("bin/agy", 5, '0', "");
            block[20] ^= 0x01;
            assertThrows(block, "checksum mismatch was accepted");
        });
        test("tar traversal and absolute names rejected", "R09", () -> {
            assertThrows(tarBlock("../escape", 0, '0', ""), "traversal name accepted");
            assertThrows(tarBlock("/etc/passwd", 0, '0', ""), "absolute name accepted");
            assertTrue(TarPath.normalize("a/../b") == null, "TarPath accepted traversal");
            assertTrue(TarPath.normalize("a/\u0001b") == null, "TarPath accepted a control character");
        });
        test("tar malformed numeric fields rejected", "R09", () -> {
            byte[] block = tarBlock("bin/agy", 5, '0', "");
            byte[] bad = "00000x44".getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(bad, 0, block, 124, bad.length);
            recomputeChecksum(block);
            assertThrows(block, "non-octal size was accepted");
        });
        test("unsupported tar types rejected", "R09", () -> {
            assertThrows(tarBlock("bin/agy", 0, '1', "bin/other"), "hardlink accepted");
            assertThrows(tarBlock("PaxHeader", 0, 'x', ""), "pax metadata accepted");
            assertThrows(tarBlock("dev/null", 0, '3', ""), "device node accepted");
        });
        test("tar symlink escapes rejected", "R09", () -> {
            assertThrows(tarBlock("bin/link", 0, '2', "../../etc/passwd"), "escaping symlink accepted");
            assertThrows(tarBlock("bin/link", 0, '2', "/system/bin/sh"), "absolute symlink accepted");
            TarPolicy.Entry ok = TarPolicy.parse(tarBlock("bin/link", 0, '2', "agy"));
            assertEquals("agy", ok.link, "relative symlink rejected");
        });
        test("tar entry size bound enforced", "R09", () -> {
            assertThrows(tarBlock("big.bin", TarPolicy.MAX_ENTRY_BYTES + 1, '0', ""),
                    "oversized entry accepted");
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

        // R01/R23: orphan cleanup is scoped to this flavor's exact files dir.
        test("release scope does not own the dev flavor", "R01", () -> {
            String releaseScope = "/data/user/0/com.agy/files";
            assertFalse(ProcessScope.isOwnedCoreProcess(
                            "/data/user/0/com.agy.dev/files/cli/agy --remote-control",
                            releaseScope),
                    "dev-flavor process matched the release scope");
            assertFalse(ProcessScope.isOwnedCoreProcess(
                            "com.agy helper --remote-control", releaseScope),
                    "bare package substring matched the release scope");
            assertTrue(ProcessScope.isOwnedCoreProcess(
                            "/data/user/0/com.agy/files/cli/agy --remote-control",
                            releaseScope),
                    "own core process was not recognized");
            assertTrue(ProcessScope.isOwnedCoreProcess(
                            "/data/user/0/com.agy/files/runtime/bin/agy-pty",
                            releaseScope),
                    "own helper process was not recognized");
        });

        // C07/R25: concurrent writers must publish one complete payload and
        // never share/leave a temporary or damage the target on failure.
        test("atomic writer publishes one complete payload under concurrency", "C07", () -> {
            File dir = Files.createTempDirectory("agy-atomic-").toFile();
            try {
                final File target = new File(dir, "state.conf");
                assertTrue(AtomicFileWriter.writeText(target, "initial"), "initial write failed");
                final int writers = 8;
                final int size = 64 * 1024;
                final CountDownLatch ready = new CountDownLatch(writers);
                final CountDownLatch go = new CountDownLatch(1);
                Thread[] threads = new Thread[writers];
                for (int i = 0; i < writers; i++) {
                    final int id = i;
                    threads[i] = new Thread(() -> {
                        ready.countDown();
                        try {
                            go.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        AtomicFileWriter.writeText(target, atomicPayload(id, size));
                    });
                    threads[i].start();
                }
                ready.await();
                go.countDown();
                for (Thread thread : threads) {
                    thread.join(20000);
                }
                String content = readAtomicFile(target);
                boolean complete = false;
                for (int i = 0; i < writers; i++) {
                    if (content.equals(atomicPayload(i, size))) {
                        complete = true;
                    }
                }
                assertTrue(complete, "target is not one complete payload (len=" + content.length() + ")");
                File[] leftovers = dir.listFiles();
                if (leftovers != null) {
                    for (File leftover : leftovers) {
                        assertFalse(leftover.getName().contains(".tmp"),
                                "temporary file was left behind: " + leftover.getName());
                    }
                }
            } finally {
                deleteAtomicTree(dir);
            }
        });
        test("failed publish keeps the previous content", "C07", () -> {
            File dir = Files.createTempDirectory("agy-atomic-locked-").toFile();
            try {
                File target = new File(dir, "state.conf");
                assertTrue(AtomicFileWriter.writeText(target, "old"), "seed write failed");
                assertTrue(dir.setWritable(false), "could not make directory read-only");
                try {
                    assertFalse(AtomicFileWriter.writeText(target, "new"),
                            "write into a read-only directory reported success");
                } finally {
                    dir.setWritable(true);
                }
                assertEquals("old", readAtomicFile(target), "previous file was damaged");
            } finally {
                deleteAtomicTree(dir);
            }
        });

        // B06/R33: one transactional CLI installer. Manifest/artifact origins,
        // digest/version syntax, ABI gate, free-space preflight, ELF identity and
        // the persisted install record are pure policy and testable offline.
        test("CLI manifest URL is exact", "B06", () -> {
            assertTrue(CliInstallPolicy.isValidManifestUrl(CliInstallPolicy.MANIFEST_URL),
                    "official manifest URL was rejected");
            assertFalse(CliInstallPolicy.isValidManifestUrl(CliInstallPolicy.MANIFEST_URL + "?x=1"),
                    "manifest URL with a query was accepted");
            assertFalse(CliInstallPolicy.isValidManifestUrl(
                            "http://antigravity-cli-auto-updater-974169037036.us-central1.run.app"
                                    + "/manifests/android_arm64.json"),
                    "HTTP manifest URL was accepted");
            assertFalse(CliInstallPolicy.isValidManifestUrl(
                            "https://attacker.example/manifests/android_arm64.json"),
                    "foreign manifest URL was accepted");
            assertFalse(CliInstallPolicy.isValidManifestUrl(null), "null manifest URL was accepted");
        });
        test("CLI artifact URL restricted to the vendor HTTPS origin", "B06", () -> {
            assertTrue(CliInstallPolicy.isValidArtifactUrl(ARTIFACT_URL),
                    "official artifact URL was rejected");
            assertFalse(CliInstallPolicy.isValidArtifactUrl(
                            ARTIFACT_URL.replace("https://", "http://")),
                    "HTTP artifact URL was accepted");
            assertFalse(CliInstallPolicy.isValidArtifactUrl(
                            ARTIFACT_URL.replace("storage.googleapis.com",
                                    "storage.googleapis.com.attacker.example")),
                    "lookalike artifact host was accepted");
            assertFalse(CliInstallPolicy.isValidArtifactUrl(
                            "https://user:pass@storage.googleapis.com/antigravity-public/"
                                    + "antigravity-cli/cli_android_arm64.tar.gz"),
                    "artifact URL with credentials was accepted");
            assertFalse(CliInstallPolicy.isValidArtifactUrl(
                            ARTIFACT_URL.replace("storage.googleapis.com",
                                    "storage.googleapis.com:8443")),
                    "artifact URL with a non-default port was accepted");
            assertFalse(CliInstallPolicy.isValidArtifactUrl(ARTIFACT_URL + "?token=1"),
                    "artifact URL with a query was accepted");
            assertFalse(CliInstallPolicy.isValidArtifactUrl(
                            "https://storage.googleapis.com/antigravity-public/antigravity-cli/"
                                    + "../etc/passwd.tar.gz"),
                    "artifact path traversal was accepted");
            assertFalse(CliInstallPolicy.isValidArtifactUrl(
                            "https://storage.googleapis.com/antigravity-public/antigravity-cli/"
                                    + "a%2Fb.tar.gz"),
                    "encoded artifact path separator was accepted");
            assertFalse(CliInstallPolicy.isValidArtifactUrl(
                            "https://storage.googleapis.com/other/cli_android_arm64.tar.gz"),
                    "artifact outside the vendor path was accepted");
        });
        test("CLI digest and version syntax is strict", "B06", () -> {
            assertEquals(SHA512, CliInstallPolicy.normalizeSha512(SHA512.toUpperCase()),
                    "digest was not normalized");
            assertTrue(CliInstallPolicy.normalizeSha512(SHA512.substring(1)) == null,
                    "short digest was accepted");
            assertTrue(CliInstallPolicy.normalizeSha512(SHA512.substring(0, 127) + "g") == null,
                    "non-hex digest was accepted");
            assertTrue(CliInstallPolicy.normalizeSha512(" ") == null, "blank digest was accepted");
            assertTrue(CliInstallPolicy.isValidVersion("1.5.3"), "valid version was rejected");
            assertFalse(CliInstallPolicy.isValidVersion("1.5.3\nrm -rf"),
                    "version with a control character was accepted");
            assertFalse(CliInstallPolicy.isValidVersion("../1.5.3"),
                    "version with a path was accepted");
            assertFalse(CliInstallPolicy.isValidVersion(""), "empty version was accepted");
        });
        test("CLI install requires arm64-v8a", "B06", () -> {
            assertTrue(CliInstallPolicy.isArm64Device(
                            new String[]{"arm64-v8a", "armeabi-v7a"}),
                    "arm64 device was rejected");
            assertFalse(CliInstallPolicy.isArm64Device(new String[]{"x86_64"}),
                    "x86_64 device was accepted");
            assertFalse(CliInstallPolicy.isArm64Device(new String[0]),
                    "empty ABI list was accepted");
            assertFalse(CliInstallPolicy.isArm64Device(null), "null ABI list was accepted");
        });
        test("CLI free-space preflight is conservative", "B06", () -> {
            long archive = 40L * 1024 * 1024;
            assertTrue(CliInstallPolicy.requiredFreeBytes(archive) > archive * 2,
                    "free-space reserve is not conservative");
            assertTrue(CliInstallPolicy.hasFreeSpace(
                            CliInstallPolicy.requiredFreeBytes(archive), archive),
                    "exact free-space requirement was rejected");
            assertFalse(CliInstallPolicy.hasFreeSpace(
                            CliInstallPolicy.requiredFreeBytes(archive) - 1, archive),
                    "insufficient free space was accepted");
            assertEquals(-1L, CliInstallPolicy.requiredFreeBytes(0),
                    "zero-size archive was planned");
            assertEquals(-1L,
                    CliInstallPolicy.requiredFreeBytes(CliInstallPolicy.MAX_ARCHIVE_BYTES + 1),
                    "oversized archive was planned");
        });
        test("aarch64 Android ELF accepted and wrong targets rejected", "B06", () -> {
            File dir = Files.createTempDirectory("agy-elf-").toFile();
            try {
                File ok = new File(dir, "ok");
                writeElfFixture(ok, 2, 0xB7, true);
                assertTrue(CliInstallPolicy.elfProblem(ok) == null,
                        "valid ELF was rejected: " + CliInstallPolicy.elfProblem(ok));

                File noLinker = new File(dir, "no-linker");
                writeElfFixture(noLinker, 2, 0xB7, false);
                assertFalse(CliInstallPolicy.elfProblem(noLinker) == null,
                        "ELF without the Android linker was accepted");

                File x86 = new File(dir, "x86");
                writeElfFixture(x86, 2, 0x3E, true);
                assertFalse(CliInstallPolicy.elfProblem(x86) == null,
                        "x86_64 ELF was accepted");

                File elf32 = new File(dir, "elf32");
                writeElfFixture(elf32, 1, 0xB7, true);
                assertFalse(CliInstallPolicy.elfProblem(elf32) == null,
                        "ELF32 was accepted");

                File text = new File(dir, "text");
                Files.write(text.toPath(), "not an elf\n".getBytes(StandardCharsets.US_ASCII));
                assertFalse(CliInstallPolicy.elfProblem(text) == null,
                        "plain text was accepted as an ELF");
            } finally {
                deleteAtomicTree(dir);
            }
        });
        test("CLI install record round-trips and rejects corruption", "B06", () -> {
            CliInstallMetadata metadata = new CliInstallMetadata(
                    CliInstallMetadata.SCHEMA, "1.5.3", SHA512, ARTIFACT_URL,
                    repeatedHex('a', 64), 1763000000L);
            String text = metadata.serialize();
            CliInstallMetadata parsed = CliInstallMetadata.parse(text);
            assertTrue(parsed != null, "install record did not parse");
            assertEquals("1.5.3", parsed.version, "version");
            assertEquals(SHA512, parsed.artifactSha512, "artifact digest");
            assertEquals(ARTIFACT_URL, parsed.sourceUrl, "source URL");
            assertEquals(1763000000L, parsed.installedAtEpochSeconds, "installed_at");
            assertTrue(CliInstallMetadata.parse(text.replace("schema=1", "schema=2")) == null,
                    "unknown schema was accepted");
            assertTrue(CliInstallMetadata.parse(text.replace("version=1.5.3\n", "")) == null,
                    "missing version was accepted");
            assertTrue(CliInstallMetadata.parse(text + "extra=1\n") == null,
                    "unknown key was accepted");
            assertTrue(CliInstallMetadata.parse(text.replace("sha512=", "sha512=zz")) == null,
                    "invalid artifact digest was accepted");
            assertTrue(CliInstallMetadata.parse(text.replace("source=" + ARTIFACT_URL,
                            "source=https://attacker.example/x.tar.gz")) == null,
                    "foreign source URL was accepted");
            assertTrue(CliInstallMetadata.parse(text + repeatedHex('x',
                            CliInstallMetadata.MAX_BYTES)) == null,
                    "oversized install record was accepted");
        });

        // F02/R34: the share provider must expose only read-only files under
        // filesDir/share, never credentials, the CLI, the prefix or app data.
        test("share policy exposes only read-only files under share/", "R34", () -> {
            File files = Files.createTempDirectory("agy-share-").toFile();
            try {
                File share = new File(files, SharePathPolicy.SHARE_DIR);
                File docs = new File(share, "docs");
                assertTrue(docs.mkdirs(), "cannot create the share tree");
                File inside = new File(docs, "notes.txt");
                Files.write(inside.toPath(), "hello".getBytes(StandardCharsets.UTF_8));
                File secret = new File(files, ".gemini/token");
                assertTrue(secret.getParentFile().mkdirs(), "cannot create credential fixture");
                Files.write(secret.toPath(), "secret".getBytes(StandardCharsets.UTF_8));

                assertEquals("docs/notes.txt", SharePathPolicy.relativeId(share, inside),
                        "share id");
                File resolved = SharePathPolicy.resolve(share, "/docs/notes.txt");
                assertTrue(resolved != null, "share id did not resolve");
                assertEquals(inside.getCanonicalPath(), resolved.getCanonicalPath(),
                        "resolved share file");
                assertTrue(SharePathPolicy.relativeId(share, secret) == null,
                        "credentials were shareable");
                assertTrue(SharePathPolicy.relativeId(share, files) == null,
                        "app root was shareable");
                assertTrue(SharePathPolicy.relativeId(share, share) == null,
                        "share root was shareable");
                assertTrue(SharePathPolicy.resolve(share, "../.gemini/token") == null,
                        "traversal id resolved");
                File absolute = SharePathPolicy.resolve(share,
                        "/data/user/0/com.agy/files/.gemini/token");
                assertTrue(absolute == null
                                || absolute.getCanonicalPath().startsWith(
                                        share.getCanonicalPath() + File.separator),
                        "absolute app path escaped the share root");
                assertTrue(SharePathPolicy.resolve(share, "docs/./notes.txt") == null,
                        "dot segment resolved");
                assertTrue(SharePathPolicy.resolve(share, "docs//notes.txt") == null,
                        "empty segment resolved");
                assertTrue(SharePathPolicy.resolve(share, "docs/notes\u0000") == null,
                        "control character resolved");
                assertTrue(SharePathPolicy.isReadOnlyMode("r"), "'r' mode rejected");
                assertFalse(SharePathPolicy.isReadOnlyMode("w"), "write mode accepted");
                assertFalse(SharePathPolicy.isReadOnlyMode("rw"), "read-write mode accepted");
                assertFalse(SharePathPolicy.isReadOnlyMode("wa"), "append mode accepted");
                assertFalse(SharePathPolicy.isReadOnlyMode(null), "null mode accepted");

                File link = new File(share, "escape");
                try {
                    Files.createSymbolicLink(link.toPath(), secret.toPath());
                    assertTrue(SharePathPolicy.resolve(share, "escape") == null,
                            "symlink escape resolved");
                } catch (UnsupportedOperationException | java.io.IOException ignored) {
                    // Filesystem without symlink support; containment is still
                    // covered by the traversal and absolute-path cases.
                }
            } finally {
                deleteAtomicTree(files);
            }
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

    private static void assertThrows(byte[] block, String message) throws Exception {
        try {
            TarPolicy.parse(block);
        } catch (java.io.IOException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void putField(byte[] block, int offset, int length, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, block, offset, Math.min(bytes.length, length));
    }

    /** Builds a valid ustar header fixture with a correct checksum. */
    private static byte[] tarBlock(String name, long size, int type, String link) {
        byte[] block = new byte[512];
        putField(block, 0, 100, name);
        putField(block, 100, 8, "0000644");
        putField(block, 108, 8, "0000000");
        putField(block, 116, 8, "0000000");
        putField(block, 124, 12, String.format("%011o", size));
        putField(block, 136, 12, "00000000000");
        block[156] = (byte) type;
        putField(block, 157, 100, link);
        putField(block, 257, 6, "ustar");
        putField(block, 263, 2, "00");
        recomputeChecksum(block);
        return block;
    }

    private static void recomputeChecksum(byte[] block) {
        for (int i = 148; i < 156; i++) {
            block[i] = ' ';
        }
        long sum = 0;
        for (byte b : block) {
            sum += (b & 0xFF);
        }
        putField(block, 148, 8, String.format("%06o\u0000 ", sum));
    }

    private static String atomicPayload(int id, int size) {
        StringBuilder sb = new StringBuilder(size);
        String header = "payload-" + id + "-";
        while (sb.length() < size) {
            sb.append(header);
        }
        return sb.substring(0, size);
    }

    private static String readAtomicFile(File file) throws Exception {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) file.length()];
            int offset = 0;
            int read;
            while (offset < buffer.length && (read = in.read(buffer, offset, buffer.length - offset)) > 0) {
                offset += read;
            }
            return new String(buffer, 0, offset, StandardCharsets.UTF_8);
        }
    }

    /** Builds a bounded ELF fixture with a valid header and optional linker path. */
    private static void writeElfFixture(File file, int elfClass, int machine, boolean linker)
            throws Exception {
        byte[] data = new byte[1024];
        data[0] = 0x7F;
        data[1] = 'E';
        data[2] = 'L';
        data[3] = 'F';
        data[4] = (byte) elfClass;
        data[5] = 1;
        data[6] = 1;
        data[16] = 3;
        data[18] = (byte) (machine & 0xFF);
        data[19] = (byte) ((machine >> 8) & 0xFF);
        if (linker) {
            byte[] needle = "/system/bin/linker64".getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(needle, 0, data, 200, needle.length);
        }
        Files.write(file.toPath(), data);
    }

    private static String repeatedHex(char value, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(value);
        }
        return sb.toString();
    }

    private static void deleteAtomicTree(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteAtomicTree(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
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
