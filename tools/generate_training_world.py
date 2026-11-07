#!/usr/bin/env python3
"""仅用标准库生成 1.21.1 列车整备训练站；不读取或改动玩家存档。"""
from __future__ import annotations

import argparse
import gzip
import io
import json
import math
from pathlib import Path
import re
import struct
import zipfile
import zlib

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/main/resources/data/starrailexpress/training"
DATA_VERSION = 3955
MARKER = b'{"id":"starrailexpress:training","version":1}'


def state(name, **properties):
    return (name if ":" in name else "minecraft:" + name,
            tuple(sorted((k, str(v).lower()) for k, v in properties.items())))


AIR = state("air")
BLOCKS = {}
ENTITIES = {}


def put(x, y, z, block, **properties):
    BLOCKS[x, y, z] = state(block, **properties) if isinstance(block, str) else block


def box(x0, y0, z0, x1, y1, z1, block, **properties):
    for y in range(y0, y1 + 1):
        for z in range(z0, z1 + 1):
            for x in range(x0, x1 + 1):
                put(x, y, z, block, **properties)


def sign(x, y, z, lines, facing="south"):
    put(x, y, z, "dark_oak_wall_sign", facing=facing, waterlogged=False)
    text = [json.dumps({"text": t, "color": "gold" if i == 0 else "black"},
                       ensure_ascii=False, separators=(",", ":")) for i, t in enumerate(lines)]
    text += ['{"text":""}'] * (4 - len(text))
    ENTITIES[x, y, z] = {"id": (8, "minecraft:sign"), "x": (3, x), "y": (3, y), "z": (3, z),
                         "is_waxed": (1, 1), "front_text": (10, {
                             "messages": (9, (8, text)), "color": (8, "black"),
                             "has_glowing_text": (1, 1)}), "back_text": (10, {
                             "messages": (9, (8, ['{"text":""}'] * 4)),
                             "color": (8, "black"), "has_glowing_text": (1, 0)})}


def lamp(x, z):
    box(x, 65, z, x, 69, z, "polished_deepslate")
    put(x, 70, z, "ochre_froglight", axis="y")
    put(x, 71, z, "dark_oak_slab", type="bottom", waterlogged=False)


def build():
    BLOCKS.clear()
    ENTITIES.clear()
    # 四层悬浮基座、铺地和不可越出的围栏；所有主通道至少六格宽。
    box(0, 61, 0, 159, 61, 127, "bedrock")
    box(0, 62, 0, 159, 63, 127, "deepslate_tiles")
    box(0, 64, 0, 159, 64, 127, "smooth_stone")
    for x in range(160):
        for z in range(128):
            if x % 8 == 0 or z % 8 == 0:
                put(x, 64, z, "polished_andesite")
    for x0, z0, x1, z1 in [(0, 0, 159, 0), (0, 127, 159, 127),
                            (0, 1, 0, 126), (159, 1, 159, 126)]:
        box(x0, 65, z0, x1, 65, z1, "polished_deepslate")
        box(x0, 66, z0, x1, 68, z1, "iron_bars")
        box(x0, 69, z0, x1, 95, z1, "barrier")
    box(4, 64, 72, 155, 64, 81, "deepslate_tiles")
    for z in (72, 81):
        box(4, 64, z, 155, 64, z, "yellow_terracotta")
    for x in range(8, 156, 12):
        for z in (4, 70, 83, 123):
            if (x, z) != (32, 70):
                lamp(x, z)
    for z in range(16, 124, 12):
        lamp(4, z)
        lamp(155, z)

    # 西北：四条实墙隔离靶道，标尺相对 z=61 射击线。
    box(8, 64, 8, 71, 64, 67, "gray_concrete")
    box(8, 65, 8, 71, 68, 9, "deepslate_bricks")
    for x in (10, 25, 40, 55, 70):
        box(x, 65, 10, x, 67, 57, "polished_deepslate")
        box(x, 68, 10, x, 68, 57, "yellow_terracotta")
    for left in (11, 26, 41, 56):
        for distance in (5, 10, 20, 30, 40):
            z = 61 - distance
            box(left, 64, z, left + 13, 64, z, "white_concrete")
            put(left, 64, z, "yellow_concrete")
            sign(left + 1, 66, 10, ["靶道 / RANGE", "5 / 10 / 20 / 30 / 40", "射程由武器决定", "Use training panel"])
        box(left + 3, 65, 10, left + 9, 67, 10, "white_concrete")
        put(left + 6, 66, 11, "target")
    box(8, 64, 61, 71, 64, 61, "yellow_concrete")
    # 背后的六格横向通道保持通畅。

    # 东北：错列低/高掩体、侧翼路线及少量可走台阶。
    box(82, 64, 8, 151, 64, 67, "light_gray_concrete")
    for x in (82, 151):
        box(x, 65, 8, x, 67, 67, "deepslate_bricks")
    box(82, 65, 8, 151, 67, 8, "deepslate_bricks")
    for ix, x in enumerate((92, 104, 116, 128, 140)):
        for iz, z in enumerate((20, 36, 52)):
            top = 65 + (ix + iz) % 2
            box(x, 65, z, x + 2, top, z + 3, "polished_deepslate")
            box(x, top, z, x + 2, top, z, "waxed_cut_copper")
    for x in (85, 145):
        box(x, 65, 33, x + 3, 65, 36, "stone_bricks")
        box(x, 65, 32, x + 3, 65, 32, "stone_brick_stairs",
            facing="south", half="bottom", shape="straight", waterlogged=False)
    for x in (88, 148):
        box(x, 64, 62, x + 2, 64, 64, "cyan_concrete")

    # 西南：复古候车大厅（只此区域为 readyArea）。
    box(8, 64, 84, 39, 64, 111, "dark_oak_planks")
    for x0, z0, x1, z1 in [(8, 84, 39, 84), (8, 111, 39, 111),
                            (8, 85, 8, 110), (39, 85, 39, 110)]:
        box(x0, 65, z0, x1, 69, z1, "bricks")
        box(x0, 67, z0, x1, 68, z1, "white_stained_glass")
        box(x0, 70, z0, x1, 70, z1, "dark_oak_log", axis="y")
    box(20, 65, 84, 27, 69, 84, "air")
    box(39, 65, 94, 39, 69, 101, "air")
    box(7, 71, 83, 40, 71, 112, "dark_oak_planks")
    box(8, 72, 84, 39, 72, 111, "deepslate_tiles")
    box(15, 72, 90, 32, 72, 105, "white_stained_glass")
    for x in (10, 37):
        for z in (86, 109):
            box(x, 65, z, x, 70, z, "dark_oak_log", axis="y")
    for z in (89, 94, 99, 104, 108):
        for x in (11, 35):
            put(x, 65, z, "trainmurdermystery:red_leather_couch", arms="single", facing="east" if x == 11 else "west")
            put(x + (1 if x == 11 else -1), 65, z, "trainmurdermystery:coffee_table")
    box(19, 64, 85, 28, 64, 108, "red_concrete")
    for x in (16, 31):
        for z in (89, 104):
            put(x, 70, z, "glowstone")
    box(17, 65, 109, 30, 67, 110, "dark_oak_planks")
    sign(19, 67, 108, ["列车整备训练站", "安全大厅 / LOBBY", "/sre:training 打开面板", "敌人不会追入大厅"], "north")
    sign(25, 67, 108, ["TRAINING STATION", "面板：道具 · 职业 · 敌人", "北：靶场 / 掩体对战", "东：座席 / 餐车 / 卧铺"], "north")
    box(20, 64, 76, 27, 64, 83, "dark_oak_planks")
    box(40, 64, 94, 55, 64, 101, "dark_oak_planks")

    # 南侧三节车厢：整平站台、金属外壳、木内饰、透光车顶及贯通走廊。
    box(50, 64, 86, 147, 64, 114, "deepslate_tiles")
    for z in (91, 109):
        box(52, 64, z, 145, 64, z, "yellow_terracotta")
    for car, start in enumerate((58, 86, 114)):
        end = start + 25
        box(start, 64, 94, end, 64, 106, "dark_oak_planks")
        for z in (94, 106):
            box(start, 65, z, end, 69, z, "trainmurdermystery:anthracite_steel")
            box(start, 66, z, end, 66, z, "trainmurdermystery:tarnished_gold")
            for x in range(start + 3, end - 1, 4):
                box(x, 67, z, x + 1, 68, z, "light_blue_stained_glass")
        for x in (start, end):
            box(x, 65, 94, x, 69, 106, "trainmurdermystery:anthracite_steel")
            box(x, 65, 99, x, 68, 102, "air")
            for half, y in (("lower", 65), ("upper", 66)):
                put(x, y, 99, "trainmurdermystery:metal_sheet_door", facing="east",
                    half=half, hinge="left", open=True, powered=False)
        # 八格宽站台入口；不把需要开门的方块放在 AI 中线。
        box(start + 9, 65, 94, start + 16, 68, 94, "air")
        box(start - 1, 70, 93, end + 1, 70, 107, "trainmurdermystery:black_hull")
        box(start + 2, 70, 99, end - 2, 70, 102, "white_stained_glass")
        box(start + 1, 64, 100, end - 1, 64, 101, "red_concrete")
        for x in range(start + 3, end - 1, 5):
            put(x, 69, 97, "glowstone")
            put(x, 69, 104, "glowstone")
            if car == 0:
                for z in (96, 104):
                    put(x, 65, z, "trainmurdermystery:red_leather_couch", arms="single", facing="south" if z == 96 else "north")
                    put(x + 1, 65, z, "trainmurdermystery:coffee_table")
            elif car == 1:
                for z in (96, 104):
                    put(x, 65, z, "trainmurdermystery:bar_table")
                    put(x + 1, 65, z, "trainmurdermystery:bar_stool")
            else:
                for z in (96, 104):
                    for dx, part in ((0, "foot"), (1, "head")):
                        put(x + dx, 65, z, "trainmurdermystery:red_trimmed_bed",
                            facing="east", part=part, occupied=False)
                        ENTITIES[x + dx, 65, z] = {"id": (8, "trainmurdermystery:trimmed_bed"),
                            "x": (3, x + dx), "y": (3, 65), "z": (3, z)}
        sign(start + 2, 68, 93, [["01 座席车", "02 餐车", "03 卧铺车"][car],
                                "TRAIN / 列车近战区", "窄道 · 转角 · 穿门", "Use panel to add bots"], "north")
    for x in (84, 85, 112, 113):
        box(x, 64, 98, x, 64, 103, "trainmurdermystery:stainless_steel")
        box(x, 65, 98, x, 68, 98, "iron_bars")
        box(x, 65, 103, x, 68, 103, "iron_bars")
        box(x, 69, 98, x, 69, 103, "trainmurdermystery:black_hull")
    # 站台园艺与嵌灯不侵占传送、生成或巡逻点。
    for x in range(54, 147, 10):
        box(x, 65, 112, x + 2, 65, 113, "bricks")
        box(x, 66, 112, x + 2, 66, 113, "azalea_leaves", persistent=True)
    return layout()


def layout():
    def area(name, bounds, spawn, bots, patrol, nodes):
        return dict(id=name, bounds=[float(v) for v in bounds], spawn=spawn,
                    botSpawns=bots, patrol=patrol, nodes=nodes)
    def point(x, z):
        return [float(x), 65.0, float(z)]
    arena_nodes = [point(x, z) for z in (12.5, 28.5, 44.5, 60.5, 64.5)
                   for x in (88.5, 100.5, 112.5, 124.5, 136.5, 148.5)]
    train_nodes = [point(x, 100.5) for x in (60.5, 66.5, 74.5, 81.5, 84.5, 88.5,
                   96.5, 104.5, 109.5, 112.5, 116.5, 124.5, 132.5, 137.5)]
    return {"version": 1, "bounds": [0.0, 61.0, 0.0, 160.0, 96.0, 128.0], "areas": [
        area("lobby", [8, 65, 84, 40, 71, 112], [24.5, 65.0, 97.5, 180.0], [],
             [point(24.5, 87.5), point(24.5, 104.5)],
             [point(24.5, 87.5), point(24.5, 97.5), point(36.5, 97.5)]),
        area("range", [8, 65, 8, 72, 72, 68], [32.5, 65.0, 63.5, 180.0],
             [point(x, z) for x in (17.5, 32.5, 47.5, 62.5)
              for z in (14.5, 20.5, 26.5, 32.5, 38.5, 44.5, 50.5, 56.5)],
             [point(28.5, 20.5), point(36.5, 20.5), point(36.5, 54.5), point(28.5, 54.5)],
             [point(x, z) for x in (17.5, 32.5, 47.5, 62.5) for z in (14.5, 32.5, 56.5, 63.5)]),
        area("arena", [82, 65, 8, 152, 76, 68], [112.5, 65.0, 64.5, 180.0],
             [point(x, z) for z in (12.5, 28.5, 44.5, 60.5)
              for x in (88.5, 96.5, 104.5, 112.5, 120.5, 128.5, 136.5, 144.5)],
             [point(90.5, 12.5), point(150.5, 12.5), point(150.5, 60.5), point(90.5, 60.5)], arena_nodes),
        area("train", [56, 65, 88, 144, 71, 112], [60.5, 65.0, 100.5, -90.0],
             [point(start + 2.5 + i * 1.7, 100.5) for start in (58, 86, 114) for i in range(12)],
             [point(60.5, 100.5), point(137.5, 100.5)], train_nodes)]}


# NBT 显式类型，避免把 Python 的 bool/int 混成错误的 Minecraft 标签类型。
def utf(text):
    encoded = text.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded


def payload(kind, value):
    if kind in (1, 2, 3, 4, 5, 6):
        return struct.pack({1: ">b", 2: ">h", 3: ">i", 4: ">q", 5: ">f", 6: ">d"}[kind], value)
    if kind == 8:
        return utf(value)
    if kind == 9:
        subtype, values = value
        return bytes([subtype]) + struct.pack(">i", len(values)) + b"".join(payload(subtype, v) for v in values)
    if kind == 10:
        return b"".join(bytes([t]) + utf(k) + payload(t, v) for k, (t, v) in value.items()) + b"\0"
    if kind == 12:
        return struct.pack(">i", len(value)) + b"".join(struct.pack(">Q", v & ((1 << 64) - 1)) for v in value)
    raise ValueError(kind)


def nbt(compound):
    return b"\x0a\0\0" + payload(10, compound)


def packed(values, bits):
    # 1.16+ 的长整型数组不允许单个值跨越 long 边界。
    per = 64 // bits
    result = []
    for start in range(0, len(values), per):
        result.append(sum(v << (i * bits) for i, v in enumerate(values[start:start + per])))
    return result


def chunk(cx, cz):
    sections = []
    for sy in range(-4, 20):
        values = [BLOCKS.get((cx * 16 + x, sy * 16 + y, cz * 16 + z), AIR)
                  for y in range(16) for z in range(16) for x in range(16)] if sy in (3, 4, 5) else [AIR] * 4096
        palette = sorted(set(values))
        indices = {b: i for i, b in enumerate(palette)}
        entries = []
        for name, props in palette:
            entry = {"Name": (8, name)}
            if props:
                entry["Properties"] = (10, {k: (8, v) for k, v in props})
            entries.append(entry)
        states = {"palette": (9, (10, entries))}
        if len(palette) > 1:
            states["data"] = (12, packed([indices[v] for v in values], max(4, (len(palette) - 1).bit_length())))
        sections.append({"Y": (1, sy), "block_states": (10, states),
                         "biomes": (10, {"palette": (9, (8, ["minecraft:the_void"]))})})
    heights = [max((y + 65 for y in range(95, 60, -1)
                    if BLOCKS.get((cx * 16 + x, y, cz * 16 + z), AIR) != AIR), default=0)
               for z in range(16) for x in range(16)]
    return nbt({"DataVersion": (3, DATA_VERSION), "xPos": (3, cx), "zPos": (3, cz), "yPos": (3, -4),
                "Status": (8, "minecraft:full"), "LastUpdate": (4, 0), "InhabitedTime": (4, 0),
                "isLightOn": (1, 0), "sections": (9, (10, sections)),
                "block_entities": (9, (10, [v for (x, y, z), v in sorted(ENTITIES.items())
                                             if x // 16 == cx and z // 16 == cz])),
                "block_ticks": (9, (10, [])), "fluid_ticks": (9, (10, [])),
                "PostProcessing": (9, (9, [(2, []) for _ in range(24)])),
                "Heightmaps": (10, {"WORLD_SURFACE": (12, packed(heights, 9))}),
                "structures": (10, {"starts": (10, {}), "References": (10, {})})})


def region():
    header = bytearray(8192)
    chunks = bytearray()
    offset = 2
    for cz in range(8):
        for cx in range(10):
            compressed = zlib.compress(chunk(cx, cz), 9)
            record = struct.pack(">I", len(compressed) + 1) + b"\x02" + compressed
            sectors = math.ceil(len(record) / 4096)
            assert sectors < 256
            index = 4 * (cx + cz * 32)
            header[index:index + 4] = struct.pack(">I", (offset << 8) | sectors)
            chunks.extend(record + bytes(sectors * 4096 - len(record)))
            offset += sectors
    return bytes(header + chunks)


def level_dat():
    flat = {"type": (8, "minecraft:flat"), "settings": (10, {
        "biome": (8, "minecraft:the_void"), "lakes": (1, 0), "features": (1, 0),
        "structure_overrides": (9, (8, [])),
        "layers": (9, (10, [{"height": (3, 1), "block": (8, "minecraft:air")}]))})}
    dimensions = {key: (10, {"type": (8, key), "generator": (10, flat)})
                  for key in ("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end")}
    data = {"DataVersion": (3, DATA_VERSION), "version": (3, 19133),
            "Version": (10, {"Id": (3, DATA_VERSION), "Name": (8, "1.21.1"),
                              "Snapshot": (1, 0), "Series": (8, "main")}),
            "LevelName": (8, "SRE 列车整备训练站"), "GameType": (3, 2), "Difficulty": (1, 2),
            "DifficultyLocked": (1, 0), "hardcore": (1, 0), "allowCommands": (1, 1),
            "initialized": (1, 1), "LastPlayed": (4, 0), "Time": (4, 6000), "DayTime": (4, 6000),
            "SpawnX": (3, 24), "SpawnY": (3, 65), "SpawnZ": (3, 97), "SpawnAngle": (5, 180.0),
            "raining": (1, 0), "thundering": (1, 0), "clearWeatherTime": (3, 2147483647),
            "rainTime": (3, 0), "thunderTime": (3, 0),
            "BorderCenterX": (6, 80.0), "BorderCenterZ": (6, 64.0), "BorderSize": (6, 160.0),
            "BorderSizeLerpTarget": (6, 160.0), "BorderSizeLerpTime": (4, 0),
            "WorldGenSettings": (10, {"seed": (4, 0), "generate_features": (1, 0),
                                       "bonus_chest": (1, 0), "dimensions": (10, dimensions)}),
            "GameRules": (10, {k: (8, v) for k, v in {
                "doDaylightCycle": "false", "doWeatherCycle": "false", "doMobSpawning": "false",
                "doPatrolSpawning": "false", "doTraderSpawning": "false", "doFireTick": "false",
                "mobGriefing": "false", "spectatorsGenerateChunks": "false", "spawnRadius": "0",
                "randomTickSpeed": "0", "announceAdvancements": "false"}.items()}),
            "DataPacks": (10, {"Enabled": (9, (8, ["vanilla"])), "Disabled": (9, (8, []))})}
    return gzip.compress(nbt({"Data": (10, data)}), mtime=0)


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def aabb(values):
    return dict(zip(("minX", "minY", "minZ", "maxX", "maxY", "maxZ"), values))


def validate_resources():
    registry = (ROOT / "src/main/java/io/wifi/starrailexpress/index/TMMBlocks.java").read_text(encoding="utf-8")
    for name, props in set(BLOCKS.values()):
        namespace, path = name.split(":")
        if namespace == "minecraft":
            continue
        assert namespace == "trainmurdermystery", name
        assert re.search(r'createWithItem\("' + re.escape(path) + r'"', registry), name
        resource = ROOT / "src/main/generated/assets" / namespace / "blockstates" / (path + ".json")
        variants = json.loads(resource.read_text(encoding="utf-8")).get("variants", {})
        assert resource.is_file(), name
        # 变体键只包含渲染相关状态；检验实际提供的状态值有匹配模型。
        assert any(all(dict(props).get(k) == v for k, v in
                       (part.split("=") for part in key.split(",") if part)) for key in variants), (name, props)


def verify_positions(definition):
    def clear(pos):
        x, y, z = map(math.floor, pos[:3])
        assert BLOCKS.get((x, y - 1, z), AIR) != AIR, ("no floor", pos)
        assert BLOCKS.get((x, y, z), AIR) == AIR, ("blocked feet", pos)
        assert BLOCKS.get((x, y + 1, z), AIR) == AIR, ("blocked head", pos)
    for area in definition["areas"]:
        if area["id"] != "lobby":
            assert len(area["botSpawns"]) >= 32
        for pos in [area["spawn"], *area["botSpawns"], *area["patrol"], *area["nodes"]]:
            clear(pos)
        route = area["patrol"]
        for a, b in zip(route, route[1:] + route[:1]):
            steps = max(1, math.ceil(math.dist(a, b) * 4))
            for i in range(steps + 1):
                clear([v + (w - v) * i / steps for v, w in zip(a, b)])
    # 连接四区的主步行路线也检查全程两格净空。
    for a, b in [((24.5, 65, 97.5), (24.5, 65, 77.5)),
                 ((24.5, 65, 77.5), (112.5, 65, 77.5)),
                 ((32.5, 65, 77.5), (32.5, 65, 63.5)),
                 ((112.5, 65, 77.5), (112.5, 65, 64.5)),
                 ((60.5, 65, 77.5), (60.5, 65, 90.5)),
                 ((60.5, 65, 90.5), (70.5, 65, 90.5)),
                 ((70.5, 65, 90.5), (70.5, 65, 100.5))]:
        for i in range(201):
            clear([v + (w - v) * i / 200 for v, w in zip(a, b)])


def generate():
    definition = build()
    validate_resources()
    verify_positions(definition)
    lobby = aabb(definition["areas"][0]["bounds"])
    bounds = aabb([1, 61, 1, 159, 96, 127])
    config = {"mapName": "training", "spawnPos": {"x": 24.5, "y": 65, "z": 97.5, "yaw": 180, "pitch": 0},
              "spectatorSpawnPos": {"x": 24.5, "y": 65, "z": 97.5, "yaw": 180, "pitch": 0},
              "readyArea": lobby, "playAreaOffset": {"x": 0, "y": 0, "z": 0}, "playArea": bounds,
              "resetTemplateArea": bounds, "resetPasteArea": bounds, "roomCount": 3,
              "roomPositions": {str(i + 1): {"x": x, "y": 65, "z": 100.5} for i, x in enumerate((70.5, 98.5, 126.5))},
              "noReset": True, "mustCopy": False,
              "settings": {"noReset": True, "mustCopy": False, "canJump": True, "canSwim": True,
                           "fogEnabled": False, "time": 6000, "daylightCycle": False, "weatherCycle": False,
                           "weather": "clear", "minigameQuestEnabled": False, "deadInDarknessTime": 0,
                           "fallToDeathHeight": 0, "haveOutsideSound": False, "sceneInsideSound": "none",
                           "sceneOutsideSound": "none", "disabledTasks": [], "disabledRoles": [], "disabledModifiers": []}}
    entries = {"level.dat": level_dat(), "region/r.0.0.mca": region(), "sre-training.json": MARKER,
               "layout.json": json_bytes(definition), "readyArea.json": json_bytes({"readyArea": lobby}),
               "train_maps/training.json": json_bytes(config)}
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, content in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (2024, 8, 8, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.create_system = 3
            info.external_attr = 0o100644 << 16
            archive.writestr(info, content, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    return stream.getvalue(), json_bytes(definition)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="只验证已提交的资源与确定性生成结果一致")
    args = parser.parse_args()
    archive, definition = generate()
    for name, content in (("world-template.zip", archive), ("layout.json", definition)):
        path = OUTPUT / name
        if args.check:
            assert path.read_bytes() == content, f"out of date: {path}"
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
    print(f"Training template verified: 80 chunks, {len(BLOCKS)} blocks, {len(archive)} ZIP bytes")


if __name__ == "__main__":
    main()
