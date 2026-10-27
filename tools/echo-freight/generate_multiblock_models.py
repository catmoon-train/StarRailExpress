#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Deterministic multiblock JSON models for the 12 echo machines.

North is the base facing: the interactive face looks toward negative Z.
part = x + 3*z + 9*y in the north-facing local frame. The root (0,0,0) is the
front-left bottom corner. East/south/west reuse the same part model with
blockstate y = 90/180/270; Java rotates the cell index to match.

Each placed part model is clipped into its own [0, 16] cell. The inventory
parent centers the whole machine inside Minecraft's legal [-16, 32] bounds.
2x2x2 machines still declare all 18 parts; unused indices are empty models so
every facing x part state resolves. Loot drops the block item from every part
once (no part=0 condition): Java clears the sibling cells without loot rolls.

Do not re-run generate_logistics_models.py or generate_terminal_models.py after
this script. Those writers still emit the old single-cell blockstates and would
overwrite the multiblock variants.
"""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/noellesroles"
DATA = ROOT / "src/main/resources/data/noellesroles"
TEX = ASSETS / "textures/block"
BLOCK_MODELS = ASSETS / "models/block"
ITEM_MODELS = ASSETS / "models/item"
BLOCKSTATES = ASSETS / "blockstates"
LOOT = DATA / "loot_table/blocks"
NS = "noellesroles"

LARGE = (
    "echo_beacon",
    "echo_core_beacon",
    "echo_crystal_extractor",
    "echo_black_crystal_extractor",
    "echo_key_altar",
)
SMALL = (
    "echo_supply_crate",
    "echo_black_market",
    "echo_black_dropoff",
    "echo_core_console",
    "echo_free_exit",
    "echo_evacuation_console",
    "echo_broker_exit",
)
IDS = LARGE + SMALL
SMALL_PARTS = (0, 1, 3, 4, 9, 10, 12, 13)
DIRS = ("north", "east", "south", "west")
YAW = {"north": None, "east": 90, "south": 180, "west": 270}

PAL = {
    "ink": (16, 14, 18),
    "navy": (28, 38, 58),
    "navy_hi": (58, 78, 112),
    "steel": (52, 60, 72),
    "steel_hi": (128, 138, 150),
    "steel_dk": (28, 32, 40),
    "brass": (176, 132, 52),
    "brass_hi": (232, 196, 96),
    "brass_dk": (104, 72, 28),
    "gold": (210, 164, 58),
    "gold_hi": (248, 220, 128),
    "cyan": (64, 188, 204),
    "cyan_hi": (176, 244, 248),
    "cyan_dk": (18, 78, 98),
    "violet": (118, 58, 172),
    "violet_hi": (198, 142, 236),
    "violet_dk": (42, 18, 68),
    "brown": (72, 46, 28),
    "brown_hi": (118, 78, 44),
    "brown_dk": (38, 24, 16),
    "cream": (228, 214, 186),
    "green": (46, 132, 72),
    "green_hi": (168, 220, 120),
    "green_dk": (16, 58, 34),
    "orange": (196, 92, 28),
    "orange_hi": (248, 176, 64),
    "orange_dk": (92, 36, 12),
}


def clamp(v: int) -> int:
    return 0 if v < 0 else 255 if v > 255 else v


def shade(c, d: int):
    return tuple(clamp(ch + d) for ch in c)


def noise(img: Image.Image, seed: int, amp: int = 6) -> None:
    px = img.load()
    n = seed & 0xFFFF
    w, h = img.size
    for y in range(h):
        for x in range(w):
            n = (n * 1103515245 + 12345) & 0x7FFFFFFF
            delta = ((n >> 16) % (amp * 2 + 1)) - amp
            r, g, b, a = px[x, y]
            px[x, y] = (clamp(r + delta), clamp(g + delta), clamp(b + delta), a)


def plate(base, hi, rivet, seed: int, motif: str) -> Image.Image:
    img = Image.new("RGBA", (32, 32), base + (255,))
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, 31, 1), fill=hi + (255,))
    d.rectangle((0, 0, 1, 31), fill=hi + (255,))
    d.rectangle((0, 30, 31, 31), fill=shade(base, -30) + (255,))
    d.rectangle((30, 0, 31, 31), fill=shade(base, -30) + (255,))
    for i in range(0, 32, 8):
        d.line((i, 2, i, 29), fill=shade(base, -14) + (255,))
    for y in (3, 15, 27):
        for x in (3, 15, 27):
            d.point((x, y), fill=rivet + (255,))
            d.point((x + 1, y), fill=shade(rivet, 36) + (255,))
    if motif == "lens":
        d.ellipse((4, 4, 27, 27), outline=PAL["cyan_hi"] + (255,))
        d.ellipse((10, 10, 21, 21), fill=PAL["cyan"] + (255,))
        d.line((15, 2, 15, 29), fill=PAL["cyan_hi"] + (255,))
        d.line((2, 15, 29, 15), fill=PAL["cyan_hi"] + (255,))
    elif motif == "gold":
        d.ellipse((6, 6, 25, 25), outline=PAL["gold_hi"] + (255,), width=2)
        d.polygon([(16, 6), (22, 16), (16, 26), (10, 16)], outline=PAL["cream"] + (255,))
    elif motif == "crystal":
        d.polygon([(16, 2), (28, 16), (16, 29), (4, 16)], outline=PAL["cream"] + (255,))
        d.polygon([(16, 8), (23, 16), (16, 23), (9, 16)], fill=PAL["cyan_hi"] + (255,))
    elif motif == "void":
        d.polygon([(16, 3), (28, 16), (16, 28), (4, 16)], outline=PAL["violet_hi"] + (255,))
        d.polygon([(16, 9), (22, 16), (16, 23), (10, 16)], fill=PAL["violet"] + (255,))
    elif motif == "slot":
        d.ellipse((8, 4, 23, 16), outline=PAL["gold_hi"] + (255,))
        d.rectangle((13, 14, 18, 27), fill=PAL["ink"] + (255,))
        d.point((15, 9), fill=PAL["cyan_hi"] + (255,))
    elif motif == "mouth":
        d.rectangle((5, 5, 26, 26), fill=PAL["ink"] + (255,))
        d.rectangle((11, 11, 20, 20), fill=PAL["violet"] + (255,))
    elif motif == "chevron":
        d.polygon([(4, 22), (16, 6), (28, 22), (22, 22), (16, 14), (10, 22)], fill=PAL["brass_hi"] + (255,))
    elif motif == "seal":
        d.ellipse((5, 5, 26, 26), outline=PAL["gold_hi"] + (255,), width=2)
        d.polygon(
            [(16, 8), (19, 14), (26, 14), (20, 18), (22, 25), (16, 21), (10, 25), (12, 18), (6, 14), (13, 14)],
            fill=PAL["violet_hi"] + (255,),
        )
    elif motif == "screen":
        d.rectangle((3, 4, 28, 24), fill=PAL["cyan_dk"] + (255,))
        d.ellipse((12, 8, 20, 16), outline=PAL["cyan_hi"] + (255,))
        d.rectangle((14, 15, 18, 23), fill=PAL["cyan_hi"] + (255,))
    elif motif == "exit":
        d.polygon([(6, 24), (16, 8), (18, 8), (18, 26), (12, 26), (12, 16), (8, 20)], fill=PAL["green_hi"] + (255,))
        d.rectangle((20, 12, 27, 22), outline=PAL["green_hi"] + (255,))
    elif motif == "hazard":
        for i in range(-32, 32, 6):
            d.line((i, 0, i + 32, 32), fill=PAL["ink"] + (255,), width=2)
        d.polygon([(16, 6), (26, 26), (6, 26)], outline=PAL["gold_hi"] + (255,))
    elif motif == "ticket":
        d.rectangle((5, 6, 26, 25), fill=PAL["cream"] + (255,), outline=PAL["ink"] + (255,))
        for y in (11, 15, 19):
            d.line((8, y, 18, y), fill=PAL["brass_dk"] + (255,))
        d.ellipse((19, 10, 25, 16), outline=PAL["brass"] + (255,))
    elif motif == "pipe":
        d.rectangle((11, 0, 20, 31), fill=PAL["steel_hi"] + (255,))
        d.rectangle((14, 0, 17, 31), fill=PAL["cyan_dk"] + (255,))
        for y in range(1, 31, 6):
            d.rectangle((9, y, 22, y + 1), fill=PAL["brass"] + (255,))
    elif motif == "vent":
        for y in (6, 12, 18, 24):
            d.rectangle((4, y, 27, y + 2), fill=shade(base, -40) + (255,))
            d.line((4, y, 27, y), fill=hi + (255,))
    noise(img, seed, 5)
    return img


TEXTURES = {
    "echo_large_hull": ("navy", "navy_hi", "brass", 11, "plate"),
    "echo_large_frame": ("steel_dk", "steel_hi", "brass_hi", 23, "plate"),
    "echo_large_brass": ("brass_dk", "brass_hi", "gold_hi", 37, "plate"),
    "echo_large_gold": ("gold", "gold_hi", "cream", 51, "gold"),
    "echo_large_brown": ("brown_dk", "brown_hi", "brass", 67, "plate"),
    "echo_large_energy": ("cyan_dk", "cyan_hi", "navy", 83, "lens"),
    "echo_large_void": ("violet_dk", "violet_hi", "gold", 97, "void"),
    "echo_large_crystal": ("cyan", "cyan_hi", "navy", 113, "crystal"),
    "echo_large_black": ("violet", "violet_hi", "ink", 127, "void"),
    "echo_large_slot": ("brass_dk", "brass_hi", "gold", 141, "slot"),
    "echo_large_mouth": ("brown", "brass", "violet", 157, "mouth"),
    "echo_large_pipe": ("steel", "steel_hi", "brass", 173, "pipe"),
    "echo_large_vent": ("steel_dk", "steel_hi", "brass", 189, "vent"),
    "echo_large_crate": ("steel", "steel_hi", "brass", 203, "chevron"),
    "echo_large_seal": ("violet_dk", "violet_hi", "gold", 217, "seal"),
    "echo_large_screen": ("cyan_dk", "cyan_hi", "cream", 233, "screen"),
    "echo_large_exit": ("green_dk", "green_hi", "cream", 249, "exit"),
    "echo_large_hazard": ("orange", "orange_hi", "ink", 263, "hazard"),
    "echo_large_ticket": ("brass_dk", "cream", "brass", 277, "ticket"),
    "echo_large_green": ("green", "green_hi", "cream", 291, "plate"),
    "echo_large_orange": ("orange_dk", "orange_hi", "gold", 307, "plate"),
}


def write_textures() -> None:
    TEX.mkdir(parents=True, exist_ok=True)
    for name, (base, hi, rivet, seed, motif) in TEXTURES.items():
        img = plate(PAL[base], PAL[hi], PAL[rivet], seed, motif)
        if img.getextrema()[3] != (255, 255) or img.size != (32, 32):
            raise SystemExit(name)
        img.save(TEX / f"{name}.png")


def r3(v: float) -> float:
    return round(float(v) + 0.0, 3)


def box(x0, y0, z0, x1, y1, z1, tex: str) -> dict:
    if x1 < x0:
        x0, x1 = x1, x0
    if y1 < y0:
        y0, y1 = y1, y0
    if z1 < z0:
        z0, z1 = z1, z0
    if x1 - x0 < 0.05:
        x1 = x0 + 0.05
    if y1 - y0 < 0.05:
        y1 = y0 + 0.05
    if z1 - z0 < 0.05:
        z1 = z0 + 0.05

    def uv_of(u0, v0, u1, v1):
        if u1 < u0:
            u0, u1 = u1, u0
        if v1 < v0:
            v0, v1 = v1, v0
        u0, u1 = max(0.0, min(16.0, u0)), max(0.0, min(16.0, u1))
        v0, v1 = max(0.0, min(16.0, v0)), max(0.0, min(16.0, v1))
        if u1 - u0 < 0.05:
            u0, u1 = min(u0, 15.75), min(u0, 15.75) + 0.25
        if v1 - v0 < 0.05:
            v0, v1 = min(v0, 15.75), min(v0, 15.75) + 0.25
        return [r3(u0), r3(v0), r3(u1), r3(v1)]

    # UV is the face rectangle folded into one 16x16 tile. Faces wider than
    # 16 repeat the plate instead of leaving the atlas.
    def tile(a, b):
        span = b - a
        if span <= 16:
            return a % 16, (a % 16) + span
        return 0.0, 16.0

    nu0, nu1 = tile(x0, x1)
    nv0, nv1 = tile(16 - y1, 16 - y0) if y1 - y0 <= 16 else (0.0, 16.0)
    # Vertical UV grows downward in model space, so use y directly then flip.
    yu0, yu1 = tile(y0, y1)
    yv0, yv1 = 16 - yu1, 16 - yu0
    zu0, zu1 = tile(z0, z1)
    faces = {
        "north": {"uv": uv_of(nu0, yv0, nu1, yv1), "texture": f"#{tex}"},
        "south": {"uv": uv_of(16 - nu1 if nu1 <= 16 else 0, yv0, 16 - nu0 if nu0 >= 0 else 16, yv1), "texture": f"#{tex}"},
        "east": {"uv": uv_of(16 - zu1 if zu1 <= 16 else 0, yv0, 16 - zu0 if zu0 >= 0 else 16, yv1), "texture": f"#{tex}"},
        "west": {"uv": uv_of(zu0, yv0, zu1, yv1), "texture": f"#{tex}"},
        "up": {"uv": uv_of(nu0, zu0, nu1, zu1), "texture": f"#{tex}"},
        "down": {"uv": uv_of(nu0, 16 - zu1 if zu1 <= 16 else 0, nu1, 16 - zu0 if zu0 >= 0 else 16), "texture": f"#{tex}"},
    }
    return {
        "from": [r3(x0), r3(y0), r3(z0)],
        "to": [r3(x1), r3(y1), r3(z1)],
        "faces": faces,
    }


def add(elements, a, b, tex: str) -> None:
    elements.append(box(a[0], a[1], a[2], b[0], b[1], b[2], tex))


def stair(elements, x0, x1, z0, z1, y0, steps, rise, tex: str, toward: str) -> None:
    span_x = x1 - x0
    span_z = z1 - z0
    for i in range(steps):
        y_a = y0 + i * rise
        y_b = y_a + rise
        if toward == "south":
            z_a = z0 + span_z * i / steps
            add(elements, (x0, y_a, z_a), (x1, y_b, z1), tex)
        elif toward == "north":
            z_b = z1 - span_z * i / steps
            add(elements, (x0, y_a, z0), (x1, y_b, z_b), tex)
        elif toward == "east":
            x_a = x0 + span_x * i / steps
            add(elements, (x_a, y_a, z0), (x1, y_b, z1), tex)
        else:
            x_b = x1 - span_x * i / steps
            add(elements, (x0, y_a, z0), (x_b, y_b, z1), tex)


def display_for(kind: str) -> dict:
    if kind == "large":
        gui, ground, fixed, hand, fp = 0.30, 0.14, 0.26, 0.18, 0.20
        trans = [0, -2, 0]
    else:
        gui, ground, fixed, hand, fp = 0.36, 0.16, 0.30, 0.22, 0.24
        trans = [0, -1, 0]
    return {
        "gui": {"rotation": [30, 225, 0], "translation": trans, "scale": [gui, gui, gui]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [ground, ground, ground]},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [fixed, fixed, fixed]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 1, 0], "scale": [hand, hand, hand]},
        "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 1, 0], "scale": [hand, hand, hand]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 1, 0], "scale": [fp, fp, fp]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 1, 0], "scale": [fp, fp, fp]},
    }


def textures(particle: str) -> dict:
    out = {"particle": f"{NS}:block/echo_large_{particle}"}
    for name in TEXTURES:
        out[name.removeprefix("echo_large_")] = f"{NS}:block/{name}"
    return out


def emit(elements, particle: str, kind: str) -> dict:
    if len(elements) < 30:
        raise SystemExit(f"model has only {len(elements)} elements")
    return {
        "credit": "echo multiblock original machine",
        "textures": textures(particle),
        "elements": elements,
        "display": display_for(kind),
    }


def beacon(core: str, trim: str, crown: str) -> list:
    """信标：八角底座 + 收窄塔身 + 四面发光面板 + 悬浮核心 + 王冠。

    与旧版「大方箱 + 侧面板」的区别：整体从 48 高的箱体收成塔形，
    中段留出环带，顶部核心悬空，远看有明确的高度轮廓。
    高度上限受物品栏模型约束（y <= 32），因此塔顶停在 31.6。
    """
    e = []
    # 底座：三级台阶，逐级收窄
    add(e, (2, 0, 2), (46, 3, 46), "frame")
    add(e, (4, 3, 4), (44, 5, 44), "hull")
    add(e, (7, 5, 7), (41, 7, 41), trim)
    # 四角立柱：细而高，撑出塔的轮廓
    for x0, z0 in ((7, 7), (37, 7), (7, 37), (37, 37)):
        add(e, (x0, 7, z0), (x0 + 4, 24, z0 + 4), "brass")
        add(e, (x0 + 1, 24, z0 + 1), (x0 + 3, 26, z0 + 3), trim)
    # 塔身：中央细柱
    add(e, (17, 7, 17), (31, 27, 31), "hull")
    # 四面发光面板（比塔身凸出 0.4，避免 z-fighting）
    add(e, (19, 10, 15.6), (29, 23, 17.2), core)
    add(e, (19, 10, 30.8), (29, 23, 32.4), core)
    add(e, (15.6, 10, 19), (17.2, 23, 29), core)
    add(e, (30.8, 10, 19), (32.4, 23, 29), core)
    # 面板上下沿的装饰条
    for y0, y1 in ((8, 9.6), (23.4, 25)):
        add(e, (19, y0, 15.4), (29, y1, 17.4), trim)
        add(e, (19, y0, 30.6), (29, y1, 32.6), trim)
        add(e, (15.4, y0, 19), (17.4, y1, 29), trim)
        add(e, (30.6, y0, 19), (32.6, y1, 29), trim)
    # 中段环带：给塔身一个水平节奏
    add(e, (15, 20, 15), (33, 21.6, 33), trim)
    add(e, (16, 14, 16), (32, 15.2, 32), "gold")
    # 塔顶平台
    add(e, (15, 27, 15), (33, 29, 33), "frame")
    # 悬浮核心
    add(e, (19, 29.4, 19), (29, 30.8, 29), core)
    # 王冠：开放核心与核心信标两种收尾
    if crown == "gold":
        add(e, (16, 30.8, 16), (32, 31.6, 32), "gold")
        for dx, dz in ((14, 22), (30, 22), (22, 14), (22, 30)):
            add(e, (dx, 30.4, dz), (dx + 4, 31.6, dz + 4), "gold")
    else:
        add(e, (17, 30.8, 17), (31, 31.6, 31), trim)
        add(e, (20, 31.2, 20), (28, 32, 28), core)
    # 底座光环：八段弧形凸起，提示「站这里投料」
    for x0, z0, x1, z1 in ((10, 0.4, 22, 2.2), (26, 0.4, 38, 2.2),
                           (10, 45.8, 22, 47.6), (26, 45.8, 38, 47.6),
                           (0.4, 10, 2.2, 22), (0.4, 26, 2.2, 38),
                           (45.8, 10, 47.6, 22), (45.8, 26, 47.6, 38)):
        add(e, (x0, 0, z0), (x1, 1.6, z1), "gold")
    return e


def extractor(crystal: str, accent: str) -> list:
    e = []
    add(e, (2, 0, 2), (46, 3, 46), "brown")
    add(e, (3, 3, 4), (45, 5, 42), "frame")
    for x0, z0 in ((1, 1), (42, 1), (1, 40), (42, 40)):
        add(e, (x0, 0, z0), (x0 + 5, 9, z0 + 5), "brass")
    add(e, (3, 5, 8), (12, 26, 38), "frame")
    add(e, (36, 5, 8), (45, 26, 38), "frame")
    add(e, (12, 20, 10), (36, 26, 36), "hull")
    add(e, (12, 5, 28), (36, 20, 40), "hull")
    add(e, (16, 6, 10), (32, 22, 24), crystal)
    add(e, (14, 8, 12), (16, 20, 22), "pipe")
    add(e, (32, 8, 12), (34, 20, 22), "pipe")
    add(e, (18, 4.2, 14), (30, 6.2, 22), accent)
    add(e, (8, 10, 4), (16, 22, 10), "frame")
    add(e, (32, 10, 4), (40, 22, 10), "frame")
    add(e, (14, 18, 2.4), (34, 22, 6), accent)
    add(e, (16, 8, 2.6), (32, 12, 5), "brass")
    stair(e, 16, 32, 0.8, 5, 0, 3, 1.5, "brass", "south")
    add(e, (18, 26, 16), (30, 29, 34), "pipe")
    add(e, (20, 26, 8), (28, 28, 16), accent)
    add(e, (14, 22, 36), (34, 28, 44), "frame")
    add(e, (18, 28, 38), (30, 31.2, 43), "vent")
    add(e, (22, 31.2, 39.5), (26, 31.8, 42), accent)
    add(e, (4, 14, 16), (8, 18, 30), "vent")
    add(e, (40, 14, 16), (44, 18, 30), "vent")
    add(e, (0.6, 12, 18), (3.2, 20, 28), "pipe")
    add(e, (44.8, 12, 18), (47.4, 20, 28), "pipe")
    add(e, (6, 26, 18), (12, 28.5, 30), "brass")
    add(e, (36, 26, 18), (42, 28.5, 30), "brass")
    return e


def altar() -> list:
    e = []
    add(e, (2, 0, 2), (46, 3, 46), "brown")
    add(e, (4, 3, 4), (44, 6, 42), "brass")
    stair(e, 10, 38, 1, 10, 0, 4, 1.4, "gold", "south")
    stair(e, 10, 38, 38, 46, 0, 3, 1.2, "brass", "north")
    add(e, (8, 6, 10), (40, 12, 38), "brown")
    add(e, (12, 12, 14), (36, 16, 34), "hull")
    add(e, (14, 16, 16), (34, 19, 32), "brass")
    add(e, (16, 8, 6.2), (32, 16, 10), "frame")
    add(e, (18, 9, 5.2), (30, 15, 7.2), "slot")
    add(e, (17, 8.4, 4.6), (19, 15.6, 6.4), "gold")
    add(e, (29, 8.4, 4.6), (31, 15.6, 6.4), "gold")
    add(e, (2, 10, 14), (8, 14, 22), "gold")
    add(e, (3, 14, 15.2), (7, 18, 20.4), "energy")
    add(e, (40, 10, 14), (46, 14, 22), "gold")
    add(e, (41, 14, 15.2), (45, 18, 20.4), "energy")
    add(e, (16, 12, 34), (32, 22, 42), "frame")
    add(e, (18, 20, 36), (30, 24, 41), "energy")
    add(e, (20, 19, 18), (28, 21, 30), "slot")
    add(e, (22, 21, 20), (26, 24, 24), "gold")
    add(e, (6, 6, 28), (12, 10, 36), "pipe")
    add(e, (36, 6, 28), (42, 10, 36), "pipe")
    add(e, (10, 16, 12), (14, 18.5, 16), "brass")
    add(e, (34, 16, 12), (38, 18.5, 16), "brass")
    add(e, (8, 16.2, 8), (40, 18.2, 14), "brass")
    add(e, (2, 18.4, 34), (46, 21, 44), "frame")
    add(e, (14, 6, 2.2), (18, 8.4, 6.2), "gold")
    add(e, (30, 6, 2.2), (34, 8.4, 6.2), "gold")
    return e


def crate() -> list:
    e = []
    add(e, (1, 0, 1), (31, 3, 31), "frame")
    add(e, (2, 3, 2), (30, 22, 30), "crate")
    add(e, (1.2, 22, 1.2), (30.8, 25, 30.8), "brass")
    add(e, (6, 25, 6), (26, 27, 26), "hull")
    for x0, z0 in ((0.4, 0.4), (28.2, 0.4), (0.4, 28.2), (28.2, 28.2)):
        add(e, (x0, 0, z0), (x0 + 3.4, 24, z0 + 3.4), "brass")
    add(e, (8, 8, 0.6), (24, 18, 2.2), "brass")
    add(e, (12, 11, 0.2), (20, 16, 1.2), "frame")
    add(e, (4, 4, 0.8), (10, 7, 2), "gold")
    add(e, (22, 4, 0.8), (28, 7, 2), "gold")
    add(e, (0.3, 8, 6), (2, 12, 26), "brass")
    add(e, (30, 8, 6), (31.7, 12, 26), "brass")
    add(e, (8, 10, 29.2), (24, 18, 31.4), "vent")
    add(e, (10, 25, 12), (22, 27.4, 20), "pipe")
    add(e, (14, 27.2, 14), (18, 28.6, 18), "gold")
    stair(e, 10, 22, 0.2, 3, 0, 2, 1.2, "frame", "south")
    add(e, (6, 20, 4), (10, 22.4, 12), "hull")
    add(e, (22, 20, 4), (26, 22.4, 12), "hull")
    add(e, (6, 6, 2.2), (10, 8, 6), "pipe")
    add(e, (22, 6, 2.2), (26, 8, 6), "pipe")
    add(e, (4, 14, 0.4), (8, 16.4, 2.2), "gold")
    add(e, (24, 14, 0.4), (28, 16.4, 2.2), "gold")
    add(e, (12, 18.2, 0.4), (20, 20, 2.2), "brass")
    add(e, (8, 22.2, 4), (14, 24.4, 8), "frame")
    add(e, (18, 22.2, 4), (24, 24.4, 8), "frame")
    add(e, (14, 3, 8), (18, 6, 14), "vent")
    add(e, (6, 24.2, 22), (12, 26.4, 28), "brass")
    add(e, (20, 24.2, 22), (26, 26.4, 28), "brass")
    return e


def market() -> list:
    e = []
    add(e, (1, 0, 1), (31, 3, 31), "frame")
    add(e, (3, 3, 4), (29, 24, 29), "hull")
    add(e, (2, 24, 3), (30, 26.5, 29), "seal")
    add(e, (7, 6, 1.2), (25, 22, 4.2), "brass")
    add(e, (9, 8, 0.6), (23, 20, 2.4), "seal")
    add(e, (13, 12, 0.2), (19, 16, 1.2), "void")
    add(e, (8, 5, 2), (10, 21, 3.4), "gold")
    add(e, (22, 5, 2), (24, 21, 3.4), "gold")
    add(e, (0.4, 8, 8), (3.2, 18, 22), "void")
    add(e, (28.8, 8, 8), (31.6, 18, 22), "void")
    add(e, (10, 8, 28), (22, 18, 31.4), "vent")
    add(e, (12, 26, 10), (20, 28.4, 22), "pipe")
    add(e, (14, 28.2, 14), (18, 30.2, 18), "gold")
    stair(e, 10, 22, 0.3, 3.2, 0, 2, 1.3, "brass", "south")
    add(e, (4, 3, 6), (8, 6, 14), "brass")
    add(e, (24, 3, 6), (28, 6, 14), "brass")
    add(e, (6, 22, 6), (12, 24.4, 12), "frame")
    add(e, (20, 22, 6), (26, 24.4, 12), "frame")
    add(e, (11, 16.2, 0.2), (15, 18.6, 1.4), "gold")
    add(e, (17, 16.2, 0.2), (21, 18.6, 1.4), "gold")
    add(e, (4, 18, 4), (8, 22, 10), "brass")
    add(e, (24, 18, 4), (28, 22, 10), "brass")
    add(e, (6, 26.2, 8), (12, 28.4, 14), "void")
    add(e, (20, 26.2, 8), (26, 28.4, 14), "void")
    add(e, (14, 3.2, 10), (18, 6.4, 16), "pipe")
    add(e, (8, 20, 24), (14, 23.2, 28), "frame")
    add(e, (18, 20, 24), (24, 23.2, 28), "frame")
    add(e, (2.2, 10, 6), (4.4, 14, 10), "gold")
    add(e, (27.6, 10, 6), (29.8, 14, 10), "gold")
    return e


def dropoff() -> list:
    e = []
    add(e, (1, 0, 1), (31, 3, 31), "frame")
    add(e, (3, 3, 6), (29, 20, 28), "hull")
    add(e, (6, 6, 1.4), (26, 18, 6), "brass")
    add(e, (8, 8, 2.2), (24, 16, 5.2), "mouth")
    add(e, (10, 10, 4.6), (22, 15, 10), "void")
    stair(e, 8, 24, 0.4, 4, 3, 3, 1.4, "brass", "south")
    add(e, (4, 16, 4), (28, 19, 8), "gold")
    add(e, (0.4, 8, 10), (3.4, 16, 22), "pipe")
    add(e, (28.6, 8, 10), (31.6, 16, 22), "pipe")
    add(e, (8, 8, 26), (24, 18, 31.2), "vent")
    add(e, (6, 20, 8), (26, 23, 26), "frame")
    add(e, (10, 23, 12), (22, 26, 24), "brown")
    add(e, (13, 26, 15), (19, 28.6, 21), "mouth")
    add(e, (14.5, 28.4, 16.5), (17.5, 30.2, 19.5), "energy")
    add(e, (4, 4, 12), (8, 7, 20), "brass")
    add(e, (24, 4, 12), (28, 7, 20), "brass")
    add(e, (12, 18, 18), (20, 20.4, 24), "pipe")
    add(e, (6, 18.2, 1.2), (10, 20.4, 4), "gold")
    add(e, (22, 18.2, 1.2), (26, 20.4, 4), "gold")
    add(e, (2, 4, 4), (6, 8, 10), "frame")
    add(e, (26, 4, 4), (30, 8, 10), "frame")
    add(e, (10, 20.2, 4), (14, 22.6, 8), "vent")
    add(e, (18, 20.2, 4), (22, 22.6, 8), "vent")
    add(e, (8, 24, 10), (14, 26.4, 16), "brass")
    add(e, (18, 24, 10), (24, 26.4, 16), "brass")
    add(e, (14, 6, 24), (18, 10, 28), "pipe")
    add(e, (2.4, 12, 2), (5.2, 16, 6), "brass")
    add(e, (26.8, 12, 2), (29.6, 16, 6), "brass")
    return e


def console(screen: str, accent: str, lamp: str) -> list:
    e = []
    add(e, (1, 0, 1), (31, 3, 31), "frame")
    add(e, (3, 3, 6), (29, 22, 29), "hull")
    stair(e, 6, 26, 1, 7, 3, 3, 1.6, accent, "south")
    add(e, (4, 8, 4), (28, 12, 16), accent)
    add(e, (6, 12, 8), (26, 24, 14), "frame")
    add(e, (8, 14, 6.4), (24, 22, 8.6), screen)
    add(e, (7, 13, 5.6), (9, 23, 7.2), "brass")
    add(e, (23, 13, 5.6), (25, 23, 7.2), "brass")
    add(e, (10, 22.2, 7), (22, 23.4, 9), lamp)
    well = "ticket" if screen == "ticket" else "slot"
    add(e, (12, 11.6, 2.4), (20, 13.2, 5.2), well)
    add(e, (13.2, 12.6, 3.2), (15.2, 14.2, 4.6), lamp)
    add(e, (0.4, 6, 10), (3.2, 18, 24), "pipe")
    add(e, (28.8, 6, 10), (31.6, 18, 24), "pipe")
    add(e, (8, 10, 26), (24, 20, 31.2), "vent")
    add(e, (10, 22, 16), (22, 25, 26), "frame")
    add(e, (12, 25, 18), (20, 28, 24), accent)
    add(e, (14, 28, 19), (18, 30.4, 23), lamp)
    add(e, (4, 4, 18), (8, 8, 26), "brass")
    add(e, (24, 4, 18), (28, 8, 26), "brass")
    add(e, (6, 22.4, 6), (12, 24.6, 12), "frame")
    add(e, (20, 22.4, 6), (26, 24.6, 12), "frame")
    add(e, (4, 12, 2.2), (8, 15, 5), accent)
    add(e, (24, 12, 2.2), (28, 15, 5), accent)
    add(e, (10, 26, 8), (14, 28.4, 14), "pipe")
    add(e, (18, 26, 8), (22, 28.4, 14), "pipe")
    add(e, (12, 23.6, 5.2), (20, 25.2, 8), lamp)
    add(e, (18, 24.2, 22), (24, 26.6, 28), "brass")
    add(e, (8, 24.2, 22), (14, 26.6, 28), "brass")
    return e


BUILDERS = {
    "echo_beacon": lambda: (beacon("energy", "brass", "gold"), "energy", "large"),
    "echo_core_beacon": lambda: (beacon("gold", "gold", "energy"), "gold", "large"),
    "echo_crystal_extractor": lambda: (extractor("crystal", "brass"), "crystal", "large"),
    "echo_black_crystal_extractor": lambda: (extractor("black", "void"), "black", "large"),
    "echo_key_altar": lambda: (altar(), "slot", "large"),
    "echo_supply_crate": lambda: (crate(), "crate", "small"),
    "echo_black_market": lambda: (market(), "seal", "small"),
    "echo_black_dropoff": lambda: (dropoff(), "mouth", "small"),
    "echo_core_console": lambda: (console("screen", "brass", "energy"), "screen", "small"),
    "echo_free_exit": lambda: (console("exit", "green", "green"), "exit", "small"),
    "echo_evacuation_console": lambda: (console("hazard", "orange", "hazard"), "hazard", "small"),
    "echo_broker_exit": lambda: (console("ticket", "brass", "gold"), "ticket", "small"),
}


def clip_element(element: dict, ox: float, oy: float, oz: float) -> dict | None:
    x0, y0, z0 = element["from"]
    x1, y1, z1 = element["to"]
    nx0, ny0, nz0 = x0 - ox, y0 - oy, z0 - oz
    nx1, ny1, nz1 = x1 - ox, y1 - oy, z1 - oz
    cx0, cy0, cz0 = max(0.0, nx0), max(0.0, ny0), max(0.0, nz0)
    cx1, cy1, cz1 = min(16.0, nx1), min(16.0, ny1), min(16.0, nz1)
    if cx1 - cx0 < 0.05 or cy1 - cy0 < 0.05 or cz1 - cz0 < 0.05:
        return None
    faces = {}
    src = element["faces"]

    def cut(face, axis_u, axis_v, flip_u, flip_v):
        su = src[face]["uv"]
        u_span = su[2] - su[0]
        v_span = su[3] - su[1]
        local = {"x": (nx0, nx1, cx0, cx1), "y": (ny0, ny1, cy0, cy1), "z": (nz0, nz1, cz0, cz1)}
        a0, a1, c0, c1 = local[axis_u]
        b0, b1, d0, d1 = local[axis_v]
        if abs(a1 - a0) < 1e-6 or abs(b1 - b0) < 1e-6 or u_span <= 0 or v_span <= 0:
            return
        tu0 = (c0 - a0) / (a1 - a0)
        tu1 = (c1 - a0) / (a1 - a0)
        tv0 = (d0 - b0) / (b1 - b0)
        tv1 = (d1 - b0) / (b1 - b0)
        if flip_u:
            tu0, tu1 = 1 - tu1, 1 - tu0
        if flip_v:
            tv0, tv1 = 1 - tv1, 1 - tv0
        u0, u1 = su[0] + u_span * tu0, su[0] + u_span * tu1
        v0, v1 = su[1] + v_span * tv0, su[1] + v_span * tv1
        if u1 < u0:
            u0, u1 = u1, u0
        if v1 < v0:
            v0, v1 = v1, v0
        u0, u1 = max(0.0, min(16.0, u0)), max(0.0, min(16.0, u1))
        v0, v1 = max(0.0, min(16.0, v0)), max(0.0, min(16.0, v1))
        if u1 - u0 < 0.05:
            u0, u1 = 0.0, 0.25
        if v1 - v0 < 0.05:
            v0, v1 = 0.0, 0.25
        faces[face] = {"uv": [r3(u0), r3(v0), r3(u1), r3(v1)], "texture": src[face]["texture"]}

    cut("north", "x", "y", False, True)
    cut("south", "x", "y", True, True)
    cut("west", "z", "y", False, True)
    cut("east", "z", "y", True, True)
    cut("up", "x", "z", False, False)
    cut("down", "x", "z", False, True)
    if not faces:
        return None
    return {
        "from": [r3(cx0), r3(cy0), r3(cz0)],
        "to": [r3(cx1), r3(cy1), r3(cz1)],
        "faces": faces,
    }


def part_origin(part: int) -> tuple[float, float, float]:
    return (part % 3) * 16.0, (part // 9) * 16.0, ((part // 3) % 3) * 16.0


def clip_model(full: dict, part: int) -> dict:
    ox, oy, oz = part_origin(part)
    elements = []
    for element in full["elements"]:
        clipped = clip_element(element, ox, oy, oz)
        if clipped is not None:
            elements.append(clipped)
    return {"credit": full["credit"], "textures": full["textures"], "elements": elements}


def empty_model(particle: str) -> dict:
    return {
        "credit": "echo multiblock unused part",
        "textures": {"particle": f"{NS}:block/echo_large_{particle}"},
        "elements": [],
    }


def blockstate(bid: str, large: bool) -> dict:
    variants = {}
    for facing in DIRS:
        for part in range(18):
            if large or part in SMALL_PARTS:
                model = f"{NS}:block/{bid}_part{part}"
            else:
                model = f"{NS}:block/{bid}_empty"
            entry = {"model": model}
            if YAW[facing] is not None:
                entry["y"] = YAW[facing]
            variants[f"facing={facing},part={part}"] = entry
    return {"variants": variants}


def loot_table(bid: str) -> dict:
    return {
        "type": "minecraft:block",
        "pools": [
            {
                "rolls": 1,
                "entries": [{"type": "minecraft:item", "name": f"{NS}:{bid}"}],
                "conditions": [{"condition": "minecraft:survives_explosion"}],
            }
        ],
    }


def write_json(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def inventory_model(full: dict, large: bool) -> dict:
    # World parts are clipped before this translation. Only the whole item model
    # is centered on (8, *, 8), keeping vanilla's strict -16..32 element limits.
    shift = 16 if large else 8
    elements = []
    for element in full["elements"]:
        item = dict(element)
        for key in ("from", "to"):
            x, y, z = element[key]
            item[key] = [r3(x - shift), y, r3(z - shift)]
        elements.append(item)
    return {**full, "elements": elements}


def validate(bid: str, full: dict, parts: dict, large: bool) -> None:
    if len(full["elements"]) < 30:
        raise SystemExit(f"{bid} inventory has {len(full['elements'])} elements")
    limit = 32.0
    for element in full["elements"]:
        x0, y0, z0 = element["from"]
        x1, y1, z1 = element["to"]
        if not (-16 <= x0 < x1 <= limit and 0 <= y0 < y1 <= 32 and -16 <= z0 < z1 <= limit):
            raise SystemExit(f"{bid} inventory out of range {element['from']} {element['to']}")
        if "rotation" in element:
            raise SystemExit(f"{bid} rotated element")
        for face in element["faces"].values():
            u0, v0, u1, v1 = face["uv"]
            if not (0 <= u0 < u1 <= 16 and 0 <= v0 < v1 <= 16):
                raise SystemExit(f"{bid} uv {face['uv']}")
    for part, model in parts.items():
        if not large and part not in SMALL_PARTS:
            continue
        if not model["elements"]:
            raise SystemExit(f"{bid} part {part} clipped to nothing")
        for element in model["elements"]:
            for axis in range(3):
                a, b = element["from"][axis], element["to"][axis]
                if a < -0.001 or b > 16.001 or a >= b:
                    raise SystemExit(f"{bid} part {part} escaped {element['from']} {element['to']}")
            for face in element["faces"].values():
                u0, v0, u1, v1 = face["uv"]
                if not (0 <= u0 < u1 <= 16 and 0 <= v0 < v1 <= 16):
                    raise SystemExit(f"{bid} part {part} uv {face['uv']}")


def main() -> None:
    write_textures()
    for bid in IDS:
        elements, particle, kind = BUILDERS[bid]()
        full = emit(elements, particle, kind)
        large = kind == "large"
        parts = {part: clip_model(full, part) for part in range(18)}
        inventory = inventory_model(full, large)
        validate(bid, inventory, parts, large)
        write_json(BLOCK_MODELS / f"{bid}.json", inventory)
        for part, model in parts.items():
            if large or part in SMALL_PARTS:
                write_json(BLOCK_MODELS / f"{bid}_part{part}.json", model)
        if not large:
            write_json(BLOCK_MODELS / f"{bid}_empty.json", empty_model(particle))
        write_json(ITEM_MODELS / f"{bid}.json", {"parent": f"{NS}:block/{bid}"})
        write_json(BLOCKSTATES / f"{bid}.json", blockstate(bid, large))
        write_json(LOOT / f"{bid}.json", loot_table(bid))
        counts = [len(parts[p]["elements"]) for p in (range(18) if large else SMALL_PARTS)]
        print(f"{bid}: inventory {len(full['elements'])} elements, parts {counts}")


if __name__ == "__main__":
    main()
