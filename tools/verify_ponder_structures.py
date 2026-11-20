"""校验 Ponderer 结构 NBT 是否能被 Minecraft 正常读取。
用法：在项目根目录执行
    python tools/verify_ponder_structures.py

检查项（对齐 Minecraft 1.21.1 StructureTemplate.load 的实际读取行为）：
  1. 解压后能完整解析（消耗字节数必须等于文件长度），否则 Minecraft 会报 ReportedNbtException。
  2. 根标签包含 size / entities / blocks，且 palette 或 palettes 至少一个。
  3. size 与 blocks[].pos 必须是 TAG_List<Int>（读成 IntArray 会让结构退化成 0x0x0）。
  4. 调色板里不得出现 minecraft: 开头的方块，且 palette 索引必须在范围内。
"""

import gzip
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
STRUCTURES = ROOT / "src/main/resources/assets/starrailexpress/ponder"

TAG_INT = 3
TAG_LIST = 9


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
        return (element, items), offset
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

def int_list(root, key, where):
    """Minecraft 用 getList(key, 3) 读整数三元组；类型不对会得到空列表。"""
    if key not in root:
        raise ValueError("%s: missing %s" % (where, key))
    value = root[key]
    if not isinstance(value, tuple):
        raise ValueError("%s: %s must be TAG_List<TAG_Int>, got TAG_Int_Array "
                         "(Minecraft reads an empty list and collapses the structure)" % (where, key))
    element, items = value
    if element != TAG_INT:
        raise ValueError("%s: %s must be TAG_List<TAG_Int>, got element type %d "
                         "(Minecraft reads an empty list and collapses the structure)"
                         % (where, key, element))
    return items


def collect_palettes(root, where):
    if "palettes" in root:
        element, lists = root["palettes"]
        if element != TAG_LIST:
            raise ValueError("%s: palettes must be TAG_List<TAG_List>" % where)
        palettes = []
        for index, (inner_element, entries) in enumerate(lists):
            if inner_element != 10:
                raise ValueError("%s: palettes[%d] must be TAG_List<TAG_Compound>" % (where, index))
            palettes.append(entries)
        return palettes
    if "palette" in root:
        element, entries = root["palette"]
        if element != 10:
            raise ValueError("%s: palette must be TAG_List<TAG_Compound>" % where)
        return [entries]
    raise ValueError("%s: missing palette or palettes" % where)


def check(path):
    raw = path.read_bytes()
    if raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)
    (tag,) = struct.unpack_from(">b", raw, 0)
    _, offset = read_string(raw, 1)
    root, consumed = read_payload(raw, offset, tag)
    if consumed != len(raw):
        raise ValueError("%s: parsed %d of %d bytes" % (path.name, consumed, len(raw)))

    where = path.name
    size = int_list(root, "size", where)
    if len(size) != 3:
        raise ValueError("%s: size must have 3 entries, got %d" % (where, len(size)))
    for key in ("entities", "blocks"):
        if key not in root:
            raise ValueError("%s: missing root key %s" % (where, key))

    palettes = collect_palettes(root, where)
    for index, entries in enumerate(palettes):
        names = [entry.get("Name") for entry in entries]
        vanilla = [block for block in names if block and block.startswith("minecraft:")]
        if vanilla:
            raise ValueError("%s: vanilla blocks in palette %d %s" % (where, index, vanilla))

    block_count = 0
    for index, entry in enumerate(root["blocks"][1]):
        pos = int_list(entry, "pos", "%s blocks[%d]" % (where, index))
        if len(pos) != 3:
            raise ValueError("%s: blocks[%d].pos must have 3 entries" % (where, index))
        for xyz, limit in zip(pos, size):
            if not 0 <= xyz < limit:
                raise ValueError("%s: blocks[%d].pos %s outside size %s"
                                 % (where, index, pos, size))
        state = entry.get("state", 0)
        if not 0 <= state < len(palettes[0]):
            raise ValueError("%s: blocks[%d].state %s outside palette size %d"
                             % (where, index, state, len(palettes[0])))
        block_count += 1

    print("%s ok  size=%s blocks=%d palette=%s" % (
        path.name, size, block_count, [entry.get("Name") for entry in palettes[0]]))


if __name__ == "__main__":
    for structure in sorted(STRUCTURES.glob("*.nbt")):
        check(structure)
