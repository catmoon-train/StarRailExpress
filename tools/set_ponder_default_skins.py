"""给未指定皮肤的自定义玩家步骤补上有效的默认 Steve 皮肤路径。"""

import json
from pathlib import Path

SCENES = Path(__file__).resolve().parent.parent / "src/main/resources/assets/starrailexpress/ponderer/scenes"
SKIN = "minecraft:textures/entity/player/wide/steve.png"


def main():
    count = 0
    for path in SCENES.glob("role_tutorial_*.json"):
        data = json.loads(path.read_text(encoding="utf-8"))
        changed = False
        for scene in data.get("scenes", ()):
            for step in scene.get("steps", ()):
                if step.get("type") == "starrailexpress:role_player" and "skin" not in step.get("parameters", {}):
                    step["parameters"]["skin"] = SKIN
                    changed = True
                    count += 1
        if changed:
            path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("default skins set:", count)


if __name__ == "__main__":
    main()
