"""生成 Ponderer 结构 NBT（只使用本模组方块）。

用法：在项目根目录执行
    python tools/generate_ponder_structures.py

注意 NBT 编码规则：
  TAG_Compound 作为根或具名标签时 = 类型字节 0x0A + 名字 + 子标签 + 0x00
  TAG_Compound 作为 TAG_List 元素时   = 只有 子标签 + 0x00
写错会导致 Minecraft 读取 NBT 越界，整个场景被 PondererLib 跳过。
"""

import gzip
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/starrailexpress/ponder"
DATA_VERSION = 3955  # Minecraft 1.21.1
SIZE = 7  # 房间边长（格）


def name_bytes(name):
    raw = name.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def tag_string(name, value):
    return b"\x08" + name_bytes(name) + name_bytes(value)


def tag_int(name, value):
    return b"\x03" + name_bytes(name) + struct.pack(">i", value)


def tag_int_array(name, values):
    payload = b"".join(struct.pack(">i", value) for value in values)
    return b"\x0b" + name_bytes(name) + struct.pack(">i", len(values)) + payload


def compound_payload(entries):
    return b"".join(entries) + b"\x00"


def tag_list(name, element_type, payloads):
    return b"\x09" + name_bytes(name) + bytes([element_type]) + struct.pack(">i", len(payloads)) + b"".join(payloads)


def tag_compound(name, entries):
    return b"\x0a" + name_bytes(name) + compound_payload(entries)


def block_entry(pos, state):
    return compound_payload([tag_int_array("pos", pos), tag_int("state", state)])


def palette_entry(block_id):
    return compound_payload([tag_string("Name", block_id)])


def write_structure(path, size, blocks, palette):
    root = tag_compound("", [
        tag_int_array("size", size),
        tag_list("entities", 10, []),
        tag_list("blocks", 10, [block_entry(pos, state) for pos, state in blocks]),
        tag_list("palette", 10, [palette_entry(block) for block in palette]),
        tag_int("DataVersion", DATA_VERSION),
    ])
    path.write_bytes(gzip.compress(root, mtime=0))
    print("wrote", path.relative_to(ROOT), "blocks:", len(blocks), "palette:", len(palette))


def build_room(floor_state, wall_state, extra_blocks):
    """开放式房间：地板 + 四面墙（顶部敞开，便于相机俯视）。"""
    blocks = []
    last = SIZE - 1
    for x in range(SIZE):
        for z in range(SIZE):
            blocks.append(([x, 0, z], floor_state(x, z)))
    for x in range(SIZE):
        for y in range(1, 4):
            blocks.append(([x, y, 0], wall_state))
            blocks.append(([x, y, last], wall_state))
    for z in range(1, last):
        for y in range(1, 4):
            blocks.append(([0, y, z], wall_state))
            blocks.append(([last, y, z], wall_state))
    blocks.extend(extra_blocks)
    return blocks


def jester():
    palette = [
        "trainmurdermystery:white_hull",
        "trainmurdermystery:navy_steel",
        "trainmurdermystery:marble_tiles",
    ]
    blocks = build_room(lambda x, z: 2 if (x + z) % 4 == 0 else 0, 1, [])
    write_structure(OUT / "role_tutorial_jester.nbt", [SIZE, 4, SIZE], blocks, palette)


def bartender():
    palette = [
        "trainmurdermystery:mahogany_planks",
        "trainmurdermystery:white_hull",
        "trainmurdermystery:bar_table",
        "trainmurdermystery:food_platter",
        "trainmurdermystery:drink_tray",
        "trainmurdermystery:light_toilet",
        "trainmurdermystery:white_trimmed_bed",
    ]
    extra = [
        ([1, 1, 1], 2),   # 吧台
        ([2, 1, 1], 3),   # 食物盘
        ([3, 1, 1], 4),   # 饮料盘
        ([1, 1, 5], 5),   # 马桶
        ([5, 1, 5], 6),   # 床
    ]
    blocks = build_room(lambda x, z: 0, 1, extra)
    write_structure(OUT / "role_tutorial_bartender.nbt", [SIZE, 4, SIZE], blocks, palette)


if __name__ == "__main__":
    jester()
    bartender()
