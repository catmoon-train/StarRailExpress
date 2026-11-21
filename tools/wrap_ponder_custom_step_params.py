"""把思索场景里自定义步骤的顶层字段包进 PondererLib 要求的 parameters 子对象。

PondererLib 的 SceneStepExecutor 只把 DslStep.parameters 传给 StepCodec.decode；
写在步骤顶层的自定义字段会落进 extensionData，运行时读不到。
用法：python tools/wrap_ponder_custom_step_params.py
"""

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCENES = ROOT / "src/main/resources/assets/starrailexpress/ponderer/scenes"


def main():
    changed = 0
    for path in sorted(SCENES.glob("role_tutorial_*.json")):
        data = json.loads(path.read_text(encoding="utf-8-sig"))
        for scene in data.get("scenes", ()):
            for step in scene.get("steps", ()):
                if not str(step.get("type", "")).startswith("starrailexpress:"):
                    continue
                if "parameters" in step:
                    continue
                params = {key: value for key, value in step.items()
                          if key not in ("type", "attachKeyFrame")}
                for key in list(step):
                    if key not in ("type", "attachKeyFrame"):
                        del step[key]
                step["parameters"] = params
                changed += 1
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("wrapped", changed, "custom steps")


if __name__ == "__main__":
    main()
