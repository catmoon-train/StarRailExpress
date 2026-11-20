"""一次性调整思索场景：合并段落、移除落地武器、避开方块的玩家站位。"""

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCENES = ROOT / "src/main/resources/assets/starrailexpress/ponderer/scenes"


def load(name):
    return json.loads((SCENES / name).read_text(encoding="utf-8"))


def save(name, data):
    (SCENES / name).write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def set_pos(step, pos, link_id):
    target = step.get("parameters", step)
    if target.get("linkId") == link_id:
        target["pos"] = pos


def tune_jester():
    data = load("role_tutorial_jester.json")
    segments = {scene["id"]: scene for scene in data["scenes"]}

    for step in segments["rampage"]["steps"]:
        if step.get("parameters", {}).get("linkId") == "jester_rampage":
            step["parameters"]["heldItem"] = "trainmurdermystery:bat"
    segments["rampage"]["steps"] = [
        step for step in segments["rampage"]["steps"]
        if (step.get("parameters", step).get("linkId") != "rampage_weapon")
    ]

    for step in segments["see_through"]["steps"]:
        set_pos(step, [1.5, 1.0, 2.5], "killer")
        set_pos(step, [5.0, 1.0, 2.5], "neutral")

    fake = segments.pop("fake_poison", None)
    if fake is not None:
        data["scenes"] = [scene for scene in data["scenes"] if scene["id"] != "fake_poison"]
    else:
        fake = {"steps": []}
    shop = segments["shop"]
    extra = [step for step in fake["steps"]
             if step["type"] in ("indicate_redstone", "spawn_particles")]
    insert_at = next(i for i, step in enumerate(shop["steps"]) if step["type"] == "idle")
    shop["steps"][insert_at:insert_at] = extra

    save("role_tutorial_jester.json", data)


def tune_bartender():
    data = load("role_tutorial_bartender.json")
    for scene in data["scenes"]:
        for step in scene["steps"]:
            set_pos(step, [5.0, 1.0, 2.5], "drinker")
            set_pos(step, [4.5, 1.0, 3.0], "poisoned")
            set_pos(step, [2.0, 1.0, 2.5], "shielded")
            set_pos(step, [4.5, 1.0, 2.5], "shielded_poisoned")
    save("role_tutorial_bartender.json", data)


if __name__ == "__main__":
    tune_jester()
    tune_bartender()
    print("tuned")
