#!/usr/bin/env python3
"""
align.py - 4-byte APK ZipAlign utility.

Uses system zipalign if available; otherwise falls back to a pure-Python ZIP
rewriter. The rewriter treats the central directory as authoritative: local
headers are rebuilt with the real CRC/sizes and without data-descriptor flag
bit 3, so archives produced by streaming writers stay valid after alignment.
Unsupported inputs (ZIP64, encryption, multi-disk, unknown compression) are
rejected instead of being copied into a corrupt "successful" output.
"""
import os
import shutil
import struct
import subprocess
import sys
import zipfile

_LOCAL_HEADER = struct.Struct("<IHHHHHIIIHH")
_CENTRAL_HEADER = struct.Struct("<IHHHHHHIIIHHHHHII")
_EOCD = struct.Struct("<IHHHHIIH")

_LOCAL_SIG = 0x04034B50
_CENTRAL_SIG = 0x02014B50
_EOCD_SIG = 0x06054B50
_FLAG_DATA_DESCRIPTOR = 0x0008
_FLAG_ENCRYPTED = 0x0001
_PADDING_IDS = (0xD5D9, 0xD935)
_MAX_UINT32 = 0xFFFFFFFF
_SUPPORTED_METHODS = (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED)


class UnsupportedArchiveError(ValueError):
    """A ZIP feature this aligner must not silently rewrite."""


def align_with_binary(in_apk, out_apk, alignment=4):
    if shutil.which("zipalign"):
        res = subprocess.run(["zipalign", "-f", "-p", str(alignment), in_apk, out_apk],
                             capture_output=True)
        return res.returncode == 0
    return False


def dostime(dt):
    year, month, day, hour, minute, second = dt
    dosdate = max(0, min(65535, ((year - 1980) << 9) | (month << 5) | day))
    dostime_val = max(0, min(65535, (hour << 11) | (minute << 5) | (second // 2)))
    return dostime_val, dosdate


def _strip_padding_extra(extra):
    """Preserve unknown extras, drop any previous zipalign padding."""
    out = bytearray()
    i = 0
    while i < len(extra):
        if i + 4 > len(extra):
            raise UnsupportedArchiveError("truncated local extra field")
        header_id, size = struct.unpack("<HH", extra[i:i + 4])
        end = i + 4 + size
        if end > len(extra):
            raise UnsupportedArchiveError("truncated local extra field")
        if header_id not in _PADDING_IDS:
            out += extra[i:end]
        i = end
    return bytes(out)


def _padding_for(offset, name_len, extra_len, alignment):
    current = offset + _LOCAL_HEADER.size + name_len + extra_len
    remainder = current % alignment
    if remainder == 0:
        return b""
    pad_len = alignment - remainder
    while pad_len < 4:
        pad_len += alignment
    return struct.pack("<HH", 0xD5D9, pad_len - 4) + (b"\x00" * (pad_len - 4))


def align_with_python(in_apk, out_apk, alignment=4):
    """Align uncompressed entry payloads while preserving bytes, CRCs and extras."""
    if alignment <= 0 or (alignment & (alignment - 1)) != 0:
        raise ValueError("alignment must be a positive power of two: %r" % (alignment,))

    total_size = os.path.getsize(in_apk)
    with zipfile.ZipFile(in_apk, "r") as zin, open(in_apk, "rb") as fin, \
            open(out_apk, "wb") as fout:
        infos = zin.infolist()
        if not infos:
            raise UnsupportedArchiveError("archive has no entries")
        if len(infos) > 0xFFFE:
            raise UnsupportedArchiveError("too many entries for a non-ZIP64 archive")

        central = []
        for info in infos:
            if info.flag_bits & _FLAG_ENCRYPTED:
                raise UnsupportedArchiveError("encrypted entry %r" % info.filename)
            if info.compress_type not in _SUPPORTED_METHODS:
                raise UnsupportedArchiveError(
                    "unsupported compression method %d for %r"
                    % (info.compress_type, info.filename))
            if (_MAX_UINT32 in (info.file_size, info.compress_size, info.header_offset)):
                raise UnsupportedArchiveError("ZIP64 entry %r is unsupported" % info.filename)

            fin.seek(info.header_offset)
            header = fin.read(_LOCAL_HEADER.size)
            if len(header) < _LOCAL_HEADER.size:
                raise UnsupportedArchiveError("truncated local header at %d" % info.header_offset)
            (sig, _ver, _flag, method, _time, _date, _crc, local_csize,
             local_usize, name_len, extra_len) = _LOCAL_HEADER.unpack(header)
            if sig != _LOCAL_SIG:
                raise UnsupportedArchiveError(
                    "bad local header signature at %d" % info.header_offset)
            if method != info.compress_type:
                raise UnsupportedArchiveError(
                    "local/central compression mismatch for %r" % info.filename)
            # A streaming writer leaves zeros here and appends a data descriptor;
            # anything else must agree with the authoritative central directory.
            if local_csize not in (0, info.compress_size):
                raise UnsupportedArchiveError(
                    "local compressed size disagrees for %r" % info.filename)
            if local_usize not in (0, info.file_size):
                raise UnsupportedArchiveError(
                    "local uncompressed size disagrees for %r" % info.filename)

            data_offset = info.header_offset + _LOCAL_HEADER.size + name_len + extra_len
            data_end = data_offset + info.compress_size
            if data_offset > total_size or data_end > total_size:
                raise UnsupportedArchiveError("entry %r runs past end of archive" % info.filename)

            filename_bytes = info.filename.encode("utf-8")
            extra = bytearray(_strip_padding_extra(info.extra or b""))
            flags = info.flag_bits & ~_FLAG_DATA_DESCRIPTOR
            entry_align = 4096 if (info.filename.endswith(".so")
                                   and info.compress_type == zipfile.ZIP_STORED) else alignment
            local_header_offset = fout.tell()
            if info.compress_type == zipfile.ZIP_STORED:
                extra += _padding_for(local_header_offset, len(filename_bytes), len(extra),
                                      entry_align)
            if len(extra) > 0xFFFF:
                raise UnsupportedArchiveError("extra field too large for %r" % info.filename)

            fout.write(_LOCAL_HEADER.pack(
                _LOCAL_SIG,
                info.extract_version,
                flags,
                info.compress_type,
                _time,
                _date,
                info.CRC,
                info.compress_size,
                info.file_size,
                len(filename_bytes),
                len(extra),
            ))
            fout.write(filename_bytes)
            fout.write(extra)

            fin.seek(data_offset)
            remaining = info.compress_size
            while remaining > 0:
                chunk = fin.read(min(remaining, 65536))
                if not chunk:
                    raise UnsupportedArchiveError("unexpected EOF in %r" % info.filename)
                fout.write(chunk)
                remaining -= len(chunk)

            central.append((info, filename_bytes, bytes(extra), flags, local_header_offset))

        cd_start = fout.tell()
        for info, filename_bytes, extra, flags, local_header_offset in central:
            comment = info.comment or b""
            mtime, mdate = dostime(info.date_time)
            fout.write(_CENTRAL_HEADER.pack(
                _CENTRAL_SIG,
                info.create_version,
                info.extract_version,
                flags,
                info.compress_type,
                mtime,
                mdate,
                info.CRC,
                info.compress_size,
                info.file_size,
                len(filename_bytes),
                len(extra),
                len(comment),
                0,
                info.internal_attr,
                info.external_attr,
                local_header_offset,
            ))
            fout.write(filename_bytes)
            fout.write(extra)
            fout.write(comment)

        cd_size = fout.tell() - cd_start
        if cd_start > _MAX_UINT32 or cd_size > _MAX_UINT32:
            raise UnsupportedArchiveError("archive requires ZIP64")
        comment = zin.comment or b""
        if len(comment) > 0xFFFF:
            raise UnsupportedArchiveError("archive comment too long")
        fout.write(_EOCD.pack(
            _EOCD_SIG,
            0,
            0,
            len(central),
            len(central),
            cd_size,
            cd_start,
            len(comment),
        ))
        fout.write(comment)
    return True


def main():
    if len(sys.argv) < 3:
        print("Usage: %s <in_apk> <out_apk> [alignment]" % sys.argv[0], file=sys.stderr)
        sys.exit(1)

    in_apk = sys.argv[1]
    out_apk = sys.argv[2]
    alignment = int(sys.argv[3]) if len(sys.argv) > 3 else 4

    if not os.path.isfile(in_apk):
        print("Error: input file %s not found" % in_apk, file=sys.stderr)
        sys.exit(1)

    if align_with_binary(in_apk, out_apk, alignment):
        print("[OK] ZipAligned using binary: %s" % out_apk)
    else:
        align_with_python(in_apk, out_apk, alignment)
        print("[OK] ZipAligned using Python 4-byte aligner: %s" % out_apk)


if __name__ == "__main__":
    main()
