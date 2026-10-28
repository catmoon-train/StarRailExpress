"""回声托运站语义像素图标。仅使用本机 Pillow；从仓库根目录运行。"""
from pathlib import Path
import argparse
import hashlib
import re
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'src/main/resources/assets/noellesroles/textures/gui/echo/cards'
REGISTRY = ROOT / 'src/main/java/org/agmas/noellesroles/game/modes/echo/cards/EchoCardRegistry.java'
CREAM = '#fff4dc'
GOLD = '#d4af37'
DARK = '#1a1008'
COLORS = {'CARRIER': '#72c17b', 'INTERCEPTOR': '#e06b65', 'BROKER': '#e0b85e'}

# 同一系列/阵营会产出多张卡，因此为按卡定制的母题单独走一张表；
# 未列出的卡仍然落到系列通用画法，保持旧图标不变。
CARD_MOTIFS = {
    'beacon_anchor': 'anchor',
    'beacon_lock': 'anchor_lock',
    'silent_route': 'route',
    'hunter_blind': 'blind',
    'armor_patch': 'armor',
    'decoy_swap': 'swap',
    'cargo_bait': 'bait',
    'key_bait': 'key_bait',
    'shadow_step': 'step',
    'phantom_stride': 'stride',
    'courier_instinct': 'instinct',
    'snare_trap': 'snare',
    'hard_snare': 'snare_hard',
    'fake_key_cache': 'key_cache',
    'mirror_double': 'mirror',
}


def icon(card, faction, series, upgraded):
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    ink = COLORS[faction]
    d.rounded_rectangle((1, 1, 30, 30), radius=4, fill=DARK, outline=GOLD if upgraded else '#8b6914')
    d.line((5, 3, 26, 3), fill='#6c5737')
    d.line((4, 27, 27, 27), fill='#483722')

    def line(points, color=CREAM, width=2):
        d.line(points, fill=color, width=width, joint='curve')

    def box(bounds, color=ink, fill=None):
        d.rectangle(bounds, fill=fill, outline=color, width=1)

    def ring(bounds, color=ink, width=1):
        d.ellipse(bounds, outline=color, width=width)

    def arrow(x, y, color=ink):
        line((x-5, y+4, x+1, y+4, x+1, y+7, x+7, y, x+1, y-7, x+1, y-4, x-5, y-4), color)

    def figure(x=16, y=8, color=CREAM):
        ring((x-2, y-2, x+2, y+2), color)
        line((x, y+3, x, y+10), color)
        line((x-5, y+5, x+5, y+5), color)
        line((x-4, y+15, x, y+10, x+4, y+15), color)

    motif = CARD_MOTIFS.get(card)
    if motif == 'anchor':
        # 信标锚定：塔身 + 底部锁锚。
        d.polygon(((16, 4), (21, 13), (19, 22), (13, 22), (11, 13)), outline=CREAM)
        line((16, 20, 16, 9, 13, 12), CREAM)
        line((9, 24, 23, 24, 23, 20), ink)
        d.arc((11, 18, 21, 28), 0, 180, fill=ink, width=2)
    elif motif == 'anchor_lock':
        d.polygon(((16, 4), (21, 13), (19, 21), (13, 21), (11, 13)), outline=CREAM)
        line((16, 19, 16, 9), CREAM)
        ring((20, 18, 27, 25), GOLD, 2)
        line((23, 21, 25, 23), GOLD, 1)
        line((8, 25, 13, 25), ink)
    elif motif == 'route':
        # 静默路线：折线路径 + 消音斜杠。
        line((5, 24, 12, 17, 12, 11, 20, 11, 20, 6), CREAM)
        d.ellipse((8, 21, 12, 25), fill=ink)
        d.ellipse((18, 4, 22, 8), fill=ink)
        line((6, 8, 26, 26), ink, 2)
    elif motif == 'blind':
        # 猎手致盲：睁眼被两道斜线遮住。
        d.arc((5, 10, 27, 22), 0, 180, fill=CREAM, width=2)
        d.arc((5, 10, 27, 22), 180, 360, fill=CREAM, width=2)
        d.ellipse((14, 13, 18, 19), fill=ink)
        line((6, 25, 26, 7), ink, 2)
        line((6, 7, 26, 25), ink, 2)
    elif motif == 'armor':
        # 护甲补丁：盾形 + 十字补片。
        d.polygon(((16, 5), (26, 10), (24, 22), (16, 27), (8, 22), (6, 10)), outline=CREAM)
        line((16, 10, 16, 22), GOLD)
        line((11, 16, 21, 16), GOLD)
    elif motif == 'swap':
        # 幻影替换：两个箭头首尾相接。
        arrow(12, 11)
        line((24, 21, 12, 21, 12, 18, 6, 24, 12, 28, 12, 25, 24, 25), CREAM)
        line((6, 8, 12, 8), ink)
    elif motif == 'bait':
        # 货物诱饵：货箱挂在钩上。
        box((9, 16, 22, 26), ink)
        line((15, 16, 15, 26), CREAM, 1)
        line((16, 5, 16, 13, 21, 8), CREAM)
        d.ellipse((20, 4, 24, 8), outline=ink)
    elif motif == 'key_bait':
        # 假钥匙：钥匙环 + 齿，带伪造印记。
        ring((6, 8, 15, 17), CREAM, 2)
        line((13, 15, 25, 27), CREAM, 2)
        line((21, 23, 24, 20), ink)
        line((24, 26, 27, 23), ink)
        line((18, 6, 27, 15), GOLD, 1)
    elif motif == 'step':
        # 影步：脚印 + 残影斜线。
        d.polygon(((19, 8), (24, 10), (22, 17), (17, 15)), fill=ink, outline=CREAM)
        d.polygon(((13, 18), (18, 20), (16, 26), (11, 24)), fill=ink, outline='#8f8f8f')
        line((6, 12, 10, 12), ink)
        line((5, 18, 9, 18), ink)
    elif motif == 'stride':
        d.polygon(((20, 6), (26, 9), (23, 17), (17, 14)), fill=ink, outline=GOLD)
        d.polygon(((14, 17), (20, 20), (17, 27), (11, 24)), fill=ink, outline='#8f8f8f')
        line((5, 10, 10, 10), GOLD)
        line((4, 16, 9, 16), GOLD)
        line((3, 22, 8, 22), GOLD)
    elif motif == 'instinct':
        # 信使直觉：罗盘 + 指向点。
        ring((6, 6, 26, 26), ink)
        d.polygon(((16, 9), (20, 19), (16, 17), (12, 19)), fill=CREAM)
        d.ellipse((22, 8, 25, 11), fill=GOLD)
        line((16, 16, 24, 8), ink, 1)
    elif motif == 'snare':
        # 捕网：环 + 网线。
        ring((7, 7, 25, 25), CREAM, 2)
        line((7, 7, 25, 25), ink, 1)
        line((25, 7, 7, 25), ink, 1)
        line((16, 7, 16, 25), ink, 1)
        line((7, 16, 25, 16), ink, 1)
    elif motif == 'snare_hard':
        ring((5, 5, 27, 27), GOLD, 2)
        ring((11, 11, 21, 21), CREAM, 1)
        line((5, 5, 27, 27), ink, 1)
        line((27, 5, 5, 27), ink, 1)
        line((16, 3, 16, 29), ink, 1)
        line((3, 16, 29, 16), ink, 1)
    elif motif == 'key_cache':
        # 双假钥匙仓：两只钥匙 + 底部货架。
        ring((4, 8, 12, 16), CREAM, 2)
        line((10, 14, 18, 22), CREAM, 2)
        ring((15, 14, 23, 22), GOLD, 2)
        line((21, 20, 27, 26), GOLD, 2)
        line((6, 26, 26, 26), ink, 2)
    elif motif == 'mirror':
        # 镜像双影：左右对称人形。
        figure(11, 8, CREAM)
        figure(21, 8, '#8f8f8f')
        line((16, 5, 16, 27), ink, 1)
    elif series == 'SILENCE':
        # 静音声筒；升级版双次密封环。
        box((7, 13, 11, 19), CREAM)
        d.polygon(((11, 13), (17, 8), (17, 24), (11, 19)), outline=CREAM)
        line((6, 24, 25, 7), ink)
        if upgraded:
            ring((19, 15, 24, 20), GOLD)
            ring((22, 20, 27, 25), GOLD)
    elif series == 'DECOY' and faction == 'CARRIER':
        figure()
        line((7, 24, 25, 24), ink)
        if upgraded:
            d.polygon(((6, 7), (8, 5), (10, 7), (8, 10)), fill=ink)
            line((22, 7, 26, 11, 22, 15), ink, 1)
        else:
            d.arc((19, 9, 28, 21), -70, 70, fill=ink, width=1)
            d.arc((20, 6, 30, 24), -65, 65, fill=ink, width=1)
    elif series == 'MOBILITY':
        # 斜向靴子及前进轨迹。
        d.polygon(((17, 7), (23, 9), (20, 17), (26, 20), (24, 24), (12, 21)), fill=ink, outline=CREAM)
        line((6, 11, 12, 11), ink)
        line((5, 16, 10, 16), ink)
        if upgraded:
            line((7, 21, 11, 24, 8, 26), GOLD)
            line((12, 5, 16, 5), GOLD)
    elif series == 'LOGISTICS':
        box((8, 9, 24, 24), CREAM)
        line((8, 14, 24, 14), ink)
        line((12, 8, 12, 25), ink)
        line((20, 8, 20, 25), ink)
        box((12, 6, 20, 10), ink)
        if upgraded:
            box((5, 15, 8, 22), GOLD)
            box((24, 15, 27, 22), GOLD)
            line((14, 18, 18, 18), GOLD)
    elif series == 'COUNTER':
        if card in ('steady_flow', 'overdrive_beacon'):
            # 稳流阀：管道 + 顺时针箭头，超载再加双箭头。
            line((7, 12, 25, 12), ink)
            line((7, 20, 25, 20), ink)
            line((10, 8, 10, 24), ink)
            line((22, 8, 22, 24), ink)
            line((12, 16, 18, 16, 16, 13), CREAM)
            if card == 'overdrive_beacon':
                line((12, 24, 20, 24, 18, 21), CREAM)
        elif card == 'beacon_boost':
            # 信标助推：塔身 + 上升箭头。
            d.polygon(((16, 6), (21, 14), (19, 24), (13, 24), (11, 14)), outline=ink)
            line((16, 22, 16, 11, 13, 14), CREAM)
            line((19, 11, 13, 11), CREAM)
        if upgraded:
            line((24, 18, 24, 26), GOLD)
            line((20, 22, 28, 22), GOLD)
    elif series == 'INTEL':
        ring((6, 6, 26, 26), ink)
        ring((10, 10, 22, 22), '#64744c')
        line((16, 16, 23, 8), CREAM)
        d.ellipse((18, 18, 20, 20), fill=CREAM)
        d.ellipse((10, 13, 12, 15), fill=ink)
        if upgraded:
            line((4, 16, 8, 16), GOLD, 1)
            line((24, 16, 28, 16), GOLD, 1)
            line((16, 4, 16, 8), GOLD, 1)
            d.ellipse((17, 9, 19, 11), fill=GOLD)
    elif series == 'ACCESS':
        if card == 'door_wedge':
            line((9, 7, 9, 25, 25, 25), CREAM)
            d.polygon(((11, 24), (23, 17), (23, 24)), fill=ink)
        else:
            ring((6, 7, 16, 17), CREAM, 2)
            line((14, 15, 25, 26), ink, 3)
            line((20, 21, 24, 17), ink)
            line((23, 24, 27, 20), ink)
            if upgraded:
                d.polygon(((22, 5), (24, 9), (28, 11), (24, 13), (22, 17), (20, 13), (17, 11), (20, 9)), fill=GOLD)
            else:
                d.arc((5, 5, 22, 22), 250, 350, fill=ink, width=1)
    elif series == 'TRACKING':
        d.polygon(((16, 5), (21, 14), (16, 24), (11, 14)), outline=CREAM)
        line((16, 7, 16, 21), ink, 1)
        d.arc((6, 6, 26, 26), 20, 160, fill=ink, width=1)
        if upgraded:
            ring((4, 4, 28, 28), GOLD)
            d.ellipse((6, 12, 9, 15), fill=ink)
            d.ellipse((23, 17, 26, 20), fill=ink)
    elif series == 'SABOTAGE':
        if card == 'stall_pulse':
            # 迟滞脉冲：沙漏 + 中断符号。
            line((11, 6, 21, 6), CREAM)
            line((11, 25, 21, 25), CREAM)
            line((12, 7, 20, 24), ink)
            line((20, 7, 12, 24), ink)
            line((8, 16, 14, 16), GOLD)
        elif card == 'stall_surge':
            # 强迟滞：双沙漏叠层。
            line((9, 5, 17, 5), CREAM)
            line((9, 16, 17, 16), CREAM)
            line((10, 6, 16, 15), ink)
            line((16, 6, 10, 15), ink)
            line((15, 17, 23, 17), CREAM)
            line((15, 27, 23, 27), CREAM)
            line((16, 18, 22, 26), ink)
            line((22, 18, 16, 26), ink)
        elif card == 'cargo_raid':
            # 截货：货箱被钩子勾走。
            box((8, 14, 22, 26), ink)
            line((10, 20, 20, 20), CREAM)
            line((15, 6, 15, 12, 21, 8), CREAM)
            d.ellipse((20, 5, 24, 9), outline=ink)
        else:
            ring((5, 6, 15, 16), CREAM)
            line((13, 15, 24, 26), CREAM)
            line((21, 8, 17, 15, 23, 16, 19, 23), ink)
    elif series == 'DECOY':
        box((9, 14, 22, 24), CREAM)
        line((15, 14, 15, 24), ink, 1)
        d.arc((5, 5, 27, 26), 205, 335, fill=ink, width=2)
        if upgraded:
            box((19, 18, 26, 25), GOLD)
            line((6, 17, 6, 23, 3, 20), ink)
        else:
            line((13, 8, 17, 8), ink)
            line((15, 6, 15, 10), ink)
    elif series == 'HUNT':
        ring((8, 8, 24, 24), ink, 2)
        line((16, 4, 16, 11), CREAM)
        line((16, 21, 16, 28), CREAM)
        line((4, 16, 11, 16), CREAM)
        line((21, 16, 28, 16), CREAM)
        if upgraded:
            d.polygon(((12, 14), (16, 10), (20, 14), (18, 21), (14, 21)), fill=ink)
        else:
            d.ellipse((14, 14, 18, 18), fill=CREAM)
    elif series == 'TRADE':
        arrow(15, 11)
        line((24, 20, 12, 20, 12, 17, 6, 23, 12, 27, 12, 24, 24, 24), CREAM)
        if upgraded:
            ring((20, 15, 27, 22), GOLD)
            line((22, 18, 24, 20, 27, 16), GOLD, 1)
    elif series == 'DISGUISE':
        d.polygon(((6, 10), (12, 12), (16, 10), (20, 12), (26, 10), (24, 21), (16, 26), (8, 21)), outline=CREAM)
        line((9, 15, 13, 16), ink)
        line((19, 16, 23, 15), ink)
        if upgraded:
            line((16, 11, 16, 25), GOLD, 1)
            d.polygon(((18, 12), (25, 11), (23, 20), (18, 23)), fill='#74572c')
            line((19, 16, 22, 15), GOLD)
    elif series == 'SELF_PRESERVE':
        figure(18, 7)
        line((6, 11, 10, 11), ink)
        line((5, 16, 12, 16), ink)
        line((7, 22, 10, 22), ink)
        if upgraded:
            d.arc((4, 5, 28, 28), 20, 160, fill=GOLD, width=2)
            line((6, 7, 9, 7), GOLD)
    elif series == 'SALVAGE':
        if card == 'pack_hook':
            line((22, 5, 16, 12, 16, 20), CREAM)
            d.arc((9, 14, 17, 24), 0, 180, fill=ink, width=2)
            box((19, 18, 27, 25), GOLD)
        else:
            d.polygon(((12, 7), (19, 5), (23, 14), (18, 23), (10, 20), (8, 13)), fill='#66527b', outline=CREAM)
            line((12, 7, 14, 14, 18, 23), ink, 1)
            if upgraded:
                d.arc((4, 4, 28, 28), 0, 180, fill=GOLD, width=2)
                line((5, 12, 5, 20), GOLD)
                line((27, 12, 27, 20), GOLD)
            else:
                line((24, 8, 27, 11, 24, 14), ink, 1)
    elif series == 'FORGERY':
        box((8, 6, 22, 25), CREAM)
        line((11, 10, 19, 10), ink, 1)
        line((11, 14, 18, 14), ink, 1)
        if upgraded:
            box((18, 16, 25, 23), GOLD)
            line((20, 18, 23, 21, 26, 17), GOLD)
            line((5, 19, 8, 19), ink)
        else:
            line((12, 22, 24, 12), ink, 3)
    elif series == 'RELAY':
        # 中继：两个节点 + 中继弧，升级版多加一跳。
        d.ellipse((6, 9, 12, 15), outline=CREAM)
        d.ellipse((20, 17, 26, 23), outline=CREAM)
        d.arc((9, 6, 23, 24), 300, 60, fill=ink, width=2)
        line((12, 12, 20, 20), ink)
        if upgraded:
            d.ellipse((14, 6, 19, 11), outline=GOLD)
            line((17, 12, 21, 16), GOLD, 1)
        else:
            line((16, 10, 18, 13), ink, 1)
    else:
        raise ValueError((card, series))
    if upgraded:
        # 升级双铆钉仅作辅助；主体也有独立结构变化。
        d.rectangle((3, 3, 4, 4), fill=GOLD)
        d.rectangle((27, 3, 28, 4), fill=GOLD)
    return im.resize((64, 64), Image.Resampling.NEAREST)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sheet', type=Path, help='可选联络表输出；临时预览请放会话scratch')
    args = parser.parse_args()
    cards = re.findall(r'cards\.add\((base|up)\("([a-z_]+)",([A-Z_]+),([A-Z_]+),', REGISTRY.read_text(encoding='utf-8'))
    # 数量与卡牌注册表保持一致：54 张 = 31 基础 + 23 高级。
    assert len(cards) == 54 and len({c[1] for c in cards}) == 54, (len(cards), len({c[1] for c in cards}))
    OUT.mkdir(parents=True, exist_ok=True)
    images = []
    for tier, name, faction, series in cards:
        im = icon(name, faction, series, tier == 'up')
        im.save(OUT / f'{name}.png', optimize=True)
        images.append((name, im))
    digests = {hashlib.sha256((OUT / f'{name}.png').read_bytes()).hexdigest() for _, name, _, _ in cards}
    assert len(digests) == len(cards), '卡牌图标不能重复'
    if args.sheet:
        sheet = Image.new('RGB', (7 * 160, 8 * 100), '#18120a')
        d = ImageDraw.Draw(sheet)
        for n, (name, im) in enumerate(images):
            x, y = (n % 7) * 160, (n // 7) * 100
            sheet.paste(im, (x + 48, y + 4), im)
            d.text((x + 2, y + 73), name, fill=CREAM)
        args.sheet.parent.mkdir(parents=True, exist_ok=True)
        sheet.save(args.sheet)
    print(f'{len(cards)} distinct 64x64 RGBA card icons generated')


if __name__ == '__main__':
    main()
