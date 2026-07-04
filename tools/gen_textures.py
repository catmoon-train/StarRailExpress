"""为 StarRailExpress SAN 物品生成 64x64 像素画纹理"""
from PIL import Image, ImageDraw
import os, math, random

OUT = "G:/exmo/StarRailExpress/src/main/resources/assets/noellesroles/textures/item"
os.makedirs(OUT, exist_ok=True)

SZ = 64

def save(img, name):
    path = os.path.join(OUT, f"{name}.png")
    img.save(path)
    print(f"  OK {name}.png")

# ─────────────────────────────────────────────────────
# 1. 花圈 (wreath)
# ─────────────────────────────────────────────────────
def gen_wreath():
    img = Image.new("RGBA", (SZ, SZ), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx, cy, r_out, r_in = 32, 32, 26, 20

    leaf_colors = [
        (34, 139, 34, 255),
        (50, 205, 50, 255),
        (0, 100, 0, 255),
    ]
    flower_colors = [
        (255, 105, 180, 255),
        (255, 255, 224, 255),
        (255, 215, 0, 255),
    ]

    # 画圆形花圈：用多个点组成环形
    for angle in range(0, 360, 4):
        rad = math.radians(angle)
        for dr in range(-4, 5, 2):
            dist = r_out + dr * 1.2
            px = int(cx + dist * math.cos(rad))
            py = int(cy + dist * math.sin(rad))
            if 0 <= px < SZ and 0 <= py < SZ:
                idx = (angle // 4 + dr) % 3
                d.point((px, py), fill=leaf_colors[idx])
        # 内圈
        for dr in range(-2, 3, 2):
            dist = r_in + dr * 2
            px = int(cx + dist * math.cos(rad))
            py = int(cy + dist * math.sin(rad))
            if 0 <= px < SZ and 0 <= py < SZ:
                d.point((px, py), fill=leaf_colors[(angle // 4 + 1) % 3])

    # 小花点缀（十字形）
    random.seed(42)
    for _ in range(8):
        angle = random.randint(0, 359)
        rad = math.radians(angle)
        dist = random.choice([r_out - 4, r_out, r_out + 3])
        fx = int(cx + dist * math.cos(rad))
        fy = int(cy + dist * math.sin(rad))
        if 2 <= fx < SZ - 2 and 2 <= fy < SZ - 2:
            fc = random.choice(flower_colors)
            for dx in range(-2, 3):
                d.point((fx + dx, fy), fill=fc)
            for dy in range(-2, 3):
                d.point((fx, fy + dy), fill=fc)
            # 花心白点
            d.point((fx, fy), fill=(255, 255, 255, 255))

    save(img, "wreath")


# ─────────────────────────────────────────────────────
# 2. 巧克力 (chocolate)
# ─────────────────────────────────────────────────────
def gen_chocolate():
    img = Image.new("RGBA", (SZ, SZ), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # 金色包装纸（上沿）
    d.rectangle([6, 2, 58, 16], fill=(218, 165, 32, 255), outline=(160, 120, 20, 255), width=1)
    # 包装纸褶皱线
    for y in range(3, 16, 4):
        d.line([(10, y), (54, y)], fill=(180, 140, 30, 150), width=1)

    # 巧克力板本体
    d.rectangle([8, 14, 56, 58], fill=(101, 67, 33, 255), outline=(60, 30, 10, 255), width=2)

    # 3x5 分隔格
    for row in range(5):
        for col in range(3):
            x0 = 12 + col * 14
            y0 = 20 + row * 8
            # 单个巧克力块
            d.rounded_rectangle([x0, y0, x0 + 12, y0 + 6], radius=2,
                               fill=(120, 80, 40, 255),
                               outline=(70, 40, 15, 255), width=1)
            # 高光
            d.point((x0 + 2, y0 + 1), fill=(180, 140, 80, 120))

    # 品牌文字区
    d.rectangle([20, 4, 44, 13], fill=(180, 130, 20, 200))

    save(img, "chocolate")


# ─────────────────────────────────────────────────────
# 3. 安神茶 (calming_tea)
# ─────────────────────────────────────────────────────
def gen_calming_tea():
    img = Image.new("RGBA", (SZ, SZ), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # 杯身（浅青瓷色）
    d.rounded_rectangle([12, 18, 52, 58], radius=8,
                        fill=(175, 238, 238, 255),
                        outline=(100, 180, 180, 255), width=2)
    # 杯口椭圆
    d.arc([12, 14, 52, 28], start=0, end=180, fill=(100, 180, 180, 255), width=2)
    # 杯底椭圆
    d.arc([16, 52, 48, 62], start=0, end=180, fill=(100, 180, 180, 200), width=1)

    # 茶液
    d.rounded_rectangle([17, 28, 47, 55], radius=4, fill=(143, 188, 143, 255))
    # 茶面高光
    d.rounded_rectangle([20, 29, 40, 33], radius=2, fill=(180, 220, 180, 180))

    # 杯把手
    d.arc([48, 26, 60, 50], start=270, end=90, fill=(100, 180, 180, 255), width=3)

    # 蒸汽（波浪线）
    sc = (200, 220, 200, 180)
    for sx in [24, 32, 40]:
        for sy in range(4, 18, 2):
            ox = 0 if (sy // 2) % 2 == 0 else 2
            if 0 <= sx + ox < SZ and 0 <= sy < SZ:
                d.point((sx + ox, sy), fill=sc)

    # 茶叶
    for lx, ly in [(26, 38), (34, 42), (30, 46), (38, 40)]:
        d.ellipse([lx, ly, lx + 4, ly + 2], fill=(76, 153, 0, 200))

    # 杯上缘线
    d.line([(14, 18), (50, 18)], fill=(80, 160, 160, 255), width=1)

    save(img, "calming_tea")


# ─────────────────────────────────────────────────────
# 4. 护身符 (talisman)
# ─────────────────────────────────────────────────────
def gen_talisman():
    img = Image.new("RGBA", (SZ, SZ), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # 主体（红色布袋）
    d.rounded_rectangle([14, 10, 50, 54], radius=6,
                        fill=(200, 40, 40, 255),
                        outline=(150, 10, 10, 255), width=2)
    # 内框金色
    d.rounded_rectangle([17, 13, 47, 51], radius=4,
                        outline=(218, 165, 32, 200), width=1)

    # 前面白底文字区
    d.rounded_rectangle([22, 22, 42, 42], radius=4,
                         fill=(255, 250, 240, 255),
                         outline=(200, 180, 160, 255), width=1)

    # 符文/文字（简笔"守"字）
    tc = (180, 0, 0, 255)
    # 竖线
    d.line([(32, 24), (32, 40)], fill=tc, width=2)
    # 横折
    d.line([(28, 26), (36, 26)], fill=tc, width=2)
    d.line([(36, 26), (36, 32)], fill=tc, width=2)
    # 中间横
    d.line([(28, 30), (36, 30)], fill=tc, width=2)
    # 下部
    d.line([(28, 34), (36, 34)], fill=tc, width=1)
    d.line([(28, 38), (36, 38)], fill=tc, width=1)

    # 顶部绳结（金色）
    d.rounded_rectangle([26, 2, 38, 12], radius=3,
                         fill=(218, 165, 32, 255),
                         outline=(180, 130, 20, 255), width=1)
    # 绳结细节
    d.line([(30, 2), (30, 12)], fill=(180, 130, 20, 180), width=1)
    d.line([(34, 2), (34, 12)], fill=(180, 130, 20, 180), width=1)

    # 流苏
    for i, sy in enumerate(range(52, 62)):
        alpha = max(40, 200 - i * 15)
        x1 = 28 + (i % 3) * 2
        x2 = 34 + (i % 3) * 2
        d.line([(x1, sy), (x1 + 1, sy)], fill=(218, 165, 32, alpha), width=1)
        d.line([(x2, sy), (x2 + 1, sy)], fill=(218, 165, 32, alpha), width=1)

    # 四角金色装饰点
    for px, py in [(16, 12), (46, 12), (16, 48), (46, 48)]:
        d.ellipse([px - 2, py - 2, px + 2, py + 2], fill=(255, 215, 0, 255))

    save(img, "talisman")


# ─────────────────────────────────────────────────────
# 5. 提神咖啡 (energizing_coffee)
# ─────────────────────────────────────────────────────
def gen_energizing_coffee():
    img = Image.new("RGBA", (SZ, SZ), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # 杯身（深棕）
    d.rounded_rectangle([10, 16, 54, 58], radius=6,
                        fill=(80, 40, 10, 255),
                        outline=(50, 20, 5, 255), width=2)
    # 杯内咖啡液
    d.rounded_rectangle([15, 24, 49, 54], radius=4, fill=(101, 67, 33, 255))
    # 咖啡高光
    d.rounded_rectangle([18, 26, 32, 30], radius=2, fill=(140, 100, 60, 180))

    # 杯把手
    d.arc([50, 24, 62, 48], start=270, end=90, fill=(50, 20, 5, 255), width=3)

    # 杯口
    d.line([(10, 18), (54, 18)], fill=(50, 20, 5, 255), width=2)

    # 闪电标志（杯身中央）
    pts = [(30, 26), (36, 26), (33, 34), (38, 34), (28, 44), (32, 36), (30, 36)]
    d.polygon(pts, fill=(255, 215, 0, 255), outline=(255, 165, 0, 255))

    # 能量光晕
    for r in range(4):
        alpha = 100 - r * 20
        d.ellipse([29 - r * 3, 25 - r * 3, 39 + r * 3, 45 + r * 3],
                   outline=(255, 215, 0, alpha), width=1)

    # 热气/能量粒子
    ec = (255, 200, 50, 150)
    for hx, hy in [(22, 6), (24, 4), (24, 8), (32, 2), (40, 5), (42, 3), (42, 7)]:
        d.ellipse([hx - 1, hy - 1, hx + 1, hy + 1], fill=ec)

    # 底部阴影
    d.ellipse([14, 56, 52, 62], fill=(40, 20, 5, 100))

    save(img, "energizing_coffee")


# ─────────────────────────────────────────────────────
# 主程序
# ─────────────────────────────────────────────────────
if __name__ == "__main__":
    print("生成 64x64 SAN 物品纹理 ...")
    gen_wreath()
    gen_chocolate()
    gen_calming_tea()
    gen_talisman()
    gen_energizing_coffee()
    print("全部完成！")
