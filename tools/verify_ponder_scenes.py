# -*- coding: utf-8 -*-
"""校验生成的职业思索场景。

用法：python tools/verify_ponder_scenes.py

检查项（对齐 docs/PONDERER_SCENE_AUTHORING.md 与 docs/PONDERER_NOTES.md）：
  1. JSON 可解析，必需字段齐全（items / structures / tags / customTags / title 三语）；
  2. 段落 id 唯一，首步是 show_structure；
  3. 时间轴：相邻两条 text 的占位区间不重叠，且每条 text 后面有足够 idle；
  4. 同段 text 的 point 互不相同；
  5. 自定义步骤的参数写在 parameters 子对象里；
  6. 引用的结构 NBT 存在；
  7. 场景索引 content/role_tutorials.json 登记了全部场景；
  8. 正文逐句取自职业介绍原文（允许阵营首行、次行与通用胜利目标）。
"""

import io
import json
import os
import re
import sys
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets")
SCENES_DIR = os.path.join(ASSETS, "starrailexpress", "ponderer", "scenes")
CONTENT = os.path.join(ASSETS, "starrailexpress", "ponderer", "content", "role_tutorials.json")
STRUCTURES = os.path.join(ASSETS, "starrailexpress", "ponder")
INTRO = os.path.join(ASSETS, "role_modifier_intro", "lang", "zh_cn.json")
NAMES = os.path.join(ASSETS, "role_name_goals", "lang", "zh_cn.json")

LOCALES = ("zh_cn", "en_us", "zh_tw")
COLOR_RE = re.compile(r"§[0-9a-fk-orA-FK-OR]")

# 通用胜利目标：不是介绍原文，但属于游戏规则本身，允许出现在第一段。
GENERIC_GOALS = {
    "击杀所有杀手，或拖到游戏时间耗尽取得胜利",
    "协助平民取得胜利",
    "击杀所有平民取得胜利",
    "协助杀手阵营取得胜利",
    "协助好人取得胜利",
}

errors = []
warnings = []


def fail(where, message):
    errors.append("%s: %s" % (where, message))


def warn(where, message):
    warnings.append("%s: %s" % (where, message))


def load(path):
    with io.open(path, encoding="utf-8-sig") as f:
        return json.load(f)


def clean(text):
    return COLOR_RE.sub("", text or "").replace("%%", "%").strip()


def check_scene(path):
    name = os.path.basename(path)
    try:
        scene = load(path)
    except Exception as error:
        fail(name, "JSON 解析失败：%s" % error)
        return None
    for key in ("formatVersion", "id", "items", "title", "structures", "tags", "customTags", "scenes"):
        if key not in scene:
            fail(name, "缺少字段 %s" % key)
    if not scene.get("items"):
        fail(name, "items 为空（Ponder 界面无法打开）")
    for key in ("en_us", "zh_cn", "zh_tw"):
        if not scene.get("title", {}).get(key):
            fail(name, "标题缺少语言 %s" % key)
    for structure in scene.get("structures", []):
        nbt = os.path.join(STRUCTURES, structure.split(":")[-1] + ".nbt")
        if not os.path.exists(nbt):
            fail(name, "结构不存在：%s" % structure)
    if scene.get("tags") is None or scene.get("customTags") is None:
        fail(name, "tags / customTags 不能为 null")

    segment_ids = set()
    for segment in scene.get("scenes", []):
        sid = segment.get("id")
        where = "%s#%s" % (name, sid)
        if not sid:
            fail(where, "段落缺少 id")
            continue
        if sid in segment_ids:
            fail(where, "段落 id 重复")
        segment_ids.add(sid)
        for key in LOCALES:
            if not segment.get("title", {}).get(key):
                fail(where, "段落标题缺少语言 %s" % key)
        steps = segment.get("steps") or []
        if not steps:
            fail(where, "段落没有步骤")
            continue
        if steps[0].get("type") != "show_structure":
            fail(where, "首步不是 show_structure（实际 %s）" % steps[0].get("type"))

        now = 0
        pending = None  # (start, duration, point)
        points = []
        for index, step in enumerate(steps):
            kind = step.get("type")
            if kind == "starrailexpress:role_player":
                pass
            if isinstance(kind, str) and kind.startswith("starrailexpress:") and "parameters" not in step:
                fail(where, "自定义步骤 %s 的参数没有写在 parameters 里" % kind)
            if kind == "idle":
                now += int(step.get("duration", 0))
            elif kind == "text":
                duration = int(step.get("duration", 60))
                text = step.get("text") or {}
                for key in LOCALES:
                    if not text.get(key):
                        fail(where, "文本缺少语言 %s" % key)
                point = step.get("point")
                if point is None:
                    fail(where, "文本缺少 point")
                else:
                    if point in points:
                        fail(where, "同段文本 point 重复：%s" % point)
                    points.append(point)
                if pending is not None:
                    start, prev_duration, _ = pending
                    if now < start + prev_duration:
                        fail(where, "文本重叠：上一条 %d tick 后开始新文本，间隔不足"
                             % (start + prev_duration - now))
                pending = (now, duration, point)
                now += 0  # text 不推进时间轴
        if pending is not None:
            start, duration, _ = pending
            if now < start + duration:
                fail(where, "最后一条文本之后 idle 不足（差 %d tick）" % (start + duration - now))
    return scene


def main():
    if not os.path.isdir(SCENES_DIR):
        print("找不到场景目录")
        return 1
    files = sorted(f for f in os.listdir(SCENES_DIR) if f.endswith(".json"))
    scenes = {}
    for filename in files:
        scene = check_scene(os.path.join(SCENES_DIR, filename))
        if scene:
            scenes[scene["id"]] = scene

    # 场景索引（Ponderer Lib 的清单格式是 <命名空间>:<路径>）
    content = load(CONTENT)
    registered = set(content.get("scenes", []))
    for filename in files:
        rel = "starrailexpress:ponderer/scenes/" + filename
        if rel not in registered:
            fail("content", "场景未登记：%s" % rel)
    for rel in registered:
        filename = rel.replace(":", "/").split("/")[-1]
        if not os.path.exists(os.path.join(SCENES_DIR, filename)):
            fail("content", "索引指向不存在的场景：%s" % rel)

    # 正文是否逐句取自介绍原文
    intro = load(INTRO)
    names = load(NAMES)
    goal_prefix = "announcement.star.goals."
    for scene_id, scene in scenes.items():
        role_id = scene_id.replace("starrailexpress:role_tutorial.", "")
        raw = intro.get("info.screen.roleid." + role_id)
        if raw is None:
            continue
        allowed = {clean(line) for line in raw.split("\n") if clean(line)}
        goal = names.get(goal_prefix + role_id)
        if goal:
            allowed.add(clean(goal))
        allowed |= GENERIC_GOALS
        for segment in scene.get("scenes", []):
            for step in segment.get("steps", []):
                if step.get("type") != "text":
                    continue
                value = clean((step.get("text") or {}).get("zh_cn", ""))
                if value and value not in allowed:
                    warn(scene_id, "正文不是介绍原文：%s" % value[:40])

    print("scenes checked:", len(files))
    print("errors:", len(errors))
    for item in errors[:60]:
        print("  ERROR", item)
    print("warnings:", len(warnings))
    for item in warnings[:40]:
        print("  WARN ", item)
    if len(warnings) > 40:
        print("  ... 其余 %d 条警告省略" % (len(warnings) - 40))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
