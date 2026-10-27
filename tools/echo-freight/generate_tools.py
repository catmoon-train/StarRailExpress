"""使用现有Pillow重建Echo工具的32px透明像素贴图，无游戏依赖。"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/noellesroles/textures/item"


def cutter():
    image = Image.new("RGBA", (32, 32))
    d = ImageDraw.Draw(image)
    # 斜向截流刃，分叉钳口与红色供能槽，不复用原版剑轮廓。
    d.polygon([(3, 25), (6, 22), (11, 27), (8, 30), (4, 29)], fill="#211a16")
    d.line([(5, 27), (8, 24)], fill="#b79251", width=3)
    d.polygon([(8, 21), (11, 18), (16, 23), (13, 26)], fill="#5f4934")
    d.line([(8, 21), (15, 24)], fill="#e0be75", width=2)
    d.polygon([(11, 18), (22, 7), (28, 3), (29, 4), (25, 10), (27, 12), (16, 23)], fill="#211a16")
    d.polygon([(13, 18), (23, 8), (26, 6), (23, 11), (25, 12), (16, 21)], fill="#a9b9b9")
    d.line([(14, 18), (24, 8)], fill="#edf3df", width=2)
    d.line([(17, 19), (24, 12)], fill="#52696e", width=2)
    d.line([(15, 20), (22, 13)], fill="#bd4d49", width=2)
    d.point((22, 12), fill="#ffbd86")
    return image


def proxy():
    image = Image.new("RGBA", (32, 32))
    d = ImageDraw.Draw(image)
    # 手持物流卡牌终端：外壳、读卡槽、声纹屏和挂扣。
    d.rounded_rectangle((6, 3, 25, 29), radius=3, fill="#211a16", outline="#b79251", width=2)
    d.rectangle((9, 1, 21, 7), fill="#5f4934", outline="#e0be75")
    d.rectangle((11, 2, 19, 5), fill="#fff0ca")
    d.rectangle((9, 9, 22, 21), fill="#15383a", outline="#85704b")
    d.line([(10, 15), (12, 15), (14, 12), (16, 18), (18, 13), (20, 15), (21, 15)], fill="#8ed6bc", width=1)
    d.rectangle((10, 24, 14, 26), fill="#c89d55")
    d.rectangle((18, 24, 21, 26), fill="#bd4d49")
    d.line([(8, 28), (23, 28)], fill="#6d5638")
    return image


def editor():
    """管理员用的地图编辑工具：暗色平板 + 网格蓝图 + 金色定位角标，区别于卡牌与截流器。"""
    image = Image.new("RGBA", (32, 32))
    d = ImageDraw.Draw(image)
    d.rounded_rectangle((3, 5, 28, 27), radius=2, fill="#211a16", outline="#b79251", width=2)
    d.rectangle((6, 8, 25, 20), fill="#0f2320", outline="#85704b")
    for gx in range(8, 25, 4):
        d.line([(gx, 9), (gx, 19)], fill="#1d3b36", width=1)
    for gy in range(10, 20, 4):
        d.line([(7, gy), (24, gy)], fill="#1d3b36", width=1)
    d.rectangle((10, 11, 12, 13), fill="#8ed6bc")
    d.rectangle((18, 15, 20, 17), fill="#e0be75")
    d.line([(9, 10), (13, 10), (13, 14), (9, 14), (9, 10)], fill="#ffd98a", width=1)
    d.rectangle((13, 2, 18, 6), fill="#5f4934", outline="#e0be75")
    d.rectangle((7, 22, 24, 25), fill="#c89d55")
    d.line([(9, 24), (22, 24)], fill="#3a2b1b", width=1)
    return image


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, image in (("echo_cutter", cutter()), ("echo_card_proxy", proxy()), ("echo_editor", editor())):
        image.save(OUT / f"{name}.png", optimize=True)
        print(f"{name}: {image.width}x{image.height} RGBA")


if __name__ == "__main__":
    main()
