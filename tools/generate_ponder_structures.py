"""生成 Ponderer 结构 NBT（只使用本模组方块）。
用法：在项目根目录执行
    python tools/generate_ponder_structures.py

NBT 编码规则（对齐 Minecraft 1.21.1 StructureTemplate.load 的实际读取行为）：
  TAG_Compound 作为根或具名标签时 = 类型字节 0x0A + 名字 + 子标签 + 0x00
  TAG_Compound 作为 TAG_List 元素时 = 只有 子标签 + 0x00（不带类型字节和名字）
  TAG_List 作为 TAG_List 元素时     = 只有 元素类型字节 + 长度 + 负载（不带名字）
  size / blocks[].pos 必须是 TAG_List<Int>；写成 TAG_Int_Array 时
  StructureTemplate.load 会读出空列表，整个结构退化成 0x0x0，思索里看不到任何方块。
  调色板键名用 palettes（TAG_List<TAG_List<TAG_Compound>>）。
"""

import gzip
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/starrailexpress/ponder"
DATA_VERSION = 3955  # Minecraft 1.21.1
SIZE = 7  # 房间边长（格）

TAG_COMPOUND = 10
TAG_INT = 3
TAG_LIST = 9


def name_bytes(name):
    raw = name.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def tag_string(name, value):
    return b"\x08" + name_bytes(name) + name_bytes(value)


def tag_int(name, value):
    return b"\x03" + name_bytes(name) + struct.pack(">i", value)


def tag_int_list(name, values):
    """TAG_List<TAG_Int>。Minecraft 用 getList(name, 3) 读 size 与 blocks[].pos。"""
    payload = b"".join(struct.pack(">i", value) for value in values)
    return b"\x09" + name_bytes(name) + bytes([TAG_INT]) + struct.pack(">i", len(values)) + payload


def compound_payload(entries):
    """TAG_Compound 的负载：子标签串联 + 结尾 0x00。"""
    return b"".join(entries) + b"\x00"


def tag_compound(name, entries):
    return b"\x0a" + name_bytes(name) + compound_payload(entries)


def compound_list_payload(payloads):
    """TAG_List<TAG_Compound> 的负载（不带名字，供嵌套使用）。"""
    return bytes([TAG_COMPOUND]) + struct.pack(">i", len(payloads)) + b"".join(payloads)


def tag_compound_list(name, payloads):
    return b"\x09" + name_bytes(name) + compound_list_payload(payloads)


def list_payload(payloads):
    """TAG_List<TAG_List> 的负载（不带名字，供嵌套使用）。"""
    return bytes([TAG_LIST]) + struct.pack(">i", len(payloads)) + b"".join(payloads)


def tag_list_of_lists(name, payloads):
    return b"\x09" + name_bytes(name) + list_payload(payloads)


def block_entry(pos, state):
    return compound_payload([tag_int_list("pos", pos), tag_int("state", state)])


def palette_entry(block_id):
    return compound_payload([tag_string("Name", block_id)])


def write_structure(path, size, blocks, palette):
    root = tag_compound("", [
        tag_int_list("size", size),
        tag_compound_list("entities", []),
        tag_compound_list("blocks", [block_entry(pos, state) for pos, state in blocks]),
        tag_list_of_lists("palettes", [compound_list_payload([palette_entry(block) for block in palette])]),
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


def generic():
    """新增职业教程可复用的通用房间：地板 + 墙 + 门框 + 床，不含职业专属方块。"""
    palette = [
        "trainmurdermystery:white_hull",
        "trainmurdermystery:navy_steel",
        "trainmurdermystery:marble_tiles",
        "trainmurdermystery:small_glass_door",
        "trainmurdermystery:white_trimmed_bed",
    ]
    extra = [
        ([3, 1, 6], 3),  # 门下半
        ([3, 2, 6], 3),  # 门上半
        ([5, 1, 5], 4),  # 床
    ]
    blocks = build_room(lambda x, z: 2 if (x + z) % 4 == 0 else 0, 1, extra)
    write_structure(OUT / "role_tutorial_common.nbt", [SIZE, 4, SIZE], blocks, palette)


if __name__ == "__main__":
    jester()
    bartender()
    generic()
