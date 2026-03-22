# StarRail Express
**[English]** [[简体中文]](README.zh.md)

## Please Note!
This Wathe addon is **highly likely** incompatible with any other Wathe addons.

Due to the `Trainmurdermystery` copyright license being ARR, and we have rewritten many Wathe functionalities, publishing it is very difficult. Hence, this companion mod.

We have rewritten the original `TrainMuderMystery` and switched to `Mojang Mappings`. Some code inevitably uses content from the original. Any similarities are purely coincidental.

However, since we still need Wathe's base decorative blocks, this mod requires Wathe as a prerequisite, even though it cannot execute any of its functions.

This mod completely blocks the original Wathe runtime and operates using its own logic.

For convenience, we used the `trainmurdermystery` namespace and our own, instead of `wathe` (changing IDs would complicate map migration).

Some parts still use `TMM`, as renaming files would require many changes, which is cumbersome.

## Disclaimer

This mod is an addon for the Wathe mod and improves many of its features. It incorporates functionalities and content from `Harpymodloader`, `StupidExpress`, `Noellesroles`, and `Harpy Simple Roles`. Some new roles reference those from the `KinsWathe` mod.

This mod is completely open-source, free, and non-commercial. We use the same `GNU General Public License v3.0 only (GPL-3.0-only)` as the upstream `Noellesroles`.

### What You Can Do (Granted Permissions)
#### Freedom to Use

You may run the program for any purpose, whether personal, academic, or commercial.

#### Freedom to Copy

You may make exact copies (verbatim distribution) of the program's source code or binary versions, with or without a fee.

#### Freedom to Modify

You may modify the program to create your own version. Upon modification, you become the copyright holder of the changes.

#### Freedom to Distribute

You may distribute the software (original or modified) to the public through any medium.

#### Commercial Use

You may use `GPL-3.0-only` software for commercial services (e.g., providing software as a service, selling devices containing the software), provided you comply with the obligations below.

### Your Obligations (Core Conditions)
#### Must Open Source (Strong Copyleft)

If you distribute (including binary or source code forms) any work containing `GPL-3.0-only` code (including modified versions or programs integrated via static/dynamic linking), you must provide complete, corresponding source code under the same `GPL-3.0-only` license.

**Note**: Internal use without distribution does not require open-sourcing.

#### License Must Persist

Your modified or derivative work must be licensed entirely under `GPL-3.0-only`; no other license may be used (including not changing to `GPL-2.0` or any non-GPL license). This is the meaning of "`only`"—the version is locked to `GPL-3.0`.

#### No Additional Restrictions

You cannot impose any "further restrictions" on top of `GPL-3.0-only`, such as prohibiting commercial use, requiring licensing fees, or restricting users' rights granted by the GPL. Any such additional terms are void.

#### Retain Copyright and Disclaimer Notices

You must not delete or modify copyright notices, license statements, or disclaimers in the source code file headers, regardless of the distribution form.

You must include a complete copy of the `GPL-3.0-only` license with the software.

#### Provide Installation Information (for User Products)

If you install `GPL-3.0-only` software in binary form on a user product (e.g., routers, set-top boxes, or other hardware) for distribution, you must provide installation information to ensure users can install modified versions (no hardware locking allowed).

#### No Linking with Closed Source

You cannot statically or dynamically link `GPL-3.0-only` code with closed-source code for distribution, unless the closed-source portion also complies with `GPL-3.0-only` or qualifies for the license's "system library" exception. Any form of linking that results in distribution constitutes a derivative work and must be fully open-sourced.

## Compatibility
This mod is theoretically incompatible with any Wathe addons. It disables Wathe's registration and initialization events, so nothing besides Wathe's resources and data will work.

Since it's unclear how to disable the tags in Wathe's `data` folder—which cause errors due to missing Wathe item and block registrations—we have additionally registered Wathe's items and blocks in this mod for compatibility with Wathe-based maps.

Please note, these items and blocks likely lack their original functionality, so try to avoid using them!

## DLC Features
### Roles
We have integrated roles and modifiers from `Harpymodloader`, `StupidExpress`, `Noellesroles`, `Harpy Simple Roles`, and `KinsWathe`, while adding many original roles and modifiers. You cannot install the aforementioned mods alongside this one.

### Items, Entities, Blocks
We have added more items, entities, and blocks to the train. You can view them in the in-game inventory.

### Features
We have added many new commands to the train, such as:
- `/tmm:money` Money management
- `/tmm:switchmap` Switch maps
- `/tmm:game` Game utility commands
- ...

We have also added voting and asynchronous copying to the train, optimizing issues in the original train like network packets and data component packets.

Preliminary, albeit non-rigorous, tests show a significant reduction in packet count and network load.

## API
Documentation is not yet available, but the mod provides a wide range of APIs for developers.

## Maps
Stored in `world/train_maps`
Saved as JSON files.

Example Content:
```json
{
  "spawnPos": { // Spawn point (where players return after game ends). New players will teleport to the world's default spawn, not here.
    "x": 0,
    "y": 0,
    "z": 0,
    "yaw": 90.0,
    "pitch": 0.0
  },
  "spectatorSpawnPos": { // Spectator spawn point (where new players who join after the game starts are placed as spectators)
    "x": 0,
    "y": 20,
    "z": 0,
    "yaw": -90.0,
    "pitch": 15.0
  },
  "readyArea": { // Ready area. Players must be inside to be considered participating in the game.
    "minX": -100,
    "minY": -10,
    "minZ": -100,
    "maxX": 100,
    "maxY": 10,
    "maxZ": 100
  },
  "playAreaOffset": { // Unused.
    "x": 0,
    "y": 0,
    "z": 200
  },
  "playArea": { // Play area. Should be at least as large as the paste area.
    "minX": 0,
    "minY": 20,
    "minZ": 0,
    "maxX": 100,
    "maxY": 30,
    "maxZ": 100
  },
  "sceneArea": { // Scene background area.
    "minX": 0,
    "minY": 40,
    "minZ": 0,
    "maxX": 100,
    "maxY": 50,
    "maxZ": 100
  },
  "sceneScroll": "X", // Background scroll direction. Supports X, Y, Z, NONE (no scroll).
  "resetPasteArea": { // Paste area. The map will be pasted here.
    "minX": 0,
    "minY": 20,
    "minZ": 0,
    "maxX": 100,
    "maxY": 30,
    "maxZ": 100
  },
  "resetTemplateArea": { // Template area. The map will be copied from here.
    "minX": 0,
    "minY": 0,
    "minZ": 0,
    "maxX": 100,
    "maxY": 10,
    "maxZ": 100
  },
  "roomCount": 2, // Number of rooms.
  "roomPositions": { // Room positions.
    "1": { // Room 1 position.
      "x": 0.0,
      "y": 20.0,
      "z": 50.0
    },
    "2": { // Room 2 position.
      "x": 50.0,
      "y": 25.0,
      "z": 0.0
    }
  },
  "disabledTasks": ["BREATHE"],
  "canSwim": true, // Whether players are allowed to enter water deeper than 2 blocks. Setting to false will kill players who enter water 2 blocks deep.
  "canJump": true, // Whether players are allowed to jump.
  "haveOutsideSound": true // Whether outdoor/indoor sound effects are enabled.
}
```
When using, remove the comments (lines starting with `//`).