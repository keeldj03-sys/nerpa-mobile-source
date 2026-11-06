#!/usr/bin/env python3
"""Check that the geodata groups the Nerpa routing presets name actually exist.

Xray resolves `geosite:foo` / `geoip:foo` against the .dat files bundled inside libv2ray.aar, and a
group it does not know is not an error: it matches nothing, so the mode keeps its label while
quietly stopping to do the one thing the label promises. `NerpaRouting` names three groups that
upstream presets never used, so they have to be re-checked on every core bump:

    tool/verify-geodata.py V2rayNG/app/libs/libv2ray.aar
    tool/verify-geodata.py V2rayNG/app/build/intermediates/assets/playstoreDebug/mergePlaystoreDebugAssets

Exit code is non-zero when a file or a group is missing. See FORK.md.
"""

import re
import struct
import sys
import tempfile
import zipfile
from pathlib import Path

# Group -> the .dat that has to carry it. `geoip:private` is rewritten to
# ext:geoip-only-cn-private.dat:private by CoreConfigManager.appendRoutingUserRule.
REQUIRED = {
    "geosite.dat": ["category-ads-all", "private", "category-ru"],
    "geoip.dat": ["ru"],
    "geoip-only-cn-private.dat": ["private"],
}


def read_varint(buf, i):
    value = shift = 0
    while True:
        byte = buf[i]
        i += 1
        value |= (byte & 0x7F) << shift
        if not byte & 0x80:
            return value, i
        shift += 7


def protobuf_fields(buf):
    """Walk one protobuf message; unknown wire formats abort the walk for that message."""
    i = 0
    while i < len(buf):
        try:
            key, i = read_varint(buf, i)
        except IndexError:
            return
        field, wire = key >> 3, key & 7
        if wire == 2:
            length, i = read_varint(buf, i)
            yield field, buf[i:i + length]
            i += length
        elif wire == 0:
            value, i = read_varint(buf, i)
            yield field, value
        elif wire in (1, 5):
            size = 8 if wire == 1 else 4
            yield field, buf[i:i + size]
            i += size
        else:
            return


def codes(path):
    """The first string field of every top-level entry, which is the group name in both formats."""
    data = path.read_bytes()
    found = set()
    for field, entry in protobuf_fields(data):
        if field != 1 or not isinstance(entry, bytes):
            continue
        for sub, value in protobuf_fields(entry):
            if sub == 1 and isinstance(value, bytes):
                found.add(value.decode("utf-8", "replace"))
                break
    return found


def locate(source):
    """Return a directory holding the three .dat files, unpacking an .aar if needed."""
    source = Path(source)
    if source.is_dir():
        return source
    with zipfile.ZipFile(source) as archive:
        members = [n for n in archive.namelist() if re.fullmatch(r"assets/[^/]+\.dat", n)]
        out = Path(tempfile.mkdtemp(prefix="nerpa-geodata-"))
        for name in members:
            with archive.open(name) as src, open(out / Path(name).name, "wb") as dst:
                dst.write(src.read())
        return out


def main(argv):
    if len(argv) != 2:
        print("usage: tool/verify-geodata.py <libv2ray.aar | directory holding the .dat files>")
        return 2
    folder = locate(argv[1])
    failed = []
    for filename, groups in REQUIRED.items():
        path = folder / filename
        if not path.is_file():
            print(f"MISSING FILE  {filename}")
            failed.append(filename)
            continue
        available = {c.upper() for c in codes(path)}
        for group in groups:
            if group.upper() in available:
                print(f"ok            {filename}  {group}")
            else:
                print(f"MISSING GROUP {filename}  {group}")
                failed.append(f"{filename}:{group}")
    if failed:
        print(f"\n{len(failed)} routing rule(s) would silently match nothing.")
        return 1
    print("\nAll groups named by NerpaRouting are present.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
