#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate six distinct echo-freight terminal block models and original textures.

North-facing models put the interactive face on -Z. Blockstates rotate that
model: east y=90, south y=180, west y=270. All geometry stays inside [0, 16].
"""

from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw

NS = "noellesroles"
ROOT = Path(__file__).resolve().parents[2]
ASSET = ROOT / "src/main/resources/assets" / NS
DATA = ROOT / "src/main/resources/data" / NS
TEX = 64

IDS = (
    "echo_supply_crate",
    "echo_black_market",
    "echo_core_console",
    "echo_free_exit",
    "echo_evacuation_console",
    "echo_broker_exit",
)

# Shared atlas names. Per-block accent plates are also original, not vanilla.
SHARED = (
    "echo_terminal_steel",
    "echo_terminal_brass",
    "echo_terminal_dark",
    "echo_terminal_panel",
    "echo_terminal_trim",
)


def clamp(v: float) -> float:
    return round(max(0.0, min(16.0, v)), 4)


def box(a, b, tex, uv, *, shade=True, cull=(), rot=None):
    """Axis-aligned cuboid. uv is a dict of face -> [u1, v1, u2, v2] in 0..16."""
    faces = {}
    names = ("north", "south", "east", "west", "up", "down")
    for name in names:
        if name not in uv:
            continue
        u1, v1, u2, v2 = uv[name]
        face = {
            "uv": [round(u1, 4), round(v1, 4), round(u2, 4), round(v2, 4)],
            "texture": tex,
        }
        if name in cull:
            face["cullface"] = name
        faces[name] = face
    el = {
        "from": [clamp(a[0]), clamp(a[1]), clamp(a[2])],
        "to": [clamp(b[0]), clamp(b[1]), clamp(b[2])],
        "faces": faces,
    }
    if not shade:
        el["shade"] = False
    if rot is not None:
        el["rotation"] = rot
    return el


def tile_uv(u, v, du, dv):
    """Pack a face into the 16x16 UV space of a 64px atlas (4 texels per unit)."""
    u = u % 16
    v = v % 16
    du = max(0.25, min(du, 15.75))
    dv = max(0.25, min(dv, 15.75))
    if u + du > 16:
        u = max(0.0, 16 - du)
    if v + dv > 16:
        v = max(0.0, 16 - dv)
    return [round(u, 4), round(v, 4), round(u + du, 4), round(v + dv, 4)]


def slab_uv(u, v, dx, dy, dz):
    """Distinct in-range UV per face. dx/dy/dz size the region, they are not pixel coords."""
    return {
        "north": tile_uv(u, v, dx, dy),
        "south": tile_uv(u + 4, v + 1, dx, dy),
        "east": tile_uv(u + 1, v + 4, dz, dy),
        "west": tile_uv(u + 5, v + 5, dz, dy),
        "up": tile_uv(u + 2, v + 8, dx, dz),
        "down": tile_uv(u + 6, v + 9, dx, dz),
    }


DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_righthand": {
        "rotation": [75, 45, 0],
        "translation": [0, 2.5, 0],
        "scale": [0.375, 0.375, 0.375],
    },
    "thirdperson_lefthand": {
        "rotation": [75, 45, 0],
        "translation": [0, 2.5, 0],
        "scale": [0.375, 0.375, 0.375],
    },
    "firstperson_righthand": {
        "rotation": [0, 135, 0],
        "translation": [0, 2, 0],
        "scale": [0.4, 0.4, 0.4],
    },
    "firstperson_lefthand": {
        "rotation": [0, 135, 0],
        "translation": [0, 2, 0],
        "scale": [0.4, 0.4, 0.4],
    },
}


def sanitize_uv(elements):
    """Minecraft UV is [0, 16). Fold any hand-authored edge of 16 back inside."""
    for el in elements:
        for face in el["faces"].values():
            u1, v1, u2, v2 = face["uv"]
            u1, u2 = sorted((u1 % 16, u2 % 16))
            v1, v2 = sorted((v1 % 16, v2 % 16))
            if u2 - u1 < 0.25:
                u1, u2 = 0.0, 0.5
            if v2 - v1 < 0.25:
                v1, v2 = 0.0, 0.5
            face["uv"] = [round(u1, 4), round(v1, 4), round(min(u2, 15.99), 4), round(min(v2, 15.99), 4)]
    return elements


def model(particle, textures, elements):
    tex = {"particle": f"{NS}:block/{particle}"}
    tex.update({k: f"{NS}:block/{v}" for k, v in textures.items()})
    return {
        "credit": "echo-freight original terminal models",
        "texture_size": [TEX, TEX],
        "textures": tex,
        "elements": sanitize_uv(elements),
        "display": DISPLAY,
    }


def steel(u, v, dx, dy, dz):
    return slab_uv(u, v, dx, dy, dz)


# ---------------------------------------------------------------------------
# Geometry. Front is -Z (north). Gaps of 0.05 keep nested plates off coplanar
# faces. Rotations use element-local origins and angles in {-45,-22.5,0,22.5,45}.
# ---------------------------------------------------------------------------

def supply_crate():
    e = []
    # Hull, slightly inset so corner caps and latches can sit outside it.
    e.append(box((1, 0, 1), (15, 13, 15), "#steel", steel(0, 0, 8, 8, 8), cull=("down",)))
    # Lid
    e.append(box((0.6, 13, 0.6), (15.4, 14.6, 15.4), "#brass", steel(16, 0, 8, 2, 8)))
    # Raised lid lip
    e.append(box((2, 14.6, 2), (14, 15.4, 14), "#panel", steel(32, 0, 6, 1, 6)))
    # Corner caps
    for x0, z0 in ((0, 0), (14, 0), (0, 14), (14, 14)):
        e.append(box((x0, 0, z0), (x0 + 2, 14.2, z0 + 2), "#trim", steel(0, 16, 2, 8, 2)))
    # Side reinforcement straps
    e.append(box((0.4, 4, 2), (1, 6, 14), "#brass", steel(8, 16, 1, 2, 6)))
    e.append(box((15, 4, 2), (15.6, 6, 14), "#brass", steel(8, 16, 1, 2, 6)))
    e.append(box((2, 4, 15), (14, 6, 15.6), "#brass", steel(8, 20, 6, 2, 1)))
    # Front latch plate + hasp + staple
    e.append(box((5.5, 6.2, 0.35), (10.5, 11.2, 1.05), "#brass", steel(16, 16, 4, 4, 1)))
    e.append(box((7.1, 7.4, 0.05), (8.9, 10.2, 0.55), "#dark", steel(24, 16, 2, 3, 1)))
    e.append(box((6.6, 8.2, 0.0), (7.2, 9.4, 0.45), "#trim", steel(28, 16, 1, 2, 1)))
    # Recessed front stencil
    e.append(box((3, 1.4, 0.55), (13, 4.6, 1.05), "#accent", {
        "north": [0, 0, 10, 4],
        "south": [0, 4, 10, 8],
        "up": [0, 8, 10, 9],
        "down": [0, 9, 10, 10],
        "east": [10, 0, 11, 4],
        "west": [11, 0, 12, 4],
    }))
    # Carry handle: two posts + a bar rotated so it reads as a grip, not a plate.
    e.append(box((4.2, 14.7, 6.4), (5.2, 15.7, 9.6), "#brass", steel(32, 16, 1, 1, 2)))
    e.append(box((10.8, 14.7, 6.4), (11.8, 15.7, 9.6), "#brass", steel(32, 16, 1, 1, 2)))
    e.append(box(
        (4.5, 15.35, 7.2), (11.5, 16.0, 8.8), "#brass", steel(36, 16, 6, 1, 2),
        rot={"origin": [8, 15.35, 8], "axis": "z", "angle": 0},
    ))
    # Rivet row on the lid front edge
    for x in (3, 6.5, 9.5, 13):
        e.append(box((x - 0.35, 13.3, 0.35), (x + 0.35, 14.1, 0.85), "#trim", steel(44, 16, 1, 1, 1)))
    return e


def black_market():
    e = []
    # Heavy safe body
    e.append(box((1.2, 0, 2.2), (14.8, 14.2, 15.2), "#steel", steel(0, 0, 8, 8, 8), cull=("down",)))
    # Purple sealed door, proud of the body
    e.append(box((2.2, 1.2, 0.7), (13.8, 13.2, 2.3), "#accent", {
        "north": [0, 0, 12, 12],
        "south": [0, 12, 12, 15.5],
        "east": [12, 0, 14, 12],
        "west": [12, 0, 14, 12],
        "up": [0, 12, 12, 14],
        "down": [0, 14, 12, 15.75],
    }))
    # Brass door frame
    e.append(box((1.6, 0.6, 0.35), (14.4, 1.3, 2.5), "#brass", steel(16, 0, 8, 1, 2)))
    e.append(box((1.6, 13.1, 0.35), (14.4, 13.8, 2.5), "#brass", steel(16, 0, 8, 1, 2)))
    e.append(box((1.6, 1.2, 0.35), (2.3, 13.2, 2.5), "#brass", steel(16, 4, 1, 8, 2)))
    e.append(box((13.7, 1.2, 0.35), (14.4, 13.2, 2.5), "#brass", steel(16, 4, 1, 8, 2)))
    # Wheel handle: hub + four spokes, two of them tilted 45 degrees
    e.append(box((6.7, 5.8, 0.05), (9.3, 8.4, 0.7), "#brass", steel(24, 0, 2, 2, 1)))
    e.append(box((7.55, 3.3, 0.15), (8.45, 10.9, 0.55), "#trim", steel(28, 0, 1, 6, 1)))
    e.append(box((4.6, 6.55, 0.15), (11.4, 7.45, 0.55), "#trim", steel(28, 8, 6, 1, 1)))
    e.append(box(
        (5.3, 4.4, 0.2), (10.7, 9.8, 0.5), "#dark", steel(32, 0, 4, 4, 1),
        rot={"origin": [8, 7.1, 0.35], "axis": "z", "angle": 45},
    ))
    # Seal medallion, tilted the other way so it does not sit on the spokes
    e.append(box(
        (6.9, 6.0, 0.0), (9.1, 8.2, 0.28), "#accent", {
            "north": [12, 0, 16, 4], "south": [12, 4, 16, 8],
            "up": [12, 8, 16, 9], "down": [12, 9, 16, 10],
            "east": [14, 8, 15, 12], "west": [15, 8, 16, 12],
        },
        rot={"origin": [8, 7.1, 0.14], "axis": "z", "angle": -22.5},
    ))
    # Hinge barrels
    e.append(box((13.9, 3.2, 1.5), (15.3, 5.2, 3.1), "#brass", steel(40, 0, 1, 2, 1)))
    e.append(box((13.9, 9.0, 1.5), (15.3, 11.0, 3.1), "#brass", steel(40, 0, 1, 2, 1)))
    # Top vent + nameplate
    e.append(box((3, 14.25, 4), (13, 14.85, 12), "#dark", steel(40, 4, 8, 1, 6)))
    e.append(box((4, 11.6, 0.15), (12, 12.7, 0.7), "#panel", steel(40, 12, 6, 1, 1)))
    # Foot pads
    for x0, z0 in ((1.4, 2.4), (13.2, 2.4), (1.4, 13.6), (13.2, 13.6)):
        e.append(box((x0, 0, z0), (x0 + 1.4, 0.55, z0 + 1.4), "#trim", steel(48, 0, 1, 1, 1)))
    return e


def core_console():
    e = []
    # Pedestal
    e.append(box((1, 0, 2), (15, 4, 15), "#steel", steel(0, 0, 8, 3, 8), cull=("down",)))
    e.append(box((2, 4, 3.5), (14, 8, 14.5), "#dark", steel(0, 16, 8, 3, 7)))
    # Sloped hood. Pivot is on the rear top edge; -22.5 tips the face toward -Z.
    e.append(box(
        (1.5, 8, 4), (14.5, 10.2, 14.8), "#steel", steel(16, 0, 8, 2, 7),
        rot={"origin": [8, 8, 14.8], "axis": "x", "angle": -22.5},
    ))
    # Screen bezel + glass, same slope, stepped forward so they do not coplane.
    e.append(box(
        (3.2, 8.35, 3.15), (12.8, 9.85, 6.3), "#trim", steel(16, 12, 6, 1, 2),
        rot={"origin": [8, 8, 14.8], "axis": "x", "angle": -22.5},
    ))
    e.append(box(
        (3.7, 8.55, 2.7), (12.3, 9.65, 3.15), "#screen", {
            "north": [0, 0, 12, 4], "south": [0, 4, 12, 8],
            "up": [0, 8, 12, 9], "down": [0, 9, 12, 10],
            "east": [12, 0, 13, 4], "west": [13, 0, 14, 4],
        },
        shade=False,
        rot={"origin": [8, 8, 14.8], "axis": "x", "angle": -22.5},
    ))
    # Key slot
    e.append(box((6.6, 5.1, 1.55), (9.4, 7.3, 2.15), "#brass", steel(32, 0, 2, 2, 1)))
    e.append(box((7.55, 5.45, 1.15), (8.45, 6.95, 1.6), "#dark", steel(36, 0, 1, 2, 1)))
    # Indicator lamps
    e.append(box((3.2, 5.5, 1.7), (4.4, 6.7, 2.15), "#accent", steel(40, 0, 1, 1, 1), shade=False))
    e.append(box((11.6, 5.5, 1.7), (12.8, 6.7, 2.15), "#accent", steel(42, 0, 1, 1, 1), shade=False))
    # Keypad grid
    for i, x in enumerate((3.4, 5.2, 7.0, 8.8, 10.6)):
        e.append(box((x, 1.3, 1.45), (x + 1.3, 2.5, 2.05), "#panel", steel(32, 4 + i, 1, 1, 1)))
    # Brass kick plate and side vents
    e.append(box((1.5, 0.4, 1.2), (14.5, 1.15, 2.2), "#brass", steel(32, 12, 8, 1, 1)))
    e.append(box((0.45, 1.2, 5), (1.05, 3.4, 12), "#dark", steel(44, 0, 1, 2, 4)))
    e.append(box((14.95, 1.2, 5), (15.55, 3.4, 12), "#dark", steel(44, 0, 1, 2, 4)))
    # Rear cable port
    e.append(box((6.5, 1.2, 14.7), (9.5, 3.0, 15.6), "#trim", steel(48, 0, 2, 2, 1)))
    return e


def free_exit():
    e = []
    # Wall controller body
    e.append(box((2, 1, 4), (14, 14, 14.5), "#steel", steel(0, 0, 8, 8, 6), cull=()))
    e.append(box((1.2, 0, 6), (14.8, 1.2, 14.8), "#trim", steel(0, 16, 8, 1, 5)))
    # Green safety door
    e.append(box((3, 3.2, 1.6), (13, 12.4, 4.1), "#accent", {
        "north": [0, 0, 10, 10], "south": [0, 10, 10, 16],
        "east": [10, 0, 12, 10], "west": [10, 0, 12, 10],
        "up": [0, 10, 10, 12], "down": [0, 12, 10, 14],
    }))
    # Running-man backlight
    e.append(box((4.2, 6.4, 1.15), (9.4, 10.8, 1.65), "#screen", {
        "north": [0, 0, 8, 6], "south": [0, 6, 8, 12],
        "up": [0, 12, 8, 13], "down": [0, 13, 8, 14],
        "east": [8, 0, 9, 6], "west": [9, 0, 10, 6],
    }, shade=False))
    # Arrow chevron, tilted so it points out of the door plane
    e.append(box(
        (9.2, 7.3, 0.85), (12.2, 9.7, 1.25), "#brass", steel(16, 0, 3, 2, 1),
        rot={"origin": [9.2, 8.5, 1.05], "axis": "y", "angle": -22.5},
    ))
    # Release bar
    e.append(box((4, 4.0, 0.7), (12, 5.3, 1.5), "#brass", steel(20, 0, 6, 1, 1)))
    e.append(box((4.3, 4.35, 1.4), (5.1, 4.95, 2.2), "#dark", steel(28, 0, 1, 1, 1)))
    e.append(box((10.9, 4.35, 1.4), (11.7, 4.95, 2.2), "#dark", steel(28, 0, 1, 1, 1)))
    # Status lamp + small plate
    e.append(box((11.2, 11.4, 1.2), (12.6, 12.8, 1.7), "#screen", steel(32, 0, 1, 1, 1), shade=False))
    e.append(box((3.4, 11.5, 1.25), (8.6, 12.6, 1.75), "#panel", steel(32, 4, 4, 1, 1)))
    # Side hinge
    e.append(box((12.8, 4.5, 3.2), (14.2, 6.6, 4.6), "#brass", steel(40, 0, 1, 2, 1)))
    e.append(box((12.8, 9.2, 3.2), (14.2, 11.3, 4.6), "#brass", steel(40, 0, 1, 2, 1)))
    # Corner bolts
    for x, y in ((2.4, 12.6), (13.0, 12.6), (2.4, 1.8), (13.0, 1.8)):
        e.append(box((x, y, 3.6), (x + 0.7, y + 0.7, 4.15), "#trim", steel(44, 0, 1, 1, 1)))
    return e


def evacuation_console():
    e = []
    # Wide alarm plinth
    e.append(box((0.4, 0, 2), (15.6, 3.2, 15.4), "#steel", steel(0, 0, 10, 2, 8), cull=("down",)))
    e.append(box((1.2, 3.2, 3), (14.8, 11.4, 14.6), "#dark", steel(0, 16, 8, 6, 7)))
    # Hazard brow, tilted down toward the operator
    e.append(box(
        (0.8, 11.2, 2.4), (15.2, 13.0, 8.2), "#accent", {
            "north": [0, 0, 14, 2], "south": [0, 2, 14, 4],
            "up": [0, 4, 14, 8], "down": [0, 8, 14, 12],
            "east": [14, 0, 16, 2], "west": [14, 2, 16, 4],
        },
        rot={"origin": [8, 11.2, 8.2], "axis": "x", "angle": 22.5},
    ))
    # Beacon
    e.append(box((6.4, 13.05, 6.2), (9.6, 15.7, 9.4), "#screen", {
        "north": [0, 0, 4, 4], "south": [4, 0, 8, 4],
        "east": [0, 4, 4, 8], "west": [4, 4, 8, 8],
        "up": [8, 0, 12, 4], "down": [8, 4, 12, 8],
    }, shade=False))
    e.append(box((7.3, 15.65, 7.1), (8.7, 16.0, 8.5), "#brass", steel(32, 0, 1, 1, 1)))
    # Big warning screen
    e.append(box((2.4, 5.2, 1.35), (13.6, 10.2, 3.05), "#trim", steel(16, 0, 8, 4, 1)))
    e.append(box((2.9, 5.6, 0.85), (13.1, 9.8, 1.4), "#screen", {
        "north": [0, 8, 12, 14], "south": [0, 14, 12, 16],
        "up": [0, 14, 12, 15], "down": [0, 15, 12, 16],
        "east": [12, 8, 13, 14], "west": [13, 8, 14, 14],
    }, shade=False))
    # Two palm buttons
    e.append(box((2.2, 3.35, 1.2), (6.4, 4.7, 2.6), "#accent", steel(36, 0, 3, 1, 1)))
    e.append(box((9.6, 3.35, 1.2), (13.8, 4.7, 2.6), "#brass", steel(40, 0, 3, 1, 1)))
    # Chevron stripes on the plinth front
    e.append(box((1.2, 0.8, 1.35), (14.8, 2.3, 2.05), "#accent", {
        "north": [0, 12, 14, 14], "south": [0, 14, 14, 16],
        "up": [0, 14, 14, 15], "down": [0, 15, 14, 16],
        "east": [14, 12, 15, 14], "west": [15, 12, 16, 14],
    }))
    # Side speaker grilles
    e.append(box((0.05, 5, 5), (1.25, 9.4, 11), "#panel", steel(44, 0, 1, 3, 4)))
    e.append(box((14.75, 5, 5), (15.95, 9.4, 11), "#panel", steel(44, 0, 1, 3, 4)))
    # Rear handle
    e.append(box((6, 6.5, 14.55), (10, 9.2, 15.7), "#brass", steel(48, 0, 3, 2, 1)))
    return e


def broker_exit():
    e = []
    # Voucher kiosk: tall narrow body, brass plinth
    e.append(box((3, 0, 3), (13, 2.2, 14.5), "#brass", steel(0, 0, 6, 2, 7), cull=("down",)))
    e.append(box((3.6, 2.2, 3.6), (12.4, 13.6, 13.8), "#steel", steel(0, 16, 6, 8, 6)))
    e.append(box((4.2, 13.6, 4.4), (11.8, 15.5, 13.2), "#dark", steel(16, 0, 5, 2, 6)))
    # Receipt slot hood
    e.append(box((4.6, 2.6, 1.8), (11.4, 4.6, 3.7), "#trim", steel(24, 0, 5, 2, 1)))
    e.append(box((5.2, 3.15, 1.35), (10.8, 3.7, 1.9), "#dark", steel(24, 4, 4, 1, 1)))
    # Coin / voucher window
    e.append(box((4.8, 8.4, 2.2), (11.2, 12.4, 3.7), "#brass", steel(32, 0, 5, 3, 1)))
    e.append(box((5.3, 8.9, 1.7), (10.7, 11.9, 2.25), "#screen", {
        "north": [0, 0, 8, 5], "south": [0, 5, 8, 10],
        "up": [0, 10, 8, 11], "down": [0, 11, 8, 12],
        "east": [8, 0, 9, 5], "west": [9, 0, 10, 5],
    }, shade=False))
    # Ticket tongue, tilted out of the slot
    e.append(box(
        (6.2, 3.55, 0.7), (9.8, 4.15, 2.5), "#accent", {
            "north": [0, 12, 4, 13], "south": [0, 13, 4, 14],
            "up": [0, 14, 4, 16], "down": [4, 14, 8, 16],
            "east": [8, 12, 10, 13], "west": [10, 12, 12, 13],
        },
        rot={"origin": [8, 3.85, 2.2], "axis": "x", "angle": -22.5},
    ))
    # Side crank
    e.append(box((12.3, 6.2, 7.4), (14.4, 7.6, 8.8), "#brass", steel(40, 0, 2, 1, 1)))
    e.append(box(
        (13.5, 5.2, 7.7), (14.2, 8.6, 8.5), "#trim", steel(44, 0, 1, 3, 1),
        rot={"origin": [13.85, 6.9, 8.1], "axis": "z", "angle": 22.5},
    ))
    # Confirmation lever on the face
    e.append(box((5.0, 5.3, 2.5), (6.6, 7.6, 3.3), "#dark", steel(46, 0, 1, 2, 1)))
    e.append(box(
        (5.3, 6.8, 1.5), (6.3, 7.5, 3.6), "#brass", steel(48, 0, 1, 1, 2),
        rot={"origin": [5.8, 7.15, 3.2], "axis": "x", "angle": -22.5},
    ))
    # Brass corner studs and a top coin slot
    for x, y in ((3.9, 12.6), (11.4, 12.6), (3.9, 2.6)):
        e.append(box((x, y, 3.15), (x + 0.7, y + 0.7, 3.7), "#brass", steel(52, 0, 1, 1, 1)))
    e.append(box((6.6, 14.7, 7.2), (9.4, 15.35, 10.6), "#brass", steel(52, 4, 2, 1, 2)))
    e.append(box((7.4, 15.3, 8.5), (8.6, 15.85, 9.3), "#dark", steel(56, 0, 1, 1, 1)))
    return e


BUILDERS = {
    "echo_supply_crate": supply_crate,
    "echo_black_market": black_market,
    "echo_core_console": core_console,
    "echo_free_exit": free_exit,
    "echo_evacuation_console": evacuation_console,
    "echo_broker_exit": broker_exit,
}

TEXTURES = {
    "echo_supply_crate": {
        "steel": "echo_terminal_steel",
        "brass": "echo_terminal_brass",
        "dark": "echo_terminal_dark",
        "panel": "echo_terminal_panel",
        "trim": "echo_terminal_trim",
        "accent": "echo_supply_crate_mark",
    },
    "echo_black_market": {
        "steel": "echo_terminal_steel",
        "brass": "echo_terminal_brass",
        "dark": "echo_terminal_dark",
        "panel": "echo_terminal_panel",
        "trim": "echo_terminal_trim",
        "accent": "echo_black_market_seal",
    },
    "echo_core_console": {
        "steel": "echo_terminal_steel",
        "brass": "echo_terminal_brass",
        "dark": "echo_terminal_dark",
        "panel": "echo_terminal_panel",
        "trim": "echo_terminal_trim",
        "accent": "echo_core_console_lamp",
        "screen": "echo_core_console_screen",
    },
    "echo_free_exit": {
        "steel": "echo_terminal_steel",
        "brass": "echo_terminal_brass",
        "dark": "echo_terminal_dark",
        "panel": "echo_terminal_panel",
        "trim": "echo_terminal_trim",
        "accent": "echo_free_exit_door",
        "screen": "echo_free_exit_sign",
    },
    "echo_evacuation_console": {
        "steel": "echo_terminal_steel",
        "brass": "echo_terminal_brass",
        "dark": "echo_terminal_dark",
        "panel": "echo_terminal_panel",
        "trim": "echo_terminal_trim",
        "accent": "echo_evacuation_hazard",
        "screen": "echo_evacuation_screen",
    },
    "echo_broker_exit": {
        "steel": "echo_terminal_steel",
        "brass": "echo_terminal_brass",
        "dark": "echo_terminal_dark",
        "panel": "echo_terminal_panel",
        "trim": "echo_terminal_trim",
        "accent": "echo_broker_ticket",
        "screen": "echo_broker_window",
    },
}

PARTICLE = {
    "echo_supply_crate": "echo_terminal_steel",
    "echo_black_market": "echo_black_market_seal",
    "echo_core_console": "echo_terminal_steel",
    "echo_free_exit": "echo_free_exit_door",
    "echo_evacuation_console": "echo_evacuation_hazard",
    "echo_broker_exit": "echo_terminal_brass",
}


def write_json(path: Path, payload) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def write_models() -> None:
    for bid in IDS:
        elements = BUILDERS[bid]()
        if len(elements) < 10:
            raise SystemExit(f"{bid} has only {len(elements)} elements")
        write_json(
            ASSET / "models/block" / f"{bid}.json",
            model(PARTICLE[bid], TEXTURES[bid], elements),
        )
        write_json(ASSET / "models/item" / f"{bid}.json", {"parent": f"{NS}:block/{bid}"})
        write_json(ASSET / "blockstates" / f"{bid}.json", {
            "variants": {
                "facing=north": {"model": f"{NS}:block/{bid}"},
                "facing=east": {"model": f"{NS}:block/{bid}", "y": 90},
                "facing=south": {"model": f"{NS}:block/{bid}", "y": 180},
                "facing=west": {"model": f"{NS}:block/{bid}", "y": 270},
            }
        })
        write_json(DATA / "loot_table/blocks" / f"{bid}.json", {
            "type": "minecraft:block",
            "pools": [{
                "bonus_rolls": 0.0,
                "rolls": 1.0,
                "conditions": [{"condition": "minecraft:survives_explosion"}],
                "entries": [{"type": "minecraft:item", "name": f"{NS}:{bid}"}],
            }],
        })


# ---------------------------------------------------------------------------
# Textures. 64px, opaque, no vanilla assets. Shared plates are industrial;
# per-block plates carry the silhouette and symbol that distinguish them.
# ---------------------------------------------------------------------------

def noise(img, amount, seed):
    import random
    rnd = random.Random(seed)
    px = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            d = rnd.randint(-amount, amount)
            px[x, y] = (max(0, min(255, r + d)), max(0, min(255, g + d)), max(0, min(255, b + d)), a)


def plate(base, seed, rivet=None):
    img = Image.new("RGBA", (TEX, TEX), base)
    d = ImageDraw.Draw(img)
    # Panel seams
    for i in range(0, TEX, 16):
        d.line((i, 0, i, TEX - 1), fill=_shade(base, -28), width=1)
        d.line((0, i, TEX - 1, i), fill=_shade(base, -22), width=1)
        d.line((i + 1, 0, i + 1, TEX - 1), fill=_shade(base, 16), width=1)
    # Rivets
    if rivet:
        for y in (6, 26, 46):
            for x in (6, 26, 46):
                d.ellipse((x, y, x + 3, y + 3), fill=rivet)
                d.point((x + 1, y + 1), fill=_shade(rivet, 40))
    # Vent slots
    for y in (18, 38):
        d.rectangle((20, y, 44, y + 2), fill=_shade(base, -48))
        d.line((20, y, 44, y), fill=_shade(base, 24))
    noise(img, 7, seed)
    return img


def _shade(c, delta):
    return tuple(max(0, min(255, ch + delta)) for ch in c[:3]) + ((c[3] if len(c) > 3 else 255),)


def paint_shared():
    files = {
        "echo_terminal_steel": plate((42, 52, 64, 255), 11, (186, 148, 78, 255)),
        "echo_terminal_brass": plate((168, 126, 62, 255), 22, (92, 64, 32, 255)),
        "echo_terminal_dark": plate((22, 18, 16, 255), 33, (120, 96, 58, 255)),
        "echo_terminal_panel": plate((58, 46, 36, 255), 44, (196, 164, 96, 255)),
        "echo_terminal_trim": plate((92, 78, 58, 255), 55, (214, 186, 112, 255)),
    }
    # Extra readable marks so shared plates are not flat noise.
    d = ImageDraw.Draw(files["echo_terminal_steel"])
    d.rectangle((8, 8, 55, 55), outline=(186, 148, 78, 255))
    d = ImageDraw.Draw(files["echo_terminal_brass"])
    for i in range(4, 60, 6):
        d.line((i, 4, i, 59), fill=(120, 86, 40, 255))
    d = ImageDraw.Draw(files["echo_terminal_dark"])
    for y in range(10, 58, 5):
        d.line((8, y, 56, y), fill=(8, 8, 10, 255))
    return files


def paint_unique():
    out = {}
    # Supply stencil: freight chevron + crate ID bars
    img = Image.new("RGBA", (TEX, TEX), (36, 44, 54, 255))
    d = ImageDraw.Draw(img)
    d.polygon([(8, 40), (32, 12), (56, 40), (46, 40), (32, 24), (18, 40)], fill=(196, 154, 72, 255))
    d.rectangle((10, 46, 54, 50), fill=(212, 176, 96, 255))
    d.rectangle((14, 54, 28, 58), fill=(90, 120, 86, 255))
    d.rectangle((32, 54, 50, 58), fill=(90, 120, 86, 255))
    noise(img, 5, 71)
    out["echo_supply_crate_mark"] = img

    # Purple sealed vault door
    img = Image.new("RGBA", (TEX, TEX), (46, 28, 68, 255))
    d = ImageDraw.Draw(img)
    d.ellipse((8, 8, 55, 55), outline=(214, 176, 96, 255), width=3)
    d.ellipse((22, 22, 41, 41), fill=(18, 10, 28, 255), outline=(168, 96, 214, 255))
    d.polygon([(32, 14), (36, 28), (50, 28), (38, 36), (42, 50), (32, 40), (22, 50), (26, 36), (14, 28), (28, 28)], fill=(186, 120, 230, 255))
    d.rectangle((4, 4, 59, 7), fill=(120, 72, 168, 255))
    noise(img, 6, 82)
    out["echo_black_market_seal"] = img

    # Core lamps
    img = Image.new("RGBA", (TEX, TEX), (28, 36, 48, 255))
    d = ImageDraw.Draw(img)
    d.ellipse((8, 8, 28, 28), fill=(92, 196, 168, 255))
    d.ellipse((36, 8, 56, 28), fill=(196, 154, 72, 255))
    d.rectangle((8, 40, 56, 52), fill=(16, 22, 30, 255))
    for x in range(12, 52, 8):
        d.rectangle((x, 44, x + 3, 48), fill=(92, 196, 168, 255))
    noise(img, 4, 93)
    out["echo_core_console_lamp"] = img

    # Core screen: keyhole schematic
    img = Image.new("RGBA", (TEX, TEX), (8, 28, 32, 255))
    d = ImageDraw.Draw(img)
    d.rectangle((4, 4, 59, 59), outline=(92, 210, 186, 255))
    d.ellipse((22, 10, 42, 30), outline=(186, 232, 214, 255), width=2)
    d.polygon([(28, 28), (36, 28), (34, 50), (30, 50)], fill=(186, 232, 214, 255))
    d.line((10, 54, 54, 54), fill=(92, 210, 186, 255))
    noise(img, 3, 104)
    out["echo_core_console_screen"] = img

    # Green exit door
    img = Image.new("RGBA", (TEX, TEX), (28, 72, 42, 255))
    d = ImageDraw.Draw(img)
    d.rectangle((4, 4, 59, 59), outline=(186, 214, 120, 255), width=2)
    d.rectangle((10, 10, 53, 18), fill=(18, 48, 28, 255))
    d.polygon([(14, 40), (28, 22), (32, 22), (32, 48), (24, 48), (24, 36), (16, 44)], fill=(214, 232, 160, 255))
    d.rectangle((36, 28, 50, 46), outline=(214, 232, 160, 255), width=2)
    noise(img, 5, 115)
    out["echo_free_exit_door"] = img

    # Running-man sign
    img = Image.new("RGBA", (TEX, TEX), (12, 48, 28, 255))
    d = ImageDraw.Draw(img)
    d.ellipse((8, 8, 20, 20), fill=(214, 236, 170, 255))
    d.polygon([(16, 22), (28, 22), (24, 40), (18, 40)], fill=(214, 236, 170, 255))
    d.line((20, 28, 8, 36), fill=(214, 236, 170, 255), width=3)
    d.line((22, 40, 12, 56), fill=(214, 236, 170, 255), width=3)
    d.line((24, 40, 36, 56), fill=(214, 236, 170, 255), width=3)
    d.polygon([(40, 30), (58, 22), (58, 40), (40, 34)], fill=(196, 164, 72, 255))
    noise(img, 3, 126)
    out["echo_free_exit_sign"] = img

    # Orange-gold hazard
    img = Image.new("RGBA", (TEX, TEX), (168, 78, 24, 255))
    d = ImageDraw.Draw(img)
    for i in range(-64, 64, 8):
        d.line((i, 0, i + 64, 64), fill=(28, 18, 12, 255), width=3)
    d.rectangle((6, 6, 57, 57), outline=(232, 186, 72, 255), width=2)
    d.polygon([(32, 16), (46, 44), (18, 44)], outline=(255, 220, 120, 255))
    d.rectangle((30, 26, 34, 36), fill=(255, 220, 120, 255))
    d.point((32, 40), fill=(255, 220, 120, 255))
    noise(img, 6, 137)
    out["echo_evacuation_hazard"] = img

    # Evacuation screen
    img = Image.new("RGBA", (TEX, TEX), (48, 18, 8, 255))
    d = ImageDraw.Draw(img)
    d.rectangle((2, 2, 61, 61), outline=(255, 176, 64, 255), width=2)
    d.polygon([(8, 48), (32, 10), (56, 48)], fill=(232, 120, 32, 255))
    d.polygon([(20, 48), (32, 24), (44, 48)], fill=(255, 214, 96, 255))
    d.rectangle((8, 52, 56, 56), fill=(255, 214, 96, 255))
    noise(img, 4, 148)
    out["echo_evacuation_screen"] = img

    # Broker ticket
    img = Image.new("RGBA", (TEX, TEX), (92, 64, 32, 255))
    d = ImageDraw.Draw(img)
    d.rectangle((6, 10, 57, 54), fill=(232, 214, 170, 255), outline=(48, 32, 16, 255))
    for y in (18, 26, 34, 42):
        d.line((12, y, 40, y), fill=(92, 64, 36, 255))
    d.ellipse((42, 20, 54, 32), outline=(168, 112, 40, 255), width=2)
    d.line((44, 36, 54, 46), fill=(168, 112, 40, 255), width=2)
    noise(img, 4, 159)
    out["echo_broker_ticket"] = img

    # Broker window: stacked coins / voucher glyphs
    img = Image.new("RGBA", (TEX, TEX), (16, 22, 32, 255))
    d = ImageDraw.Draw(img)
    d.rectangle((4, 4, 59, 59), outline=(212, 176, 96, 255))
    for i, y in enumerate((14, 28, 42)):
        d.ellipse((12, y, 36, y + 10), outline=(212, 176, 96, 255), width=2)
        d.rectangle((40, y + 1, 52, y + 8), fill=(92, 168, 132, 255) if i != 1 else (196, 92, 72, 255))
    noise(img, 3, 170)
    out["echo_broker_window"] = img
    return out


def write_textures() -> None:
    out = TEX_DIR()
    out.mkdir(parents=True, exist_ok=True)
    for name, img in {**paint_shared(), **paint_unique()}.items():
        if img.mode != "RGBA":
            raise SystemExit(name)
        if img.size != (TEX, TEX):
            raise SystemExit(name)
        # Opaque: no cutout required.
        if img.getextrema()[3] != (255, 255):
            raise SystemExit(f"{name} is not opaque")
        img.save(out / f"{name}.png")


def TEX_DIR() -> Path:
    return ASSET / "textures/block"


def main() -> None:
    raise SystemExit(
        "refusing to run: generate_multiblock_models.py owns the echo machine "
        "blockstates, part models and loot tables. This writer still emits the "
        "old single-cell variants and would overwrite them."
    )
    write_models()
    write_textures()
    print(f"wrote {len(IDS)} terminals into {ASSET}")


if __name__ == "__main__":
    main()
