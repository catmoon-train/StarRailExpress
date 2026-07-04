# StarRail Express - Harpy Train Enhanced Mod

## 🚂 Introduction
StarRail Express is a multiplayer social deduction game mod for Minecraft 1.21.1 Fabric. It deeply重构s the classic "TrainMurderMystery" gameplay, integrating the best features from multiple renowned role mods and adding a vast amount of original content. This mod is completely open-source and free, licensed under GPL-3.0-only, dedicated to providing the smoothest, richest, and most visually stunning train experience.

## ✨ Core Features

### 🎭 Massive Role System (100+ Roles)
We have integrated and created over 100 unique roles across four major factions, ensuring every match is full of surprises:
- **Civilian**: 40+ roles. Including Doctor, Detective, Athlete, Superstar, Psychologist, etc. Win by completing tasks or assisting Vigilantes.
- **Killer**: 25+ roles. Including Ninja, Executioner, Bomber, Morphling, Manipulator, etc. Goal: Eliminate all non-killer players.
- **Neutral**: 30+ roles. Including Gambler, Thief, Vulture, NianShou, Monokuma, etc., each with unique personal win conditions.
- **Vigilante/Sheriff**: 10+ roles. Including Patroller, Better Vigilante, Baseball Player, etc., capable of killing killers.
- **Original Featured Roles**: Such as "The Fool" (Tarot Club mechanics), "Shadow Falcon" (Stealth Assassination), "Pilot" (Aerial Support), and more.

### 🧩 Rich Modifier System (20+ Modifiers)
Adds randomness and fun to the game:
- **Physical**: Giant, Tiny, Feather (Lightweight), Vigorous.
- **Economic**: Magnate (Start with extra money), Taxed.
- **Special**: Lovers (Shared health), Secretive (Hidden identity), Paranoid, Black & White.
- **Task-related**: Taskmaster (Accelerated task progress).

### 🗺️ Quick Map Editing & Voting System
- **Visual Map Configuration**: Supports JSON format to quickly define spawn points, ready areas, play areas, room positions, and background scroll directions.
- **Map Voting**: Built-in `/tmm:votemap` command, supporting pause, resume, and stop functions, allowing players to decide the next map together.
- **Asynchronous Copy Optimization**: Significantly reduces server lag and network packet pressure during map resets, achieving silky-smooth round transitions.
- **Massive Preset Maps**: Comes with multiple carefully designed train and scene maps, and supports community custom map imports.

### 💻 Refreshed UI & Interaction Experience
- **Better-looking UI**: Rewrote in-game HUD, role display screens, shop interfaces, and settlement panels. Adopts a modern design style with vivid colors and clear information hierarchy.
- **Stamina Bar Display**: Real-time display of player sprint stamina, making chases and escapes more strategic.
- **Task Point Hints**: Intelligent task navigation system provides clear direction indicators and distance hints on the screen, saying goodbye to getting lost.
- **Dynamic Skin System**: Weapons (Knife, Gun, Bat) support various quality skins (Common, Uncommon, Rare, Epic, Legendary),直观ly displayed in the UI.
- **Smart Key Prompts**: Integrated SmartKeyPrompts to dynamically display operation hints based on held items (e.g., Assassinate, Shot, Swing).

### 🛠️ Powerful Game Features & Commands
- **Comprehensive Command System**:
  - `/tmm:money` - Admin money management.
  - `/tmm:switchmap` - Quickly load, save, scan, and randomly switch maps.
  - `/tmm:game` - Game flow control (start, end, force win).
  - `/tmm:showStats` - View detailed personal and server-wide statistics.
- **Diverse Game Modes**:
  - **Murder**: Classic Killer vs. Civilian/Vigilante gameplay.
  - **Gambler**: Gamblers must survive through betting within a time limit or face elimination.
  - **TNT Tag**: A tense "hot potato" style bomb passing game where the last holder is eliminated.
  - **Day Night Fight**: Deep gameplay combining day/night cycles, survival elements, and faction warfare.
  - **Fourth Room**: A special exploration mode full of unknowns and challenges.
  - **More Fun Modes**: Over 15 unique modes including Hide and Seek, Sniper War, Devil Roulette, and more.
- **Replay System**: Records key game events, supporting post-match review of exciting moments.

### ⚡ Performance & Network Optimization
- **Network Packet Reduction**: Tests show a significant reduction in packet count compared to the original Wathe/TMM, greatly reducing server bandwidth pressure.
- **Ultra Performance Mode**: Clients can choose "Ultra Performance Mode" to disable complex background rendering and minimize render distance for extremely high FPS.
- **Data Component Optimization**: Optimized CCA (Cardinal Components API) data synchronization logic to reduce unnecessary network transmission.

## ⚠️ Important Notes

### Compatibility
- **Incompatible with other Wathe Addons**: This mod blocks the original Wathe runtime. **DO NOT** install other Wathe addons like Harpymodloader, StupidExpress, Noellesroles, etc., simultaneously.
- **Requires Wathe**: Only used for base decorative block resources; this mod does not use any of its logic.
- **Conflicting Mods**: CustomSkinLoader (may cause skin loading issues).
- **Recommended Mods**: Sodium, Iris, ModMenu, Simple Voice Chat, Cloth Config API.

### Technical Features
- Rewritten using Mojang Mappings for cleaner code.
- Keeps `trainmurdermystery` and `starrailexpress` namespaces to maintain compatibility with existing maps.
- Completely independent runtime logic for higher stability.

## 📦 Dependencies
- Minecraft 1.21.1
- Fabric Loader
- **Wathe** (Required)
- Cloth Config API
- ModMenu
- Simple Voice Chat
- Architectury API
- Exposure & Exposure Polaroid
- SRE Resource

## 🎯 Use Cases
- Large-scale multiplayer social deduction servers
- Party games with friends
- Role-playing (RP) maps
- Custom PVP/PVE hybrid gameplay

## 💻 Development Info
- **Full API Documentation**: [docs/api.md](docs/api.md) — Covers role registration, event system, skill system, HUD rendering, etc.
- **Extension Creation Guide**: [CreateExtention.md](CreateExtention.md) — Quick start to writing your own extension mods.
- **License**: GNU General Public License v3.0 only (GPL-3.0-only)

---

**Note**: Do NOT import Wathe libraries when developing extensions — they will cause crashes!
**Join the Community**: Welcome to join our QQ Group or Discord for the latest maps, bug reports, or gameplay discussions.
