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


def palette_entry(block_id, properties=None):
    """调色板项；双格方块（床）需要把 facing/part/occupied 写进 Properties。"""
    entries = [tag_string("Name", block_id)]
    if properties:
        entries.append(tag_compound("Properties",
                                    [tag_string(key, value) for key, value in properties.items()]))
    return compound_payload(entries)


def bed_palette(block_id):
    """床的脚/头两半；朝向北，头在脚的 -Z 方向。"""
    return [
        (block_id, {"facing": "north", "part": "foot", "occupied": "false"}),
        (block_id, {"facing": "north", "part": "head", "occupied": "false"}),
    ]


def write_structure(path, size, blocks, palette):
    entries = [palette_entry(*item) if isinstance(item, tuple) else palette_entry(item)
               for item in palette]
    root = tag_compound("", [
        tag_int_list("size", size),
        tag_compound_list("entities", []),
        tag_compound_list("blocks", [block_entry(pos, state) for pos, state in blocks]),
        tag_list_of_lists("palettes", [compound_list_payload(entries)]),
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


def build_back_wall_room(floor_state, wall_state, extra_blocks):
    """开放式房间：地板 + 只有背面（z=last）一堵墙，避免侧壁和前墙挡住俯视镜头。"""
    blocks = []
    last = SIZE - 1
    for x in range(SIZE):
        for z in range(SIZE):
            blocks.append(([x, 0, z], floor_state(x, z)))
    for x in range(SIZE):
        for y in range(1, 4):
            blocks.append(([x, y, last], wall_state))
    blocks.extend(extra_blocks)
    return blocks


def jester():
    palette = [
        "trainmurdermystery:white_hull",
        "trainmurdermystery:navy_steel",
        "trainmurdermystery:marble_tiles",
        "trainmurdermystery:food_platter",
        "trainmurdermystery:drink_tray",
    ]
    extra = [
        ([2, 1, 1], 3),  # 食物盘（假毒演示）
        ([4, 1, 1], 4),  # 饮料盘（假毒演示）
    ]
    blocks = build_back_wall_room(lambda x, z: 2 if (x + z) % 4 == 0 else 0, 1, extra)
    write_structure(OUT / "role_tutorial_jester.nbt", [SIZE, 4, SIZE], blocks, palette)


def bartender():
    palette = [
        "trainmurdermystery:mahogany_planks",
        "trainmurdermystery:white_hull",
        "trainmurdermystery:bar_table",
        "trainmurdermystery:food_platter",
        "trainmurdermystery:drink_tray",
        "trainmurdermystery:light_toilet",
        *bed_palette("trainmurdermystery:white_trimmed_bed"),
    ]
    extra = [
        ([1, 1, 1], 2),   # 吧台
        ([2, 1, 1], 3),   # 食物盘
        ([3, 1, 1], 4),   # 饮料盘
        ([1, 1, 5], 5),   # 马桶
        ([5, 1, 5], 6),   # 床脚
        ([5, 1, 4], 7),   # 床头（朝北，床占两格）
    ]
    blocks = build_back_wall_room(lambda x, z: 0, 1, extra)
    write_structure(OUT / "role_tutorial_bartender.nbt", [SIZE, 4, SIZE], blocks, palette)


def generic():
    """新增职业教程可复用的通用房间：地板 + 墙 + 门框 + 床，不含职业专属方块。"""
    palette = [
        "trainmurdermystery:white_hull",
        "trainmurdermystery:navy_steel",
        "trainmurdermystery:marble_tiles",
        "trainmurdermystery:small_glass_door",
        *bed_palette("trainmurdermystery:white_trimmed_bed"),
    ]
    extra = [
        ([3, 1, 6], 3),  # 门下半
        ([3, 2, 6], 3),  # 门上半
        ([5, 1, 5], 4),  # 床脚
        ([5, 1, 4], 5),  # 床头（朝北，床占两格）
    ]
    blocks = build_room(lambda x, z: 2 if (x + z) % 4 == 0 else 0, 1, extra)
    write_structure(OUT / "role_tutorial_common.nbt", [SIZE, 4, SIZE], blocks, palette)


def recaller():
    """召回者教程：只有背墙的通透房间，标点处用地砖方台做地面标记。

    教程里玩家模型站在标点 [2,1,4]，走到 [2,1,1] 再被传送回来，所以房间要保持通透，
    只留背墙，避免侧墙挡住俯视镜头。
    """

    def floor(x, z):
        # 标点方台：3x3 大理石台面，正中一格换成船体板，让"记录的地点"有可辨认的地标。
        if 1 <= x <= 3 and 3 <= z <= 5:
            return 0 if (x, z) == (2, 4) else 2
        return 2 if (x + z) % 4 == 0 else 0

    palette = [
        "trainmurdermystery:white_hull",
        "trainmurdermystery:navy_steel",
        "trainmurdermystery:marble_tiles",
    ]
    blocks = build_back_wall_room(floor, 1, [])
    write_structure(OUT / "role_tutorial_recaller.nbt", [SIZE, 4, SIZE], blocks, palette)


# ==== 全覆盖规划新增的结构 ====


def build_shell(size, floor_state, wall_state, extra_blocks, walls="back"):
    """通用外壳：地板 + 按需的墙。

    walls: "back"（只留背面一堵墙）、"all"（四面墙）、"sides"（只留左右两面墙）。
    尺寸是 (x, y, z)，地板在 y=0，墙在 y=1..y-1。
    """
    sx, sy, sz = size
    blocks = []
    for x in range(sx):
        for z in range(sz):
            blocks.append(([x, 0, z], floor_state(x, z)))
    for x in range(sx):
        for y in range(1, sy):
            if walls in ("back", "all"):
                blocks.append(([x, y, sz - 1], wall_state))
            if walls in ("back", "all"):
                blocks.append(([x, y, 0], wall_state))
    for z in range(1, sz - 1):
        for y in range(1, sy):
            if walls in ("sides", "all"):
                blocks.append(([0, y, z], wall_state))
                blocks.append(([sx - 1, y, z], wall_state))
    blocks.extend(extra_blocks)
    return blocks


def room():
    """标准房间：木地板 + 背墙 + 房间木门 + 床 + 茶几 + 壁灯。

    玩家模型站位 [3,1,3]；床在右上角，沙发与茶几在左侧。
    """
    palette = [
        "trainmurdermystery:mahogany_planks",       # 0 地板
        "trainmurdermystery:white_hull",           # 1 墙
        "trainmurdermystery:marble_tiles",         # 2 地砖点缀
        "trainmurdermystery:small_wood_door",      # 3 门
        "trainmurdermystery:coffee_table",         # 4 茶几
        "trainmurdermystery:white_lounge_couch",   # 5 沙发
        "trainmurdermystery:wall_lamp",            # 6 壁灯
        *bed_palette("trainmurdermystery:white_trimmed_bed"),  # 7 脚 8 头
    ]
    extra = [
        ([3, 1, 6], 3),  # 门下半
        ([3, 2, 6], 3),  # 门上半
        ([5, 1, 5], 7),  # 床脚
        ([5, 1, 4], 8),  # 床头
        ([1, 1, 2], 4),  # 茶几
        ([2, 1, 1], 5),  # 沙发
        ([1, 1, 3], 5),  # 沙发
        ([1, 2, 6], 6),  # 壁灯
    ]
    blocks = build_shell([SIZE, 4, SIZE], lambda x, z: 2 if (x + z) % 4 == 0 else 0, 1, extra)
    write_structure(OUT / "role_tutorial_room.nbt", [SIZE, 4, SIZE], blocks, palette)


def carriage():
    """列车车厢：不锈钢地板与侧壁、长条座椅、端门、顶灯条。

    车厢横向 9 格，玩家模型站位 [4,1,3]。
    """
    width, depth = 9, 7
    palette = [
        "trainmurdermystery:stainless_steel",      # 0 地板
        "trainmurdermystery:dark_steel",           # 1 墙
        "trainmurdermystery:stainless_steel_walkway",  # 2 过道
        "trainmurdermystery:barrier_panel",        # 3 车窗
        "trainmurdermystery:white_lounge_couch",   # 4 座椅
        "trainmurdermystery:navy_steel_door",      # 5 端门
        "trainmurdermystery:neon_tube",            # 6 灯条
        "trainmurdermystery:trimmed_railing",      # 7 扶手
    ]
    extra = []
    # 侧壁车窗
    for z in range(1, depth - 1, 2):
        extra.append(([0, 2, z], 3))
        extra.append(([width - 1, 2, z], 3))
    # 两排座椅
    for x in range(1, width - 1):
        if x % 2 == 0:
            continue
        extra.append(([x, 1, 1], 4))
        extra.append(([x, 1, depth - 2], 4))
    # 端门与灯条
    extra.append(([0, 1, 3], 5))
    extra.append(([0, 2, 3], 5))
    extra.append(([1, 3, 3], 6))
    extra.append(([width - 2, 3, 3], 6))
    extra.append(([0, 1, 1], 7))
    extra.append(([0, 1, depth - 2], 7))
    blocks = build_shell([width, 4, depth],
                         lambda x, z: 2 if z in (0, depth - 1) else 0,
                         1, extra, walls="all")
    write_structure(OUT / "role_tutorial_carriage.nbt", [width, 4, depth], blocks, palette)


def corridor():
    """门与走廊：深色钢地板、两侧钢墙、中间一道钢门、检票闸机与扶手。

    纵向 9 格，玩家模型站位 [3,1,6]（在闸机后、门前）。
    """
    depth = 9
    palette = [
        "trainmurdermystery:anthracite_steel_tiles",  # 0 地板
        "trainmurdermystery:anthracite_steel",        # 1 墙
        "trainmurdermystery:khaki_steel_door",        # 2 门
        "starrailexpress:ticket_gate",                # 3 检票闸机
        "trainmurdermystery:trimmed_railing",         # 4 扶手
        "trainmurdermystery:wall_lamp",               # 5 壁灯
        "trainmurdermystery:security_monitor",        # 6 监控
    ]
    extra = [
        ([3, 1, 4], 2),  # 门下半
        ([3, 2, 4], 2),  # 门上半
        ([3, 1, 2], 3),  # 闸机
        ([1, 1, 6], 4),
        ([5, 1, 6], 4),
        ([1, 2, 8], 5),
        ([3, 2, 8], 6),
    ]
    blocks = build_shell([SIZE, 4, depth], lambda x, z: 0, 1, extra, walls="all")
    write_structure(OUT / "role_tutorial_corridor.nbt", [SIZE, 4, depth], blocks, palette)


def graveyard():
    """墓地：黑大理石地面、棺材、栏杆墓碑、提灯。"""
    palette = [
        "trainmurdermystery:dark_marble",        # 0 地面
        "trainmurdermystery:black_hull",          # 1 墙
        "noellesroles:coffin",                    # 2 棺材
        "trainmurdermystery:trimmed_railing_post",  # 3 墓碑
        "trainmurdermystery:trimmed_lantern",     # 4 提灯
        "trainmurdermystery:marble_tiles",        # 5 石板路
    ]
    extra = [
        ([1, 1, 1], 2),
        ([5, 1, 4], 2),
        ([2, 1, 5], 3),
        ([4, 1, 1], 3),
        ([1, 2, 6], 4),
        ([5, 2, 6], 4),
    ]
    blocks = build_shell([SIZE, 4, SIZE],
                         lambda x, z: 5 if abs(x - 3) <= 1 and abs(z - 3) <= 2 else 0,
                         1, extra)
    write_structure(OUT / "role_tutorial_graveyard.nbt", [SIZE, 4, SIZE], blocks, palette)


def water():
    """水域：玻璃围出的水池、钢制走道、水阀与水泵。

    本模组没有水方块（水是原版），因此用玻璃围出池子、走道用钢，
    水阀 / 水泵是本模组的任务点方块，用来点明「水」的场景语义。
    """
    palette = [
        "trainmurdermystery:marble_tiles",         # 0 池底
        "trainmurdermystery:hull_glass",           # 1 玻璃池壁
        "trainmurdermystery:stainless_steel_walkway",  # 2 走道
        "noellesroles:water_valve",                # 3 水阀
        "noellesroles:water_pump",                 # 4 水泵
        "trainmurdermystery:stainless_steel",      # 5 池沿
    ]
    extra = [
        # 池壁（玻璃）
        ([1, 1, 1], 1), ([1, 1, 2], 1), ([1, 1, 3], 1), ([1, 1, 4], 1), ([1, 1, 5], 1),
        ([5, 1, 1], 1), ([5, 1, 2], 1), ([5, 1, 3], 1), ([5, 1, 4], 1), ([5, 1, 5], 1),
        ([3, 1, 1], 1), ([3, 1, 5], 1),
        # 走道
        ([3, 1, 0], 2), ([3, 1, 6], 2),
        # 池沿
        ([2, 1, 1], 5), ([4, 1, 5], 5),
        # 水阀与水泵
        ([1, 1, 6], 3),
        ([5, 1, 6], 4),
    ]
    blocks = build_shell([SIZE, 4, SIZE], lambda x, z: 0, 5, extra, walls="back")
    write_structure(OUT / "role_tutorial_water.nbt", [SIZE, 4, SIZE], blocks, palette)


def command_room():
    """命令方块事件舞台：黑船体板机房里间，墙面监控、反应堆与灯条。"""
    palette = [
        "trainmurdermystery:black_hull_sheets",    # 0 地面
        "trainmurdermystery:black_hull",           # 1 墙
        "trainmurdermystery:security_monitor",     # 2 监控
        "noellesroles:reactor",                    # 3 反应堆
        "trainmurdermystery:neon_tube",            # 4 灯条
        "trainmurdermystery:dark_steel_wall",      # 5 设备墙
        "trainmurdermystery:metal_sheet_door",     # 6 门
    ]
    extra = [
        ([3, 1, 6], 6),
        ([3, 2, 6], 6),
        ([1, 2, 6], 2),
        ([5, 2, 6], 2),
        ([2, 1, 2], 3),
        ([1, 3, 3], 4),
        ([5, 3, 3], 4),
        ([0, 1, 3], 5),
        ([6, 1, 3], 5),
    ]
    blocks = build_shell([SIZE, 4, SIZE], lambda x, z: 0, 1, extra)
    write_structure(OUT / "event_command_room.nbt", [SIZE, 4, SIZE], blocks, palette)


def night_town():
    """黑夜事件舞台：夜间小镇广场，木地板、路灯、信箱与长椅。"""
    width, depth = 9, 7
    palette = [
        "trainmurdermystery:bubinga_herringbone",  # 0 地面
        "trainmurdermystery:dark_steel",           # 1 墙
        "trainmurdermystery:trimmed_lantern",      # 2 路灯
        "trainmurdermystery:mailbox",              # 3 信箱
        "trainmurdermystery:bar_table",            # 4 长桌
        "trainmurdermystery:bar_stool",            # 5 长凳
        "trainmurdermystery:small_wood_door",      # 6 门
        "trainmurdermystery:marble_tiles",         # 7 石板路
    ]
    extra = [
        ([1, 1, 6], 6),
        ([1, 2, 6], 6),
        ([7, 1, 6], 6),
        ([7, 2, 6], 6),
        ([1, 2, 2], 2),
        ([7, 2, 2], 2),
        ([0, 1, 4], 3),
        ([3, 1, 4], 4),
        ([4, 1, 4], 5),
        ([8, 1, 4], 3),
    ]
    blocks = build_shell([width, 4, depth],
                         lambda x, z: 7 if abs(x - 4) <= 1 else 0,
                         1, extra, walls="all")
    write_structure(OUT / "event_night_town.nbt", [width, 4, depth], blocks, palette)


def halloween_porch():
    """万圣节事件舞台：门廊小镇，木地板、门廊栏杆、南瓜灯与柜子。"""
    palette = [
        "trainmurdermystery:mahogany_planks",      # 0 地板
        "trainmurdermystery:dark_steel",           # 1 墙
        "trainmurdermystery:trimmed_railing",      # 2 门廊栏杆
        "trainmurdermystery:trimmed_lantern",      # 3 提灯
        "trainmurdermystery:bubinga_cabinet",      # 4 柜子
        "trainmurdermystery:small_wood_door",      # 5 门
        "trainmurdermystery:bar_stool",            # 6 长凳
        "trainmurdermystery:marble_tiles",        # 7 石板
    ]
    extra = [
        ([3, 1, 6], 5),
        ([3, 2, 6], 5),
        ([1, 1, 6], 3),
        ([5, 1, 6], 3),
        ([0, 1, 4], 2),
        ([6, 1, 4], 2),
        ([1, 1, 2], 4),
        ([4, 1, 2], 6),
        ([3, 1, 3], 7),
    ]
    blocks = build_shell([SIZE, 4, SIZE], lambda x, z: 7 if (x, z) == (3, 3) else 0, 1, extra)
    write_structure(OUT / "event_halloween_porch.nbt", [SIZE, 4, SIZE], blocks, palette)


if __name__ == "__main__":
    jester()
    bartender()
    generic()
    recaller()
    room()
    carriage()
    corridor()
    graveyard()
    water()
    command_room()
    night_town()
    halloween_porch()
