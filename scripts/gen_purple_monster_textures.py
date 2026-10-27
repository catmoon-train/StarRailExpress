"""Generate noisy purple skins and iris eyes for both purple-monster forms."""
from __future__ import annotations

import os
import random
from PIL import Image

OUT = os.path.join(
    os.path.dirname(__file__),
    "..",
    "src",
    "main",
    "resources",
    "assets",
    "starrailexpress",
    "textures",
    "entity",
)
OUT = os.path.normpath(OUT)

BASE = (28, 16, 36)
HIGH = (52, 32, 64)
LOW = (12, 6, 18)
SCLERA = (248, 243, 238)
IRIS_OUT = (92, 68, 124)
IRIS_MID = (168, 136, 196)
IRIS_IN = (58, 40, 82)
PUPIL = (14, 8, 18)
HIGHLIGHT = (255, 255, 255)
LID = (24, 14, 30)
GLOW_WHITE = (255, 248, 242)
GLOW_IRIS = (210, 180, 240)


def clamp(v: int) -> int:
    return max(0, min(255, v))


def mix(a, b, t: float):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def noisy_purple(rng: random.Random) -> tuple[int, int, int, int]:
    n = rng.gauss(0, 14)
    if rng.random() < 0.18:
        n += rng.choice((-22, -16, 18, 24))
    if rng.random() < 0.08:
        n += rng.choice((-30, 28))
    t = (n + 24) / 48
    c = mix(LOW, HIGH, max(0.0, min(1.0, t)))
    c = (clamp(c[0] + int(n * 0.35)), clamp(c[1] + int(n * 0.2)), clamp(c[2] + int(n * 0.45)))
    return (*c, 255)


def fill_rect(img: Image.Image, x: int, y: int, w: int, h: int, rng: random.Random):
    px = img.load()
    W, H = img.size
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            if 0 <= xx < W and 0 <= yy < H:
                px[xx, yy] = noisy_purple(rng)


def shade_cube(img: Image.Image, u: int, v: int, w: int, h: int, d: int, rng: random.Random):
    fill_rect(img, u + d, v, w, d, rng)
    fill_rect(img, u + d + w, v, w, d, rng)
    fill_rect(img, u, v + d, d, h, rng)
    fill_rect(img, u + d, v + d, w, h, rng)
    fill_rect(img, u + d + w, v + d, d, h, rng)
    fill_rect(img, u + d + w + d, v + d, w, h, rng)


def put(img: Image.Image, x: int, y: int, color):
    px = img.load()
    W, H = img.size
    if 0 <= x < W and 0 <= y < H:
        px[x, y] = (*color, 255) if len(color) == 3 else color


def draw_pixels(img: Image.Image, x: int, y: int, rows):
    for yy, row in enumerate(rows):
        for xx, color in enumerate(row):
            if color is not None:
                put(img, x + xx, y + yy, color)


def draw_iris_8x8(img: Image.Image, x: int, y: int):
    # 参考图：居中杏仁眼，白巩膜 + 浅紫虹膜 + 深瞳孔
    s = None
    w, o, i, p, h = SCLERA, IRIS_OUT, IRIS_MID, PUPIL, HIGHLIGHT
    draw_pixels(img, x, y, [
        [s, s, s, s, s, s, s, s],
        [s, s, w, w, w, w, s, s],
        [s, w, o, i, i, o, w, s],
        [w, o, i, h, p, i, o, w],
        [w, o, i, p, p, i, o, w],
        [s, w, o, i, i, o, w, s],
        [s, s, w, w, w, w, s, s],
        [s, s, s, s, s, s, s, s],
    ])


def draw_iris_6x6(img: Image.Image, x: int, y: int):
    s = None
    w, o, i, p, h = SCLERA, IRIS_OUT, IRIS_MID, PUPIL, HIGHLIGHT
    draw_pixels(img, x, y, [
        [s, w, w, w, w, s],
        [w, o, i, i, o, w],
        [w, i, h, p, i, w],
        [w, o, p, p, o, w],
        [s, w, o, o, w, s],
        [s, s, w, w, s, s],
    ])


def glow_from_eye(src: Image.Image, dest: Image.Image, x: int, y: int, w: int, h: int):
    sp = src.load()
    dp = dest.load()
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            r, g, b, a = sp[xx, yy]
            if a < 200:
                continue
            if r > 200 and g > 190:
                dp[xx, yy] = (*GLOW_WHITE, 255)
            elif r > 80 and b > r and g < 210:
                dp[xx, yy] = (*GLOW_IRIS, 90)


def write_form1():
    rng = random.Random(20260912)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    shade_cube(img, 0, 0, 8, 8, 8, rng)
    shade_cube(img, 16, 16, 8, 12, 4, rng)
    shade_cube(img, 40, 16, 4, 12, 4, rng)
    shade_cube(img, 32, 48, 4, 12, 4, rng)
    shade_cube(img, 0, 16, 4, 12, 4, rng)
    shade_cube(img, 16, 48, 4, 12, 4, rng)
    fill_rect(img, 8, 8, 8, 8, rng)
    draw_iris_8x8(img, 8, 8)
    # 帽子层仅正面有眼睛，其余透明
    draw_iris_8x8(img, 40, 8)
    img.save(os.path.join(OUT, "purple_monster.png"))
    eyes = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    glow_from_eye(img, eyes, 8, 8, 8, 8)
    glow_from_eye(img, eyes, 40, 8, 8, 8)
    eyes.save(os.path.join(OUT, "purple_monster_eyes.png"))


def write_form2():
    rng = random.Random(20260913)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    shade_cube(img, 0, 0, 6, 6, 6, rng)
    shade_cube(img, 24, 0, 4, 8, 4, rng)
    shade_cube(img, 40, 4, 2, 16, 2, rng)
    shade_cube(img, 0, 16, 2, 16, 2, rng)
    shade_cube(img, 8, 16, 2, 18, 2, rng)
    shade_cube(img, 16, 16, 2, 14, 2, rng)
    shade_cube(img, 24, 16, 2, 15, 2, rng)
    shade_cube(img, 32, 16, 2, 14, 2, rng)
    shade_cube(img, 40, 22, 2, 13, 2, rng)
    fill_rect(img, 6, 6, 6, 6, rng)
    draw_iris_6x6(img, 6, 6)
    draw_iris_6x6(img, 6, 46)
    img.save(os.path.join(OUT, "purple_monster_second.png"))
    eyes = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    glow_from_eye(img, eyes, 6, 6, 6, 6)
    glow_from_eye(img, eyes, 6, 46, 6, 6)
    eyes.save(os.path.join(OUT, "purple_monster_second_eyes.png"))


def main():
    os.makedirs(OUT, exist_ok=True)
    write_form1()
    write_form2()
    print("wrote", OUT)


if __name__ == "__main__":
    main()
