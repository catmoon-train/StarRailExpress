"""校验 Ponderer 结构 NBT 是否能被 Minecraft 正常读取。

用法：在项目根目录执行
    python tools/verify_ponder_structures.py

检查项：
  1. 解压后能完整解析（消耗字节数必须等于文件长度），否则 Minecraft 会报 ReportedNbtException。
  2. 根标签包含 size / entities / blocks / palette。
  3. palette 里不得出现 minecraft: 开头的方块。
"""

import gzip
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
STRUCTURES = ROOT / "src/main/resources/assets/starrailexpress/ponder"


def read_string(data, offset):
    (length,) = struct.unpack_from(">H", data, offset)
    offset += 2
    return data[offset:offset + length].decode("utf-8"), offset + length


def read_payload(data, offset, tag):
    if tag == 1:
        return data[offset], offset + 1
    if tag == 2:
        return struct.unpack_from(">h", data, offset)[0], offset + 2
    if tag == 3:
        return struct.unpack_from(">i", data, offset)[0], offset + 4
    if tag == 4:
        return struct.unpack_from(">q", data, offset)[0], offset + 8
    if tag == 5:
        return struct.unpack_from(">f", data, offset)[0], offset + 4
    if tag == 6:
        return struct.unpack_from(">d", data, offset)[0], offset + 8
    if tag == 7:
        (length,) = struct.unpack_from(">i", data, offset)
        return data[offset + 4:offset + 4 + length], offset + 4 + length
    if tag == 8:
        return read_string(data, offset)
    if tag == 9:
        (element,) = struct.unpack_from(">b", data, offset)
        (count,) = struct.unpack_from(">i", data, offset + 1)
        offset += 5
        items = []
        for _ in range(count):
            value, offset = read_payload(data, offset, element)
            items.append(value)
        return items, offset
    if tag == 10:
        result = {}
        while True:
            (child,) = struct.unpack_from(">b", data, offset)
            offset += 1
            if child == 0:
                return result, offset
            key, offset = read_string(data, offset)
            value, offset = read_payload(data, offset, child)
            result[key] = value
    if tag == 11:
        (count,) = struct.unpack_from(">i", data, offset)
        offset += 4
        return list(struct.unpack_from(">" + "i" * count, data, offset)), offset + 4 * count
    if tag == 12:
        (count,) = struct.unpack_from(">i", data, offset)
        offset += 4
        return list(struct.unpack_from(">" + "q" * count, data, offset)), offset + 8 * count
    raise ValueError("unknown tag %d at %d" % (tag, offset))


def check(path):
    raw = path.read_bytes()
    if raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)
    (tag,) = struct.unpack_from(">b", raw, 0)
    _, offset = read_string(raw, 1)
    root, consumed = read_payload(raw, offset, tag)
    if consumed != len(raw):
        raise ValueError("%s: parsed %d of %d bytes" % (path.name, consumed, len(raw)))
    for key in ("size", "entities", "blocks", "palette"):
        if key not in root:
            raise ValueError("%s: missing root key %s" % (path.name, key))
    palette = [entry.get("Name") for entry in root["palette"]]
    vanilla = [block for block in palette if block and block.startswith("minecraft:")]
    if vanilla:
        raise ValueError("%s: vanilla blocks in palette %s" % (path.name, vanilla))
    print("%s ok  size=%s blocks=%d palette=%s" % (
        path.name, root["size"], len(root["blocks"]), palette))


if __name__ == "__main__":
    for structure in sorted(STRUCTURES.glob("*.nbt")):
        check(structure)
