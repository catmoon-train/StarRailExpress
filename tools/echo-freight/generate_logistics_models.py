#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate echo logistics block models, item models, blockstates, loot tables
and original opaque pixel textures for namespace noellesroles.

North is the base orientation: the machine face looks toward negative Z.
Blockstate y-rotations: east=90, south=180, west=270 (vanilla horizontal facing).

Textures are 32x32 opaque atlases shared as echo_logistics_* so they do not
collide with echo_machine_* assets owned by other agents.
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
SIZE = 32

# Retro industrial palette: deep brown / navy steel, brass rivets, cyan energy.
PAL = {
    "brown_deep": (42, 28, 18, 255),
    "brown": (74, 48, 28, 255),
    "brown_hi": (112, 74, 42, 255),
    "navy": (28, 38, 58, 255),
    "navy_hi": (48, 66, 96, 255),
    "steel": (58, 64, 74, 255),
    "steel_hi": (118, 126, 138, 255),
    "steel_dk": (32, 36, 42, 255),
    "brass": (184, 138, 52, 255),
    "brass_hi": (232, 196, 96, 255),
    "brass_dk": (112, 78, 28, 255),
    "gold": (214, 168, 62, 255),
    "gold_hi": (248, 220, 128, 255),
    "cyan": (72, 196, 210, 255),
    "cyan_hi": (168, 244, 248, 255),
    "cyan_dk": (24, 92, 112, 255),
    "violet": (122, 64, 176, 255),
    "violet_hi": (196, 140, 236, 255),
    "violet_dk": (48, 22, 72, 255),
    "ink": (16, 14, 18, 255),
    "cream": (228, 214, 186, 255),
    "rust": (132, 62, 36, 255),
}

IDS = [
    "echo_beacon",
    "echo_core_beacon",
    "echo_crystal_extractor",
    "echo_key_altar",
    "echo_black_crystal_extractor",
    "echo_black_dropoff",
]


def clamp(v: int) -> int:
    return max(0, min(255, v))


def shade(c, d: int):
    return tuple(clamp(x + d) if i < 3 else x for i, x in enumerate(c))


def new_canvas() -> Image.Image:
    return Image.new("RGBA", (SIZE, SIZE), PAL["ink"])


def noise(img: Image.Image, seed: int, amp: int = 8) -> None:
    px = img.load()
    w, h = img.size
    n = seed & 0xFFFF
    for y in range(h):
        for x in range(w):
            n = (n * 1103515245 + 12345) & 0x7FFFFFFF
            d = ((n >> 16) % (amp * 2 + 1)) - amp
            r, g, b, a = px[x, y]
            px[x, y] = (clamp(r + d), clamp(g + d), clamp(b + d), a)


def rivet_grid(draw: ImageDraw.ImageDraw, color, step: int = 8, inset: int = 2) -> None:
    for y in range(inset, SIZE - inset, step):
        for x in range(inset, SIZE - inset, step):
            draw.point((x, y), fill=color)
            if x + 1 < SIZE:
                draw.point((x + 1, y), fill=shade(color, 28))


def plate(name: str, base, hi, rivet, seed: int) -> None:
    img = Image.new("RGBA", (SIZE, SIZE), base)
    draw = ImageDraw.Draw(img)
    draw.rectangle((0, 0, SIZE - 1, 1), fill=hi)
    draw.rectangle((0, 0, 1, SIZE - 1), fill=hi)
    draw.rectangle((0, SIZE - 2, SIZE - 1, SIZE - 1), fill=shade(base, -28))
    draw.rectangle((SIZE - 2, 0, SIZE - 1, SIZE - 1), fill=shade(base, -28))
    for i in range(0, SIZE, 8):
        draw.line((i, 2, i, SIZE - 3), fill=shade(base, -12))
    rivet_grid(draw, rivet, 8, 3)
    noise(img, seed, 6)
    img.save(TEX / f"{name}.png")


def draw_textures() -> None:
    TEX.mkdir(parents=True, exist_ok=True)
    plate("echo_logistics_hull", PAL["navy"], PAL["navy_hi"], PAL["brass"], 11)
    plate("echo_logistics_frame", PAL["steel_dk"], PAL["steel_hi"], PAL["brass_hi"], 29)
    plate("echo_logistics_brass", PAL["brass_dk"], PAL["brass_hi"], PAL["gold_hi"], 47)
    plate("echo_logistics_gold", PAL["gold"], PAL["gold_hi"], PAL["cream"], 63)
    plate("echo_logistics_brown", PAL["brown_deep"], PAL["brown_hi"], PAL["brass"], 81)

    energy = new_canvas()
    d = ImageDraw.Draw(energy)
    d.rectangle((0, 0, SIZE - 1, SIZE - 1), fill=PAL["cyan_dk"])
    d.ellipse((2, 2, 29, 29), outline=PAL["cyan"], width=1)
    d.ellipse((7, 7, 24, 24), outline=PAL["cyan_hi"], width=1)
    d.ellipse((12, 12, 19, 19), fill=PAL["cyan_hi"])
    d.line((15, 1, 15, 30), fill=PAL["cyan"])
    d.line((1, 15, 30, 15), fill=PAL["cyan"])
    d.rectangle((0, 0, SIZE - 1, SIZE - 1), outline=PAL["navy"])
    noise(energy, 101, 5)
    energy.save(TEX / "echo_logistics_energy.png")

    void = new_canvas()
    d = ImageDraw.Draw(void)
    d.rectangle((0, 0, SIZE - 1, SIZE - 1), fill=PAL["violet_dk"])
    d.polygon([(16, 3), (28, 16), (16, 28), (4, 16)], outline=PAL["violet_hi"])
    d.polygon([(16, 8), (23, 16), (16, 23), (9, 16)], fill=PAL["violet"])
    d.point((16, 16), fill=PAL["violet_hi"])
    for i in range(4, 28, 4):
        d.point((i, 4), fill=PAL["violet"])
        d.point((i, 27), fill=PAL["violet"])
    d.rectangle((0, 0, SIZE - 1, SIZE - 1), outline=PAL["ink"])
    noise(void, 131, 6)
    void.save(TEX / "echo_logistics_void.png")

    mouth = new_canvas()
    d = ImageDraw.Draw(mouth)
    d.rectangle((0, 0, SIZE - 1, SIZE - 1), fill=PAL["brown"])
    d.rectangle((4, 4, 27, 27), fill=PAL["ink"])
    d.rectangle((8, 8, 23, 23), fill=(8, 6, 10, 255))
    d.rectangle((12, 12, 19, 19), fill=PAL["cyan_dk"])
    d.rectangle((0, 0, SIZE - 1, SIZE - 1), outline=PAL["brass"])
    for x in (3, 28):
        for y in (3, 28):
            d.point((x, y), fill=PAL["brass_hi"])
    noise(mouth, 157, 4)
    mouth.save(TEX / "echo_logistics_mouth.png")

    slot = new_canvas()
    d = ImageDraw.Draw(slot)
    d.rectangle((0, 0, SIZE - 1, SIZE - 1), fill=PAL["brass_dk"])
    d.rectangle((2, 2, 29, 29), outline=PAL["brass_hi"])
    d.ellipse((8, 4, 23, 18), fill=PAL["ink"], outline=PAL["gold"])
    d.rectangle((13, 14, 18, 27), fill=PAL["ink"])
    d.rectangle((18, 18, 23, 21), fill=PAL["ink"])
    d.rectangle((18, 23, 22, 26), fill=PAL["ink"])
    d.point((15, 10), fill=PAL["cyan_hi"])
    noise(slot, 173, 4)
    slot.save(TEX / "echo_logistics_slot.png")

    crystal = new_canvas()
    d = ImageDraw.Draw(crystal)
    d.rectangle((0, 0, SIZE - 1, SIZE - 1), fill=PAL["cyan"])
    for y in range(SIZE):
        tone = PAL["cyan_hi"] if y % 4 < 2 else PAL["cyan_dk"]
        d.line((4, y, 27, y), fill=tone)
    d.polygon([(16, 2), (26, 16), (16, 29), (6, 16)], outline=PAL["cream"])
    d.rectangle((0, 0, SIZE - 1, SIZE - 1), outline=PAL["navy"])
    noise(crystal, 191, 4)
    crystal.save(TEX / "echo_logistics_crystal.png")

    black = new_canvas()
    d = ImageDraw.Draw(black)
    d.rectangle((0, 0, SIZE - 1, SIZE - 1), fill=PAL["violet"])
    for y in range(SIZE):
        tone = PAL["violet_hi"] if (y // 2) % 2 == 0 else PAL["violet_dk"]
        d.line((3, y, 28, y), fill=tone)
    d.polygon([(16, 2), (27, 16), (16, 29), (5, 16)], outline=PAL["gold_hi"])
    d.rectangle((0, 0, SIZE - 1, SIZE - 1), outline=PAL["ink"])
    noise(black, 211, 5)
    black.save(TEX / "echo_logistics_black_crystal.png")

    pipe = new_canvas()
    d = ImageDraw.Draw(pipe)
    d.rectangle((0, 0, SIZE - 1, SIZE - 1), fill=PAL["steel"])
    d.rectangle((10, 0, 21, SIZE - 1), fill=PAL["steel_hi"])
    d.rectangle((14, 0, 17, SIZE - 1), fill=PAL["cyan_dk"])
    for y in range(1, SIZE, 6):
        d.rectangle((8, y, 23, y + 1), fill=PAL["brass"])
    noise(pipe, 233, 4)
    pipe.save(TEX / "echo_logistics_pipe.png")


def face(texture: str, uv) -> dict:
    return {"uv": [round(v, 3) for v in uv], "texture": f"#{texture}"}


def box(a, b, faces: dict) -> dict:
    return {
        "from": [round(v, 3) for v in a],
        "to": [round(v, 3) for v in b],
        "faces": faces,
    }


def cube(a, b, tex: str, uv_mode: str = "plate") -> dict:
    """Axis-aligned box inside the 0-16 cell. Plate UVs track face position on
    the shared atlas; full UVs show the whole motif (lens, crystal, slot, mouth)."""
    x0, y0, z0 = a
    x1, y1, z1 = b

    def span(u0, v0, u1, v1):
        return [u0, v0, u1, v1]

    if uv_mode == "full":
        uv = span(0, 0, 16, 16)
        faces = {k: face(tex, uv) for k in ("north", "south", "east", "west", "up", "down")}
    else:
        faces = {
            "north": face(tex, span(x0, 16 - y1, x1, 16 - y0)),
            "south": face(tex, span(16 - x1, 16 - y1, 16 - x0, 16 - y0)),
            "east": face(tex, span(16 - z1, 16 - y1, 16 - z0, 16 - y0)),
            "west": face(tex, span(z0, 16 - y1, z1, 16 - y0)),
            "up": face(tex, span(x0, z0, x1, z1)),
            "down": face(tex, span(x0, 16 - z1, x1, 16 - z0)),
        }
    return box(a, b, faces)


def display() -> dict:
    return {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
        "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
        "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 2, 0], "scale": [0.40, 0.40, 0.40]},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 2, 0], "scale": [0.40, 0.40, 0.40]},
    }


def model(elements: list, particle: str) -> dict:
    textures = {
        "particle": f"{NS}:block/{particle}",
        "hull": f"{NS}:block/echo_logistics_hull",
        "frame": f"{NS}:block/echo_logistics_frame",
        "brass": f"{NS}:block/echo_logistics_brass",
        "gold": f"{NS}:block/echo_logistics_gold",
        "brown": f"{NS}:block/echo_logistics_brown",
        "energy": f"{NS}:block/echo_logistics_energy",
        "void": f"{NS}:block/echo_logistics_void",
        "mouth": f"{NS}:block/echo_logistics_mouth",
        "slot": f"{NS}:block/echo_logistics_slot",
        "crystal": f"{NS}:block/echo_logistics_crystal",
        "black": f"{NS}:block/echo_logistics_black_crystal",
        "pipe": f"{NS}:block/echo_logistics_pipe",
    }
    return {
        "credit": "echo logistics original block model",
        "textures": textures,
        "elements": elements,
        "display": display(),
    }


def base_plinth(elements, hull="hull", frame="frame", brass="brass") -> None:
    elements.append(cube([1, 0, 1], [15, 2, 15], hull))
    elements.append(cube([0, 2, 0], [16, 3.2, 16], frame))
    for x0, z0 in ((0, 0), (14, 0), (0, 14), (14, 14)):
        elements.append(cube([x0, 3.2, z0], [x0 + 2, 6.2, z0 + 2], brass))
        elements.append(cube([x0 + 0.35, 6.2, z0 + 0.35], [x0 + 1.65, 6.9, z0 + 1.65], brass, "full"))


def side_pipes(elements, y0, y1, tex="pipe") -> None:
    elements.append(cube([0.3, y0, 6], [1.3, y1, 10], tex))
    elements.append(cube([14.7, y0, 6], [15.7, y1, 10], tex))


def beacon_elements(core_tex: str, trim: str, antenna: bool) -> list:
    e = []
    base_plinth(e)
    e.append(cube([3, 3.2, 4], [13, 11, 13], "hull"))
    e.append(cube([2.4, 10.6, 3.4], [13.6, 12.1, 13.6], trim))
    e.append(cube([4.4, 4.4, 2.1], [11.6, 9.6, 4.15], core_tex, "full"))
    e.append(cube([3.8, 3.9, 2.5], [4.7, 10.1, 3.5], "frame"))
    e.append(cube([11.3, 3.9, 2.5], [12.2, 10.1, 3.5], "frame"))
    e.append(cube([4.7, 3.9, 2.45], [11.3, 4.7, 3.3], "brass"))
    e.append(cube([4.7, 9.3, 2.45], [11.3, 10.1, 3.3], "brass"))
    side_pipes(e, 5, 9.2)
    e.append(cube([6, 5, 13], [10, 9, 15.3], "pipe"))
    e.append(cube([7, 12.1, 7], [9, 14.4, 9], trim))
    if antenna:
        e.append(cube([7.35, 14.4, 7.35], [8.65, 15.7, 8.65], "gold", "full"))
        e.append(cube([6.1, 15.15, 7.65], [9.9, 15.7, 8.35], "gold"))
    else:
        e.append(cube([6.4, 14.4, 6.4], [9.6, 15.6, 9.6], core_tex, "full"))
    return e


def extractor_elements(crystal_tex: str, accent: str) -> list:
    e = []
    base_plinth(e, hull="brown", frame="frame", brass="brass")
    e.append(cube([1.4, 3.2, 3], [4.6, 12.2, 13], "frame"))
    e.append(cube([11.4, 3.2, 3], [14.6, 12.2, 13], "frame"))
    e.append(cube([4.6, 10.3, 3], [11.4, 12.2, 13], "hull"))
    e.append(cube([4.6, 3.2, 10], [11.4, 10.3, 13], "hull"))
    e.append(cube([6.1, 3.4, 3.1], [9.9, 10.5, 7.5], crystal_tex, "full"))
    e.append(cube([4.6, 6.0, 4.3], [5.95, 7.5, 6.3], "pipe"))
    e.append(cube([10.05, 6.0, 4.3], [11.4, 7.5, 6.3], "pipe"))
    e.append(cube([4.1, 3.2, 2.2], [11.9, 4.0, 4.4], accent))
    e.append(cube([2.8, 12.2, 3.8], [13.2, 13.4, 6.0], accent))
    e.append(cube([7.1, 12.2, 6.0], [8.9, 13.4, 12.2], "pipe"))
    e.append(cube([6.4, 12.2, 10.8], [9.6, 15.4, 14.1], "frame"))
    e.append(cube([7.1, 15.4, 11.6], [8.9, 15.9, 13.3], accent, "full"))
    return e


def altar_elements() -> list:
    e = []
    base_plinth(e, hull="brown", frame="brass", brass="gold")
    e.append(cube([2, 3.2, 3], [14, 7.2, 14], "brown"))
    e.append(cube([3, 7.2, 4], [13, 10.2, 13], "hull"))
    e.append(cube([4, 10.2, 5], [12, 12.5, 12], "brass"))
    e.append(cube([5, 4.2, 1.4], [11, 9.7, 3.05], "slot", "full"))
    e.append(cube([4.2, 3.7, 2.0], [5.1, 10.2, 3.3], "frame"))
    e.append(cube([10.9, 3.7, 2.0], [11.8, 10.2, 3.3], "frame"))
    e.append(cube([1.1, 8.2, 3.1], [3.3, 9.7, 5.3], "gold"))
    e.append(cube([1.5, 9.7, 3.5], [2.8, 11.3, 4.8], "gold"))
    e.append(cube([12.7, 8.2, 3.1], [14.9, 9.7, 5.3], "gold"))
    e.append(cube([13.2, 9.7, 3.5], [14.5, 11.3, 4.8], "gold"))
    e.append(cube([6, 7.2, 12.1], [10, 11.6, 14.7], "frame"))
    e.append(cube([7, 11.6, 12.9], [9, 13.3, 14.3], "energy", "full"))
    e.append(cube([6.4, 12.5, 5.8], [9.6, 13.2, 11.2], "slot", "full"))
    e.append(cube([7.1, 13.2, 7.1], [8.9, 14.4, 8.3], "gold"))
    return e


def dropoff_elements() -> list:
    e = []
    base_plinth(e)
    e.append(cube([2, 3.2, 4], [14, 12.2, 14], "hull"))
    e.append(cube([3, 4, 1.3], [13, 11.1, 4.15], "mouth", "full"))
    e.append(cube([2.3, 3.4, 1.7], [3.2, 11.7, 3.2], "brass"))
    e.append(cube([12.8, 3.4, 1.7], [13.7, 11.7, 3.2], "brass"))
    e.append(cube([3.2, 10.8, 1.5], [12.8, 11.9, 2.8], "brass"))
    e.append(cube([3.2, 3.3, 1.5], [12.8, 4.3, 2.8], "brass"))
    e.append(cube([4.4, 5.1, 4.15], [11.6, 9.9, 6.3], "void", "full"))
    e.append(cube([3, 12.2, 4], [13, 13.5, 13], "frame"))
    e.append(cube([5, 13.5, 6], [11, 14.7, 12], "brown"))
    e.append(cube([6.4, 14.7, 7.4], [9.6, 15.7, 10.6], "mouth", "full"))
    side_pipes(e, 6, 11.2)
    e.append(cube([6, 6, 14], [10, 10, 15.4], "brass"))
    e.append(cube([7.1, 7.1, 15.2], [8.9, 8.9, 15.85], "energy", "full"))
    return e


def build_models() -> dict:
    beacon = beacon_elements("energy", "brass", antenna=True)
    core = beacon_elements("gold", "gold", antenna=False)
    core.extend([
        cube([2.2, 6.15, 3.55], [13.8, 7.15, 13.7], "gold"),
        cube([2.2, 8.55, 3.55], [13.8, 9.55, 13.7], "gold"),
        cube([3.1, 3.25, 3.15], [12.9, 4.05, 4.05], "gold"),
    ])
    return {
        "echo_beacon": model(beacon, "echo_logistics_energy"),
        "echo_core_beacon": model(core, "echo_logistics_gold"),
        "echo_crystal_extractor": model(extractor_elements("crystal", "brass"), "echo_logistics_crystal"),
        "echo_key_altar": model(altar_elements(), "echo_logistics_slot"),
        "echo_black_crystal_extractor": model(extractor_elements("black", "void"), "echo_logistics_black_crystal"),
        "echo_black_dropoff": model(dropoff_elements(), "echo_logistics_mouth"),
    }


def write_json(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def blockstate(bid: str) -> dict:
    model_id = f"{NS}:block/{bid}"
    return {
        "variants": {
            "facing=north": {"model": model_id},
            "facing=east": {"model": model_id, "y": 90},
            "facing=south": {"model": model_id, "y": 180},
            "facing=west": {"model": model_id, "y": 270},
        }
    }


def item_model(bid: str) -> dict:
    return {"parent": f"{NS}:block/{bid}"}


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


def main() -> None:
    raise SystemExit(
        "refusing to run: generate_multiblock_models.py owns the echo machine "
        "blockstates, part models and loot tables. This writer still emits the "
        "old single-cell variants and would overwrite them."
    )
    draw_textures()
    for bid, data in build_models().items():
        write_json(BLOCK_MODELS / f"{bid}.json", data)
        write_json(ITEM_MODELS / f"{bid}.json", item_model(bid))
        write_json(BLOCKSTATES / f"{bid}.json", blockstate(bid))
        write_json(LOOT / f"{bid}.json", loot_table(bid))
        print(f"{bid}: {len(data['elements'])} elements")


if __name__ == "__main__":
    main()
