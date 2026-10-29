"""一键生成缺失的药水效果(mob_effect)贴图。

从 ModEffects.java / SEEffects.java 解析所有 register("id", new XxxEffect(...))，
对每个没有 18x18 贴图的效果，按其颜色与类别生成像素风占位图标。

用法:
    python scripts/gen_effect_icons.py            # 仅生成缺失的
    python scripts/gen_effect_icons.py --force    # 覆盖全部重新生成
    python scripts/gen_effect_icons.py --dry-run  # 只列出缺失，不写文件
"""
from __future__ import annotations

import argparse
import hashlib
import math
import os
import re

from PIL import Image

ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), ".."))
RESOURCES = os.path.join(ROOT, "src", "main", "resources", "assets")

SOURCES = [
    ("src/main/java/org/agmas/noellesroles/init/ModEffects.java", "noellesroles"),
    ("src/main/java/pro/fazeclan/river/stupid_express/constants/SEEffects.java", "stupid_express"),
]

SIZE = 18
CENTER = (SIZE - 1) / 2.0

CATEGORY_BG = {
    "BENEFICIAL": (18, 38, 24, 236),
    "HARMFUL": (44, 16, 16, 236),
    "NEUTRAL": (26, 26, 30, 236),
}
CATEGORY_BORDER = {
    "BENEFICIAL": (60, 110, 70),
    "HARMFUL": (120, 50, 50),
    "NEUTRAL": (90, 90, 100),
}
CATEGORY_DEFAULT_COLOR = {
    "BENEFICIAL": 0x44FF66,
    "HARMFUL": 0xFF4444,
    "NEUTRAL": 0xC8C8D0,
}


def clamp(v):
    return max(0, min(255, v))


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def parse_color(text, default):
    m = re.search(r"0x[0-9A-Fa-f]+", text)
    if m:
        return int(m.group(0), 16)
    for m in re.finditer(r"(?<![\w.])\d{5,8}(?![\w.])", text):
        v = int(m.group(0))
        if 0 < v <= 0xFFFFFF:
            return v
    return default


def parse_effects():
    effects = []
    pattern = re.compile(r'register\(\s*"(\w+)"\s*,\s*new\s+(\w+)\s*\(([^)]*)\)', re.MULTILINE)
    for rel, namespace in SOURCES:
        path = os.path.join(ROOT, rel)
        if not os.path.isfile(path):
            continue
        src = open(path, encoding="utf-8").read()
        for m in pattern.finditer(src):
            eid, cls, args = m.group(1), m.group(2), m.group(3)
            cat = re.search(r"MobEffectCategory\.(\w+)", args)
            category = cat.group(1) if cat else "NEUTRAL"
            color = parse_color(args, CATEGORY_DEFAULT_COLOR.get(category, 0xFFFFFF))
            effects.append((namespace, eid, category, color))
    return effects


# ---------- 像素图案 (motif) ----------

def _diamond(x, y, r):
    return abs(x - CENTER) + abs(y - CENTER) <= r

def _circle(x, y, r):
    return (x - CENTER) ** 2 + (y - CENTER) ** 2 <= r * r

def m_diamond():
    return [[_diamond(x, y, 8) for x in range(SIZE)] for y in range(SIZE)]

def m_circle():
    return [[_circle(x, y, 8) for x in range(SIZE)] for y in range(SIZE)]

def m_ring():
    return [[(_circle(x, y, 8) and not _circle(x, y, 5)) for x in range(SIZE)] for y in range(SIZE)]

def m_plus():
    mask = [[False] * SIZE for _ in range(SIZE)]
    for i in range(SIZE):
        for j in range(4, 14):
            mask[i][j] = True
            mask[j][i] = True
    return mask

def m_up_arrow():
    mask = [[False] * SIZE for _ in range(SIZE)]
    for y in range(2, 9):
        for x in range(int(CENTER) - (y - 1), int(CENTER) + y):
            mask[y][x] = True
    for y in range(9, 16):
        for x in range(7, 11):
            mask[y][x] = True
    return mask

def m_down_arrow():
    mask = [[False] * SIZE for _ in range(SIZE)]
    for y in range(9, 16):
        half = 15 - y
        for x in range(int(CENTER) - half, int(CENTER) + half + 1):
            mask[y][x] = True
    for y in range(2, 9):
        for x in range(7, 11):
            mask[y][x] = True
    return mask

def m_triangle_up():
    return [[abs(x - CENTER) <= (y - 1) and 1 <= y <= 15 for x in range(SIZE)] for y in range(SIZE)]

def m_square_frame():
    mask = [[False] * SIZE for _ in range(SIZE)]
    for i in range(SIZE):
        for j in range(SIZE):
            if 3 <= i <= 14 and 3 <= j <= 14 and not (5 <= i <= 12 and 5 <= j <= 12):
                mask[i][j] = True
    return mask

def m_heart():
    mask = [[False] * SIZE for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            dx = x - CENTER
            dy = y - CENTER - 1
            v = (dx * dx + dy * dy - 30) ** 3 - 30 * dx * dx * dy * dy * dy
            mask[y][x] = v <= 0 and dy <= 4
    return mask

def m_star():
    mask = [[False] * SIZE for _ in range(SIZE)]
    pts = []
    for k in range(10):
        ang = -math.pi / 2 + k * math.pi / 5
        r = 8 if k % 2 == 0 else 3.2
        pts.append((CENTER + r * math.cos(ang), CENTER + r * math.sin(ang)))

    def inside(px, py):
        flag = False
        n = len(pts)
        j = n - 1
        for i in range(n):
            xi, yi = pts[i]
            xj, yj = pts[j]
            if (yi > py) != (yj > py) and px < (xj - xi) * (py - yi) / (yj - yi + 1e-9) + xi:
                flag = not flag
            j = i
        return flag

    for y in range(SIZE):
        for x in range(SIZE):
            mask[y][x] = inside(x + 0.5, y + 0.5)
    return mask

def m_eye():
    mask = [[False] * SIZE for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            outline = _circle(x, y, 7.5) and not _circle(x, y, 6.0)
            mask[y][x] = outline or _circle(x, y, 2.2)
    return mask

def m_skull():
    mask = [[False] * SIZE for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            if _circle(x, y, 7.5) and y <= 11:
                mask[y][x] = True
            if 11 <= y <= 14 and 4 <= x <= 13:
                mask[y][x] = True
    for (ex, ey) in [(5, 7), (12, 7)]:
        for y in range(SIZE):
            for x in range(SIZE):
                if (x - ex) ** 2 + (y - ey) ** 2 <= 3:
                    mask[y][x] = False
    for y in range(9, 11):
        for x in range(8, 10):
            mask[y][x] = False
    for x in (6, 9, 12):
        for y in range(11, 15):
            mask[y][x] = False
    return mask

def _outline_only(mask):
    out = [[False] * SIZE for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            if mask[y][x]:
                nb = any(
                    0 <= x + dx < SIZE and 0 <= y + dy < SIZE and not mask[y + dy][x + dx]
                    for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1))
                )
                out[y][x] = nb
    return out

def m_flask():
    mask = [[False] * SIZE for _ in range(SIZE)]
    for y in range(2, 5):
        for x in range(7, 11):
            mask[y][x] = True
    for y in range(5, 15):
        half = 2 + (y - 5) * 0.6
        for x in range(int(CENTER - half), int(CENTER + half + 1)):
            mask[y][x] = True
    for y in range(15, 17):
        for x in range(4, 14):
            mask[y][x] = True
    return _outline_only(mask)

def m_crosshair():
    mask = [[False] * SIZE for _ in range(SIZE)]
    for i in range(SIZE):
        mask[i][9] = True
        mask[9][i] = True
    for y in range(SIZE):
        for x in range(SIZE):
            d2 = (x - 9) ** 2 + (y - 9) ** 2
            if 4 <= d2 <= 9:
                mask[y][x] = True
    mask[9][9] = True
    return mask

def m_chevron():
    mask = [[False] * SIZE for _ in range(SIZE)]
    for y in range(SIZE):
        w = abs(y - 9)
        if w <= 7:
            for x in range(SIZE):
                if abs(x - CENTER) >= w - 1 and abs(x - CENTER) <= w:
                    mask[y][x] = True
    return mask

def m_hexagon():
    mask = [[False] * SIZE for _ in range(SIZE)]
    for y in range(SIZE):
        for x in range(SIZE):
            dx = abs(x - CENTER)
            dy = abs(y - CENTER)
            if dy <= 6 and dx <= 7 - max(0, dy - 3):
                mask[y][x] = True
    return _outline_only(mask)

MOTIFS = [
    m_diamond, m_circle, m_ring, m_plus, m_up_arrow, m_down_arrow,
    m_triangle_up, m_square_frame, m_heart, m_star, m_eye, m_skull,
    m_flask, m_crosshair, m_chevron, m_hexagon,
]


def pick_motif(name):
    h = int(hashlib.md5(name.encode()).hexdigest(), 16)
    return MOTIFS[h % len(MOTIFS)]()


def render_icon(category, color, name):
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = img.load()
    bg = CATEGORY_BG.get(category, CATEGORY_BG["NEUTRAL"])
    border = CATEGORY_BORDER.get(category, CATEGORY_BORDER["NEUTRAL"])

    def in_round(x, y):
        return 1 <= x <= 16 and 1 <= y <= 16 and not (x in (1, 16) and y in (1, 16))

    for y in range(SIZE):
        for x in range(SIZE):
            if in_round(x, y):
                px[x, y] = bg

    # 边框
    for y in range(SIZE):
        for x in range(SIZE):
            if in_round(x, y):
                on_edge = any(not in_round(x + dx, y + dy) for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)))
                if on_edge:
                    px[x, y] = (*border, 255)

    base = ((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF)
    light = mix(base, (255, 255, 255), 0.45)
    dark = mix(base, (0, 0, 0), 0.45)
    motif = pick_motif(name)

    for y in range(SIZE):
        for x in range(SIZE):
            if motif[y][x]:
                # 顶左高光 / 底右阴影
                grad = (x + y) / (2 * (SIZE - 1))
                c = mix(light, dark, grad)
                # 边缘再加深一点
                nb = any(
                    0 <= x + dx < SIZE and 0 <= y + dy < SIZE and not motif[y + dy][x + dx]
                    for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1))
                )
                if nb:
                    c = mix(c, (0, 0, 0), 0.35)
                px[x, y] = (*c, 255)
    return img


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true", help="覆盖已有贴图重新生成")
    ap.add_argument("--dry-run", action="store_true", help="只列出缺失，不写文件")
    args = ap.parse_args()

    effects = parse_effects()
    missing, existing, generated = [], 0, 0
    for namespace, eid, category, color in effects:
        out_dir = os.path.join(RESOURCES, namespace, "textures", "mob_effect")
        out_path = os.path.join(out_dir, f"{eid}.png")
        exists = os.path.isfile(out_path)
        if exists and not args.force:
            existing += 1
            continue
        missing.append((namespace, eid, category, color))
        if args.dry_run:
            continue
        os.makedirs(out_dir, exist_ok=True)
        render_icon(category, color, eid).save(out_path)
        generated += 1

    print(f"解析到效果总数: {len(effects)}")
    print(f"已有贴图: {existing}")
    print(f"缺失贴图: {len(missing)}")
    if args.dry_run:
        print("--dry-run 模式，未写入文件。缺失列表:")
    else:
        print(f"已生成: {generated}")
    for namespace, eid, category, color in missing:
        print(f"  [{namespace}] {eid}  ({category}, #{color:06X})")


if __name__ == "__main__":
    main()
