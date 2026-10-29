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


# ---------------------------------------------------------------- 造型小工具
def quad(e, cx, cz, x0, y0, z0, x1, y1, z1, tex, mx=True, mz=True) -> None:
    """以 (cx, cz) 为镜像轴放置 1 / 2 / 4 个对称方块。"""
    xs = [(x0, x1)]
    if mx:
        xs.append((2 * cx - x1, 2 * cx - x0))
    zs = [(z0, z1)]
    if mz:
        zs.append((2 * cz - z1, 2 * cz - z0))
    for a, b in xs:
        for c, d in zs:
            add(e, (a, y0, c), (b, y1, d), tex)


def taper(e, cx, cz, half, y0, y1, tex, seg=4, tip_tex=None) -> None:
    """阶梯收缩的方尖晶体（黑晶尖刺 / 塔尖）。"""
    for i in range(seg):
        h = half * (1.0 - 0.2 * i)
        a = y0 + (y1 - y0) * i / seg
        b = y0 + (y1 - y0) * (i + 1) / seg
        add(e, (cx - h, a, cz - h), (cx + h, b, cz + h), tip_tex if tip_tex and i >= seg - 2 else tex)


def disc(e, cx, cy, cz, r, depth, tex) -> None:
    """朝 -z 的圆盘：三层方盒近似圆（模型不允许 rotation 时的最圆解）。"""
    for k, (ax, ay) in enumerate(((0.74, 1.1), (1.1, 0.74), (1.0, 1.0))):
        front = cz - depth * (0.42 + 0.29 * k)
        add(e, (cx - r * ax, cy - r * ay, front), (cx + r * ax, cy + r * ay, cz), tex)


# ---------------------------------------------------------------- 大机器 3x2x3
def build_echo_beacon() -> list:
    """开放信标：八角台基 + 四角立柱 + 中央塔身 + 四面发光面板 + 悬浮核心 + 王冠天线。

    剪影细高：台基 48 宽，塔身收到 10 宽，顶端王冠停在 y=32（物品栏上限）。
    四面面板比塔身凸出 2.2，避免同面 z-fighting。
    """
    e = []
    cx = cz = 24.0
    # 八角台基：中心方台 + 四边中间延伸，四角留空形成八角轮廓
    add(e, (8, 0, 8), (40, 2.6, 40), "frame")
    quad(e, cx, cz, 4, 0, 12, 44, 2.6, 36, "frame", mx=False)
    quad(e, cx, cz, 12, 0, 4, 36, 2.6, 44, "frame", mz=False)
    add(e, (10, 2.6, 10), (38, 4.8, 38), "hull")
    quad(e, cx, cz, 6, 2.6, 13, 42, 4.8, 35, "hull", mx=False)
    quad(e, cx, cz, 13, 2.6, 6, 35, 4.8, 42, "hull", mz=False)
    # 黄铜束腰
    add(e, (15, 4.8, 15), (33, 6.6, 33), "brass")
    # 四角立柱
    quad(e, cx, cz, 12, 6.6, 12, 16, 26, 16, "brass")
    quad(e, cx, cz, 11.4, 26, 11.4, 16.6, 27.6, 16.6, "gold")
    # 中央塔身
    add(e, (19, 6.6, 19), (29, 28, 29), "hull")
    # 四面发光面板（比塔身凸出 2.2）
    add(e, (20, 9, 16.8), (28, 24, 19.2), "energy")
    add(e, (20, 9, 28.8), (28, 24, 31.2), "energy")
    add(e, (16.8, 9, 20), (19.2, 24, 28), "energy")
    add(e, (28.8, 9, 20), (31.2, 24, 28), "energy")
    # 面板上下压条
    quad(e, cx, cz, 19.4, 7.4, 16.4, 28.6, 8.8, 19.4, "brass", mx=False)
    quad(e, cx, cz, 19.4, 24, 16.4, 28.6, 25.4, 19.4, "brass", mx=False)
    quad(e, cx, cz, 16.4, 7.4, 19.4, 19.4, 8.8, 28.6, "brass", mz=False)
    quad(e, cx, cz, 16.4, 24, 19.4, 19.4, 25.4, 28.6, "brass", mz=False)
    # 环带：连接四根立柱，撑住上部轮廓
    quad(e, cx, cz, 12, 18, 12, 36, 20, 16, "brass", mx=False)
    quad(e, cx, cz, 12, 18, 12, 16, 20, 36, "brass", mz=False)
    add(e, (20, 28.8, 20), (28, 30.8, 28), "energy")
    # 王冠天线：细分段收尖
    add(e, (21.4, 30.8, 21.4), (26.6, 31.4, 26.6), "gold")
    add(e, (22.4, 31.4, 22.4), (25.6, 31.8, 25.6), "gold")
    add(e, (23.4, 31.8, 23.4), (24.6, 32, 24.6), "energy")
    # 冠环四角尖顶
    quad(e, cx, cz, 12.2, 27.6, 12.2, 15.8, 30, 15.8, "gold")
    quad(e, cx, cz, 12.8, 30, 12.8, 15.2, 31.2, 15.2, "energy")
    # 地面 8 段光环
    quad(e, cx, cz, 12, 0, 0.6, 36, 1.4, 4, "gold", mx=False)
    quad(e, cx, cz, 0.6, 0, 12, 4, 1.4, 36, "gold", mz=False)
    quad(e, cx, cz, 0.6, 0, 0.6, 5, 1.4, 5, "gold")
    return e


def build_echo_core_beacon() -> list:
    """核心信标：宽扁阶梯共鸣台 + 四根向内收的支撑臂 + 悬浮大晶体 + 金冠环 + 发光节点。

    剪影矮壮：整机铺满 48x48，最高点只有 y=25.4，与细高的开放信标形成对照。
    中央晶体用 5 层 box 叠出菱形轮廓。
    """
    e = []
    cx = cz = 24.0
    # 阶梯共鸣台（三级，逐级收窄）
    add(e, (1, 0, 1), (47, 2.4, 47), "frame")
    add(e, (5, 2.4, 5), (43, 4.4, 43), "hull")
    add(e, (10, 4.4, 10), (38, 6.2, 38), "brass")
    # 台角铆块
    quad(e, cx, cz, 2, 0, 2, 9, 3.6, 9, "brass")
    # 台面放射能量槽
    quad(e, cx, cz, 14, 6.2, 8, 34, 7, 12, "energy", mx=False)
    quad(e, cx, cz, 8, 6.2, 14, 12, 7, 34, "energy", mz=False)
    # 四根向内收的支撑臂：三级阶梯，越往上越靠中心
    for sx in (-1, 1):
        for sz in (-1, 1):
            for i in range(3):
                a = 13 - i * 4
                b = a + 4
                y0 = 5.0 + i * 3.6
                add(e, (24 + sx * a, y0, 24 + sz * a), (24 + sx * b, y0 + 3.6, 24 + sz * b), "brass")
    # 臂根配重块
    quad(e, cx, cz, 13, 6.2, 13, 18, 8.4, 18, "gold")
    # 中央悬浮大晶体：菱形轮廓
    add(e, (23, 9.4, 23), (25, 12, 25), "crystal")
    add(e, (20, 12, 20), (28, 15, 28), "crystal")
    add(e, (17, 15, 17), (31, 20, 31), "crystal")
    add(e, (20, 20, 20), (28, 23, 28), "crystal")
    add(e, (22, 23, 22), (26, 25.4, 26), "crystal")
    # 晶体棱面高光
    quad(e, cx, cz, 17, 15, 17, 20.4, 20, 20.4, "energy")
    # 金冠环：四角柱 + 四边横梁
    quad(e, cx, cz, 11, 15.8, 11, 15, 18.6, 15, "gold")
    quad(e, cx, cz, 15, 16.2, 11, 33, 17.6, 15, "gold", mx=False)
    quad(e, cx, cz, 11, 16.2, 15, 15, 17.6, 33, "gold", mz=False)
    # 四个发光节点
    quad(e, cx, cz, 11.4, 18.6, 11.4, 14.6, 20.4, 14.6, "energy")
    # 台缘灯带
    quad(e, cx, cz, 18, 6.2, 3, 30, 7.4, 6, "energy", mx=False)
    quad(e, cx, cz, 3, 6.2, 18, 6, 7.4, 30, "energy", mz=False)
    return e


def build_echo_crystal_extractor() -> list:
    """晶簇采集机：长条传送带（滚轮 + 侧栏）+ 采集仓 + 三爪夹钳 + 顶部排气筒 + 压力表。

    剪影高而机械：传送带沿 z 贯通整机，龙门架把上部撑到 y=32。
    """
    e = []
    cx = cz = 24.0
    # 地基
    add(e, (2, 0, 2), (46, 2.4, 46), "frame")
    quad(e, cx, cz, 0, 0, 0, 7, 3.4, 7, "brass")
    # 传送带：带面 + 两侧栏
    add(e, (11, 5.6, 0.6), (37, 7.2, 47.4), "hull")
    add(e, (8.6, 4.2, 0.6), (11, 9.8, 47.4), "frame")
    add(e, (37, 4.2, 0.6), (39.4, 9.8, 47.4), "frame")
    # 滚轮：比侧栏宽，两端露出
    for z0 in (2, 12, 22, 32, 42):
        add(e, (8, 3.2, z0), (40, 5.2, z0 + 3.2), "pipe")
    # 采集仓
    add(e, (12, 7.2, 3), (36, 20, 20), "hull")
    add(e, (14, 7.2, 1.4), (34, 18, 4), "frame")
    add(e, (16, 9, 0.6), (32, 16, 2.4), "crystal")
    add(e, (13, 20, 4), (35, 23, 18), "frame")
    add(e, (15, 23, 6), (33, 25, 16), "brass")
    # 顶部排气筒（双筒）
    add(e, (16, 25, 8), (22, 30, 14), "pipe")
    add(e, (15.2, 30, 7.2), (22.8, 31.4, 14.8), "vent")
    add(e, (17, 31.4, 9), (21, 32, 13), "energy")
    add(e, (26, 25, 8), (31, 28.6, 13), "pipe")
    add(e, (25.2, 28.6, 7.2), (31.8, 29.8, 13.8), "vent")
    # 压力表（西侧）
    add(e, (10, 11, 8), (12.6, 17, 16), "brass")
    add(e, (9.4, 11.6, 8.6), (10.4, 16.4, 15.4), "energy")
    add(e, (9.1, 13.4, 9.4), (9.5, 14.6, 14.6), "gold")
    # 龙门架：四角立柱 + 顶部横梁
    quad(e, cx, cz, 3, 9, 3, 7, 26, 7, "frame")
    quad(e, cx, cz, 3, 22, 3, 45, 25, 8, "frame", mx=False)
    quad(e, cx, cz, 3, 22, 8, 8, 25, 45, "frame", mz=False)
    # 龙门架斜撑
    quad(e, cx, cz, 3, 9, 7, 7, 12, 14, "brass", mx=False)
    # 三爪夹钳（吊在龙门架下）
    add(e, (19, 16, 20), (29, 22, 27), "frame")
    add(e, (20, 22, 21), (28, 25, 26), "pipe")
    add(e, (19.6, 11, 21.4), (22, 16, 23.8), "brass")
    add(e, (26, 11, 21.4), (28.4, 16, 23.8), "brass")
    add(e, (22.4, 11, 24.6), (25.6, 16, 27), "brass")
    add(e, (19.2, 9.6, 21), (22.4, 11, 24.2), "gold")
    add(e, (25.6, 9.6, 21), (28.8, 11, 24.2), "gold")
    add(e, (22, 9.6, 24.2), (26, 11, 27.4), "gold")
    # 尾部接收槽
    add(e, (12, 7.2, 40), (36, 14, 47), "frame")
    add(e, (14, 9, 38.6), (34, 12.6, 40.4), "energy")
    add(e, (16, 14, 41), (32, 16, 46), "vent")
    # 侧面散热片
    quad(e, cx, cz, 2, 12, 12, 4, 18, 34, "vent", mz=False)
    return e


def build_echo_black_crystal_extractor() -> list:
    """黑晶采集点：低矮晶洞 + 不规则黑晶尖刺 + 中央坑口 + 紫封印环 + 侧面抽取管。

    与晶簇采集机的差别是剪影：全机最高只有 y=23.2，尖刺从地面长出来而不是吊在架子上。
    每格心一簇尖刺，第三段跨过 y=16，保证 18 个 part 都非空。
    """
    e = []
    cx = cz = 24.0
    # 晶洞地面：三层交叠岩板
    add(e, (0, 0, 0), (48, 2.2, 48), "black")
    add(e, (3, 2.2, 3), (45, 4.2, 45), "frame")
    add(e, (8, 4.2, 8), (40, 5.8, 40), "void")
    # 中央坑口
    add(e, (16, 5.8, 16), (32, 7.6, 32), "mouth")
    add(e, (19, 6.6, 19), (29, 8.4, 29), "black")
    # 紫封印环
    quad(e, cx, cz, 11, 5.8, 11, 15, 8, 15, "seal")
    quad(e, cx, cz, 15, 5.8, 11, 33, 7.4, 15, "seal", mx=False)
    quad(e, cx, cz, 11, 5.8, 15, 15, 7.4, 33, "seal", mz=False)
    # 黑晶尖刺：每格一簇，阶梯收缩
    spikes = (
        (8, 8, 3.4, 17.6), (24, 8, 4.0, 20.4), (40, 8, 3.2, 17.2),
        (8, 24, 4.2, 19.6), (24, 24, 4.8, 23.2), (40, 24, 3.8, 18.4),
        (8, 40, 3.2, 17.4), (24, 40, 4.4, 20.8), (40, 40, 3.6, 18.0),
    )
    for sx, sz, half, top in spikes:
        taper(e, sx, sz, half, 3.6, top, "black", 4, "void")
    # 侧面抽取管
    add(e, (0, 7, 17), (4, 13, 31), "pipe")
    add(e, (44, 7, 17), (48, 13, 31), "pipe")
    add(e, (0.4, 13, 20), (3.6, 15.6, 28), "brass")
    add(e, (44.4, 13, 20), (47.6, 15.6, 28), "brass")
    # 抽取管接入坑口的短接头
    add(e, (4, 8.4, 21), (12, 11, 27), "pipe")
    add(e, (36, 8.4, 21), (44, 11, 27), "pipe")
    return e


def build_echo_key_altar() -> list:
    """声纹钥匙祭坛：阶梯台座 + 中央钥匙槽立柱 + 左右音叉 + 共鸣背板 + 侧翼能量柱。

    左右各一支细长 U 形音叉，背后是能量背板，正面有共鸣弧。
    """
    e = []
    cx = cz = 24.0
    # 台座三级
    add(e, (2, 0, 2), (46, 3, 46), "brown")
    add(e, (6, 3, 6), (42, 6, 42), "brass")
    add(e, (10, 6, 10), (38, 9, 38), "hull")
    quad(e, cx, cz, 2, 0, 2, 9, 4.4, 9, "brass")
    # 正面台阶
    stair(e, 14, 34, 0.6, 8, 0, 3, 1.6, "gold", "south")
    # 中央钥匙槽立柱
    add(e, (19, 9, 19), (29, 27, 29), "frame")
    add(e, (18.4, 26, 18.4), (29.6, 28, 29.6), "brass")
    add(e, (20, 28, 20), (28, 30.6, 28), "energy")
    # 钥匙槽（朝前，凸出立柱 1.6）
    add(e, (20, 13, 17.4), (28, 22, 19.4), "slot")
    add(e, (18.6, 12.4, 16.8), (20, 22.6, 19.8), "gold")
    add(e, (28, 12.4, 16.8), (29.4, 22.6, 19.8), "gold")
    add(e, (20, 22.6, 16.8), (28, 23.8, 19.8), "gold")
    add(e, (21.6, 10.6, 16.8), (26.4, 12.4, 19.8), "gold")
    # 左右音叉：细长 U 形
    quad(e, cx, cz, 4.6, 10, 17.6, 7.4, 29, 22.4, "brass", mz=False)
    quad(e, cx, cz, 11, 10, 17.6, 13.8, 29, 22.4, "brass", mz=False)
    quad(e, cx, cz, 4.6, 8, 17.6, 13.8, 10, 22.4, "brass", mz=False)
    quad(e, cx, cz, 4.6, 29, 17.6, 7.4, 31, 22.4, "energy", mz=False)
    quad(e, cx, cz, 11, 29, 17.6, 13.8, 31, 22.4, "energy", mz=False)
    quad(e, cx, cz, 4, 6, 16.6, 14.4, 8, 23.4, "gold", mz=False)
    # 共鸣背板
    add(e, (12, 10, 35), (36, 25, 41), "frame")
    add(e, (14, 12, 33.6), (34, 23, 35.6), "energy")
    add(e, (15.6, 11, 41), (20.4, 24, 42.6), "brass")
    add(e, (21.6, 11, 41), (26.4, 24, 42.6), "brass")
    add(e, (27.6, 11, 41), (32.4, 24, 42.6), "brass")
    # 侧翼能量柱
    quad(e, cx, cz, 0.8, 9, 17.6, 5, 25, 30.4, "energy")
    quad(e, cx, cz, 0.4, 25, 17.2, 5.4, 27, 30.8, "gold")
    # 前方两座共鸣灯
    quad(e, cx, cz, 4, 9, 3, 11, 21, 10, "brass")
    quad(e, cx, cz, 5.6, 12, 4.6, 9.4, 19, 8.4, "energy")
    # 前栏共鸣弧
    add(e, (10, 17, 3.4), (38, 19, 7.4), "brass")
    add(e, (12, 17.4, 3), (36, 18.6, 4.4), "energy")
    return e


# ---------------------------------------------------------------- 小机器 2x2x2
def build_echo_supply_crate() -> list:
    """补给箱：木箱主体 + 金属包角 + 提手 + 锁扣 + 顶部标签板 + 前面板开口。"""
    e = []
    cx = cz = 16.0
    # 托盘与四角垫脚
    add(e, (2, 0, 2), (30, 2.4, 30), "frame")
    quad(e, cx, cz, 1.2, 0, 1.2, 7.4, 4.6, 7.4, "brass")
    # 木箱主体
    add(e, (3.4, 2.4, 4), (28.6, 18.6, 28), "crate")
    # 下箍 / 上箍 / 盖框 / 箱盖
    add(e, (2.6, 6.6, 3.4), (29.4, 8.8, 28.6), "brass")
    add(e, (2.4, 18.6, 3.2), (29.6, 21, 28.8), "brass")
    add(e, (2.8, 21, 3.6), (29.2, 22.4, 28.4), "frame")
    add(e, (3.6, 22.4, 4.4), (28.4, 23.4, 27.6), "crate")
    # 四角包角：从 y=2.4 一直包到 y=21，跨过 y=16 撑起上层四个象限
    quad(e, cx, cz, 2.6, 2.4, 3.2, 7, 21, 7.6, "frame")
    quad(e, cx, cz, 2.2, 21, 2.8, 7.4, 23.2, 7.4, "brass")
    # 正面竖向木条
    for x0 in (8.6, 14.6, 20.6):
        add(e, (x0, 2.4, 3.4), (x0 + 2.6, 18.6, 4.6), "brown")
    # 侧面横向木条
    quad(e, cx, cz, 3.4, 8.8, 3.5, 28.6, 10.6, 4.5, "brown", mx=False)
    # 前面板开口（三层内凹）
    add(e, (7, 12.6, 2.4), (25, 18, 4.2), "frame")
    add(e, (8.6, 13.8, 1.8), (23.4, 16.8, 2.8), "vent")
    add(e, (10.6, 14.6, 1.2), (21.4, 15.8, 2), "hull")
    # 锁扣与指示灯
    add(e, (12.6, 15.4, 1.6), (19.4, 20.4, 4.4), "gold")
    add(e, (14.4, 16.6, 1.1), (17.6, 19.4, 1.8), "energy")
    # 提手
    add(e, (14.4, 23.4, 13.4), (25.6, 24.8, 16.6), "brass")
    quad(e, cx, cz, 14.4, 21.8, 13.4, 17, 23.4, 16.6, "brass", mx=False)
    # 顶部标签板
    add(e, (4.6, 23.4, 6), (13.4, 24.6, 26), "gold")
    add(e, (6, 24.6, 7.6), (12, 25.4, 24.4), "ticket")
    # 侧面标签与提环
    quad(e, cx, cz, 1.4, 9, 9, 3.6, 14, 23, "ticket", mz=False)
    quad(e, cx, cz, 1.6, 15.4, 11, 3.8, 18, 21, "frame", mz=False)
    # 顶部四角铆钉
    quad(e, cx, cz, 4.6, 23.4, 4.4, 7.6, 24.6, 7.4, "gold")
    # 背部通风栅
    add(e, (6, 6, 27.4), (26, 17, 29.2), "vent")
    # 底部导轨
    quad(e, cx, cz, 2.4, 0, 8, 4.6, 2.4, 24, "frame", mz=False)
    quad(e, cx, cz, 8, 0, 2.4, 24, 1.6, 4.6, "brass", mx=False)
    return e


def build_echo_black_market() -> list:
    """黑市：保险箱体 + 圆形转盘 + 紫色封印 + 投币口 + 顶部招牌灯。"""
    e = []
    cx = cz = 16.0
    # 底座与铆块
    add(e, (2, 0, 2), (30, 2.8, 30), "frame")
    quad(e, cx, cz, 2, 0, 2, 7, 4.4, 7, "brass")
    # 箱体
    add(e, (3.6, 2.8, 4), (28.4, 21, 28), "hull")
    # 门板与外框
    add(e, (4.6, 4.4, 2.6), (27.4, 19.4, 4.8), "brass")
    add(e, (6, 6, 2.2), (26, 18, 3.4), "frame")
    # 圆形转盘（三层方盒近似圆）
    disc(e, 16, 12, 2.0, 4.4, 1.6, "frame")
    add(e, (13, 9, 0.9), (19, 15, 1.5), "gold")
    add(e, (15.2, 10.4, 0.5), (16.8, 13.6, 1.1), "frame")
    add(e, (13.6, 11.4, 0.5), (18.4, 12.6, 1.1), "frame")
    add(e, (15.4, 11.4, 0.2), (16.6, 12.6, 0.6), "energy")
    # 紫色封印环（围住转盘）
    quad(e, cx, cz, 8.6, 7.4, 1.8, 10.6, 16.6, 3, "seal", mz=False)
    quad(e, cx, cz, 10.6, 16.6, 1.8, 21.4, 18, 3, "seal", mx=False)
    quad(e, cx, cz, 10.6, 6.2, 1.8, 21.4, 7.4, 3, "seal", mx=False)
    # 投币口
    add(e, (13, 20.2, 2.2), (19, 21.6, 3.8), "slot")
    add(e, (14.2, 21.6, 2), (17.8, 22.6, 3.4), "gold")
    # 顶部招牌灯
    add(e, (5, 22.6, 6), (27, 25.4, 20), "hull")
    add(e, (7, 25.4, 8), (25, 26.8, 18), "energy")
    add(e, (6, 21, 5), (26, 22.6, 21), "gold")
    # 招牌立柱
    quad(e, cx, cz, 5.4, 25.4, 6.4, 8, 28, 9, "brass")
    # 侧面通风与提环
    quad(e, cx, cz, 2.6, 8, 9, 4.6, 17, 23, "vent", mz=False)
    quad(e, cx, cz, 2.2, 11, 11.4, 4, 13.4, 20.6, "brass", mz=False)
    # 门铰链
    quad(e, cx, cz, 6, 16.4, 2.2, 8.4, 18.6, 4, "gold", mz=False)
    # 数字键盘
    add(e, (20.6, 8, 1.9), (25.4, 15, 3.1), "frame")
    quad(e, cx, cz, 21.4, 9, 1.5, 23, 10.6, 2.1, "energy", mx=False)
    quad(e, cx, cz, 21.4, 11.4, 1.5, 23, 13, 2.1, "energy", mx=False)
    # 底部滑撬
    quad(e, cx, cz, 2.4, 0, 9, 4.4, 2, 23, "brass", mz=False)
    quad(e, cx, cz, 2.4, 0, 2.4, 29.6, 1.8, 4.4, "frame", mx=False)
    # 招牌吊环
    quad(e, cx, cz, 5.4, 26.8, 6.4, 8, 27.8, 9, "gold")
    return e


def build_echo_black_dropoff() -> list:
    """黑晶投放机：斜置漏斗 + 斜管 + 接收口 + 指示灯 + 支架。"""
    e = []
    cx = cz = 16.0
    # 底座
    add(e, (2, 0, 2), (30, 2.6, 30), "frame")
    quad(e, cx, cz, 2, 0, 2, 7, 4.4, 7, "brass")
    # 支架：四角立柱 + 顶帽
    quad(e, cx, cz, 3.4, 2.6, 3.4, 7, 20, 7, "frame")
    quad(e, cx, cz, 2.8, 20, 2.8, 7.6, 21.6, 7.6, "brass")
    # 斜置漏斗：四级收缩
    add(e, (4, 21.6, 4), (28, 23.4, 28), "hull")
    add(e, (5, 18, 5), (27, 21.6, 27), "brown")
    add(e, (8, 14, 8), (24, 18, 24), "brown")
    add(e, (11, 10.4, 11), (21, 14, 21), "brass")
    # 漏斗外箍
    quad(e, cx, cz, 4.4, 19.4, 4.4, 27.6, 20.4, 27.6, "brass", mx=False)
    quad(e, cx, cz, 7.4, 15, 7.4, 24.6, 16, 24.6, "brass", mx=False)
    # 接收口
    add(e, (12, 8.2, 12), (20, 10.4, 20), "mouth")
    add(e, (13.4, 7.4, 13.4), (18.6, 8.4, 18.6), "void")
    # 斜管：向右上方甩出的抽取管
    add(e, (24, 17, 15), (28, 19.4, 21), "pipe")
    add(e, (25.4, 19.4, 15), (29.4, 21.8, 21), "pipe")
    add(e, (26.8, 21.8, 15), (30.8, 24.2, 21), "pipe")
    add(e, (26.4, 24.2, 14.4), (31.6, 26, 21.6), "vent")
    # 顶部指示灯
    add(e, (12.6, 23.4, 12.6), (19.4, 25, 19.4), "frame")
    add(e, (13.6, 25, 13.6), (18.4, 26.8, 18.4), "energy")
    add(e, (13, 26.8, 13), (19, 27.8, 19), "gold")
    # 侧面护板与支撑
    quad(e, cx, cz, 2.4, 4.4, 8, 4.4, 16, 24, "hull", mz=False)
    quad(e, cx, cz, 7, 4.4, 3, 9, 6.6, 9, "brass", mx=False)
    quad(e, cx, cz, 2.4, 4.4, 2.4, 5.6, 12, 5.6, "pipe")
    # 前面斜槽
    add(e, (10, 4.4, 2.2), (22, 7.4, 6), "brass")
    add(e, (11.4, 5.2, 1.6), (20.6, 6.6, 2.6), "energy")
    # 背部散热
    add(e, (7, 8, 27.4), (25, 17, 29.2), "vent")
    # 指示灯柱
    quad(e, cx, cz, 9.4, 17, 20.6, 11.8, 21.4, 23, "energy")
    return e


def build_echo_core_console() -> list:
    """核心门控制台：斜面操作台 + 屏幕 + 钥匙孔槽 + 立柱指示灯 + 侧面散热片。"""
    e = []
    cx = cz = 16.0
    # 底座与机身
    add(e, (2, 0, 2), (30, 2.8, 30), "frame")
    quad(e, cx, cz, 2, 0, 2, 7, 4.6, 7, "brass")
    add(e, (3.4, 2.8, 6), (28.6, 16.4, 28), "hull")
    add(e, (2.6, 16.4, 5), (29.4, 18, 29), "brass")
    # 斜面操作台（四级台阶朝前抬高）
    stair(e, 5, 27, 3.4, 15, 18, 4, 1.7, "frame", "north")
    # 屏幕与外框
    add(e, (7, 20.2, 6.4), (25, 23.4, 12), "screen")
    quad(e, cx, cz, 5.8, 19.4, 5.6, 7, 24, 12.8, "brass", mz=False)
    add(e, (7, 23.4, 5.6), (25, 24.6, 12.8), "brass")
    add(e, (7.6, 19.6, 5.2), (24.4, 20.6, 6.2), "gold")
    # 钥匙孔槽
    add(e, (12, 12.6, 4.4), (20, 16, 6.6), "slot")
    add(e, (13.4, 13.6, 3.8), (18.6, 15.4, 4.6), "gold")
    # 立柱指示灯
    add(e, (12.6, 24.6, 17), (19.4, 28.6, 24), "frame")
    add(e, (13.6, 28.6, 18), (18.4, 30.6, 23), "energy")
    add(e, (13, 30.6, 17.4), (19, 31.6, 23.6), "gold")
    quad(e, cx, cz, 12.6, 22.6, 17, 19.4, 24.6, 24, "brass", mx=False)
    # 侧面散热片
    quad(e, cx, cz, 1.6, 6, 9, 3.6, 15, 12, "vent", mz=False)
    quad(e, cx, cz, 1.6, 6, 15, 3.6, 15, 18, "vent", mz=False)
    quad(e, cx, cz, 1.6, 6, 21, 3.6, 16, 24, "vent", mz=False)
    # 操作按钮
    add(e, (8, 18, 13.6), (11, 19.6, 15.6), "energy")
    add(e, (13, 18, 13.6), (16, 19.6, 15.6), "gold")
    add(e, (18, 18, 13.6), (21, 19.6, 15.6), "energy")
    quad(e, cx, cz, 8.6, 18.6, 15.6, 10.4, 19.2, 16.6, "gold")
    # 桌面铭牌
    add(e, (6, 18, 24), (26, 19.4, 27.6), "ticket")
    # 背部走线与侧面管
    add(e, (8, 4, 28.2), (24, 14, 29.6), "pipe")
    quad(e, cx, cz, 2.4, 4, 20, 4.4, 12, 26, "pipe", mz=False)
    # 底部踢脚
    quad(e, cx, cz, 3, 0, 9, 5, 2, 23, "frame", mz=False)
    return e


def build_echo_free_exit() -> list:
    """免费出口：门框 + 绿色箭头指示 + 光柱 + 台阶 + 指示牌。"""
    e = []
    cx = cz = 16.0
    # 台阶与地板
    stair(e, 4, 28, 0.4, 6, 0, 3, 1.4, "green", "south")
    add(e, (2, 0, 2), (30, 2.6, 30), "frame")
    # 门框立柱与门楣
    quad(e, cx, cz, 2.6, 2.6, 8, 6.6, 26, 11, "frame", mz=False)
    add(e, (2.6, 26, 8), (29.4, 29, 11), "frame")
    add(e, (4, 2.6, 8.6), (28, 4.2, 10.4), "green")
    # 门板
    add(e, (6.6, 4.2, 9.2), (25.4, 25, 10.2), "energy")
    # 绿色箭头指示：横杆 + 箭头
    add(e, (10.4, 19.4, 7.4), (20, 21.4, 8.6), "exit")
    add(e, (19.4, 17.6, 7.2), (23.4, 23.2, 8.4), "exit")
    add(e, (21.6, 18.6, 7), (25.4, 22.2, 8.2), "exit")
    # 光柱：细长绿色发光，穿过门中心并跨两格
    add(e, (13.6, 4.2, 13.6), (18.4, 27, 18.4), "green")
    add(e, (14.6, 4.2, 14.6), (17.4, 30.4, 17.4), "energy")
    # 光柱底座
    add(e, (11.6, 3.4, 11.6), (20.4, 4.8, 20.4), "gold")
    add(e, (12.6, 4.8, 12.6), (19.4, 5.8, 19.4), "green")
    # 指示牌
    add(e, (7, 19.4, 11.6), (25, 22.6, 13.4), "hull")
    add(e, (8.6, 20.2, 11), (23.4, 21.8, 12), "exit")
    # 门楣射灯
    quad(e, cx, cz, 8, 24.4, 9.4, 11, 26, 10.6, "energy")
    # 侧面翼板
    quad(e, cx, cz, 0.6, 4.2, 9, 2.6, 24, 11, "green", mz=False)
    quad(e, cx, cz, 0.4, 24, 8.6, 2.8, 26, 11.4, "gold", mz=False)
    # 顶部盖板
    add(e, (1.6, 29, 7), (30.4, 30.8, 12), "brass")
    add(e, (3.4, 30.8, 8.4), (28.6, 31.8, 10.6), "gold")
    # 门框立柱装饰
    quad(e, cx, cz, 3.4, 6, 7.4, 5.8, 9, 8.4, "gold", mz=False)
    quad(e, cx, cz, 3.4, 20, 7.4, 5.8, 23, 8.4, "gold", mz=False)
    # 地面导轨
    quad(e, cx, cz, 4, 0, 10.6, 28, 1.4, 12, "brass", mx=False)
    # 后部通道墙：给 z >= 16 的一半补上真实体量
    add(e, (3.4, 2.6, 19), (28.6, 25, 24), "hull")
    add(e, (5.4, 5, 17.6), (26.6, 22, 19.2), "frame")
    quad(e, cx, cz, 6.6, 8, 19, 13.4, 20, 19.8, "green", mx=False)
    add(e, (4, 25, 18), (28, 27.4, 25), "brass")
    add(e, (10.6, 3.4, 17), (21.4, 4.6, 18), "gold")
    return e


def build_echo_evacuation_console() -> list:
    """撤离控制台：橙金警示终端 + 拉杆 + 旋转警示灯 + 喇叭口 + 底座。"""
    e = []
    cx = cz = 16.0
    # 底座与机体
    add(e, (2, 0, 2), (30, 3, 30), "hazard")
    quad(e, cx, cz, 2, 0, 2, 7, 4.6, 7, "brass")
    add(e, (3.6, 3, 5), (28.4, 19, 26), "orange")
    add(e, (2.8, 19, 4), (29.2, 22.4, 27), "hazard")
    add(e, (3.6, 22.4, 5), (28.4, 24, 26), "frame")
    # 斜面控制面板
    stair(e, 6, 26, 5, 14, 24, 3, 1.5, "brass", "north")
    # 面板按钮
    quad(e, cx, cz, 8, 24.6, 15, 9.6, 26, 17, "energy", mx=False)
    add(e, (13, 24.6, 15), (16, 26, 17), "gold")
    quad(e, cx, cz, 18, 24.6, 15, 21, 26, 17, "energy", mx=False)
    quad(e, cx, cz, 9.6, 24, 13.6, 22.4, 24.6, 14.6, "gold", mx=False)
    # 拉杆
    add(e, (20.4, 22.4, 6), (26, 24.4, 11.6), "frame")
    add(e, (22, 24.4, 7.4), (24.4, 28.6, 10), "pipe")
    add(e, (21, 28.6, 6.4), (25.4, 30.4, 11), "gold")
    quad(e, cx, cz, 20.4, 21.8, 6.4, 26, 22.4, 11.6, "brass", mx=False)
    # 旋转警示灯
    add(e, (8.6, 22.4, 18), (15.4, 24, 24.4), "frame")
    add(e, (9.4, 24, 18.8), (14.6, 28, 23.6), "orange")
    add(e, (10.2, 28, 19.6), (13.8, 29.6, 22.8), "energy")
    add(e, (9.8, 29.6, 19.2), (14.2, 30.8, 23.2), "gold")
    quad(e, cx, cz, 8.2, 24, 17.6, 15.8, 24.8, 24.8, "gold", mx=False)
    # 喇叭口
    add(e, (0.4, 10, 10), (4, 18, 22), "orange")
    add(e, (0.4, 12, 12), (1.6, 16, 20), "hazard")
    add(e, (3.4, 8.6, 9.4), (6, 10, 22.6), "brass")
    add(e, (3.4, 18, 9.4), (6, 19.4, 22.6), "brass")
    # 侧面板与散热
    quad(e, cx, cz, 2.6, 5, 8, 4.6, 17, 12, "vent", mz=False)
    quad(e, cx, cz, 2.6, 5, 16, 4.6, 17, 20, "vent", mz=False)
    # 警示条纹
    quad(e, cx, cz, 4, 12, 4.2, 12, 14.6, 5.4, "gold", mx=False)
    quad(e, cx, cz, 4, 6, 4.2, 12, 8, 5.4, "gold", mx=False)
    # 后部排风
    add(e, (7, 6, 25.4), (25, 18, 27.4), "vent")
    # 顶部小旗杆
    add(e, (25.4, 24, 20.4), (27.4, 31, 22.4), "brass")
    add(e, (25.8, 27, 18), (27.2, 30.6, 20.4), "hazard")
    # 底座警示条
    quad(e, cx, cz, 4, 0, 4.2, 28, 2.4, 5.4, "gold", mx=False)
    return e


def build_echo_broker_exit() -> list:
    """投机客退场终端：黄铜票据终端 + 票据出口 + 柜台面 + 铃铛 + 招牌。"""
    e = []
    cx = cz = 16.0
    # 底座与机体
    add(e, (2, 0, 2), (30, 3, 30), "frame")
    quad(e, cx, cz, 2, 0, 2, 7, 4.6, 7, "brass")
    add(e, (3.6, 3, 6), (28.4, 19, 28), "brass")
    # 柜台面
    add(e, (1.6, 19, 4), (30.4, 21.6, 29.6), "gold")
    add(e, (2.6, 21.6, 5), (29.4, 22.6, 28.6), "ticket")
    # 票据出口
    add(e, (11, 12, 4.2), (21, 16.4, 6.6), "slot")
    add(e, (12.4, 13.2, 3.6), (19.6, 15.4, 4.4), "gold")
    add(e, (13, 8, 5), (19, 12, 6.2), "ticket")
    quad(e, cx, cz, 10, 11, 5.4, 22, 12, 6.2, "frame", mx=False)
    # 铃铛
    add(e, (20.6, 22.6, 12), (25.4, 24, 17), "frame")
    add(e, (21.4, 24, 12.8), (24.6, 26.4, 16.2), "gold")
    add(e, (22.2, 26.4, 13.6), (23.8, 27.4, 15.4), "brass")
    add(e, (21, 21.8, 11.6), (25, 22.6, 17.4), "brass")
    # 招牌
    add(e, (4, 22.6, 20), (28, 26, 27), "frame")
    add(e, (5.6, 23.4, 19.4), (26.4, 25.2, 20.4), "ticket")
    add(e, (5.6, 26, 20.4), (26.4, 27.4, 26.6), "brass")
    # 招牌立柱
    quad(e, cx, cz, 4.4, 26, 20.4, 7.4, 29.6, 23.4, "gold")
    # 抽屉与拉手
    for x0 in (5, 13, 21):
        add(e, (x0, 6, 5), (x0 + 6.4, 11, 6.4), "brown")
    quad(e, cx, cz, 7, 8.4, 4.6, 9.6, 9.6, 5.4, "gold", mx=False)
    # 侧面票据架
    quad(e, cx, cz, 2, 6, 10, 4, 15, 24, "ticket", mz=False)
    # 顶部黄铜环
    quad(e, cx, cz, 7, 21.6, 7, 25, 22.6, 9.6, "brass", mx=False)
    quad(e, cx, cz, 7, 21.6, 23, 25, 22.6, 25.6, "brass", mx=False)
    # 背部排气
    add(e, (8, 6, 27.4), (24, 17, 29.4), "vent")
    # 地面滑轨
    quad(e, cx, cz, 3, 0, 10, 5, 2.4, 22, "brass", mz=False)
    # 价目牌
    quad(e, cx, cz, 4.6, 12, 6, 9, 18, 7.2, "ticket", mx=False)
    # 顶部小灯
    quad(e, cx, cz, 4.6, 27.4, 20.4, 7.2, 29, 23.2, "energy")
    return e


BUILDERS = {
    "echo_beacon": lambda: (build_echo_beacon(), "energy", "large"),
    "echo_core_beacon": lambda: (build_echo_core_beacon(), "crystal", "large"),
    "echo_crystal_extractor": lambda: (build_echo_crystal_extractor(), "crystal", "large"),
    "echo_black_crystal_extractor": lambda: (build_echo_black_crystal_extractor(), "black", "large"),
    "echo_key_altar": lambda: (build_echo_key_altar(), "slot", "large"),
    "echo_supply_crate": lambda: (build_echo_supply_crate(), "crate", "small"),
    "echo_black_market": lambda: (build_echo_black_market(), "seal", "small"),
    "echo_black_dropoff": lambda: (build_echo_black_dropoff(), "mouth", "small"),
    "echo_core_console": lambda: (build_echo_core_console(), "screen", "small"),
    "echo_free_exit": lambda: (build_echo_free_exit(), "exit", "small"),
    "echo_evacuation_console": lambda: (build_echo_evacuation_console(), "hazard", "small"),
    "echo_broker_exit": lambda: (build_echo_broker_exit(), "ticket", "small"),
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
