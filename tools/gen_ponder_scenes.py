# -*- coding: utf-8 -*-
"""生成全部职业与事件的 Ponder 思索场景。

用法：python tools/gen_ponder_scenes.py

输入：
  - assets/role_modifier_intro/lang/{zh_cn,en_us,zh_tw}.json 的 info.screen.roleid.<id>
  - assets/role_name_goals/lang/*.json 的 announcement.star.role.<id> / announcement.star.goals.<id>
  - tools/ponder_role_facts.json（tools/extract_ponder_role_facts.py 的产物）

输出：
  - assets/starrailexpress/ponderer/scenes/role_tutorial_<id>.json
  - assets/starrailexpress/ponderer/content/role_tutorials.json（场景索引）

规则（见 docs/PONDERER_NOTES.md 与 docs/职业思索系统全覆盖规划.md）：
  - 教程正文逐句引用介绍原文，不自行改写；
  - 每段一个功能，段落 id 用功能名；
  - 每条 text 后面补足 idle（≥ duration），同段 text 的 point 互不相同；
  - 所有文本三语齐全；tags / customTags 不写 null。
"""

import io
import json
import os
import re
from collections import OrderedDict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets")
SCENES_DIR = os.path.join(ASSETS, "starrailexpress", "ponderer", "scenes")
CONTENT = os.path.join(ASSETS, "starrailexpress", "ponderer", "content", "role_tutorials.json")
FACTS = os.path.join(ROOT, "tools", "ponder_role_facts.json")
REPORT = os.path.join(ROOT, "tools", "ponder_scene_report.json")

LOCALES = ("zh_cn", "en_us", "zh_tw")

# 其它模式职业：不做普通模式教程。
OTHER_MODE = {
    "custom_pending", "discovery_civilian", "hide_and_seek_seeker", "super_loose_end",
    "ghost_loose_end", "dirt_id", "goose", "duck",
    "echo_carrier", "echo_interceptor", "echo_broker",
}
# 占位条目：没有职业注册。
PLACEHOLDER = {"error404_puppet", "example_neutral", "null", "unknown"}
# 事件条目：不是可选职业，但同样有思索。
EVENTS = ("command_block", "night_event", "halloween_event")
# 已有的场景，不覆盖。
EXISTING = {"bartender", "jester", "recaller"}

# ---------------------------------------------------------------- 结构

# 结构 key -> (nbt 名, 玩家站位, 第二名玩家站位)
STRUCTURES = {
    "common": ("role_tutorial_common", [3.0, 1.0, 3.0], [5.0, 1.0, 2.0]),
    "room": ("role_tutorial_room", [3.0, 1.0, 3.0], [5.0, 1.0, 2.0]),
    "carriage": ("role_tutorial_carriage", [4.0, 1.0, 3.0], [6.0, 1.0, 2.0]),
    "corridor": ("role_tutorial_corridor", [3.0, 1.0, 6.0], [5.0, 1.0, 7.0]),
    "graveyard": ("role_tutorial_graveyard", [3.0, 1.0, 3.0], [5.0, 1.0, 2.0]),
    "water": ("role_tutorial_water", [3.0, 1.0, 3.0], [3.0, 1.0, 4.0]),
    "command_room": ("event_command_room", [3.0, 1.0, 3.0], [5.0, 1.0, 2.0]),
    "night_town": ("event_night_town", [4.0, 1.0, 3.0], [6.0, 1.0, 3.0]),
    "halloween_porch": ("event_halloween_porch", [3.0, 1.0, 4.0], [5.0, 1.0, 3.0]),
}

# 结构选择关键词（按优先级从上到下）。
STRUCTURE_RULES = [
    ("carriage", ["车厢", "列車", "列车", "火车", "车厢里", "车顶", "车厢"]),
    ("water", ["水下", "泳池", "游泳", "潜水", "水里", "水池"]),
    ("graveyard", ["墓地", "棺材", "坟", "墓碑", "灵堂", "尸体", "遺體", "遗体"]),
    ("corridor", ["走廊", "闸机", "检票", "铁门", "闸门", "通道", "车厢连接处"]),
    ("room", ["房间", "地图", "宿舍", "客厅", "办公室", "据点", "住处"]),
]

# ---------------------------------------------------------------- 段落划分

# 段落顺序即场景里的分段顺序。
SEGMENT_ORDER = ["items", "skill", "domain", "screen", "shop", "body", "panel",
                 "vision", "mood", "vitals", "bond", "form", "details"]

# 关键词 -> 段落。命中数多的段落优先；同数时按下面的先后顺序。
SEGMENT_KEYWORDS = OrderedDict([
    ("domain", ["领域", "領域"]),
    ("form", ["疯狂模式", "瘋狂模式", "狂暴", "形态", "形態", "变身", "變身",
              "进化", "進化", "阶段", "階段", "时刻", "時刻", "伪装", "偽裝", "外观"]),
    ("bond", ["绑定", "綁定", "追随", "追隨", "寄宿", "附身", "队友", "隊友",
              "同伴", "跟随", "跟隨", "另一名玩家", "一起生成", "一同生成"]),
    ("vision", ["透视", "透視", "直觉", "直覺", "发光", "發光", "高亮", "视野", "視野",
                "看见", "看見", "看到", "洞察", "标记", "標記", "轮廓", "輪廓"]),
    ("mood", ["心情", "san值", "san 值", "理智", "恐慌", "精神值", "san", "SAN"]),
    ("vitals", ["虚拟血量", "虛擬血量", "血量", "护盾", "護盾", "生命值", "回血", "治疗", "治療"]),
    ("panel", ["界面", "筆記", "笔记", "信件", "记录", "記錄", "终端", "終端", "面板",
               "清单", "清單", "查看", "纸条", "書籤", "书签", "任务列表", "日記", "日记"]),
    ("body", ["尸体", "屍體", "遗体", "遺體", "死后", "死後"]),
    ("screen", ["选择玩家", "選擇玩家", "选人", "指定一名玩家", "目标玩家", "目標玩家", "选一个玩家"]),
    ("skill", ["技能", "冷卻", "冷却", "按下", "主动", "主動", "被动", "被動",
               "CD", "施放", "釋放", "释放", "按键", "按鍵", "开关", "開關"]),
    ("shop", ["商店", "購買", "购买", "售价", "售價", "金币", "金幣", "花费", "花費"]),
    ("items", ["开局", "開局", "初始", "自带", "自帶", "持有", "拥有", "擁有", "随身", "隨身",
               "装备", "裝備", "道具", "物品", "物品栏", "物品欄", "右键", "右鍵", "手持"]),
])

# 段落 -> 演示步骤的构造方式
SEGMENT_TITLE_KEY = {
    "items": "ponder.starrailexpress.label.items",
    "skill": "ponder.starrailexpress.label.skill",
    "screen": "ponder.starrailexpress.screen.select_player",
    "shop": "gui.starrailexpress.shop.role_shop",
    "body": "ponder.starrailexpress.label.body",
    "panel": "ponder.starrailexpress.label.notes",
    "vision": "ponder.starrailexpress.label.vision",
    "mood": "ponder.starrailexpress.label.sanity",
    "vitals": "ponder.starrailexpress.label.vitals",
    "bond": "ponder.starrailexpress.label.binding",
    "form": "ponder.starrailexpress.label.psycho",
    "domain": "ponder.starrailexpress.step.role_domain",
    "details": "ponder.starrailexpress.step.role_render",
}

# 领域 -> 职业 id 映射（领域等级与服务端 ModEffects.DOMAIN_MARK 一一对应）。
DOMAIN_BY_ROLE = {
    "the_fool": "fool_meeting",
    "warlock": "warlock_ash_marrow",
    "adventurer": "adventurer_travelogue",
    "pooler": "pooler_moon",
    "roulette_devil": "devil_black_room",
}

# 领域技相关句子：把这些句子挪到领域段落里，别让领域段只有画面没有解说。
DOMAIN_KEYWORDS = {
    "the_fool": ["愚者", "塔罗", "开会", "会议"],
    "warlock": ["角斗场", "领域", "祭场", "灰髓"],
    "adventurer": ["游记", "旅途", "格罗赛尔", "营地"],
    "pooler": ["吞月", "水中镜", "月球", "水缸"],
    "roulette_devil": ["轮盘", "决斗", "黑室", "赌局"],
}

# 阵营首行关键词 -> 胜利目标类别
FACTION_CIVILIAN = "civilian"
FACTION_VIGILANTE = "vigilante"
FACTION_KILLER = "killer"
FACTION_KILLER_NEUTRAL = "killer_neutral"
FACTION_GOOD_NEUTRAL = "good_neutral"
FACTION_INDEPENDENT = "independent"
FACTION_SPECIAL = "special"

GENERIC_GOALS = {
    FACTION_CIVILIAN: (
        "击杀所有杀手，或拖到游戏时间耗尽取得胜利",
        "Kill all the killers, or survive until the match timer runs out",
        "殺死所有殺手，或拖到遊戲時間耗盡取得勝利"),
    FACTION_VIGILANTE: (
        "协助平民取得胜利",
        "Help the passengers win the match",
        "協助平民取得勝利"),
    FACTION_KILLER: (
        "击杀所有平民取得胜利",
        "Kill all the passengers to win the match",
        "殺死所有平民取得勝利"),
    FACTION_KILLER_NEUTRAL: (
        "协助杀手阵营取得胜利",
        "Help the killers win the match",
        "協助殺手陣營取得勝利"),
    FACTION_GOOD_NEUTRAL: (
        "协助好人取得胜利",
        "Help the good side win the match",
        "協助好人取得勝利"),
}

# 次行里属于「阵营关系」的标记。
RELATION_MARKERS = [
    (FACTION_KILLER_NEUTRAL, ["与杀手一同获胜", "與殺手一同獲勝"]),
    (FACTION_GOOD_NEUTRAL, ["与平民一同获胜", "與平民一同獲勝", "与好人一同获胜", "與好人一同獲勝"]),
    (FACTION_INDEPENDENT, ["拥有独立胜利条件", "擁有獨立勝利條件"]),
]

# 一条介绍最多引用多少句（超过部分只留在介绍页，规划文档第 9 节）。
MAX_QUOTED_LINES = 12

# 兜底物品：场景的 items 字段不能为空。
FALLBACK_ITEMS = ["trainmurdermystery:note", "trainmurdermystery:bat",
                  "trainmurdermystery:knife"]

COLOR_RE = re.compile(r"§[0-9a-fk-orA-FK-OR]")


def load_json(path):
    with io.open(path, encoding="utf-8-sig") as f:
        return json.load(f)


def clean(text):
    """去掉无效颜色码、把 %% 还原成 %。"""
    if not text:
        return ""
    text = COLOR_RE.sub("", text)
    text = text.replace("%%", "%")
    return text.strip()


def tr(values):
    """把三语文本整理成场景 JSON 的 {en_us, zh_cn, zh_tw} 对象。"""
    return {
        "en_us": values[1] or values[0],
        "zh_cn": values[0],
        "zh_tw": values[2] or values[0],
    }


def argb(rgb):
    """0xRRGGBB -> 带不透明 alpha 的有符号 ARGB。"""
    value = (0xFF000000 | (rgb & 0xFFFFFF)) & 0xFFFFFFFF
    return value - 0x100000000 if value >= 0x80000000 else value


def classify(lines):
    """把介绍句子分配到段落。"""
    buckets = {key: [] for key in SEGMENT_KEYWORDS}
    buckets["details"] = []
    for line in lines:
        best = None
        best_score = 0
        for key, words in SEGMENT_KEYWORDS.items():
            score = sum(1 for word in words if word in line)
            if score > best_score:
                best = key
                best_score = score
        buckets[best or "details"].append(line)
    return buckets


def pick_structure(text):
    for key, words in STRUCTURE_RULES:
        if any(word in text for word in words):
            return key
    return "common"


def faction_of(first_line, relation_line):
    """按首行 + 关系行判断胜利目标类别。"""
    if "警长阵营" in first_line or "警長陣營" in first_line:
        return FACTION_VIGILANTE
    if "杀手阵营" in first_line or "殺手陣營" in first_line:
        return FACTION_KILLER
    if "平民阵营" in first_line or "平民陣營" in first_line:
        return FACTION_CIVILIAN
    for kind, markers in RELATION_MARKERS:
        if any(marker in relation_line for marker in markers):
            return kind
    return FACTION_SPECIAL


def relation_of(line):
    """次行是不是阵营关系行。"""
    for kind, markers in RELATION_MARKERS:
        if any(marker in line for marker in markers):
            return kind, line
    if "仅由命令方块事件产生" in line or "僅由命令方塊事件產生" in line:
        return FACTION_KILLER, line
    if "不会分配给玩家" in line or "不會分配給玩家" in line:
        return FACTION_SPECIAL, line
    return None, None


def text_duration(text):
    length = max(len(part) for part in text.values())
    return max(55, min(190, 40 + int(length * 1.8)))


def make_text(text, point, duration):
    return {
        "type": "text",
        "duration": duration,
        "text": text,
        "point": point,
        "placeNearTarget": True,
        "attachKeyFrame": True,
    }


def player_step(pos, link_id, skin=None, outline=None, held=None):
    params = {"pos": pos, "yaw": 180.0, "linkId": link_id}
    if skin:
        params["skin"] = skin
    if outline is not None:
        params["outline"] = outline
    if held:
        params["heldItem"] = held
    return {"type": "starrailexpress:role_player", "attachKeyFrame": True, "parameters": params}


def show_structure(nbt):
    return {"type": "show_structure", "structure": "starrailexpress:" + nbt,
            "scale": 1.0, "attachKeyFrame": True}


class SceneBuilder:
    """一个段落的步骤收集器，负责文本位置与时间轴自检。"""

    def __init__(self, origin):
        self.origin = origin
        self.steps = []
        self.text_count = 0

    def add(self, step):
        self.steps.append(step)

    def point(self, index):
        x, y, z = self.origin
        return [x, round(y + 0.8 + 0.45 * index, 2), z]

    def say(self, text):
        duration = text_duration(text)
        self.add(make_text(text, self.point(self.text_count), duration))
        self.text_count += 1
        self.add({"type": "idle", "duration": duration + 10})
        return duration

    def finish(self):
        if self.steps:
            self.add({"type": "rotate_camera_y", "degrees": 12.0, "duration": 50,
                      "attachKeyFrame": True})


def build_role_scene(rid, facts, intros, names, goals):
    """生成一个职业的思索场景。"""
    intro = {loc: [clean(line) for line in intros[loc].get("info.screen.roleid." + rid, "").split("\n")]
             for loc in LOCALES}
    # 三语句子按索引对齐：zh_cn 的行数为准。
    zh_lines = [line for line in intro["zh_cn"] if line]
    en_lines = [line for line in intro["en_us"] if line]
    tw_lines = [line for line in intro["zh_tw"] if line]

    def trio(index):
        zh = zh_lines[index] if index < len(zh_lines) else ""
        en = en_lines[index] if index < len(en_lines) else zh
        tw = tw_lines[index] if index < len(tw_lines) else zh
        return {"en_us": en or zh, "zh_cn": zh, "zh_tw": tw or zh}

    if not zh_lines:
        return None

    faction_line = trio(0)
    relation_kind, relation_index = None, None
    for index in range(1, min(len(zh_lines), 3)):
        kind, _ = relation_of(zh_lines[index])
        if kind:
            relation_kind = kind
            relation_index = index
            break

    content_indices = [i for i in range(1, len(zh_lines))
                       if i != relation_index]
    content = [trio(i) for i in content_indices]

    text_all = " ".join(zh_lines)
    structure_key = pick_structure(text_all)
    nbt, player_pos, second_pos = STRUCTURES[structure_key]

    role_color = facts.get("color")
    outline_color = argb(role_color) if role_color is not None else -65536

    buckets = classify([zh_lines[i] for i in content_indices])
    # 领域技的解说句挪到领域段落
    domain_words = DOMAIN_KEYWORDS.get(rid)
    if domain_words:
        moved = []
        for key in list(buckets):
            if key == "domain":
                continue
            keep = []
            for line in buckets[key]:
                if any(word in line for word in domain_words):
                    moved.append(line)
                else:
                    keep.append(line)
            buckets[key] = keep
        if moved:
            buckets["domain"] = moved + (buckets.get("domain") or [])
    # 句子 -> 三语对象
    sentence_map = {}
    for position, index in enumerate(content_indices):
        sentence_map[zh_lines[index]] = trio(index)

    # 段内文本分配
    assignment = OrderedDict()
    used = 0
    assigned = set()
    for key in SEGMENT_ORDER:
        if key == "details":
            continue
        lines = buckets.get(key) or []
        if not lines:
            continue
        take = lines[:max(0, MAX_QUOTED_LINES - used)]
        if not take:
            continue
        assignment[key] = [sentence_map[line] for line in take]
        assigned.update(take)
        used += len(take)
    rest = [line for line in (buckets.get("details") or []) if line not in assigned]
    if rest and used < MAX_QUOTED_LINES:
        take = rest[:MAX_QUOTED_LINES - used]
        assignment["details"] = [sentence_map[line] for line in take]
        used += len(take)
        assigned.update(take)
        rest = rest[len(take):]

    # 胜利目标
    faction = faction_of(zh_lines[0], zh_lines[relation_index] if relation_index else "")
    goal_text = None
    goal_values = goals.get(rid, "")
    if goal_values:
        goal_text = {"en_us": clean(goal_values.get("en_us", "")) or clean(goal_values.get("zh_cn", "")),
                     "zh_cn": clean(goal_values.get("zh_cn", "")),
                     "zh_tw": clean(goal_values.get("zh_tw", "")) or clean(goal_values.get("zh_cn", ""))}
        if not goal_text["zh_cn"]:
            goal_text = None
    if goal_text is None and faction in GENERIC_GOALS:
        goal_text = tr(GENERIC_GOALS[faction])

    role_name = names.get(rid, {})
    title = {"en_us": role_name.get("en_us") or role_name.get("zh_cn") or rid,
             "zh_cn": role_name.get("zh_cn") or rid,
             "zh_tw": role_name.get("zh_tw") or role_name.get("zh_cn") or rid}

    scenes = []

    # ---- 第一段：阵营与胜利目标 ----
    builder = SceneBuilder(player_pos)
    builder.add(show_structure(nbt))
    builder.add(player_step(player_pos, rid + "_main"))
    builder.add({"type": "idle", "duration": 20})
    builder.say(faction_line)
    if relation_index is not None:
        builder.say(trio(relation_index))
    if goal_text:
        builder.say(goal_text)
    builder.finish()
    scenes.append({"id": "faction", "title": title, "steps": builder.steps})

    # ---- 其余段落 ----
    for key in SEGMENT_ORDER:
        if key == "details" and "details" not in assignment:
            continue
        lines = assignment.get(key)
        # 技能界面：职业在背包里有「选人列表」时一定要演示这个界面，
        # 即使介绍里没有单独一句描述它（神权冷笑 / 巫毒师 / 变形者等）。
        forced = key == "screen" and bool(facts.get("inventoryScreen"))
        if key == "domain" and rid in DOMAIN_BY_ROLE:
            forced = True
        if not lines and not forced:
            continue
        lines = lines or []
        builder = SceneBuilder(player_pos)
        builder.add(show_structure(nbt))
        builder.add(player_step(player_pos, rid + "_main"))

        if key == "items":
            initial = facts.get("initialItems") or []
            shop = facts.get("shopItems") or []
            if initial:
                builder.add({"type": "starrailexpress:role_items", "attachKeyFrame": True,
                             "parameters": {"role": rid,
                                            "origin": [player_pos[0] - 1.4, player_pos[1] + 0.55,
                                                       player_pos[2] - 1.4],
                                            "spacing": 0.7, "max": 4}})
            elif shop:
                builder.add({"type": "create_item_entity", "attachKeyFrame": True,
                             "pos": [player_pos[0] - 1.4, player_pos[1] + 0.55,
                                     player_pos[2] - 1.4],
                             "motion": [0.0, 0.0, 0.0], "count": 1,
                             "item": shop[0], "linkId": rid + "_item"})
        elif key == "skill":
            builder.add({"type": "starrailexpress:role_key", "attachKeyFrame": True,
                         "parameters": {"point": [player_pos[0], player_pos[1] + 1.9, player_pos[2]],
                                        "direction": "down", "duration": 60}})
            builder.add({"type": "starrailexpress:role_skill", "attachKeyFrame": True,
                         "parameters": {"label": SEGMENT_TITLE_KEY["skill"],
                                        "lines": [dict(line) for line in lines]}})
        elif key == "screen":
            builder.add({"type": "starrailexpress:role_screen", "attachKeyFrame": True,
                         "parameters": {"title": SEGMENT_TITLE_KEY["screen"],
                                        "hint": "ponder.starrailexpress.screen.pick_hint",
                                        "columns": 6}})
        elif key == "shop":
            builder.add({"type": "starrailexpress:role_shop", "attachKeyFrame": True,
                         "parameters": {"role": rid, "columns": 4}})
        elif key == "body":
            # 尸体演示：另起一个「受害者」模型，让他倒下消失并在同一位置留下尸体，
            # 职业本人站在旁边观察——这才是「尸体交互」类职业的真实画面。
            builder.add(player_step(second_pos, rid + "_victim"))
            builder.add({"type": "starrailexpress:role_death", "attachKeyFrame": True,
                         "parameters": {"linkId": rid + "_victim",
                                        "pos": second_pos,
                                        "name": "ponder.starrailexpress.label.victim",
                                        "delay": 14}})
        elif key == "panel":
            builder.add({"type": "starrailexpress:role_panel", "attachKeyFrame": True,
                         "parameters": {"title": SEGMENT_TITLE_KEY["panel"],
                                        "lines": [dict(line) for line in lines]}})
        elif key == "vision":
            builder.add(player_step(second_pos, rid + "_target", outline=outline_color))
            builder.add({"type": "starrailexpress:role_render", "attachKeyFrame": True,
                         "parameters": {"label": SEGMENT_TITLE_KEY["vision"],
                                        "lines": [dict(line) for line in lines]}})
        elif key == "mood":
            builder.add({"type": "starrailexpress:role_mood", "attachKeyFrame": True,
                         "parameters": {"label": SEGMENT_TITLE_KEY["mood"],
                                        "before": 0.35, "after": 0.85,
                                        "lines": [dict(line) for line in lines]}})
        elif key == "vitals":
            builder.add({"type": "starrailexpress:role_vitals", "attachKeyFrame": True,
                         "parameters": {"bars": [
                             {"label": "ponder.starrailexpress.label.vitals",
                              "before": 0.4, "after": 1.0, "color": -65536},
                             {"label": "ponder.starrailexpress.label.shield",
                              "before": 0.0, "after": 0.7, "color": -16776961}],
                             "lines": [dict(line) for line in lines]}})
        elif key == "bond":
            builder.add(player_step(second_pos, rid + "_ally", outline=outline_color))
            builder.add({"type": "starrailexpress:role_bond", "attachKeyFrame": True,
                         "parameters": {"lines": [dict(line) for line in lines],
                                        "color": outline_color}})
        elif key == "form":
            skin = facts.get("psychoSkin")
            builder.add(player_step(second_pos, rid + "_psycho",
                                    skin=("starrailexpress:" + skin) if skin else None,
                                    outline=outline_color))
            builder.add({"type": "starrailexpress:role_disguise", "attachKeyFrame": True,
                         "parameters": {"label": SEGMENT_TITLE_KEY["form"],
                                        "lines": [dict(line) for line in lines]}})
        elif key == "domain":
            domain = DOMAIN_BY_ROLE.get(rid)
            if domain:
                builder.add({"type": "starrailexpress:role_domain", "attachKeyFrame": True,
                             "parameters": {"domain": domain, "pos": [0, 0, 0]}})
        for line in lines:
            builder.say(line)
        builder.finish()
        scenes.append({"id": key, "title": title, "steps": builder.steps})

    items = []
    for candidate in facts.get("initialItems", []) + facts.get("shopItems", []):
        if candidate not in items:
            items.append(candidate)
    if not items:
        items = list(FALLBACK_ITEMS)
    scene = {
        "formatVersion": 1,
        "id": "starrailexpress:role_tutorial." + rid,
        "items": items[:4],
        "title": title,
        "structures": ["starrailexpress:" + nbt],
        "tags": [],
        "customTags": {},
        "scenes": scenes,
    }
    return scene, used, len(rest)


# ---------------------------------------------------------------- 事件场景


def event_scene(rid, facts, intros, names, goals):
    """三个事件条目的思索：按各自的机制分段。"""
    intro = {loc: [clean(line) for line in intros[loc].get("info.screen.roleid." + rid, "").split("\n")]
             for loc in LOCALES}
    zh_lines = [line for line in intro["zh_cn"] if line]
    en_lines = [line for line in intro["en_us"] if line]
    tw_lines = [line for line in intro["zh_tw"] if line]

    def trio(index):
        zh = zh_lines[index] if index < len(zh_lines) else ""
        en = en_lines[index] if index < len(en_lines) else zh
        tw = tw_lines[index] if index < len(tw_lines) else zh
        return {"en_us": en or zh, "zh_cn": zh, "zh_tw": tw or zh}

    role_name = names.get(rid, {})
    title = {"en_us": role_name.get("en_us") or role_name.get("zh_cn") or rid,
             "zh_cn": role_name.get("zh_cn") or rid,
             "zh_tw": role_name.get("zh_tw") or role_name.get("zh_cn") or rid}

    if rid == "command_block":
        nbt, player_pos, second_pos = STRUCTURES["command_room"]
        segments = [
            ("event_start", [0, 1, 2, 3], None),
            ("abnormal", [4, 5, 6, 7], "second"),
            ("field_event", [8, 9], None),
            ("settle", [10, 11], None),
        ]
        ambience = {"color": 0x6A2CA8, "alpha": 0.42, "mode": "sky"}
    elif rid == "night_event":
        nbt, player_pos, second_pos = STRUCTURES["night_town"]
        segments = [
            ("nightfall", [0, 1, 2], None),
            ("flashlight", [3], None),
            ("undead", [4], "second"),
            ("restore", [], None),
        ]
        ambience = {"color": 0x101828, "alpha": 0.55, "mode": "fog"}
    else:
        nbt, player_pos, second_pos = STRUCTURES["halloween_porch"]
        segments = [
            ("village", [0, 1, 2], None),
            ("knock", [3, 4], None),
            ("contract", [5, 6], "second"),
            ("combat", [7], None),
            ("pumpkin", [8], None),
        ]
        ambience = {"color": 0xC8712A, "alpha": 0.40, "mode": "sky"}

    scenes = []
    for index, (segment_id, line_indices, second) in enumerate(segments):
        builder = SceneBuilder(player_pos)
        builder.add(show_structure(nbt))
        builder.add({"type": "starrailexpress:role_ambience", "attachKeyFrame": True,
                     "parameters": ambience})
        builder.add(player_step(player_pos, rid + "_main"))
        if second:
            builder.add(player_step(second_pos, rid + "_other", outline=-65536))
        builder.add({"type": "idle", "duration": 20})

        if rid == "command_block":
            if segment_id == "event_start":
                builder.add({"type": "starrailexpress:role_vitals", "attachKeyFrame": True,
                             "parameters": {"bars": [
                                 {"label": "破坏进度", "before": 0.0, "after": 0.35, "color": -65536},
                                 {"label": "事件倒计时", "before": 1.0, "after": 0.6, "color": 0xFFAA44}]}})
            elif segment_id == "abnormal":
                builder.add({"type": "starrailexpress:role_disguise", "attachKeyFrame": True,
                             "parameters": {"label": "职业改写",
                                            "lines": [trio(line_indices[1])]}})
            elif segment_id == "field_event":
                builder.add({"type": "starrailexpress:role_timer", "attachKeyFrame": True,
                             "parameters": {"progress": [0.0, 0.5]}})
        elif rid == "night_event":
            if segment_id == "nightfall":
                builder.add({"type": "starrailexpress:role_timer", "attachKeyFrame": True,
                             "parameters": {"progress": [0.0, 1.0]}})
            elif segment_id == "undead":
                builder.add({"type": "starrailexpress:role_death", "attachKeyFrame": True,
                             "parameters": {"linkId": rid + "_main", "pos": player_pos,
                                            "name": "ponder.starrailexpress.label.victim",
                                            "delay": 12}})
        else:
            if segment_id == "village":
                builder.add({"type": "starrailexpress:role_shop", "attachKeyFrame": True,
                             "parameters": {"role": rid, "columns": 4,
                                            "items": [{"item": "minecraft:pumpkin", "price": 0,
                                                       "currency": "money"}]}})
            elif segment_id == "combat":
                builder.add({"type": "starrailexpress:role_render", "attachKeyFrame": True,
                             "parameters": {"label": "万圣节亡灵"}})

        for line_index in line_indices:
            builder.say(trio(line_index))
        builder.finish()
        scenes.append({"id": segment_id, "title": title, "steps": builder.steps})

    scene = {
        "formatVersion": 1,
        "id": "starrailexpress:role_tutorial." + rid,
        "items": ["trainmurdermystery:note"],
        "title": title,
        "structures": ["starrailexpress:" + nbt],
        "tags": [],
        "customTags": {},
        "scenes": scenes,
    }
    return scene


# ---------------------------------------------------------------- 主流程


def main():
    intros = {loc: load_json(os.path.join(ASSETS, "role_modifier_intro", "lang", loc + ".json"))
               for loc in LOCALES}
    name_data = {loc: load_json(os.path.join(ASSETS, "role_name_goals", "lang", loc + ".json"))
                 for loc in LOCALES}
    names = {}
    for loc in LOCALES:
        for key, value in name_data[loc].items():
            if key.startswith("announcement.star.role."):
                names.setdefault(key[len("announcement.star.role."):], {})[loc] = value
    goals = {}
    for loc in LOCALES:
        for key, value in name_data[loc].items():
            if key.startswith("announcement.star.goals."):
                goals.setdefault(key[len("announcement.star.goals."):], {})[loc] = value

    facts_all = load_json(FACTS)["roles"]

    written = []
    report = {"roles": {}, "omitted": {}}

    for rid in sorted(intros["zh_cn"].keys()):
        if not rid.startswith("info.screen.roleid.") or rid.endswith(".simple"):
            continue
        role_id = rid[len("info.screen.roleid."):]
        if role_id in OTHER_MODE or role_id in PLACEHOLDER or role_id in EXISTING:
            continue
        facts = facts_all.get(role_id, {})
        if role_id in EVENTS:
            scene = event_scene(role_id, facts, intros, names, goals)
            omitted = 0
        else:
            built = build_role_scene(role_id, facts, intros, names, goals)
            if built is None:
                continue
            scene, used, omitted = built
        path = os.path.join(SCENES_DIR, "role_tutorial_%s.json" % role_id)
        with io.open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(json.dumps(scene, ensure_ascii=False, indent=2))
            f.write("\n")
        written.append("starrailexpress:ponderer/scenes/role_tutorial_%s.json" % role_id)
        report["roles"][role_id] = {
            "segments": [item["id"] for item in scene["scenes"]],
            "structure": scene["structures"][0],
            "texts": sum(1 for item in scene["scenes"] for step in item["steps"]
                         if step.get("type") == "text"),
        }
        if omitted:
            report["omitted"][role_id] = omitted

    # 场景索引：保留已有条目，按名称排序补齐。
    # Ponderer Lib 的清单格式是 `<命名空间>:<路径>`（见 Ponderer docs/PONDERER_LIB.md），
    # 历史上写成 `<命名空间>/<路径>` 的条目在这里一并纠正。
    content = load_json(CONTENT)
    merged = []
    for path in content.get("scenes", []):
        if "/" in path and ":" not in path:
            namespace, _, rest = path.partition("/")
            path = namespace + ":" + rest
        if path not in merged:
            merged.append(path)
    for path in written:
        if path not in merged:
            merged.append(path)
    merged.sort()
    content["scenes"] = merged
    with io.open(CONTENT, "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(content, ensure_ascii=False, indent=2))
        f.write("\n")

    report["total"] = len(written)
    with io.open(REPORT, "w", encoding="utf-8") as f:
        f.write(json.dumps(report, ensure_ascii=False, indent=1))
    print("scenes written:", len(written))
    print("content entries:", len(merged))
    print("roles with omitted lines:", len(report["omitted"]))


if __name__ == "__main__":
    main()
