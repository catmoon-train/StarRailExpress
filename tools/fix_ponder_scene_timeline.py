# -*- coding: utf-8 -*-
"""修正已有思索场景的时间轴：段落最后一条文本之后补足 idle。

用法：python tools/fix_ponder_scene_timeline.py [场景文件...]

规则（docs/PONDERER_SCENE_AUTHORING.md 第 3 节）：
  text 的 duration 是显示存活时间，不推进时间轴；只有 idle 会推进。
  因此段落里最后一条文本之后也要留足 duration 的 idle，否则文案还没读完镜头就转走了。
  相邻两条文本之间的 idle 同样必须 ≥ 上一条的 duration。
"""

import io
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCENES_DIR = os.path.join(ROOT, "src", "main", "resources", "assets",
                           "starrailexpress", "ponderer", "scenes")


def fix_segment(steps):
    changed = False
    # 相邻文本之间：idle 不足就补。
    index = 0
    while index < len(steps):
        step = steps[index]
        if step.get("type") != "text":
            index += 1
            continue
        duration = int(step.get("duration", 60))
        # 找到这条文本之后、下一个 text 之前的 idle 累计
        idle_total = 0
        cursor = index + 1
        while cursor < len(steps) and steps[cursor].get("type") != "text":
            if steps[cursor].get("type") == "idle":
                idle_total += int(steps[cursor].get("duration", 0))
            cursor += 1
        if idle_total < duration:
            need = duration - idle_total
            # 优先加长最后一条 idle；没有 idle 就插入一条
            target = None
            for back in range(index + 1, cursor):
                if steps[back].get("type") == "idle":
                    target = back
            if target is None:
                steps.insert(index + 1, {"type": "idle", "duration": need})
                changed = True
                index += 2
                continue
            steps[target]["duration"] = int(steps[target].get("duration", 0)) + need
            changed = True
        index = cursor if cursor > index else index + 1
    return changed


def main():
    files = sys.argv[1:]
    if not files:
        files = [os.path.join(SCENES_DIR, name) for name in sorted(os.listdir(SCENES_DIR))
                 if name.endswith(".json")]
    for path in files:
        with io.open(path, encoding="utf-8-sig") as f:
            scene = json.load(f)
        changed = False
        for segment in scene.get("scenes", []):
            changed |= fix_segment(segment.get("steps") or [])
        if changed:
            with io.open(path, "w", encoding="utf-8", newline="\n") as f:
                f.write(json.dumps(scene, ensure_ascii=False, indent=2))
                f.write("\n")
        print(("fixed " if changed else "ok    ") + os.path.basename(path))


if __name__ == "__main__":
    main()
