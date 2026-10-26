"""回声托运站职业徽章图标：6 个职业各一张 32x32 RGBA，仅用本机 Pillow。"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'src/main/resources/assets/noellesroles/textures/gui/echo/classes'
CREAM = '#fff4dc'
GOLD = '#d4af37'
DARK = '#1a1008'
HUNTER = '#e06b65'
SURVIVOR = '#72c17b'


def badge(faction):
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    ink = HUNTER if faction == 'hunter' else SURVIVOR
    # 圆形徽章底：深底 + 阵营色双环
    d.ellipse((1, 1, 30, 30), fill=DARK, outline=GOLD)
    d.ellipse((4, 4, 27, 27), outline=ink)
    return im, d, ink


def ravager():
    im, d, ink = badge('hunter')
    # 三道爪痕
    for offset in (0, 6, 12):
        d.line((9 + offset, 8, 5 + offset, 24), fill=CREAM, width=2)
    d.line((20, 10, 26, 14), fill=GOLD, width=2)
    return im


def warden():
    im, d, ink = badge('hunter')
    # 锁 + 钉子
    d.rectangle((10, 15, 22, 25), outline=CREAM, width=2)
    d.arc((12, 7, 20, 19), 180, 360, fill=CREAM, width=2)
    d.line((16, 18, 16, 22), fill=GOLD, width=2)
    d.line((6, 26, 26, 26), fill=ink, width=2)
    return im


def stalker():
    im, d, ink = badge('hunter')
    # 脚印 + 速度线
    d.ellipse((11, 9, 18, 17), outline=CREAM)
    d.ellipse((13, 18, 20, 26), outline=CREAM)
    for y in (11, 16, 21):
        d.line((4, y, 9, y), fill=ink, width=1)
    return im


def vanguard():
    im, d, ink = badge('survivor')
    # 盾 + 上箭头
    d.polygon(((16, 6), (25, 11), (23, 21), (16, 27), (9, 21), (7, 11)), outline=CREAM)
    d.line((16, 21, 16, 12, 13, 15), fill=GOLD, width=2)
    d.line((19, 15, 16, 12), fill=GOLD, width=2)
    return im


def trapper():
    im, d, ink = badge('survivor')
    # 三层同心干扰环
    for r in ((5, 5, 27, 27), (9, 9, 23, 23), (13, 13, 19, 19)):
        d.ellipse(r, outline=CREAM)
    d.line((6, 16, 26, 16), fill=ink, width=1)
    d.line((16, 6, 16, 26), fill=ink, width=1)
    return im


def hauler():
    im, d, ink = badge('survivor')
    # 齿轮 + 货箱
    d.ellipse((8, 8, 24, 24), outline=CREAM, width=2)
    for angle in range(0, 360, 45):
        import math
        x = 16 + math.cos(math.radians(angle)) * 9
        y = 16 + math.sin(math.radians(angle)) * 9
        d.line((16, 16, x, y), fill=ink, width=1)
    d.rectangle((13, 13, 19, 19), fill=GOLD)
    return im


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, fn in (('ravager', ravager), ('warden', warden), ('stalker', stalker),
                     ('vanguard', vanguard), ('trapper', trapper), ('hauler', hauler)):
        fn().resize((64, 64), Image.Resampling.NEAREST).save(OUT / f'{name}.png', optimize=True)
    print('6 class icons generated')


if __name__ == '__main__':
    main()
