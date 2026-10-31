"""生成玩家魔女的配件贴图，并从 Minecraft 实际顶点生成预览。

python -X utf8 tools/generate_halloween_witch.py --texture
./gradlew --offline test --tests '*HalloweenWitchModelTest'
python -X utf8 tools/generate_halloween_witch.py --preview

皮肤原文件永不修改。几何在 HalloweenWitchAccessories.java；预览顶点来自
Minecraft ModelPart.render，包含原版玩家 UV、外层膨胀和实际姿态。
"""
import argparse
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
SKIN = ROOT / 'src/main/resources/assets/noellesroles/textures/entity/halloween_canyue_skin.png'
TEXTURE = SKIN.with_name('halloween_canyue_accessories.png')
MESH = ROOT / 'build/halloween-witch-preview-mesh.json'
PREVIEW = ROOT / 'docs/images/halloween-canyue-preview.png'
REGIONS = [
    # x,y,w,h,主色,高光,暗色。与 Java texOffs 固定对应，不涉及玩家皮肤。
    (0, 0, 64, 24, '#403044', '#63465c', '#2c2333'),
    (64, 0, 64, 24, '#a43d56', '#ce677a', '#782c44'),
    (0, 24, 32, 16, '#d5a967', '#f6d69a', '#a5754b'),
    (32, 24, 32, 16, '#f8eadd', '#fff8ee', '#dec9cf'),
    (64, 24, 64, 16, '#702b43', '#99435b', '#4f2339'),
    (0, 48, 96, 8, '#79503c', '#b27b4b', '#563a34'),
    (0, 64, 96, 16, '#d1a469', '#f1cf8d', '#a77c4f'),
    (0, 80, 96, 16, '#bf935e', '#e9bf7f', '#946d49'),
    (0, 96, 32, 16, '#de853e', '#f6b768', '#ad5a32'),
    (32, 96, 32, 16, '#c77536', '#eaa052', '#99512f'),
    (64, 96, 16, 16, '#677247', '#a2a260', '#4a5338'),
]


def accessory_texture():
    image = Image.new('RGBA', (128, 128))
    for x, y, w, h, main, light, dark in REGIONS:
        colors = [tuple(bytes.fromhex(c[1:])) for c in (main, light, dark)]
        for yy in range(h):
            for xx in range(w):
                color = colors[0]
                if y == 96:
                    # 南瓜纵向棱纹与青色瓜蒂，和木杆横纹分开。
                    if xx % 4 == 0:
                        color = colors[2]
                    elif xx % 4 == 1:
                        color = colors[1]
                elif y >= 48:
                    if yy % 4 == 0:
                        color = colors[2]
                    elif yy % 4 == 1 and xx % 7 != 0:
                        color = colors[1]
                elif y == 24 and x < 64:
                    color = colors[1 if yy % 7 < 2 else 0]
                else:
                    # 帽身用有层次的紫黑布料，帽带和衬里各自独立配色；不堆重复亮色杂点。
                    shade = min(3, xx // 18 + yy // 10) / 10
                    color = tuple(round(a * (1 - shade) + b * shade) for a, b in zip(colors[0], colors[2]))
                    if 1 <= yy <= 2:
                        color = tuple(round(a * .8 + b * .2) for a, b in zip(color, colors[1]))
                image.putpixel((x + xx, y + yy), (*color, 255))
    # 6×5×5 南瓜的 north 面从 (5,101) 开始：眼睛和齿状笑嘴。
    draw = ImageDraw.Draw(image)
    for xx, yy in ((1, 1), (4, 1), (1, 3), (2, 4), (3, 4), (4, 3)):
        draw.point((5 + xx, 101 + yy), fill='#66342e')
    draw.point((7, 103), fill='#ffd897')
    draw.point((8, 103), fill='#ffd897')
    return image


def render(mesh, yaw, width=760, height=800, scale=17, target=(3, 1, 0), pitch=10):
    """顶点与归一化 UV 全部取自 Java 导出；仅做相机投影、最近邻采样和深度测试。"""
    yaw, pitch = math.radians(yaw), math.radians(pitch)
    cy, sy, cx, sx = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    camera = rx @ ry
    canvas = np.zeros((height, width, 4), dtype=np.uint8)
    depth_buffer = np.full((height, width), np.inf)
    light = np.array([-.4, .65, -.6]); light /= np.linalg.norm(light)
    for name, texture in (('skin', SKIN), ('accessories', TEXTURE)):
        atlas = np.asarray(Image.open(texture).convert('RGBA'))
        vertices = np.asarray(mesh[name], dtype=float).reshape(-1, 4, 5)
        for quad in vertices:
            world = quad[:, :3]
            projected = (world - target) @ camera.T
            screen = np.column_stack((width / 2 + projected[:, 0] * scale,
                                      height / 2 - projected[:, 1] * scale, projected[:, 2]))
            normal = np.cross(world[1] - world[0], world[2] - world[0])
            normal /= max(1e-8, np.linalg.norm(normal))
            shade = .78 + .22 * abs(float(normal @ light))
            for tri in ((0, 1, 2), (0, 2, 3)):
                p, uv = screen[list(tri)], quad[list(tri), 3:]
                x0, y0 = np.maximum((0, 0), np.floor(p[:, :2].min(0))).astype(int)
                x1, y1 = np.minimum((width - 1, height - 1), np.ceil(p[:, :2].max(0))).astype(int)
                if x1 < x0 or y1 < y0:
                    continue
                xx, yy = np.meshgrid(np.arange(x0, x1 + 1) + .5, np.arange(y0, y1 + 1) + .5)
                den = ((p[1, 1] - p[2, 1]) * (p[0, 0] - p[2, 0])
                       + (p[2, 0] - p[1, 0]) * (p[0, 1] - p[2, 1]))
                if abs(den) < 1e-9:
                    continue
                a = ((p[1, 1] - p[2, 1]) * (xx - p[2, 0]) + (p[2, 0] - p[1, 0]) * (yy - p[2, 1])) / den
                b = ((p[2, 1] - p[0, 1]) * (xx - p[2, 0]) + (p[0, 0] - p[2, 0]) * (yy - p[2, 1])) / den
                c = 1 - a - b
                depth = a * p[0, 2] + b * p[1, 2] + c * p[2, 2]
                current = depth_buffer[y0:y1 + 1, x0:x1 + 1]
                mask = (a >= -1e-6) & (b >= -1e-6) & (c >= -1e-6) & (depth < current)
                u = np.clip(((a * uv[0, 0] + b * uv[1, 0] + c * uv[2, 0]) * atlas.shape[1]).astype(int), 0, atlas.shape[1] - 1)
                v = np.clip(((a * uv[0, 1] + b * uv[1, 1] + c * uv[2, 1]) * atlas.shape[0]).astype(int), 0, atlas.shape[0] - 1)
                colors = atlas[v, u].copy()
                colors[:, :, :3] = (colors[:, :, :3].astype(float) * shade).astype(np.uint8)
                mask &= colors[:, :, 3] >= 26
                canvas[y0:y1 + 1, x0:x1 + 1][mask] = colors[mask]
                current[mask] = depth[mask]
    return Image.fromarray(canvas)


def compose(mesh, path, angles, title, scale=17, target=(3, 1, 0)):
    image = Image.new('RGB', (1600, 1000), '#252331')
    draw = ImageDraw.Draw(image)
    font_path = Path('C:/Windows/Fonts/msyh.ttc')
    font = ImageFont.truetype(str(font_path), 30) if font_path.exists() else ImageFont.load_default()
    small = ImageFont.truetype(str(font_path), 18) if font_path.exists() else ImageFont.load_default()
    draw.text((44, 22), title, font=font, fill='#f9e5d2')
    draw.text((46, 70), '万圣残月统一形象 · 原图玩家皮肤 · 低帽檐与手持南瓜', font=small, fill='#d5a3b3')
    for yaw, x in zip(angles, (20, 820)):
        view = render(mesh, yaw, scale=scale, target=target)
        image.paste(view, (x, 130), view)
    draw.text((44, 954), 'Minecraft 实际烘焙顶点与 UV 的正交预览（非游戏截图）', font=small, fill='#bfaeb8')
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)
    print(path.relative_to(ROOT))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--texture', action='store_true')
    parser.add_argument('--preview', action='store_true')
    args = parser.parse_args()
    if args.texture or not args.preview:
        accessory_texture().save(TEXTURE)
        print(TEXTURE.relative_to(ROOT))
    if args.preview:
        if not MESH.exists():
            parser.error('请先运行 HalloweenWitchModelTest 导出真实模型顶点')
        mesh = json.loads(MESH.read_text(encoding='utf-8'))
        compose(mesh, PREVIEW, (-25, 55), '万圣残月 / 玩家模型 + 魔法配件')
        compose(mesh, PREVIEW.with_name('halloween-canyue-back.png'), (155, -110), '万圣残月 / 背面与另一侧')
        compose(mesh, PREVIEW.with_name('halloween-canyue-face.png'), (-5, 30), '万圣残月 / 原图皮肤头部近景', scale=33, target=(0, 7, 0))
