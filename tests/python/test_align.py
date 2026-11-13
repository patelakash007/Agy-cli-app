#!/usr/bin/env python3
"""Offline regression tests for modules/builder/align.py.

The tests exercise the pure-Python aligner directly so they do not depend on a
system zipalign binary. Fixtures are written under build-test/.
"""
import importlib.util
import io
import struct
import tempfile
import unittest
import zipfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
ALIGN_SCRIPT = REPO_ROOT / "modules" / "builder" / "align.py"
BUILD_TEST_ROOT = REPO_ROOT / "build-test"

_spec = importlib.util.spec_from_file_location("agy_align", ALIGN_SCRIPT)
align = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(align)


class NonSeekable(io.RawIOBase):
    """File-like sink that makes zipfile emit a data descriptor."""

    def __init__(self):
        self.buf = bytearray()

    def writable(self):
        return True

    def write(self, data):
        self.buf.extend(data)
        return len(data)

    def seekable(self):
        return False

    def tell(self):
        return len(self.buf)


class AlignTests(unittest.TestCase):
    def setUp(self):
        BUILD_TEST_ROOT.mkdir(parents=True, exist_ok=True)
        self.tmp = Path(tempfile.mkdtemp(prefix="align-", dir=BUILD_TEST_ROOT))

    def tearDown(self):
        import shutil

        shutil.rmtree(self.tmp, ignore_errors=True)

    def make_zip(self, name, entries, compression=zipfile.ZIP_STORED, extra=None, data_descriptor=False):
        path = self.tmp / name
        if data_descriptor:
            sink = NonSeekable()
            with zipfile.ZipFile(sink, "w", compression=compression) as zf:
                for entry_name, payload in entries:
                    zf.writestr(entry_name, payload)
            path.write_bytes(bytes(sink.buf))
        else:
            with zipfile.ZipFile(path, "w", compression=compression) as zf:
                for entry_name, payload in entries:
                    info = zipfile.ZipInfo(entry_name)
                    info.compress_type = compression
                    if extra is not None:
                        info.extra = extra
                    zf.writestr(info, payload)
        return path

    def align_and_read(self, source, alignment=4):
        output = self.tmp / (source.stem + "-aligned.zip")
        align.align_with_python(str(source), str(output), alignment)
        with zipfile.ZipFile(output) as zf:
            payloads = {name: zf.read(name) for name in zf.namelist()}
            bad = zf.testzip()
        return output, payloads, bad

    def test_stored_payload_preserved(self):
        entries = [("classes.dex", b"dex-payload" * 32), ("assets/readme.txt", b"hello" * 64)]
        source = self.make_zip("stored.zip", entries)
        _, payloads, bad = self.align_and_read(source)
        self.assertIsNone(bad)
        self.assertEqual(payloads, dict(entries))

    def test_deflated_payload_preserved(self):
        entries = [("classes.dex", b"dex-payload" * 32), ("assets/readme.txt", b"hello" * 64)]
        source = self.make_zip("deflated.zip", entries, compression=zipfile.ZIP_DEFLATED)
        _, payloads, bad = self.align_and_read(source)
        self.assertIsNone(bad)
        self.assertEqual(payloads, dict(entries))

    def test_extra_fields_preserved(self):
        extra = b"\xfe\xca\x04\x00test"
        entries = [("classes.dex", b"payload")]
        source = self.make_zip("extra.zip", entries, compression=zipfile.ZIP_DEFLATED, extra=extra)
        output, payloads, bad = self.align_and_read(source)
        self.assertIsNone(bad)
        self.assertEqual(payloads, dict(entries))
        with zipfile.ZipFile(output) as zf:
            self.assertEqual(zf.getinfo("classes.dex").extra, extra)

    def test_non_ascii_filename_preserved(self):
        entries = [("assets/日本語.txt", b"unicode-payload")]
        source = self.make_zip("unicode.zip", entries)
        _, payloads, bad = self.align_and_read(source)
        self.assertIsNone(bad)
        self.assertEqual(payloads, dict(entries))

    def test_stored_entries_are_aligned(self):
        entries = [("classes.dex", b"x" * 100), ("assets/data.bin", b"y" * 100)]
        source = self.make_zip("aligned.zip", entries)
        output, _, bad = self.align_and_read(source)
        self.assertIsNone(bad)
        with zipfile.ZipFile(output) as zf, open(output, "rb") as raw:
            for info in zf.infolist():
                if info.compress_type != zipfile.ZIP_STORED:
                    continue
                raw.seek(info.header_offset)
                header = raw.read(30)
                _, _, _, _, _, _, _, _, _, name_len, extra_len = struct.unpack("<IHHHHHIIIHH", header)
                data_offset = info.header_offset + 30 + name_len + extra_len
                self.assertEqual(data_offset % 4, 0, info.filename)

    def test_data_descriptor_payload_preserved(self):
        """R19: local-header compressed size is zero when a data descriptor is used."""
        entries = [("classes.dex", b"dex-payload" * 32)]
        source = self.make_zip("descriptor.zip", entries, data_descriptor=True)
        try:
            _, payloads, bad = self.align_and_read(source)
        except Exception as exc:  # noqa: BLE001 - report the known failure clearly
            self.fail(f"R19: align.py produced an unreadable ZIP for a data descriptor: {exc}")
        self.assertIsNone(bad, "R19: aligned ZIP failed CRC validation")
        self.assertEqual(payloads, dict(entries), "R19: payload was not preserved")

    def test_archive_comment_preserved(self):
        entries = [("classes.dex", b"payload")]
        source = self.make_zip("commented.zip", entries)
        with zipfile.ZipFile(source, "a") as zf:
            zf.comment = b"signed-by-test"
        output, payloads, bad = self.align_and_read(source)
        self.assertIsNone(bad)
        self.assertEqual(payloads, dict(entries))
        with zipfile.ZipFile(output) as zf:
            self.assertEqual(zf.comment, b"signed-by-test")

    def test_truncated_input_is_rejected(self):
        source = self.tmp / "truncated.zip"
        source.write_bytes(b"PK\x03\x04" + b"\x00" * 10)
        output = self.tmp / "truncated-aligned.zip"
        with self.assertRaises((zipfile.BadZipFile, ValueError, OSError)):
            align.align_with_python(str(source), str(output), 4)


if __name__ == "__main__":
    unittest.main()
