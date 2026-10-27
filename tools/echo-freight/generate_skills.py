"""生成专长信物 32px 物品贴图与六个技能图标，无游戏依赖（Pillow）。"""
import math
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
ITEM_OUT = ROOT / "src/main/resources/assets/noellesroles/textures/item"
ICON_OUT = ROOT / "src/main/resources/assets/noellesroles/textures/gui/echo/skills"

DARK = "#211a16"
BRASS = "#b79251"
GOLD = "#e0be75"
CREAM = "#fff0ca"
RED = "#bd4d49"
TEAL = "#8ed6bc"
STEEL = "#a9b9b9"


def pentagon(d, cx, cy, radius, fill=None, outline=None, width=2):
    points = [
        (cx + math.cos(-math.pi / 2 + i * math.pi * 2 / 5) * radius,
         cy + math.sin(-math.pi / 2 + i * math.pi * 2 / 5) * radius)
        for i in range(5)
    ]
    d.polygon(points, fill=fill, outline=outline, width=width)


def token():
    image = Image.new("RGBA", (32, 32))
    d = ImageDraw.Draw(image)
    # 与 HUD 同形的五边形信物：铜边、深底、中心金色人字纹。
    pentagon(d, 16, 16.5, 13, fill=DARK, outline=BRASS, width=2)
    pentagon(d, 16, 16.5, 9, fill="#15383a", outline=GOLD, width=1)
    d.line([(11, 18), (16, 12), (21, 18)], fill=CREAM, width=2)
    d.line([(13, 21), (16, 17), (19, 21)], fill=GOLD, width=2)
    d.point((16, 16), fill=CREAM)
    return image


def steady_haul():
    image = Image.new("RGBA", (32, 32))
    d = ImageDraw.Draw(image)
    # 稳健搬运：加固货箱 + 横向束带。
    d.rectangle((5, 10, 27, 27), fill=DARK, outline=BRASS, width=2)
    d.rectangle((8, 13, 24, 24), fill="#5f4934", outline=GOLD, width=1)
    d.line([(5, 18), (27, 18)], fill=RED, width=3)
    d.line([(16, 10), (16, 27)], fill=RED, width=2)
    d.rectangle((12, 6, 20, 11), fill=BRASS, outline=GOLD)
    d.line([(9, 29), (23, 29)], fill="#6d5638", width=2)
    return image


def swift_pick():
    image = Image.new("RGBA", (32, 32))
    d = ImageDraw.Draw(image)
    # 迅捷拾取：伸向货箱的手，两道速度线。
    d.rectangle((18, 16, 28, 27), fill=DARK, outline=BRASS, width=2)
    d.line([(20, 19), (26, 19)], fill=GOLD, width=2)
    d.polygon([(4, 9), (14, 9), (16, 15), (10, 22), (5, 18)], fill=TEAL, outline="#52696e")
    d.line([(6, 12), (12, 12)], fill=CREAM, width=1)
    d.line([(2, 6), (9, 6)], fill=GOLD, width=2)
    d.line([(1, 10), (7, 10)], fill=GOLD, width=2)
    d.line([(2, 14), (6, 14)], fill=GOLD, width=1)
    return image


def lock_on():
    image = Image.new("RGBA", (32, 32))
    d = ImageDraw.Draw(image)
    # 锁定：四段准星 + 中心红点。
    d.ellipse((6, 6, 26, 26), outline=BRASS, width=2)
    d.ellipse((11, 11, 21, 21), outline=GOLD, width=1)
    for x1, y1, x2, y2 in ((16, 2, 16, 8), (16, 24, 16, 30), (2, 16, 8, 16), (24, 16, 30, 16)):
        d.line([(x1, y1), (x2, y2)], fill=CREAM, width=2)
    d.ellipse((14, 14, 18, 18), fill=RED)
    d.point((16, 16), fill=CREAM)
    return image


def surge():
    image = Image.new("RGBA", (32, 32))
    d = ImageDraw.Draw(image)
    # 过载涌动：管道上炸开的电弧。
    d.rectangle((3, 20, 29, 26), fill=DARK, outline="#52696e", width=2)
    d.line([(6, 23), (26, 23)], fill=TEAL, width=1)
    d.polygon([(18, 3), (10, 16), (15, 16), (12, 29), (23, 14), (17, 14), (21, 3)], fill=GOLD, outline=CREAM)
    d.point((16, 15), fill=CREAM)
    return image


def dark_cache():
    image = Image.new("RGBA", (32, 32))
    d = ImageDraw.Draw(image)
    # 暗舱：上锁的暗格，只在边缘露一线光。
    d.rectangle((4, 13, 28, 28), fill=DARK, outline=BRASS, width=2)
    d.rectangle((7, 16, 25, 25), fill="#2a1f18", outline="#6d5638")
    d.arc((10, 5, 22, 17), start=180, end=360, fill=STEEL, width=3)
    d.rectangle((12, 12, 20, 21), fill=BRASS, outline=GOLD)
    d.line([(16, 14), (16, 18)], fill=DARK, width=2)
    d.line([(8, 26), (24, 26)], fill=TEAL, width=1)
    return image


def broker_deal():
    image = Image.new("RGBA", (32, 32))
    d = ImageDraw.Draw(image)
    # 中间人交易：两枚对向铜钱与一条成交箭头。
    d.ellipse((3, 12, 17, 26), fill=DARK, outline=BRASS, width=2)
    d.ellipse((15, 12, 29, 26), fill=DARK, outline=GOLD, width=2)
    d.rectangle((9, 18, 11, 20), fill=CREAM)
    d.rectangle((21, 18, 23, 20), fill=CREAM)
    d.line([(8, 8), (24, 8)], fill=TEAL, width=2)
    d.polygon([(24, 5), (29, 8), (24, 11)], fill=TEAL)
    d.line([(8, 30), (24, 30)], fill=RED, width=1)
    return image


def main():
    ITEM_OUT.mkdir(parents=True, exist_ok=True)
    ICON_OUT.mkdir(parents=True, exist_ok=True)
    for name, image in (("echo_skill_token", token()),):
        image.save(ITEM_OUT / (name + ".png"), optimize=True)
        print("%s: %dx%d RGBA" % (name, image.width, image.height))
    for name, image in (
        ("steady_haul", steady_haul()),
        ("swift_pick", swift_pick()),
        ("lock_on", lock_on()),
        ("surge", surge()),
        ("dark_cache", dark_cache()),
        ("broker_deal", broker_deal()),
    ):
        image.save(ICON_OUT / (name + ".png"), optimize=True)
        print("skill %s: %dx%d RGBA" % (name, image.width, image.height))


if __name__ == "__main__":
    main()
