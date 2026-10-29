#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate the echo-freight terrain blocks: hide bush and pallet.

Both blocks are single-cell, block-entity free and fully opaque-textured, matching
the existing echo machine pipeline (blockstates + block models + item models +
loot tables + original 32x32 textures). North is the base orientation.

Pallet has three visual states driven by the blockstate:
  down=false, broken=false -> upright board you can walk through
  down=true,  broken=false -> lowered board filling the cell
  down=false, broken=true  -> snapped board, leaning and shorter
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

# Leaf greens for the bush, warm pine for the pallet.
BUSH = {
    "deep": (26, 52, 30, 255),
    "mid": (44, 88, 48, 255),
    "hi": (74, 132, 72, 255),
    "tip": (108, 168, 92, 255),
    "dry": (92, 104, 52, 255),
}
WOOD = {
    "deep": (58, 40, 24, 255),
    "mid": (96, 68, 40, 255),
    "hi": (140, 102, 62, 255),
    "grain": (72, 50, 30, 255),
    "nail": (168, 168, 176, 255),
}


def write_json(path: Path, payload: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


# --------------------------------------------------------------------------- textures


def bush_texture() -> Image.Image:
    """Opaque leaf cluster: layered blobs so it reads as a bush from any side."""
    img = Image.new("RGBA", (SIZE, SIZE), BUSH["deep"])
    d = ImageDraw.Draw(img)
    # Deterministic leaf clumps; no randomness so regenerating gives identical output.
    clumps = [
        (4, 6, 9, 10, BUSH["mid"]),
        (18, 4, 10, 11, BUSH["mid"]),
        (10, 14, 12, 10, BUSH["mid"]),
        (2, 18, 10, 9, BUSH["deep"]),
        (20, 18, 9, 9, BUSH["deep"]),
        (8, 4, 7, 7, BUSH["hi"]),
        (20, 8, 7, 7, BUSH["hi"]),
        (12, 18, 8, 7, BUSH["hi"]),
        (14, 8, 5, 5, BUSH["tip"]),
        (5, 13, 4, 4, BUSH["tip"]),
        (23, 15, 4, 4, BUSH["tip"]),
    ]
    for x, y, w, h, color in clumps:
        d.ellipse([x, y, x + w, y + h], fill=color)
    # A few dry twigs to break up the silhouette.
    for x0, y0, x1, y1 in ((6, 26, 10, 20), (22, 24, 18, 18), (15, 27, 15, 21)):
        d.line([x0, y0, x1, y1], fill=BUSH["dry"], width=1)
    return img


def pallet_texture() -> Image.Image:
    """Plank board: horizontal planks with grain and corner nails."""
    img = Image.new("RGBA", (SIZE, SIZE), WOOD["mid"])
    d = ImageDraw.Draw(img)
    plank_h = 8
    for index, y in enumerate(range(0, SIZE, plank_h)):
        shade = WOOD["hi"] if index % 2 == 0 else WOOD["mid"]
        d.rectangle([0, y, SIZE - 1, y + plank_h - 1], fill=shade)
        d.line([0, y, SIZE - 1, y], fill=WOOD["deep"])
        # grain streaks
        for gx in range(2, SIZE - 2, 6):
            d.line([gx, y + 2, gx + 3, y + 2], fill=WOOD["grain"])
    # vertical bracing
    for x in (3, SIZE - 6):
        d.rectangle([x, 0, x + 2, SIZE - 1], fill=WOOD["deep"])
        for y in (2, SIZE - 4):
            d.point((x + 1, y), fill=WOOD["nail"])
    return img


def palette_texture() -> Image.Image:
    """Single shared palette: 1px column used as tint helper is not needed,
    so we reuse the plank texture as the particle/base map."""
    return pallet_texture()


# --------------------------------------------------------------------------- models

# 16x16 UV helper: element coordinates in block space, uv in texture space.
def cube(origin, size, faces, rotation=None):
    x, y, z = origin
    w, h, d = size
    element = {
        "from": [x, y, z],
        "to": [x + w, y + h, z + d],
        "faces": faces,
    }
    if rotation is not None:
        element["rotation"] = rotation
    return element


def all_faces(texture, cull=None):
    faces = {}
    for name in ("north", "east", "south", "west", "up", "down"):
        faces[name] = {"texture": texture}
        if cull and name in cull:
            faces[name]["cullface"] = name
    return faces


def bush_model() -> dict:
    tex = f"{NS}:block/echo_hide_bush"
    # Two crossing leaf planes plus a low core so the cell reads as filled from every angle.
    return {
        "textures": {"particle": tex, "leaf": tex},
        "elements": [
            cube([1, 0, 7], [14, 15, 2], {
                "north": {"texture": "#leaf"}, "south": {"texture": "#leaf"},
                "east": {"texture": "#leaf"}, "west": {"texture": "#leaf"},
                "up": {"texture": "#leaf"},
            }),
            cube([7, 0, 1], [2, 15, 14], {
                "north": {"texture": "#leaf"}, "south": {"texture": "#leaf"},
                "east": {"texture": "#leaf"}, "west": {"texture": "#leaf"},
                "up": {"texture": "#leaf"},
            }),
            cube([3, 0, 3], [10, 6, 10], {
                "north": {"texture": "#leaf"}, "south": {"texture": "#leaf"},
                "east": {"texture": "#leaf"}, "west": {"texture": "#leaf"},
                "up": {"texture": "#leaf"},
            }),
        ],
    }


def pallet_model(upright: bool) -> dict:
    tex = f"{NS}:block/echo_pallet"
    if upright:
        # Thin standing board: walkable, visually obvious.
        elements = [
            cube([2, 0, 6], [12, 16, 4], {
                "north": {"texture": "#board"}, "south": {"texture": "#board"},
                "east": {"texture": "#board"}, "west": {"texture": "#board"},
                "up": {"texture": "#board"}, "down": {"texture": "#board"},
            }),
            cube([2, 0, 4], [3, 2, 8], all_faces("#brace")),
            cube([11, 0, 4], [3, 2, 8], all_faces("#brace")),
        ]
    else:
        # Lowered board: fills the cell, blocks passage.
        elements = [
            cube([1, 0, 1], [14, 15, 14], {
                "north": {"texture": "#board"}, "south": {"texture": "#board"},
                "east": {"texture": "#board"}, "west": {"texture": "#board"},
                "up": {"texture": "#board"}, "down": {"texture": "#board"},
            }),
        ]
    return {"textures": {"particle": tex, "board": tex, "brace": tex}, "elements": elements}


def pallet_broken_model() -> dict:
    tex = f"{NS}:block/echo_pallet"
    # Snapped board: leaning, shorter, obviously unusable.
    return {
        "textures": {"particle": tex, "board": tex, "brace": tex},
        "elements": [
            cube([3, 0, 8], [11, 12, 3], {
                "north": {"texture": "#board"}, "south": {"texture": "#board"},
                "east": {"texture": "#board"}, "west": {"texture": "#board"},
                "up": {"texture": "#board"}, "down": {"texture": "#board"},
            }, rotation={"origin": [3, 0, 9], "axis": "z", "angle": -18, "rescale": True}),
        ],
    }


def item_model(texture: str) -> dict:
    return {"parent": "minecraft:item/generated", "textures": {"layer0": texture}}


def loot_table(block_id: str) -> dict:
    return {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "bonus_rolls": 0,
            "entries": [{"type": "minecraft:item", "name": f"{NS}:{block_id}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    }


def main() -> None:
    TEX.mkdir(parents=True, exist_ok=True)

    # --- textures
    bush_texture().save(TEX / "echo_hide_bush.png")
    pallet_texture().save(TEX / "echo_pallet.png")

    # --- bush
    write_json(BLOCKSTATES / "echo_hide_bush.json", {
        "variants": {
            "facing=north": {"model": f"{NS}:block/echo_hide_bush"},
            "facing=east": {"model": f"{NS}:block/echo_hide_bush", "y": 90},
            "facing=south": {"model": f"{NS}:block/echo_hide_bush", "y": 180},
            "facing=west": {"model": f"{NS}:block/echo_hide_bush", "y": 270},
        }
    })
    write_json(BLOCK_MODELS / "echo_hide_bush.json", bush_model())
    write_json(ITEM_MODELS / "echo_hide_bush.json", item_model(f"{NS}:block/echo_hide_bush"))
    write_json(LOOT / "echo_hide_bush.json", loot_table("echo_hide_bush"))

    # --- pallet: upright / lowered / broken, each rotated for four facings
    write_json(BLOCK_MODELS / "echo_pallet_up.json", pallet_model(True))
    write_json(BLOCK_MODELS / "echo_pallet_down.json", pallet_model(False))
    write_json(BLOCK_MODELS / "echo_pallet_broken.json", pallet_broken_model())

    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for down in (False, True):
            for broken in (False, True):
                if down and broken:
                    # 被拆时一定是立着且损坏，这个组合不会出现，但仍给出模型避免缺省。
                    model = f"{NS}:block/echo_pallet_broken"
                elif down:
                    model = f"{NS}:block/echo_pallet_down"
                elif broken:
                    model = f"{NS}:block/echo_pallet_broken"
                else:
                    model = f"{NS}:block/echo_pallet_up"
                entry = {"model": model}
                if y:
                    entry["y"] = y
                variants[f"facing={facing},down={'true' if down else 'false'},"
                         f"broken={'true' if broken else 'false'}"] = entry
    write_json(BLOCKSTATES / "echo_pallet.json", {"variants": variants})
    write_json(ITEM_MODELS / "echo_pallet.json", item_model(f"{NS}:block/echo_pallet_up"))
    write_json(LOOT / "echo_pallet.json", loot_table("echo_pallet"))

    print("echo terrain blocks generated:")
    for path in (
        BLOCKSTATES / "echo_hide_bush.json", BLOCKSTATES / "echo_pallet.json",
        BLOCK_MODELS / "echo_hide_bush.json", BLOCK_MODELS / "echo_pallet_up.json",
        BLOCK_MODELS / "echo_pallet_down.json", BLOCK_MODELS / "echo_pallet_broken.json",
        TEX / "echo_hide_bush.png", TEX / "echo_pallet.png",
    ):
        print("  ", path.relative_to(ROOT))


if __name__ == "__main__":
    main()
