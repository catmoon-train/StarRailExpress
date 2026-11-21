# -*- coding: utf-8 -*-
"""把已有场景里的 CustomName 改成 JSON 文本组件。

用法：python tools/fix_ponder_custom_name.py

背景：Ponderer 建实体时走 `entity.load(data)`，`Entity.load` 用
`Component.Serializer.fromJson` 解析 CustomName，所以 CustomName 必须是一个
JSON 文本组件；直接写 `'酒保'` 会在加载时抛 JsonSyntaxException，
整个场景会被判为 unavailable（日志里是 `scene ... unavailable`）。

翻译键写法（`{"translate":"..."}`）还能让名字随客户端语言显示。
"""

import io
import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCENES = os.path.join(ROOT, "src", "main", "resources", "assets",
                      "starrailexpress", "ponderer", "scenes")

PATTERN = re.compile(r"\{CustomName:'(?P<name>[^']*)',CustomNameVisible:1b\}")


def to_component(name):
    """把名字转成 JSON 文本组件。"""
    if name.startswith("{"):
        return name
    escaped = name.replace("\\", "\\\\").replace('"', '\\"')
    return '{"text":"%s"}' % escaped


def fix_nbt(nbt):
    if not nbt or "CustomName:'" not in nbt:
        return nbt
    return PATTERN.sub(lambda m: "{CustomName:'%s',CustomNameVisible:1b}" % to_component(m.group("name")),
                       nbt)


def walk(steps):
    for step in steps:
        if isinstance(step.get("nbt"), str):
            step["nbt"] = fix_nbt(step["nbt"])
        if isinstance(step.get("parameters"), dict):
            nbt = step["parameters"].get("nbt")
            if isinstance(nbt, str):
                step["parameters"]["nbt"] = fix_nbt(nbt)


def main():
    for filename in sorted(os.listdir(SCENES)):
        if not filename.endswith(".json"):
            continue
        path = os.path.join(SCENES, filename)
        with io.open(path, encoding="utf-8-sig") as f:
            raw = f.read()
        if "CustomName:'" not in raw:
            continue
        scene = json.loads(raw)
        for segment in scene.get("scenes", []):
            walk(segment.get("steps") or [])
        with io.open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(json.dumps(scene, ensure_ascii=False, indent=2))
            f.write("\n")
        print("fixed", filename)


if __name__ == "__main__":
    main()
