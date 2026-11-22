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

import glob
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


def skill_lines(ctx, keywords):
    """按关键词从整篇介绍里挑技能句。

    分类器会把「查看他的物品栏界面」归到 panel、「开局拥有水晶球」归到 items，
    但这些都是技能的一部分。专属 override 必须按自己的关键词取全文句子，
    只用 ctx["lines"]（skill 桶）会把大半机制漏掉。
    """
    found = [line for line in ctx.get("all_lines", [])
             if line.get("zh_cn") and any(word in line["zh_cn"] for word in keywords)]
    return found or ctx["lines"]


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


# ---------------------------------------------------------------- 技能演绎
#
# 技能段落不能只摆一个键帽 + 一段说明。下面按介绍原文里真正发生的机制
# 组合出可看见的动作：按键、走动、交换位置、传送、蹲下、递交物品、
# 中毒 / 护盾 / 治愈粒子、金币与冷却变化、尸体与透视等。
# 每条介绍句都配一段对应动作，做到「按一次键 → 画面变一次」。

# 粒子 id 必须以 Java 源码为准：只按职业查 SOURCE_PARTICLE_BY_ROLE，
# 不再按介绍里的词猜粒子，也不拿 minecraft:effect 兜底。

# 源码里按职业写死的粒子，优先级高于上面的通用词表。
# 依据见 docs/PONDERER_NOTES.md §22；新增条目必须附源码文件与行号。
SOURCE_PARTICLE_BY_ROLE = {
    "alchemist": "wathe:poison",
    "arsonist": "stupid_express:arsonist_flame",
    "conspirator": "minecraft:dragon_breath",
    # CreeperRoleData:240 / NoellesrolesClient:691-730 左轮击杀爆炸的彩虹粒子。
    "creeper": "minecraft:dust_color_transition",
    "delayer": "minecraft:enchant",
    "diviner": "minecraft:enchant",
    "doctor": "minecraft:heart",
    "dreadlord": "minecraft:squid_ink",
    "dream": "minecraft:dust",
    "executioner": "minecraft:soul_fire_flame",
    "ghost_eye": "minecraft:sculk_soul",
    "glitch_robot": "minecraft:effect",
    "great_detective": "minecraft:smoke",
    "ibuki_suika": "minecraft:explosion_emitter",
    "leather_pig": "minecraft:cloud",
    "loose_end": "minecraft:portal",
    "magician": "minecraft:soul_fire_flame",
    "magic_apprentice": "minecraft:snowflake",
    # BloodBottleItem#spawnBloodParticles -> BloodMain.BLOOD_PARTICLE（noellesroles:deathblood）
    "mortician_bodymaker": "noellesroles:deathblood",
    "musician_phantom": "minecraft:portal",
    "nostalgist": "minecraft:reverse_portal",
    "nurse": "minecraft:heart",
    "paper_doll": "minecraft:cloud",
    "pooler": "minecraft:splash",
    "return_traveler": "minecraft:reverse_portal",
    "roulette_devil": "wathe:black_smoke",
    "star_gatherer": "minecraft:end_rod",
    "tailor": "minecraft:end_rod",
    "trapper": "minecraft:electric_spark",
    "undead_lord": "minecraft:witch",
    "warlock": "minecraft:soul",
    "wizard": "minecraft:lava",
    "wraith_assassin": "minecraft:soul_fire_flame",
    "zhang_angel": "minecraft:electric_spark",
}
SOUND_BY_WORD = [
    (("传送", "瞬移", "召回", "回到"), "minecraft:entity.enderman.teleport"),
    (("爆炸", "炸"), "minecraft:entity.generic.explode"),
    (("击杀", "杀死", "死亡"), "minecraft:entity.player.hurt"),
]


def scene_particle(particle, pos, count=8, duration=60, spread=None):
    return {"type": "spawn_particles", "particle": particle, "pos": list(pos),
            "distribution": "box", "spread": spread or [0.35, 0.6, 0.35],
            "count": count, "duration": duration, "motion": [0, 0.04, 0]}


def scene_sound(sound):
    return {"type": "play_sound", "sound": sound, "soundVolume": 1.0,
            "pitch": 1.0, "source": "players"}


# 物品使用产生的可见效果，逐项对照 Item / Entity 源码，不用职业粒子兜底。
# 依据见 docs/PONDERER_NOTES.md「物品使用效果」一节。
ITEM_EFFECT_BY_ID = {
    "noellesroles:blood_bottle": ("noellesroles:deathblood", "minecraft:block.grass.fall"),
    "noellesroles:purify_bomb": ("minecraft:dripping_water", "minecraft:entity.guardian.attack"),
    "noellesroles:chlorine_bomb": ("minecraft:sneeze", "minecraft:entity.generic.extinguish_fire"),
    "noellesroles:smoke_grenade": ("minecraft:campfire_cosy_smoke",
                                   "minecraft:entity.firework_rocket.blast"),
    "noellesroles:bomb": ("minecraft:explosion", "minecraft:entity.generic.explode"),
    "noellesroles:antidote": (None, "noellesroles:syringe_stab"),
    "noellesroles:passbook": (None, "minecraft:item.book.page_turn"),
}


def item_effect_steps(item_id, pos, count=10, duration=70):
    """按物品源码补粒子 / 音效；没有可见效果的物品不硬塞。"""
    effect = ITEM_EFFECT_BY_ID.get(item_id)
    if not effect:
        return []
    steps = []
    if effect[0]:
        steps.append(scene_particle(effect[0], pos, count=count, duration=duration))
    if effect[1]:
        steps.append(scene_sound(effect[1]))
    return steps


def custom(kind, params):
    return {"type": "starrailexpress:" + kind, "parameters": params, "attachKeyFrame": True}


def key_cap(point, switch=False):
    params = {"point": list(point), "direction": "down", "duration": 60}
    if switch:
        # 技能切换键（默认 Y）与技能释放键（默认 G）是两套绑定，不能混用键帽。
        params["keyBind"] = "next_ability"
        params["text"] = "ponder.starrailexpress.ponder.press_next_ability"
    else:
        params["keyBind"] = "ability"
    return custom("role_key", params)


def parse_seconds(text):
    values = re.findall(r"(\d+(?:\.\d+)?)\s*(?:秒|s\b|S\b)", text)
    return float(values[0]) if values else None


def parse_coins(text):
    values = re.findall(r"(\d+)\s*(?:金币|金幣|coin)", text, flags=re.IGNORECASE)
    return int(values[0]) if values else None


def pick_item(facts, fallback="trainmurdermystery:note"):
    for key in ("initialItems", "shopItems"):
        items = facts.get(key) or []
        if items:
            return items[0]
    return fallback


def _load_item_names():
    """物品显示名 -> 物品 id，用来按介绍句子摆出真正讲到的道具。"""
    mapping = {}
    for path in sorted(glob.glob(os.path.join(ASSETS, "*", "lang", "zh_cn.json"))):
        data = load_json(path)
        for key, value in data.items():
            parts = key.split(".")
            if len(parts) == 3 and parts[0] == "item" and value and "\n" not in value:
                name = value.strip()
                if len(name) >= 2:
                    mapping.setdefault(name, set()).add(parts[1] + ":" + parts[2])
    return mapping


ITEM_IDS_BY_NAME = _load_item_names()
# 介绍里的叫法与物品显示名不一致时（例如「加固道具」实际叫维修工具），按这里补。
ITEM_ALIASES = {
    "加固道具": "noellesroles:reinforcement",
    "鹤顶红": "noellesroles:hedinghong",
    "初始军刀": "noellesroles:sp_knife",
    "小麦": "minecraft:wheat",
    "鸡蛋": "minecraft:egg",
    "糖": "minecraft:sugar",
}
for _alias, _item_id in ITEM_ALIASES.items():
    ITEM_IDS_BY_NAME.setdefault(_alias, set()).add(_item_id)


def item_for_line(facts, zh):
    """句子里明确提到某个道具时，演示那个道具，而不是职业的第一个物品。

    同一句里出现多个道具名时按出现先后取，避免「可加固门（防一次撬棍）」
    被后面的「撬棍」抢走，摆出完全不相干的物品。
    """
    if not zh:
        return pick_item(facts)
    candidates = (facts.get("initialItems") or []) + (facts.get("shopItems") or [])
    hits = sorted((zh.find(name), -len(name), name)
                  for name in ITEM_IDS_BY_NAME if name in zh)
    for _, _, name in hits:
        ids = ITEM_IDS_BY_NAME[name]
        for item_id in candidates:
            if item_id in ids:
                return item_id
    for _, _, name in hits:
        ids = ITEM_IDS_BY_NAME[name]
        if len(ids) == 1:
            return next(iter(ids))
        # 同名物品跨命名空间时优先通用物品，避免把「左轮手枪」画成某个职业专属皮肤。
        for namespace in ("trainmurdermystery", "noellesroles", "starrailexpress"):
            item_id = namespace + ":" + name
            if item_id in ids:
                return item_id
        return sorted(ids)[0]
    return pick_item(facts)


def particle_for(text):
    for words, particle in PARTICLE_BY_WORD:
        if any(word in text for word in words):
            return particle
    return None


def particle_for_role(text, rid):
    """只使用在对应职业源码里确认过的粒子；没有证据就不发射。"""
    return SOURCE_PARTICLE_BY_ROLE.get(rid)


def sound_for(text):
    for words, sound in SOUND_BY_WORD:
        if any(word in text for word in words):
            return sound
    return None


# 这些段落里的句子才需要追加动作；body / vision / bond / form / domain / shop / screen
# 已经有各自的专属演示，再叠一套动作只会让画面变乱。
ACT_SEGMENTS = {"skill", "panel", "details", "items", "mood", "vitals"}
# 「金币」「使用」太宽：商店价格、普通道具描述都会命中，给没有技能的职业
# 平白添一个技能键帽。真正要演示的触发方式用下面这些明确的词。
ACTION_WORDS = ("技能", "按下", "右键", "左键", "按住", "蹲下", "传送", "交换", "标记",
                "查看", "中毒", "护盾", "治愈", "治疗", "冷却", "切换", "召回",
                "瞬移", "施放", "释放", "打开背包", "打开物品栏")


def has_action(zh):
    return any(word in zh for word in ACTION_WORDS)


# 这些句子讲的是「换技能」，不是「换手里的道具」。以前生成器把「切换」
# 一律演成 role_hold + 右键道具，于是葬仪等职业介绍切换键时画面里
# 出现了右键血瓶 / 钥匙，且键帽错误地显示成技能键。
SWITCH_KEY_WORDS = (
    "技能切换键", "技能切換鍵", "切换键", "切換鍵", "切换技能", "切換技能",
    "可切换技能", "切換技能", "切换模式", "切換模式", "切换法术", "切換法術",
    "切换已解锁", "切换变声", "切換變聲", "切换类型", "切換類型",
    "切换建造", "切换调制", "切換調制", "切换技能槽位", "切换两个技能",
    "切换四个技能", "切换三个技能", "切换技能之间", "【切换技能】", "【切換技能】",
)


def is_switch_line(zh):
    return any(word in zh for word in SWITCH_KEY_WORDS)


# 介绍里凡是讲到死亡，对应那一幕就要真的把玩家模型演死，不能只有文字。
DEATH_WORDS = ("死亡", "死去", "致死", "杀死", "击杀", "击倒", "阵亡", "去世", "丧生", "自尽", "殉")
IMMUNE_DEATH_WORDS = ("免疫死亡", "不会死亡", "不死亡", "防止死亡", "免除死亡", "抵挡死亡",
                      "阻止死亡", "避免死亡", "无法死亡", "不会因", "不会致死", "免于死亡",
                      "不会将其击杀", "无法击杀", "不能杀死", "无法致死", "不能致死",
                      "不会让你轻易死亡", "从死亡的边缘", "死亡标记", "非强制死亡",
                      "无法被击杀", "不会死亡")
SELF_DEATH_WORDS = ("你死亡", "当你死亡", "自己死亡", "自身死亡", "死亡时", "死亡后", "死后",
                    "被击杀", "被杀死", "被杀", "阵亡", "舍己", "自爆", "自尽")
OTHER_PLAYER_WORDS = ("场上", "有人", "其他玩家", "其它玩家", "附近的玩家", "周围有玩家",
                      "周围的玩家", "另一名玩家", "另一位", "目标玩家", "被标记的玩家")


def is_death_line(zh):
    return any(word in zh for word in DEATH_WORDS)


def is_immune_death(zh):
    return any(word in zh for word in IMMUNE_DEATH_WORDS)


def is_self_death(zh):
    if any(word in zh for word in OTHER_PLAYER_WORDS):
        return False
    return any(word in zh for word in SELF_DEATH_WORDS)


def death_act_steps(zh, rid, player_pos, second_pos, index, outline):
    """死亡介绍句对应的动画：讲自己就演主角，讲击杀/场上玩家就演目标。"""
    if not is_death_line(zh) or is_immune_death(zh):
        return []
    victim = "ponder.starrailexpress.label.victim"
    if is_self_death(zh):
        # 死亡瞬间不留尸体，立刻重建主角，避免后续句子没有模型可驱动。
        return [
            custom("role_death", {"linkId": rid + "_main", "pos": list(player_pos),
                                  "name": victim, "corpse": False}),
            player_step(player_pos, rid + "_main"),
        ]
    target_link = rid + "_death_target_" + str(index)
    return [
        player_step(second_pos, target_link, outline=outline),
        custom("role_death", {"linkId": target_link, "pos": list(second_pos), "name": victim}),
    ]


def build_skill_act(zh, rid, facts, base, target, outline, suffix="0", line=None):
    """把一条技能介绍翻译成一组动作步骤。

    返回 ``(steps, used_state_panel)``。``used_state_panel`` 用来避免同一段里
    两个左上角覆盖层叠在一起（金币 / 冷却 / 血量）。``suffix`` 让每条介绍句的
    目标玩家 / 物品实体使用独立 linkId，前一句的尸体或物品不会串到下一句。
    ``line`` 传原文的三语对象，覆盖层文案才能跟着客户端语言切换。
    """
    overlay_line = dict(line) if line else {"zh_cn": zh, "en_us": zh, "zh_tw": zh}
    px, py, pz = base
    tx, ty, tz = target
    head = [px, py + 1.9, pz]
    # 键帽挂在头顶，右键提示放到摆出的物品上方：两块控件不能共用一个 point。
    controls = [px - 0.8, py + 1.2, pz]
    steps = []
    panel = False
    link = rid + "_main"
    target_link = rid + "_skill_target_" + suffix
    item_link = rid + "_skill_item_" + suffix
    item = item_for_line(facts, zh)

    # 被动描述（「拥有护盾」「击杀获得金币」）不该有技能键帽和走动；
    # 只有明确写了触发方式的句子才演「按键 → 动作」。
    triggered = any(word in zh for word in
                    ("技能", "按下", "按键", "右键", "左键", "按住", "施放", "释放", "切换", "蹲下"))
    switched = is_switch_line(zh)

    def key():
        if triggered:
            steps.append(key_cap(head, switch=switched))

    if switched:
        # 切模式只按切换键，画面上不该出现右键道具或手持物。
        # 用方块指示表示当前模式变化，键帽显示玩家实际绑定的切换键。
        steps.append(key_cap(head, switch=True))
        steps.append({"type": "indicate_success", "blockPos": [int(px), 0, int(pz)]})
        steps.append({"type": "idle", "duration": 25})
        return steps, panel

    def walk_to(point, duration):
        if triggered:
            steps.append(custom("role_walk", {"linkId": link, "to": list(point), "duration": duration}))

    def emit(particle, point, **kwargs):
        # 源码没写粒子的机制不产生粒子，避免用 minecraft:effect 乱兜底。
        if particle:
            steps.append(scene_particle(particle, point, **kwargs))

    def particles_at(point, **kwargs):
        emit(particle_for_role(zh, rid), point, **kwargs)

    # 互动类机制需要一个目标玩家；放在模型视线前方，不与主角重合。
    target_added = False

    def add_target():
        nonlocal target_added
        if target_added:
            return
        steps.append(player_step(target, target_link, outline=outline))
        target_added = True

    # 只要这一句在讲玩家 / 其他人，画面里就必须真的有第二个玩家。
    if any(word in zh for word in ("玩家", "其他人", "他人", "目标", "对方")):
        add_target()

    if "交换" in zh and "位置" in zh:
        add_target()
        key()
        walk_to([tx, ty, tz - 0.8], 40)
        steps.append({"type": "idle", "duration": 20})
        emit(SOURCE_PARTICLE_BY_ROLE.get(rid), [px, py + 0.9, pz], count=12, duration=40)
        emit(SOURCE_PARTICLE_BY_ROLE.get(rid), [tx, ty + 0.9, tz], count=12, duration=40)
        steps.append(custom("role_teleport", {"linkId": link, "pos": [tx, ty, tz]}))
        steps.append(custom("role_teleport", {"linkId": target_link, "pos": [px, py, pz]}))
        steps.append({"type": "indicate_success", "blockPos": [int(px), int(py), int(pz)]})
        steps.append({"type": "idle", "duration": 30})
        return steps, panel

    if "标记" in zh and any(word in zh for word in ("传送", "回到", "召回", "返回", "记录")):
        mark = [int(px), 0, int(pz)]
        away = [px, py, max(1, pz - 2)]
        key()
        steps.append({"type": "highlight_section", "blockPos": mark, "blockPos2": mark,
                      "duration": 380, "color": "green"})
        steps.append({"type": "idle", "duration": 35})
        walk_to(away, 50)
        steps.append({"type": "idle", "duration": 20})
        key()
        emit(SOURCE_PARTICLE_BY_ROLE.get(rid), [away[0], away[1] + 0.9, away[2]], count=12, duration=45)
        sound = sound_for(zh)
        if sound:
            steps.append(scene_sound(sound))
        steps.append(custom("role_teleport", {"linkId": link, "pos": [px, py, pz]}))
        emit(SOURCE_PARTICLE_BY_ROLE.get(rid), [px, py + 0.9, pz], count=12, duration=45)
        steps.append({"type": "indicate_success", "blockPos": mark})
        steps.append({"type": "idle", "duration": 30})
        return steps, panel

    if any(word in zh for word in ("查看", "观察")) and any(
            word in zh for word in ("物品栏", "背包", "界面", "身上")):
        add_target()
        key()
        steps.append(custom("role_outline", {"linkId": target_link, "color": outline}))
        steps.append(custom("role_panel", {
            "title": "ponder.starrailexpress.label.items",
            "lines": [overlay_line]}))
        panel = True
        steps.append(custom("role_walk", {"linkId": target_link,
                                          "to": [tx + 1.2, ty, tz], "duration": 40}))
        steps.append({"type": "idle", "duration": 25})
        return steps, panel

    if "蹲下" in zh or "潜行" in zh:
        key()
        steps.append(custom("role_pose", {"linkId": link, "pose": "crouching"}))
        steps.append({"type": "highlight_section", "blockPos": [int(px) - 1, 0, int(pz) - 1],
                      "blockPos2": [int(px) + 1, 0, int(pz) + 1],
                      "duration": 240, "color": "green"})
        emit(particle_for_role(zh, rid), [px, py + 0.4, pz], count=10, duration=70)
        steps.append({"type": "idle", "duration": 40})
        steps.append(custom("role_pose", {"linkId": link, "pose": "standing"}))
        return steps, panel

    if any(word in zh for word in ("击杀", "杀死", "击倒", "尸体")):
        add_target()
        key()
        walk_to([tx - 0.9, ty, tz], 45)
        steps.append({"type": "idle", "duration": 15})
        emit(particle_for_role(zh, rid), [tx, ty + 1.1, tz], count=10, duration=30)
        steps.append(custom("role_death", {"linkId": target_link, "pos": [tx, ty, tz],
                                           "name": "ponder.starrailexpress.label.victim"}))
        emit(particle_for_role(zh, rid), [tx, ty + 0.6, tz], count=8, duration=45)
        steps.append({"type": "indicate_success", "blockPos": [int(tx), 0, int(tz)]})
        steps.append({"type": "idle", "duration": 30})
        return steps, panel

    if any(word in zh for word in ("中毒", "下毒", "毒药", "剧毒")):
        add_target()
        key()
        steps.append({"type": "create_item_entity", "pos": [px - 0.8, py + 0.5, pz],
                      "motion": [0.0, 0.0, 0.0], "count": 1, "item": item,
                      "linkId": item_link})
        steps.append({"type": "show_controls", "action": "right", "item": item,
                      "duration": 70, "point": controls,
                      "direction": "down"})
        walk_to([tx - 0.9, ty, tz], 40)
        emit(particle_for_role(zh, rid), [tx, ty + 0.8, tz], count=8, duration=80)
        steps.append({"type": "idle", "duration": 35})
        return steps, panel

    if any(word in zh for word in ("治愈", "治疗", "回血", "恢复", "护盾")):
        add_target()
        key()
        emit(particle_for_role(zh, rid), [tx, ty + 0.9, tz], count=10, duration=80)
        steps.append(custom("role_vitals", {"bars": [
            {"label": "ponder.starrailexpress.label.vitals", "before": 0.35,
             "after": 1.0, "color": -65536},
            {"label": "ponder.starrailexpress.label.shield", "before": 0.0,
             "after": 0.7, "color": -16776961}]}))
        panel = True
        steps.append({"type": "indicate_success", "blockPos": [int(tx), 0, int(tz)]})
        steps.append({"type": "idle", "duration": 35})
        return steps, panel

    seconds = parse_seconds(zh)
    if seconds is not None and any(word in zh for word in
                                   ("冷却", "秒", "延迟", "延遲", "无法移动", "無法移動", "持续", "持續")):
        key()
        steps.append({"type": "highlight_section", "blockPos": [int(px), 0, int(pz)],
                      "duration": 220, "color": "blue"})
        steps.append(custom("role_timer", {"progress": [0.0, 1.0],
                                           "lines": [overlay_line]}))
        panel = True
        steps.append({"type": "idle", "duration": 40})
        particles_at([px, py + 0.9, pz], count=8, duration=60)
        return steps, panel

    if any(word in zh for word in ("无法移动", "無法移動", "延迟", "延遲")):
        key()
        steps.append({"type": "highlight_section", "blockPos": [int(px), 0, int(pz)],
                      "duration": 200, "color": "blue"})
        steps.append(custom("role_timer", {"progress": [0.0, 1.0],
                                           "lines": [overlay_line]}))
        panel = True
        steps.append({"type": "idle", "duration": 40})
        return steps, panel

    coins = parse_coins(zh)
    if coins is not None:
        key()
        steps.append({"type": "create_item_entity", "pos": [px + 0.9, py + 0.5, pz],
                      "motion": [0.0, 0.0, 0.0], "count": 1, "item": item,
                      "linkId": item_link})
        steps.append(custom("role_currency", {"progress": [1.0, 0.5],
                                              "lines": [overlay_line]}))
        panel = True
        steps.append({"type": "idle", "duration": 35})
        return steps, panel

    if any(word in zh for word in ("传送", "瞬移", "回到", "召回", "前往")):
        key()
        emit(SOURCE_PARTICLE_BY_ROLE.get(rid), [px, py + 0.9, pz], count=12, duration=50)
        sound = sound_for(zh)
        if sound:
            steps.append(scene_sound(sound))
        steps.append(custom("role_teleport", {"linkId": link, "pos": [tx, ty, tz]}))
        emit(SOURCE_PARTICLE_BY_ROLE.get(rid), [tx, ty + 0.9, tz], count=12, duration=50)
        steps.append({"type": "indicate_success", "blockPos": [int(tx), 0, int(tz)]})
        steps.append({"type": "idle", "duration": 30})
        return steps, panel

    if any(word in zh for word in ("切换", "更换")):
        key()
        steps.append(custom("role_hold", {"linkId": link, "item": item}))
        steps.append({"type": "show_controls", "action": "right", "item": item,
                      "duration": 65, "point": controls, "direction": "down"})
        steps.append({"type": "idle", "duration": 30})
        return steps, panel

    if "右键" in zh or "左键" in zh:
        key()
        steps.append({"type": "create_item_entity", "pos": [px - 0.8, py + 0.5, pz],
                      "motion": [0.0, 0.0, 0.0], "count": 1, "item": item,
                      "linkId": item_link})
        steps.append({"type": "show_controls", "action": "right" if "右键" in zh else "left",
                      "item": item, "duration": 70, "point": controls, "direction": "down"})
        walk_to([tx - 0.9, ty, tz], 35)
        effects = item_effect_steps(item, [tx, ty + 0.8, tz])
        if effects:
            steps.extend(effects)
        else:
            particles_at([tx, ty + 0.8, tz], count=8, duration=55)
        steps.append({"type": "idle", "duration": 25})
        return steps, panel

    # 兜底也是一次真实演示：键帽 + 走向目标 + 目标处指示，而不是纯文字。
    key()
    walk_to([tx - 0.9, ty, tz], 40)
    steps.append({"type": "highlight_section", "blockPos": [int(tx), 0, int(tz)],
                  "blockPos2": [int(tx), 0, int(tz)], "duration": 200, "color": "green"})
    particles_at([tx, ty + 0.9, tz], count=6, duration=50)
    steps.append({"type": "indicate_success", "blockPos": [int(tx), 0, int(tz)]})
    steps.append({"type": "idle", "duration": 25})
    return steps, panel


# PONDERER_SCENE_AUTHORING.md §4：避免连续三块纯文本。加计数用的视觉步骤集合。
VISUAL_STEP_TYPES = {
    "spawn_particles", "highlight_section", "create_item_entity", "create_entity",
    "show_controls", "indicate_success", "indicate_redstone", "set_block",
    "starrailexpress:role_key", "starrailexpress:role_walk",
    "starrailexpress:role_teleport", "starrailexpress:role_pose",
    "starrailexpress:role_hold", "starrailexpress:role_hide",
    "starrailexpress:role_outline", "starrailexpress:role_scale",
}


class SceneBuilder:
    """一个段落的步骤收集器，负责文本位置与时间轴自检。"""

    def __init__(self, origin):
        self.origin = origin
        self.steps = []
        self.text_count = 0
        self.consecutive_texts = 0

    def add(self, step):
        if step.get("type") in VISUAL_STEP_TYPES:
            self.consecutive_texts = 0
        self.steps.append(step)

    def point(self, index):
        # 文档要求注释指向玩家模型；把高度限制在 2.5 格内，多的注释用小范围横移错开。
        x, y, z = self.origin
        level = index % 7
        ring = index // 7
        return [round(x + 0.15 * ring, 2), round(y + 0.8 + 0.28 * level, 2),
                round(z - 0.15 * ring, 2)]

    def say(self, text):
        duration = text_duration(text)
        if self.consecutive_texts >= 2:
            # 第三块文案前补一个非阻塞指示，避免「连续三个纯文本块」。
            self.add({"type": "indicate_success",
                      "blockPos": [int(self.origin[0]), 0, int(self.origin[2])]})
        self.add(make_text(text, self.point(self.text_count), duration))
        self.text_count += 1
        self.consecutive_texts += 1
        self.add({"type": "idle", "duration": duration + 10})
        return duration

    def finish(self):
        if self.steps:
            self.add({"type": "rotate_camera_y", "degrees": 12.0, "duration": 50,
                      "attachKeyFrame": True})


# ---------------------------------------------------------------- 逐职业特写
#
# 这些职业的机制没法用通用模版表达（抱人上车、刀枪外观互换、浇油 / 点火、
# 商店唱片、没有技能的 baka 等），单独写清楚每一幕放谁、拿什么、变什么。
SKIN_REMILIA = "starrailexpress:textures/entity/custom_psycho/remilia.png"
SKIN_BLACK = "noellesroles:textures/entity/custom_psycho/black_psycho.png"
SKIN_WHITE = "noellesroles:textures/entity/custom_psycho/white_psycho.png"
SKIN_JESTER = "starrailexpress:textures/entity/custom_psycho/jester.png"
SKIN_CAT = "starrailexpress:textures/entity/custom_psycho/cat_killer.png"

ROLE_ITEMS = {
    "kafu_chino": ["noellesroles:chino_coffee"],
    "houjuu_nue": ["trainmurdermystery:knife", "trainmurdermystery:revolver",
                   "trainmurdermystery:lockpick", "noellesroles:master_key"],
    "arsonist": ["stupid_express:jerry_can", "stupid_express:lighter",
                 "stupid_express:large_oil_can", "stupid_express:fire_gun"],
    "baka": ["noellesroles:danmuku", "noellesroles:problem_set"],
    "disc_master": ["noellesroles:speaker", "minecraft:music_disc_13"],
    # 下列职业的 facts 初始/商店物品抽取不全或只抽到通用工具，按介绍与源码补齐。
    "bomber": ["noellesroles:bomb"],
    "cake_maker": ["minecraft:wheat", "minecraft:egg", "minecraft:sugar"],
    "doctor": ["noellesroles:defibrillator", "noellesroles:antidote",
               "noellesroles:pill", "noellesroles:purify_bomb"],
    "imitator": ["noellesroles:hedinghong"],
    "locksmith": ["noellesroles:noell_paperclip", "trainmurdermystery:lockpick"],
    "oldman": ["noellesroles:wheelchair"],
    "prankster": ["noellesroles:blank_cartridge", "noellesroles:smoke_grenade",
                  "noellesroles:lockpick"],
    "tomato_head": ["noellesroles:tomato"],
    "undead_lord": ["noellesroles:bone_staff"],
    "veteran": ["noellesroles:sp_knife"],
    "wizard": ["noellesroles:wizard_staff"],
}

# 这些职业没有主动技能键，不能因为介绍里写了「右键道具」就塞技能键帽和走动。
NO_SKILL_KEY_ROLES = {"baka"}

# 本能透视颜色必须以 RoleInstinctRegister / InstinctManager / 角色常量为准。
# 这里只覆盖介绍里明确提到具体颜色、且源码有对应分支的职业；其余沿用职业基础色。
INSTINCT_COLOR_BY_ROLE = {
    "amon": 0xFF786E8C,
    "anatman": 0xFFFFFF00,
    "asatya": 0xFFFFFF00,
    "candlebearer": 0xFFFFD278,
    "commander": 0xFF3F48CC,
    "cupid": 0xFFFFA500,
    "dictator": 0xFF3C60B0,
    "executioner": 0xFF00FEFE,
    "ghost_eye": 0xFFFFFFFF,
    "godfather": 0xFF8B4513,
    "infected": 0xFFFFA500,
    "initiate": 0xFFFFD154,
    "janitor": 0xFF8B4513,
    "jester": 0xFFFFC0CB,
    "leader": 0xFFFF00FF,
    "licensed_villain": 0xFF2B2B2B,
    "mafioso": 0xFF8B4513,
    "meatball": 0xFF0000B4,
    "nutritionist": 0xFF8B4513,
    "parasol": 0xFF8B4513,
    "pelican": 0xFFFFA500,
    "rabbit_wansui": 0xFFFFFDFD,
    "roulette_devil": 0xFF3388FF,
    "voice_changer": 0xFF9400D3,
    "wraith_assassin": 0xFF2C8DFF,
}

# 同一段里需要多个目标、各自不同颜色时，按这个顺序摆模型。
VISION_COLORS_BY_ROLE = {
    "initiate": [0xFFFFD154, 0xFFFFD154],
    "godfather": [0xFF8B4513, 0xFF87CEEB],
    "janitor": [0xFF8B4513, 0xFF87CEEB],
    "mafioso": [0xFF8B4513, 0xFF87CEEB],
    "nutritionist": [0xFF8B4513, 0xFF87CEEB],
    "parasol": [0xFF8B4513, 0xFF87CEEB],
    "leader": [0xFFFF00FF, 0xFFFFFF00, 0xFF0000FF],
    "candlebearer": [0xFFFFD278, 0xFF0000FF, 0xFF808080],
    "cupid": [0xFFFFA500, 0xFFFF69B4],
    "wraith_assassin": [0xFF2C8DFF, 0xFFFFD84A],
    "licensed_villain": [0xFF2B2B2B, 0xFFC13838],
}

ORANGE = 0xFFA500
GRAY = 0x808080


def item_entity(pos, item, link_id):
    return {"type": "create_item_entity", "pos": list(pos), "motion": [0.0, 0.0, 0.0],
            "count": 1, "item": item, "linkId": link_id, "attachKeyFrame": True}


def show_right_click(point, item, duration=70):
    return {"type": "show_controls", "action": "right", "item": item, "duration": duration,
            "point": list(point), "direction": "down", "attachKeyFrame": True}


def override_kafu_skill(builder, ctx):
    """咖啡师把范围内的兔兔玩家抱到头顶，目标缩小并做蹲坐姿态。"""
    p, t = ctx["player_pos"], ctx["second_pos"]
    main, rider = ctx["rid"] + "_main", ctx["rid"] + "_rider"
    builder.add(player_step(t, rider, outline=ctx["outline_color"]))
    builder.add({"type": "idle", "duration": 18})
    for index, line in enumerate(ctx["lines"]):
        head = [p[0], p[1] + 2.0, p[2]]
        builder.add(key_cap(head))
        # 目标先回到咖啡师脚边，再「被抱起来」落到视觉头顶；
        # Minecraft 玩家没有 SITTING pose，用蹲下 + 0.62 缩放表达坐在头顶的小兔兔。
        builder.add(custom("role_teleport", {"linkId": rider, "pos": [p[0] + 0.6, p[1], p[2]]}))
        builder.add({"type": "idle", "duration": 12})
        builder.add(custom("role_teleport", {"linkId": rider, "pos": [p[0], p[1] + 1.8, p[2]]}))
        builder.add(custom("role_pose", {"linkId": rider, "pose": "crouching"}))
        builder.add(custom("role_scale", {"linkId": rider, "scale": 0.62}))
        builder.add(custom("role_timer", {"progress": [1.0, 0.0], "lines": [line]}))
        builder.add({"type": "indicate_success", "blockPos": [int(p[0]), 0, int(p[2])]})
        builder.say(line)


def override_nue_form(builder, ctx):
    """鵺：手里拿真刀却显示枪、拿真枪却显示刀、拿开锁器显示万能钥匙。"""
    p, t = ctx["player_pos"], ctx["second_pos"]
    main = ctx["rid"] + "_main"
    builder.add(player_step(t, ctx["rid"] + "_observer", outline=ctx["outline_color"]))
    pairs = [
        ("trainmurdermystery:knife", "trainmurdermystery:revolver"),
        ("trainmurdermystery:revolver", "trainmurdermystery:knife"),
        ("trainmurdermystery:lockpick", "noellesroles:master_key"),
    ]
    for index, line in enumerate(ctx["lines"]):
        for slot, (real, shown) in enumerate(pairs):
            ground = [p[0] - 1.2 + slot * 0.8, p[1] + 0.55, p[2] - 1.0]
            builder.add(item_entity(ground, real, ctx["rid"] + "_real_" + str(index) + "_" + str(slot)))
            builder.add(custom("role_hold", {"linkId": main, "item": shown}))
            builder.add(show_right_click([p[0], p[1] + 2.0, p[2]], real, 55))
            builder.add({"type": "idle", "duration": 18})
        builder.say(line)


def override_nue_skill(builder, ctx):
    """鵺的短冷却刀击：演示挥刀 + 目标消失，不要把它演成普通击杀流程。"""
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    builder.add(player_step(t, rid + "_target", outline=ctx["outline_color"]))
    for line in ctx["lines"]:
        builder.add(custom("role_hold", {"linkId": main, "item": "trainmurdermystery:knife"}))
        builder.add(key_cap([p[0], p[1] + 2.0, p[2]]))
        builder.add(custom("role_walk", {"linkId": main, "to": [t[0] - 0.8, t[1], t[2]], "duration": 36}))
        builder.add(custom("role_death", {"linkId": rid + "_target", "pos": t}))
        builder.add(custom("role_timer", {"progress": [1.0, 0.0], "lines": [line]}))
        builder.say(line)


def override_arsonist_items(builder, ctx):
    """纵火犯：浇油、点火失败警告、疯魔喷火、大油罐，全程多个玩家在场。"""
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    currency_used = False
    disguise_used = False
    third = [t[0], t[1], t[2] + 1.4]
    builder.add(player_step(t, rid + "_oiled"))
    builder.add(player_step(third, rid + "_dry"))
    oiled, dry = rid + "_oiled", rid + "_dry"
    cans = ["stupid_express:jerry_can", "stupid_express:lighter"]
    for index, line in enumerate(ctx["lines"]):
        zh = line.get("zh_cn", "")
        if index == 0:
            for slot, item in enumerate(cans):
                builder.add(item_entity([p[0] - 1.2 + slot * 0.8, p[1] + 0.55, p[2] - 1.0],
                                        item, rid + "_start_" + str(slot)))
            builder.add(custom("role_hold", {"linkId": main, "item": cans[0]}))
            builder.add(custom("role_outline", {"linkId": oiled, "color": GRAY}))
        elif ("浇上汽油" in zh or "浇油" in zh) and "警告" not in zh:
            builder.add(custom("role_hold", {"linkId": main, "item": cans[0]}))
            builder.add(show_right_click([p[0], p[1] + 2.0, p[2]], cans[0]))
            builder.add(custom("role_walk", {"linkId": main, "to": [t[0] - 0.8, t[1], t[2]], "duration": 40}))
            builder.add(scene_particle("minecraft:smoke", [t[0], t[1] + 0.9, t[2]], count=10, duration=70))
            builder.add(custom("role_outline", {"linkId": oiled, "color": ORANGE}))
            if not currency_used:
                builder.add(custom("role_currency", {"progress": [0.3, 0.6], "lines": [line]}))
                currency_used = True
        elif "警告" in zh:
            builder.add(custom("role_hold", {"linkId": main, "item": cans[1]}))
            builder.add(show_right_click([p[0], p[1] + 2.0, p[2]], cans[1]))
            builder.add(custom("role_timer", {"progress": [0.0, 1.0], "lines": [line]}))
        elif "疯魔皮肤" in zh or "喷火枪" in zh:
            builder.add(custom("role_hold", {"linkId": main, "item": "stupid_express:flamethrower"}))
            if not disguise_used:
                builder.add(custom("role_disguise", {"label": "ponder.starrailexpress.label.psycho",
                                                     "lines": [line]}))
                disguise_used = True
        elif "大油罐" in zh:
            builder.add(custom("role_hold", {"linkId": main, "item": "stupid_express:large_oil_can"}))
            builder.add(item_entity([p[0] - 1.0, p[1] + 0.55, p[2] - 1.0],
                                    "stupid_express:large_oil_can", rid + "_large_can"))
            builder.add(show_right_click([p[0], p[1] + 2.0, p[2]], "stupid_express:large_oil_can"))
            builder.add(scene_particle("stupid_express:arsonist_flame", [t[0], t[1] + 0.9, t[2]], count=12, duration=70))
            builder.add(custom("role_outline", {"linkId": oiled, "color": ORANGE}))
            builder.add(custom("role_outline", {"linkId": dry, "color": GRAY}))
        builder.say(line)


def override_arsonist_skill(builder, ctx):
    """80% 玩家被浇油后，右键打火机点火；被烧死者不留尸体。"""
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    third = [t[0], t[1], t[2] + 1.4]
    builder.add(player_step(t, rid + "_burn_1", outline=ORANGE))
    builder.add(player_step(third, rid + "_burn_2", outline=ORANGE))
    for line in ctx["lines"]:
        builder.add(custom("role_hold", {"linkId": main, "item": "stupid_express:lighter"}))
        builder.add(key_cap([p[0], p[1] + 2.0, p[2]]))
        builder.add(show_right_click([p[0] - 0.8, p[1] + 1.2, p[2]], "stupid_express:lighter"))
        builder.add(scene_particle("stupid_express:arsonist_flame", [t[0], t[1] + 0.9, t[2]], count=14, duration=80))
        builder.add(scene_particle("stupid_express:arsonist_flame", [third[0], third[1] + 0.9, third[2]],
                                   count=14, duration=80))
        builder.add(custom("role_death", {"linkId": rid + "_burn_1", "pos": t, "corpse": False}))
        builder.add(custom("role_death", {"linkId": rid + "_burn_2", "pos": third, "corpse": False}))
        builder.add(scene_particle("stupid_express:arsonist_kill", [t[0], t[1] + 0.9, t[2]], count=12, duration=60))
        builder.say(line)


def override_arsonist_shop(builder, ctx):
    """纵火犯商店：开锁器 / 撬棍 / 大油罐 / 火枪，每句对应商品摆在地上。"""
    p = ctx["player_pos"]
    rid = ctx["rid"]
    items = ["trainmurdermystery:lockpick", "trainmurdermystery:crowbar",
             "stupid_express:large_oil_can", "stupid_express:fire_gun"]
    builder.add({"type": "starrailexpress:role_shop", "attachKeyFrame": True,
                 "parameters": {"role": rid, "columns": 4}})
    for index, line in enumerate(ctx["lines"]):
        if index == 1:
            for slot, item in enumerate(items):
                builder.add(item_entity([p[0] - 1.4 + slot * 0.75, p[1] + 0.55, p[2] - 1.2],
                                        item, rid + "_shop_" + str(slot)))
        elif "火枪" in line.get("zh_cn", ""):
            builder.add(custom("role_hold", {"linkId": rid + "_main", "item": "stupid_express:fire_gun"}))
            builder.add(show_right_click([p[0], p[1] + 2.0, p[2]], "stupid_express:fire_gun"))
        builder.say(line)


def override_arsonist_body(builder, ctx):
    """被烧死的玩家不留下尸体：目标消失且没有 player_body。"""
    t = ctx["second_pos"]
    builder.add(player_step(t, ctx["rid"] + "_burned"))
    for line in ctx["lines"]:
        builder.add(custom("role_death", {"linkId": ctx["rid"] + "_burned", "pos": t, "corpse": False}))
        builder.add(scene_particle("minecraft:smoke", [t[0], t[1] + 0.8, t[2]], count=8, duration=50))
        builder.say(line)


def override_arsonist_vision(builder, ctx):
    """透视颜色必须和介绍一致：浇油橙、其他人灰。"""
    p, t = ctx["player_pos"], ctx["second_pos"]
    third = [t[0], t[1], t[2] + 1.4]
    builder.add(player_step(t, ctx["rid"] + "_oiled", outline=ORANGE))
    builder.add(player_step(third, ctx["rid"] + "_dry", outline=GRAY))
    builder.add({"type": "starrailexpress:role_render", "attachKeyFrame": True,
                 "parameters": {"label": SEGMENT_TITLE_KEY["vision"],
                                "lines": [dict(line) for line in ctx["lines"]]}})
    for line in ctx["lines"]:
        builder.add({"type": "idle", "duration": 20})
        builder.say(line)


def override_noisemaker_skill(builder, ctx):
    """大嗓门：按技能键 → 自身发光 + 前方玩家击退眩晕 + 周围玩家发光 + 两声音效。"""
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    target = rid + "_shout_target"
    bystander = [t[0] + 1.4, t[1], t[2] + 0.6]
    builder.add(player_step(t, target))
    builder.add(player_step(bystander, rid + "_shout_bystander"))
    builder.add({"type": "idle", "duration": 15})
    for index, line in enumerate(ctx["lines"]):
        builder.add(key_cap([p[0], p[1] + 2.0, p[2]]))
        # 自身 LOUD_VOICE 发光 + 15 格内玩家 GLOWING（源码 NoiseMakerRoleData:117-138）。
        builder.add(custom("role_outline", {"linkId": main, "color": 0xFFFFFF66}))
        for link in (target, rid + "_shout_bystander"):
            builder.add(custom("role_outline", {"linkId": link, "color": 0xFFFFFF66}))
        # NOTE_BLOCK_HARP + WARDEN_SONIC_BOOM（源码 :126-127, :174-175）。
        builder.add(scene_sound("minecraft:block.note_block.harp"))
        # 正前方玩家被击退：x 方向推远 2.2 格并短暂腾空（源码水平 1.4 / Y 0.42）。
        builder.add(custom("role_knockback", {
            "linkId": target,
            "from": t,
            "to": [t[0] + 2.2, t[1], t[2]],
            "duration": 14,
            "arc": 0.42,
        }))
        builder.add(custom("role_timer", {"progress": [1.0, 0.0], "lines": [line]}))
        builder.add(scene_sound("minecraft:entity.warden.sonic_boom"))
        builder.add({"type": "idle", "duration": 30})
        builder.say(line)


def override_broadcaster_skill(builder, ctx):
    """广播员：按技能键打开广播编辑页，花 50 金币发送后顶部出现广播消息。"""
    p = ctx["player_pos"]
    rid = ctx["rid"]
    lines = ctx["lines"] or [
        {"zh_cn": "你开局拥有200金币，可以花费50金币进行一次全服广播，开局自带一个对讲机。",
         "en_us": "You start with 200 coins and can spend 50 on a server-wide broadcast; you also start with a walkie-talkie.",
         "zh_tw": "你開局擁有200金幣，可以花費50金幣進行一次全廣播，開局自帶一個對講機。"}]
    for index, line in enumerate(lines):
        builder.add(key_cap([p[0], p[1] + 2.0, p[2]]))
        builder.add(custom("role_currency", {"progress": [1.0, 0.5], "lines": [line]}))
        builder.add(custom("role_broadcast", {
            "title": "screen.noellesroles.broadcaster.title",
            "lines": ["screen.noellesroles.broadcaster.placeholder",
                      "tip.broadcaster.with_cost"],
            "cost": 50,
            "duration": 120,
        }))
        builder.say(line)


def override_baka_items(builder, ctx):
    """baka 没有技能键：弹幕长按右键蓄力后命中目标，目标死亡。"""
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    builder.add(player_step(t, rid + "_target"))
    for line in ctx["lines"]:
        builder.add(custom("role_hold", {"linkId": main, "item": "noellesroles:danmuku"}))
        builder.add(item_entity([p[0] - 1.0, p[1] + 0.55, p[2] - 1.0],
                                "noellesroles:danmuku", rid + "_danmuku"))
        builder.add(show_right_click([p[0], p[1] + 2.0, p[2]], "noellesroles:danmuku", 90))
        builder.add(custom("role_death", {"linkId": rid + "_target", "pos": t}))
        builder.say(line)


def override_baka_panel(builder, ctx):
    """习题集确实有右键打开界面，但那不是技能键，也不需要走动。"""
    p = ctx["player_pos"]
    rid = ctx["rid"]
    builder.add({"type": "starrailexpress:role_panel", "attachKeyFrame": True,
                 "parameters": {"title": SEGMENT_TITLE_KEY["panel"],
                                "lines": [dict(line) for line in ctx["lines"]]}})
    for line in ctx["lines"]:
        builder.add(item_entity([p[0] - 1.0, p[1] + 0.55, p[2] - 1.0],
                                "noellesroles:problem_set", rid + "_problem_set"))
        builder.add(custom("role_hold", {"linkId": rid + "_main", "item": "noellesroles:problem_set"}))
        builder.add(show_right_click([p[0], p[1] + 2.0, p[2]], "noellesroles:problem_set"))
        builder.say(line)


def override_baka_shop(builder, ctx):
    p = ctx["player_pos"]
    t = ctx["second_pos"]
    rid = ctx["rid"]
    builder.add({"type": "starrailexpress:role_shop", "attachKeyFrame": True,
                 "parameters": {"role": rid, "columns": 4}})
    for index, line in enumerate(ctx["lines"]):
        builder.add(item_entity([p[0] - 1.0, p[1] + 0.55, p[2] - 1.0],
                                "noellesroles:problem_set", rid + "_shop_book"))
        zh = line.get("zh_cn", "")
        if is_death_line(zh) and not is_immune_death(zh):
            for step in death_act_steps(zh, rid, p, t, index, ctx.get("outline_color")):
                builder.add(step)
        builder.say(line)


def override_baka_mood(builder, ctx):
    """考试结果只改心情和金币，不额外摆物品、不加按键。"""
    builder.add({"type": "starrailexpress:role_mood", "attachKeyFrame": True,
                 "parameters": {"label": SEGMENT_TITLE_KEY["mood"],
                                "before": 0.35, "after": 0.85,
                                "lines": [dict(line) for line in ctx["lines"]]}})
    for line in ctx["lines"]:
        builder.say(line)


def override_baka_details(builder, ctx):
    """baka 的其余句子只讲结果，不要再自动加按键 / 走动 / 随手物品。"""
    for line in ctx["lines"]:
        builder.say(line)


def override_disc_master_shop(builder, ctx):
    """唱片师：商店的一句介绍对应一台音箱 + 几张真实唱片。"""
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid = ctx["rid"]
    builder.add(player_step(t, rid + "_listener", outline=ctx["outline_color"]))
    builder.add({"type": "starrailexpress:role_shop", "attachKeyFrame": True,
                 "parameters": {"role": rid, "columns": 4}})
    items = ["noellesroles:speaker", "minecraft:music_disc_13",
             "minecraft:music_disc_cat", "minecraft:music_disc_blocks"]
    for line in ctx["lines"]:
        for slot, item in enumerate(items):
            builder.add(item_entity([p[0] - 1.4 + slot * 0.75, p[1] + 0.55, p[2] - 1.2],
                                    item, rid + "_disc_" + str(slot)))
        builder.add(custom("role_hold", {"linkId": rid + "_main", "item": "noellesroles:speaker"}))
        builder.add(show_right_click([p[0], p[1] + 2.0, p[2]], "minecraft:music_disc_13"))
        builder.say(line)


def override_mortician_bodymaker_items(builder, ctx):
    """血瓶：摆血瓶，右键在死者周围泼血；血瓶是道具，不是技能键，不放键帽。"""
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    blood = "noellesroles:blood_bottle"
    builder.add(player_step(t, rid + "_victim"))
    builder.add(custom("role_death", {"linkId": rid + "_victim", "pos": t,
                                      "name": "ponder.starrailexpress.label.victim"}))
    builder.add({"type": "idle", "duration": 15})
    for line in ctx["lines"]:
        builder.add(custom("role_hold", {"linkId": main, "item": blood}))
        builder.add(item_entity([p[0] - 0.8, p[1] + 0.5, p[2]], blood, rid + "_blood_bottle"))
        # 只有鼠标右键提示，键帽不参与这一幕，UI 不会叠在一起。
        builder.add(show_right_click([p[0] - 0.8, p[1] + 1.2, p[2]], blood))
        # BloodBottleItem：12 发 noellesroles:deathblood + SoundEvents.GRASS_FALL。
        builder.add(scene_particle("noellesroles:deathblood", [t[0], t[1] + 1.0, t[2]],
                                   count=12, duration=70, spread=[0.9, 1.2, 0.9]))
        builder.add(scene_sound("minecraft:block.grass.fall"))
        builder.add({"type": "indicate_success", "blockPos": [int(t[0]), 0, int(t[2])]})
        builder.add({"type": "idle", "duration": 35})
        builder.say(line)


def override_mortician_bodymaker_skill(builder, ctx):
    """曳柩对尸体按技能键；不要把技能演成右键钥匙。"""
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    builder.add(player_step(t, rid + "_corpse_target", outline=ctx["outline_color"]))
    head = [p[0], p[1] + 2.0, p[2]]
    for line in ctx["lines"]:
        zh = line.get("zh_cn", "")
        if zh.startswith("技能（"):
            # 这一句介绍的是技能切换键，不是按技能键释放，也没有物品使用。
            builder.add(key_cap(head, switch=True))
            builder.add(custom("role_skill", {"label": "ponder.starrailexpress.label.skill",
                                              "lines": [line]}))
        elif "曳柩" in zh:
            builder.add(key_cap(head))
            builder.add(custom("role_walk", {"linkId": main,
                                             "to": [t[0] - 0.8, t[1], t[2]], "duration": 40}))
            builder.add(custom("role_death", {"linkId": rid + "_corpse_target", "pos": t,
                                              "name": "ponder.starrailexpress.label.victim"}))
            builder.add({"type": "indicate_success", "blockPos": [int(t[0]), 0, int(t[2])]})
        elif "丧钟" in zh:
            builder.add(key_cap(head))
            builder.add({"type": "highlight_section",
                         "blockPos": [int(p[0]) - 2, 0, int(p[2]) - 2],
                         "blockPos2": [int(p[0]) + 2, 0, int(p[2]) + 2],
                         "duration": 220, "color": "red"})
            builder.add(custom("role_timer", {"progress": [0.0, 1.0], "lines": [line]}))
        elif "清洗" in zh:
            builder.add(key_cap(head))
            builder.add({"type": "highlight_section",
                         "blockPos": [int(p[0]) - 2, 0, int(p[2]) - 2],
                         "blockPos2": [int(p[0]) + 2, 0, int(p[2]) + 2],
                         "duration": 220, "color": "blue"})
            builder.add(custom("role_timer", {"progress": [0.0, 1.0], "lines": [line]}))
        builder.say(line)


PARTICLE_WORDS = ("粒子", "释放", "投掷", "喷洒", "喷出", "泼洒")


def ensure_particles(builder, lines, rid=""):
    """文档要求：源码里发射了粒子时，介绍对应画面里也要出现同一个粒子。"""
    body = "".join(line.get("zh_cn", "") for line in lines)
    if not any(word in body for word in PARTICLE_WORDS):
        return
    if any(step.get("type") == "spawn_particles" for step in builder.steps):
        return
    particle = particle_for_role(body, rid)
    if not particle:
        # 源码没有对应粒子发射，就不凭空补一个。
        return
    x, y, z = builder.origin
    builder.steps.insert(1, scene_particle(particle, [x, y + 0.9, z],
                                           count=8, duration=60))


def mob_entity(entity, pos, link_id, nbt=None):
    step = {"type": "create_entity", "entity": entity, "pos": list(pos),
            "yaw": 180.0, "linkId": link_id, "attachKeyFrame": True}
    if nbt:
        step["nbt"] = nbt
    return step


def disguise_perform(builder, ctx, lines, entity=None, skin=None, block=None,
                     scale=None, second_skin=None, extra=None):
    """把「玩家消失 → 新形态出现」真正画出来，而不是只丢一块文字面板。

    每个职业传进来的实体 / 皮肤 / 方块都不同，这里是共用的摆放顺序，
    具体变成什么由各职业的 override 明确指定。
    """
    p = ctx["player_pos"]
    t = ctx["second_pos"]
    rid = ctx["rid"]
    main = rid + "_main"
    if extra:
        for step in extra:
            builder.add(step)
    if entity is None and skin is None and scale is None:
        # 纯身份伪装（例如轮盘恶魔的黑烟）也要把玩家藏起来再贴标签。
        builder.add(custom("role_hide", {"linkId": main, "visible": False}))
    else:
        if entity is not None:
            builder.add(custom("role_hide", {"linkId": main, "visible": False}))
            builder.add(mob_entity(entity, p, rid + "_disguised"))
        if block is not None:
            builder.add(custom("role_hide", {"linkId": main, "visible": False}))
            builder.add({"type": "set_block", "block": block,
                         "blockPos": [int(p[0]), int(p[1]), int(p[2])],
                         "blockPos2": [int(p[0]), int(p[1]), int(p[2])]})
        if skin is not None:
            builder.add(custom("role_hide", {"linkId": main, "visible": False}))
            builder.add(player_step(p, rid + "_disguised", skin=skin))
            if second_skin is not None:
                builder.add(custom("role_hide", {"linkId": rid + "_observer", "visible": False}))
                builder.add(player_step(t, rid + "_observer_new", skin=second_skin))
        if scale is not None:
            builder.add(custom("role_scale", {"linkId": main, "scale": scale}))
    builder.add({"type": "starrailexpress:role_disguise", "attachKeyFrame": True,
                 "parameters": {"label": SEGMENT_TITLE_KEY["form"],
                                "lines": [dict(line) for line in lines], "color": 0xFFA500}})
    builder.add({"type": "idle", "duration": 25})
    for index, line in enumerate(lines):
        zh = line.get("zh_cn", "")
        if is_death_line(zh) and not is_immune_death(zh):
            for step in death_act_steps(zh, rid, p, t, index, ctx.get("outline_color")):
                builder.add(step)
        builder.say(line)


def override_rabbit_form(builder, ctx):
    lines = [line for line in ctx["all_lines"] if any(
        w in line.get("zh_cn", "") for w in ("变为兔兔", "变成兔兔", "所有玩家均变成"))]
    p, t = ctx["player_pos"], ctx["second_pos"]
    third = [t[0] - 2.0, t[1], t[2]]
    extra = [player_step(t, ctx["rid"] + "_target"), player_step(third, ctx["rid"] + "_third")]
    disguise_perform(builder, ctx, lines or ctx["all_lines"][:2], extra=extra)
    builder.add(custom("role_hide", {"linkId": ctx["rid"] + "_target", "visible": False}))
    builder.add(custom("role_hide", {"linkId": ctx["rid"] + "_third", "visible": False}))
    builder.add(mob_entity("minecraft:rabbit", t, ctx["rid"] + "_rabbit_1"))
    builder.add(mob_entity("minecraft:rabbit", third, ctx["rid"] + "_rabbit_2"))


def override_snow_golem_form(builder, ctx):
    lines = ctx["lines"] or [line for line in ctx["all_lines"] if "雪傀儡" in line.get("zh_cn", "")]
    disguise_perform(builder, ctx, lines, entity="minecraft:snow_golem")


def override_metal_monster_form(builder, ctx):
    lines = [line for line in ctx["all_lines"] if "钢卷" in line.get("zh_cn", "")]
    disguise_perform(builder, ctx, lines or ctx["all_lines"][:2], block="noellesroles:steel_coil")


def override_morphling_form(builder, ctx):
    lines = [line for line in ctx["all_lines"] if "皮肤" in line.get("zh_cn", "")]
    disguise_perform(builder, ctx, lines or ctx["all_lines"][:1], skin=SKIN_BLACK)
    builder.add(custom("role_timer", {"progress": [1.0, 0.0],
                                      "lines": lines or ctx["all_lines"][:1]}))


def override_tomato_form(builder, ctx):
    lines = [line for line in ctx["all_lines"] if "西红柿" in line.get("zh_cn", "")]
    p = ctx["player_pos"]
    disguise_perform(builder, ctx, lines or ctx["all_lines"][:1],
                     extra=[item_entity([p[0], p[1] + 0.55, p[2]],
                                        "noellesroles:tomato", ctx["rid"] + "_tomato")])
    builder.add(custom("role_hide", {"linkId": ctx["rid"] + "_main", "visible": False}))
    builder.add(custom("role_timer", {"progress": [1.0, 0.0], "lines": lines or []}))


def override_embalmer_form(builder, ctx):
    lines = [line for line in ctx["all_lines"] if "皮肤" in line.get("zh_cn", "")]
    extra = [player_step(ctx["second_pos"], ctx["rid"] + "_observer")]
    disguise_perform(builder, ctx, lines or ctx["all_lines"][:1],
                     skin=SKIN_REMILIA, second_skin=SKIN_BLACK, extra=extra)


def override_skincrawler_form(builder, ctx):
    lines = [line for line in ctx["all_lines"] if "皮肤" in line.get("zh_cn", "")]
    t = ctx["second_pos"]
    extra = [item_entity([t[0], t[1] + 0.55, t[2]], "trainmurdermystery:player_body",
                         ctx["rid"] + "_stolen_body")]
    disguise_perform(builder, ctx, lines or ctx["all_lines"][:1], skin=SKIN_REMILIA, extra=extra)


def override_doomed_sinner_form(builder, ctx):
    lines = [line for line in ctx["all_lines"] if "皮肤" in line.get("zh_cn", "")]
    t = ctx["second_pos"]
    extra = [player_step(t, ctx["rid"] + "_donor")]
    disguise_perform(builder, ctx, lines or ctx["all_lines"][:1], skin=SKIN_CAT, extra=extra)
    builder.add(custom("role_death", {"linkId": ctx["rid"] + "_donor", "pos": t}))


def override_amon_form(builder, ctx):
    lines = [line for line in ctx["all_lines"] if "皮肤" in line.get("zh_cn", "")]
    extra = [player_step(ctx["second_pos"], ctx["rid"] + "_host")]
    disguise_perform(builder, ctx, lines or ctx["all_lines"][:1],
                     skin=SKIN_REMILIA, scale=1.0, extra=extra)
    builder.add(custom("role_scale", {"linkId": ctx["rid"] + "_main", "scale": 0.25}))


def override_leather_pig_form(builder, ctx):
    lines = [line for line in ctx["all_lines"] if "一头猪" in line.get("zh_cn", "")]
    disguise_perform(builder, ctx, lines or ctx["all_lines"][:1], entity="minecraft:pig")


def override_nature_spirit_form(builder, ctx):
    lines = [line for line in ctx["all_lines"] if "伪装成脚下" in line.get("zh_cn", "")]
    disguise_perform(builder, ctx, lines or ctx["all_lines"][:1], block="minecraft:grass_block")


def override_pumpkin_spirit_form(builder, ctx):
    lines = [line for line in ctx["all_lines"] if "变成蝙蝠" in line.get("zh_cn", "")]
    disguise_perform(builder, ctx, lines or ctx["all_lines"][:1], entity="minecraft:bat")


def override_magician_form(builder, ctx):
    lines = [line for line in ctx["all_lines"] if "扮演" in line.get("zh_cn", "") or
             "伪装" in line.get("zh_cn", "")]
    disguise_perform(builder, ctx, lines or ctx["all_lines"][:2], skin=SKIN_JESTER)


def override_vulture_form(builder, ctx):
    lines = [line for line in ctx["all_lines"] if "变为随机杀手" in line.get("zh_cn", "") or
             "变成杀手" in line.get("zh_cn", "")]
    disguise_perform(builder, ctx, lines or ctx["all_lines"][:1], skin=SKIN_BLACK)


def override_gambler_form(builder, ctx):
    lines = [line for line in ctx["all_lines"] if "变成" in line.get("zh_cn", "")]
    disguise_perform(builder, ctx, lines or ctx["all_lines"][:1], skin=SKIN_WHITE)


def override_roulette_devil_form(builder, ctx):
    lines = [line for line in ctx["all_lines"] if "常态伪装" in line.get("zh_cn", "") or
             "黑色烟雾" in line.get("zh_cn", "")]
    p = ctx["player_pos"]
    disguise_perform(builder, ctx, lines or ctx["all_lines"][:2],
                     extra=[scene_particle("wathe:black_smoke", [p[0], p[1] + 0.9, p[2]],
                                           count=12, duration=80)])


def override_dreadlord_form(builder, ctx):
    lines = [line for line in ctx["all_lines"] if "伪装" in line.get("zh_cn", "") or
             "皮肤" in line.get("zh_cn", "") or "杀戮形态" in line.get("zh_cn", "")]
    extra = [player_step(ctx["second_pos"], ctx["rid"] + "_observer")]
    disguise_perform(builder, ctx, lines or ctx["all_lines"][:2],
                     skin=SKIN_REMILIA, second_skin=SKIN_CAT, scale=1.5, extra=extra)


def override_entity303_form(builder, ctx):
    lines = [line for line in ctx["all_lines"] if "窃皮" in line.get("zh_cn", "") or
             "偷脸" in line.get("zh_cn", "") or "伪装" in line.get("zh_cn", "")]
    extra = [player_step(ctx["second_pos"], ctx["rid"] + "_face_donor")]
    disguise_perform(builder, ctx, lines or ctx["all_lines"][:2], skin=SKIN_BLACK, extra=extra)


def override_monokuma_form(builder, ctx):
    lines = [line for line in ctx["all_lines"] if "伪装为义警" in line.get("zh_cn", "") or
             "黑白熊" in line.get("zh_cn", "")]
    disguise_perform(builder, ctx, lines or ctx["all_lines"][:2], skin=SKIN_WHITE, scale=1.5)


def override_purple_monster_form(builder, ctx):
    lines = [line for line in ctx["all_lines"] if "伪装成" in line.get("zh_cn", "") or
             "本体" in line.get("zh_cn", "")]
    extra = [player_step(ctx["second_pos"], ctx["rid"] + "_loved_one")]
    disguise_perform(builder, ctx, lines or ctx["all_lines"][:2],
                     skin=SKIN_REMILIA, second_skin=SKIN_CAT, extra=extra)


def override_raven_form(builder, ctx):
    lines = [line for line in ctx["all_lines"] if "黑色伪装" in line.get("zh_cn", "") or
             "猎杀态" in line.get("zh_cn", "")]
    disguise_perform(builder, ctx, lines or ctx["all_lines"][:1], skin=SKIN_BLACK)


def override_kasen_shop(builder, ctx):
    """仙人：商店买狼狗后，原版狼真的出现在场景里。"""
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid = ctx["rid"]
    builder.add({"type": "starrailexpress:role_shop", "attachKeyFrame": True,
                 "parameters": {"role": rid, "columns": 4}})
    builder.add(item_entity([p[0] - 1.0, p[1] + 0.55, p[2] - 1.0],
                            "trainmurdermystery:note", rid + "_wolf_shop"))
    for line in ctx["lines"]:
        if "狼狗" in line.get("zh_cn", ""):
            builder.add(mob_entity("minecraft:wolf", t, rid + "_wolf"))
            builder.add({"type": "indicate_success", "blockPos": [int(t[0]), 0, int(t[2])]})
            builder.add({"type": "idle", "duration": 25})
        builder.say(line)


def override_candlebearer_skill(builder, ctx):
    """秉烛人：按技能键隐身 18 秒；对玩家/尸体点烛使其发光，成功敲钟。"""
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    builder.add(player_step(t, rid + "_candle_target", outline=ctx["outline_color"]))
    builder.add(player_step(t, rid + "_candle_corpse"))
    builder.add(custom("role_death", {"linkId": rid + "_candle_corpse", "pos": t,
                                      "name": "ponder.starrailexpress.label.victim"}))
    for index, line in enumerate(ctx["lines"]):
        builder.add(key_cap([p[0], p[1] + 2.0, p[2]]))
        builder.add(custom("role_hide", {"linkId": main, "visible": False}))
        builder.add(custom("role_effects", {"lines": ["隐身 18 秒"]}))
        builder.add(custom("role_outline", {"linkId": rid + "_candle_target",
                                            "color": 0xFFFFFF66}))
        builder.add(custom("role_outline", {"linkId": rid + "_candle_corpse",
                                            "color": 0xFFFFFF66}))
        builder.add(scene_sound("minecraft:block.bell.resonate"))
        builder.add({"type": "idle", "duration": 30})
        builder.say(line)
    builder.add(custom("role_hide", {"linkId": main, "visible": True}))


def override_medium_skill(builder, ctx):
    """通灵师：对尸体按技能键，花 150 金币把死者拉回尸体旁 20 秒并开启通灵界面。"""
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid = ctx["rid"]
    builder.add(player_step(t, rid + "_body"))
    builder.add(custom("role_death", {"linkId": rid + "_body", "pos": t,
                                      "name": "ponder.starrailexpress.label.victim"}))
    for line in ctx["lines"]:
        builder.add(key_cap([p[0], p[1] + 2.0, p[2]]))
        builder.add(custom("role_currency", {"progress": [1.0, 0.25],
                                             "lines": ["花费 150 金币"]}))
        builder.add(scene_sound("minecraft:block.amethyst_block.chime"))
        builder.add(custom("role_teleport", {"linkId": rid + "_body",
                                             "pos": [t[0], t[1], t[2]]}))
        builder.add(custom("role_panel", {"title": "通灵界面",
                                          "lines": ["死者与通灵师对话 · 20 秒"]}))
        builder.add(custom("role_timer", {"progress": [0.0, 1.0], "lines": ["20 秒"]}))
        builder.add({"type": "idle", "duration": 30})
        builder.say(line)


def override_photographer_skill(builder, ctx):
    """摄影师：拍照致盲目标 → 放画框 → 右键画框传送并再次致盲。"""
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    target = rid + "_photo_target"
    builder.add(player_step(t, target, outline=ctx["outline_color"]))
    for index, line in enumerate(ctx["lines"]):
        builder.add(show_right_click([p[0] - 0.8, p[1] + 1.2, p[2]],
                                     "minecraft:camera"))
        builder.add(custom("role_effects", {"lines": ["黑屏 3 秒 · 致盲"]}))
        builder.add(custom("role_outline", {"linkId": target, "color": 0xFFFFFFFF}))
        # PhotographerFrameEvents:192-193 画框传送只有 ENDERMAN_TELEPORT 音效，没有粒子。
        builder.add(scene_sound("minecraft:entity.enderman.teleport"))
        builder.add(custom("role_teleport", {"linkId": main,
                                             "pos": [p[0] - 1.2, p[1], p[2] - 1.2]}))
        builder.add({"type": "idle", "duration": 30})
        builder.say(line)


def override_pooler_skill(builder, ctx):
    """祈月者：按技能切换键选技能；奔涌给三叉戟附魔 + 水粒子；吞月 / 水中镜切领域。"""
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    builder.add(player_step(t, rid + "_moon_target", outline=ctx["outline_color"]))
    for index, line in enumerate(ctx["lines"]):
        zh = line.get("zh_cn", "")
        if "切换" in zh:
            builder.add(key_cap([p[0], p[1] + 2.0, p[2]], switch=True))
            builder.add(custom("role_panel", {"title": "技能轮盘",
                                              "lines": ["奔涌 / 吞月 / 水中镜 / 称颂"]}))
        elif "奔涌" in zh or "三叉戟" in zh:
            builder.add(key_cap([p[0], p[1] + 2.0, p[2]]))
            builder.add(custom("role_hold", {"linkId": main, "item": "minecraft:trident"}))
            builder.add(scene_particle("minecraft:splash", [p[0], p[1] + 0.6, p[2]],
                                       count=20, duration=60))
            builder.add(scene_particle("minecraft:bubble", [p[0], p[1] + 0.8, p[2]],
                                       count=12, duration=60))
            builder.add(scene_sound("minecraft:item.trident.riptide_3"))
        elif "吞月" in zh or "水中镜" in zh:
            builder.add(key_cap([p[0], p[1] + 2.0, p[2]]))
            builder.add(custom("role_teleport", {"linkId": rid + "_moon_target",
                                                 "pos": [t[0] + 1.2, t[1], t[2]]}))
        elif "称颂" in zh:
            builder.add(key_cap([p[0], p[1] + 2.0, p[2]]))
            builder.add(scene_particle("minecraft:end_rod", [t[0], t[1] + 1.2, t[2]],
                                       count=10, duration=50))
        builder.add({"type": "idle", "duration": 25})
        builder.say(line)


def override_thief_skill(builder, ctx):
    """小偷：按技能键按当前模式偷钱 / 偷物品 / 卖物品，按切换键切模式。"""
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    target = rid + "_theft_target"
    builder.add(player_step(t, target, outline=ctx["outline_color"]))
    builder.add({"type": "idle", "duration": 15})
    modes = [
        ("偷钱", "noellesroles:passbook", 0.6, 1.0),
        ("偷物品", "noellesroles:master_key", 1.0, 1.0),
        ("卖物品", "trainmurdermystery:note", 1.0, 0.5),
    ]
    for index, (name, item, after, coins) in enumerate(modes):
        builder.add(key_cap([p[0], p[1] + 2.0, p[2]], switch=(index == 0)))
        builder.add(custom("role_panel", {"title": "偷钱 / 偷物品 / 卖物品",
                                          "lines": ["当前模式：" + name]}))
        builder.add(custom("role_walk", {"linkId": main,
                                         "to": [t[0] - 0.9, t[1], t[2]], "duration": 36}))
        builder.add(item_entity([t[0] - 1.0, t[1] + 0.55, t[2]], item,
                                rid + "_stolen_" + str(index)))
        builder.add(custom("role_currency", {"progress": [1.0, coins],
                                             "lines": ["目标金币 -> 小偷金币"]}))
        builder.add({"type": "indicate_success", "blockPos": [int(t[0]), 0, int(t[2])]})
        builder.add({"type": "idle", "duration": 30})
    for line in ctx["lines"]:
        builder.say(line)


def override_creeper_skill(builder, ctx):
    """苦力怕：被左轮击杀 => 5 格爆炸 + 彩虹粒子 + generic.explode，命中 >5 人再爆一次。"""
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid = ctx["rid"]
    victim = rid + "_creeper_victim"
    builder.add(player_step(t, victim, outline=ctx["outline_color"]))
    builder.add(item_entity([p[0] - 1.0, p[1] + 0.55, p[2]],
                            "trainmurdermystery:revolver", rid + "_revolver"))
    builder.add({"type": "idle", "duration": 18})
    for line in ctx["lines"]:
        builder.add(key_cap([p[0], p[1] + 2.0, p[2]]))
        builder.add(custom("role_death", {"linkId": victim, "pos": t,
                                          "name": "ponder.starrailexpress.label.victim"}))
        builder.add(scene_particle("minecraft:dust_color_transition", [t[0], t[1] + 0.9, t[2]],
                                   count=30, duration=70, spread=[2.4, 1.2, 2.4]))
        builder.add(scene_sound("minecraft:entity.generic.explode"))
        builder.add({"type": "highlight_section",
                     "blockPos": [int(t[0]) - 2, 0, int(t[2]) - 2],
                     "blockPos2": [int(t[0]) + 2, 0, int(t[2]) + 2],
                     "duration": 180, "color": "red"})
        builder.add({"type": "idle", "duration": 40})
        builder.say(line)


def override_doctor_skill(builder, ctx):
    """医生：起搏器蓄力保护 → 目标死亡 → 原地留下不可交互尸体 → 30 秒后复活。"""
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    target = rid + "_patient"
    builder.add(player_step(t, target, outline=ctx["outline_color"]))
    builder.add(custom("role_hold", {"linkId": main, "item": "noellesroles:defibrillator"}))
    for index, line in enumerate(ctx["lines"]):
        if "起搏器" in line.get("zh_cn", ""):
            builder.add(show_right_click([p[0] - 0.8, p[1] + 1.2, p[2]],
                                         "noellesroles:defibrillator"))
            builder.add(custom("role_vitals", {"bars": [
                {"label": "保护", "before": 0.0, "after": 1.0, "color": -16776961}]}))
        elif "死亡" in line.get("zh_cn", ""):
            builder.add(custom("role_death", {"linkId": target, "pos": t,
                                              "name": "ponder.starrailexpress.label.victim"}))
            builder.add(custom("role_timer", {"progress": [0.0, 1.0],
                                              "lines": ["30 秒后复活"]}))
            builder.add(player_step(t, rid + "_patient_revived"))
            builder.add(custom("role_vitals", {"bars": [
                {"label": "复活", "before": 0.0, "after": 1.0, "color": -65536}]}))
        builder.say(line)


def override_vulture_skill(builder, ctx):
    """秃鹫：按技能键吃周围尸体，自身缓慢 II，吃够数量后全场音效并换成杀手。"""
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    builder.add(player_step(t, rid + "_meal"))
    builder.add(custom("role_death", {"linkId": rid + "_meal", "pos": t,
                                      "name": "ponder.starrailexpress.label.victim"}))
    for index, line in enumerate(ctx["lines"]):
        builder.add(key_cap([p[0], p[1] + 2.0, p[2]]))
        builder.add(custom("role_effects", {"lines": ["缓慢 II · 40 tick"]}))
        builder.add(custom("role_currency", {"progress": [0.4, 1.0], "lines": ["吃尸计数"]}))
        if index == len(ctx["lines"]) - 1:
            builder.add(scene_sound("minecraft:entity.hoglin.zombify"))
            builder.add(custom("role_outline", {"linkId": main, "color": -65536}))
        builder.add({"type": "idle", "duration": 30})
        builder.say(line)


def override_locksmith_skill(builder, ctx):
    """锁匠：看门攒灵感 → 消耗钥胚配钥 → 开锁器右键给门上锁/解锁。"""
    p = ctx["player_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    door = [int(p[0]), 1, 6]
    builder.add({"type": "set_block", "block": "starrailexpress:light_blue_oak_door",
                 "blockProperties": {"half": "lower", "facing": "north"},
                 "blockPos": door, "blockPos2": [door[0], door[1] + 1, door[2]],
                 "attachKeyFrame": True})
    for index, line in enumerate(ctx["lines"]):
        builder.add({"type": "highlight_section", "blockPos": door, "duration": 180,
                     "color": "green"})
        builder.add(custom("role_timer", {"progress": [0.0, 1.0], "lines": ["观察门 · 灵感 +1"]}))
        builder.add(item_entity([p[0] - 1.0, p[1] + 0.55, p[2]],
                                "noellesroles:noell_key_blank", rid + "_blank_" + str(index)))
        builder.add(show_right_click([p[0] - 0.8, p[1] + 1.2, p[2]],
                                     "trainmurdermystery:lockpick"))
        builder.add({"type": "indicate_success", "blockPos": door})
        builder.add({"type": "idle", "duration": 30})
        builder.say(line)


def override_jade_general_skill(builder, ctx):
    """玉将军：面前摆一扇列车门，飞踢撞门、门打开、目标被击退。"""
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    door = [int(p[0]), 1, 6]
    builder.add({"type": "set_block", "block": "trainmurdermystery:metal_sheet_door",
                 "blockProperties": {"half": "lower", "facing": "north"},
                 "blockPos": door, "blockPos2": [door[0], door[1] + 1, door[2]],
                 "attachKeyFrame": True})
    builder.add(player_step([p[0], p[1], p[2] + 1.5], rid + "_kick_target",
                            outline=ctx["outline_color"]))
    for line in ctx["lines"]:
        if "飞踢" in line.get("zh_cn", "") or "踹开" in line.get("zh_cn", ""):
            builder.add(key_cap([p[0], p[1] + 2.0, p[2]]))
            builder.add(custom("role_walk", {"linkId": main,
                                             "to": [p[0], p[1], p[2] + 2.0], "duration": 30}))
            builder.add({"type": "set_block", "block": "minecraft:air",
                         "blockPos": door, "blockPos2": [door[0], door[1] + 1, door[2]]})
            builder.add({"type": "indicate_success", "blockPos": door})
            builder.add(custom("role_knockback", {"linkId": rid + "_kick_target",
                                                  "from": [p[0], p[1], p[2] + 1.5],
                                                  "to": [t[0], t[1], t[2] + 1.0],
                                                  "duration": 12, "arc": 0.17}))
        builder.say(line)


def override_telegrapher_skill(builder, ctx):
    """电报员：G 打开电报编辑页，每局 6 次；匿名消息以淡蓝标题与语音播报给全场。"""
    p = ctx["player_pos"]
    rid = ctx["rid"]
    lines = skill_lines(ctx, ["电报", "技能键", "语音播报"])
    for index, line in enumerate(lines):
        builder.add(key_cap([p[0], p[1] + 2.0, p[2]]))
        builder.add(custom("role_telegraph", {
            "title": tr(("电报", "Telegram", "電報")),
            "hint": tr(("匿名发送 · 全服标题与语音播报",
                        "Anonymous send - server-wide title and narration",
                        "匿名傳送 · 全服標題與語音播報")),
            "message": tr(("匿名消息：车内出现异常",
                           "Anonymous: something is wrong on the train",
                           "匿名訊息：車內出現異常")),
            "remaining": max(1, 6 - index),
            "duration": 150,
        }))
        if index == 0:
            builder.add(custom("role_currency", {
                "progress": [1.0, 5 / 6],
                "lines": [tr(("每局最多发送 6 次电报", "At most six telegrams per match",
                              "每局最多傳送 6 次電報"))]}))
        builder.add({"type": "idle", "duration": 20})
        builder.say(line)


def override_agent_skill(builder, ctx):
    """探员：瞄准按键查看目标物品栏，目标移动即关闭；蹲下聆听；得知阴谋家身份位置。"""
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    target = rid + "_inspect_target"
    builder.add(player_step(t, target, outline=ctx["outline_color"]))
    builder.add({"type": "idle", "duration": 12})
    lines = skill_lines(ctx, ["技能键", "物品栏", "移动", "蹲下", "阴谋家"])
    for line in lines:
        zh = line.get("zh_cn", "")
        if "物品栏" in zh:
            builder.add(key_cap([p[0], p[1] + 2.0, p[2]]))
            builder.add(custom("role_outline", {"linkId": target, "color": 0x55FFFF}))
            builder.add(custom("role_panel", {
                "title": tr(("目标物品栏", "Target inventory", "目標物品欄")),
                "lines": [tr(("只读审查", "Read-only inspection", "唯讀審查")), line]}))
            builder.add(custom("role_currency", {
                "progress": [1.0, 0.25],
                "lines": [tr(("审查花费 150 金币", "Inspection costs 150 coins",
                              "審查花費 150 金幣"))]}))
        elif "移动" in zh or "关闭" in zh:
            builder.add(custom("role_walk", {"linkId": target,
                                             "to": [t[0] + 1.3, t[1], t[2]], "duration": 36}))
            builder.add(custom("role_panel", {
                "title": tr(("目标物品栏", "Target inventory", "目標物品欄")),
                "lines": [tr(("目标移动 · 界面关闭", "Target moved - panel closed",
                              "目標移動 · 介面關閉"))]}))
            builder.add(custom("role_timer", {
                "progress": [1.0, 0.0],
                "lines": [tr(("冷却 60 秒", "60-second cooldown", "冷卻 60 秒"))]}))
        elif "蹲下" in zh:
            builder.add(custom("role_pose", {"linkId": main, "pose": "crouching"}))
            builder.add({"type": "highlight_section",
                         "blockPos": [int(p[0]) - 2, 0, int(p[2]) - 2],
                         "blockPos2": [int(p[0]) + 2, 0, int(p[2]) + 2],
                         "duration": 220, "color": "green"})
            builder.add(custom("role_effects", {
                "lines": [tr(("聆听周围动静", "Listen for nearby movement", "聆聽周圍動靜"))]}))
            builder.add({"type": "idle", "duration": 30})
            builder.add(custom("role_pose", {"linkId": main, "pose": "standing"}))
        elif "阴谋家" in zh:
            builder.add(custom("role_outline", {"linkId": target, "color": -65536}))
            builder.add(custom("role_panel", {
                "title": tr(("身份揭示", "Identity revealed", "身分揭示")),
                "lines": [tr(("得知阴谋家的名字与位置",
                              "You learn the conspirator's name and position",
                              "得知陰謀家的名字與位置")), line]}))
        builder.add({"type": "idle", "duration": 20})
        builder.say(line)


def override_fortuneteller_skill(builder, ctx):
    """算命大师：花 200 金币卜算，目标濒死时传送到身边并免死 120 秒。"""
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    target = rid + "_fate_target"
    builder.add(player_step(t, target, outline=ctx["outline_color"]))
    lines = skill_lines(ctx, ["卜算", "金币", "冷却"])
    for line in lines:
        zh = line.get("zh_cn", "")
        if "卜算" in zh or "金币" in zh:
            builder.add(key_cap([p[0], p[1] + 2.0, p[2]]))
            builder.add(custom("role_currency", {
                "progress": [1.0, 0.0],
                "lines": [tr(("花费 200 金币", "Spend 200 coins", "花費 200 金幣"))]}))
            builder.add(custom("role_outline", {"linkId": target, "color": 0x99DDFF}))
            builder.add(custom("role_walk", {"linkId": main,
                                             "to": [t[0] - 0.9, t[1], t[2]], "duration": 36}))
            builder.add(custom("role_vitals", {"bars": [{
                "label": tr(("濒死", "Near death", "瀕死")),
                "before": 0.15, "after": 0.9, "color": -65536}]}))
            builder.add(custom("role_teleport", {"linkId": target,
                                                 "pos": [p[0] + 1.1, p[1], p[2]]}))
            builder.add(custom("role_effects", {
                "lines": [tr(("免除死亡 120 秒", "Death nullified for 120 seconds",
                              "免除死亡 120 秒"))]}))
            builder.add(custom("role_timer", {
                "progress": [0.0, 1.0],
                "lines": [tr(("保护 120 秒", "Protected for 120 seconds", "保護 120 秒"))]}))
        elif "冷却" in zh:
            builder.add(custom("role_timer", {
                "progress": [1.0, 0.0],
                "lines": [tr(("技能冷却", "Skill cooldown", "技能冷卻"))]}))
        builder.add({"type": "idle", "duration": 25})
        builder.say(line)


def override_diviner_skill(builder, ctx):
    """占卜家：背包选目标 → 水晶球右键尸体占卜 3 秒 → 传送 → 10 秒回传 / 50 秒冷却。"""
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    ball = "noellesroles:crystal_ball"
    target = rid + "_divine_target"
    corpse_pos = [t[0], t[1], t[2] - 1.0]
    corpse = rid + "_divine_corpse"
    builder.add(player_step(t, target, outline=ctx["outline_color"]))
    builder.add(player_step(corpse_pos, corpse))
    builder.add(custom("role_death", {"linkId": corpse, "pos": corpse_pos,
                                      "name": "ponder.starrailexpress.label.victim"}))
    lines = skill_lines(ctx, ["水晶球", "头像", "3 秒", "10 秒", "50%", "每具尸体", "发光"])
    for line in lines:
        zh = line.get("zh_cn", "")
        if "头像" in zh or "开局拥有" in zh:
            builder.add(item_entity([p[0] - 1.0, p[1] + 0.55, p[2]], ball, rid + "_ball"))
            builder.add(custom("role_hold", {"linkId": main, "item": ball}))
            builder.add(custom("role_screen", {
                "title": tr(("选择占卜目标", "Choose a divination target", "選擇占卜目標")),
                "hint": tr(("点击背包中的玩家头像", "Click a player head in the inventory",
                            "點擊背包中的玩家頭像")),
                "columns": 6}))
        elif "3 秒" in zh:
            builder.add(show_right_click([p[0] - 0.8, p[1] + 1.2, p[2]], ball))
            builder.add(scene_particle("minecraft:soul_fire_flame",
                                       [corpse_pos[0], corpse_pos[1] + 0.9, corpse_pos[2]],
                                       count=10, duration=60))
            builder.add(scene_sound("minecraft:block.amethyst_block.resonate"))
            builder.add(custom("role_timer", {
                "progress": [0.0, 1.0],
                "lines": [tr(("占卜 3 秒", "Divining for 3 seconds", "占卜 3 秒"))]}))
            builder.add(scene_particle("minecraft:enchant",
                                       [corpse_pos[0], corpse_pos[1] + 1.0, corpse_pos[2]],
                                       count=12, duration=60))
            builder.add(scene_sound("minecraft:block.amethyst_block.chime"))
            builder.add(custom("role_teleport", {"linkId": main, "pos": [t[0] - 0.8, t[1], t[2]]}))
        elif "10 秒" in zh or "50 秒" in zh:
            builder.add(show_right_click([p[0] - 0.8, p[1] + 1.2, p[2]], ball))
            builder.add(custom("role_teleport", {"linkId": main, "pos": list(p)}))
            builder.add(custom("role_timer", {
                "progress": [0.0, 1.0],
                "lines": [tr(("10 秒内可回传", "Return within 10 seconds", "10 秒內可回傳"))]}))
            builder.add(custom("role_timer", {
                "progress": [1.0, 0.0],
                "lines": [tr(("超时后水晶球冷却 50 秒",
                              "Ball goes on a 50-second cooldown after timeout",
                              "逾時後水晶球冷卻 50 秒"))]}))
        elif "50%" in zh:
            builder.add(custom("role_currency", {
                "progress": [1.0, 0.5],
                "lines": [tr(("50% 概率破坏水晶球", "50% chance to break the ball",
                              "50% 機率破壞水晶球"))]}))
        elif "每具尸体" in zh:
            builder.add({"type": "highlight_section", "blockPos": [int(corpse_pos[0]), 0,
                                                                    int(corpse_pos[2])],
                         "duration": 180, "color": "green"})
            builder.add(custom("role_panel", {
                "title": tr(("占卜记录", "Divination record", "占卜記錄")),
                "lines": [tr(("每具尸体只能被同一名占卜家占卜一次",
                              "Each body can only be divined once by the same seer",
                              "每具屍體只能被同一名占卜家占卜一次"))]}))
        elif "发光" in zh:
            builder.add(custom("role_death", {"linkId": main, "pos": p,
                                              "name": "ponder.starrailexpress.label.victim",
                                              "corpse": False}))
            builder.add(custom("role_outline", {"linkId": target, "color": 0xFFFFFF66}))
            builder.add(custom("role_timer", {
                "progress": [1.0, 0.0],
                "lines": [tr(("周围 5 格发光 8 秒", "Nearby players glow for 8 seconds",
                              "周圍 5 格發光 8 秒"))]}))
        builder.add({"type": "idle", "duration": 25})
        builder.say(line)


def override_necromancer_skill(builder, ctx):
    """死灵法师：杀手阵亡攒次数 → 右键尸体复活为普通杀手 → 图腾音效与全服通报。"""
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    corpse = rid + "_revive_corpse"
    fallen_pos = [t[0] + 1.4, t[1], t[2] + 0.8]
    fallen = rid + "_fallen_killer"
    builder.add(player_step(t, corpse))
    builder.add(custom("role_death", {"linkId": corpse, "pos": t,
                                      "name": "ponder.starrailexpress.label.victim"}))
    builder.add(player_step(fallen_pos, fallen, outline=ctx["outline_color"]))
    lines = skill_lines(ctx, ["复活", "阵亡", "开锁器", "图腾音效"])
    for line in lines:
        zh = line.get("zh_cn", "")
        if "拥有复活" in zh:
            builder.add({"type": "indicate_success", "blockPos": [int(t[0]), 0, int(t[2])]})
        elif "阵亡" in zh:
            builder.add(custom("role_death", {"linkId": fallen, "pos": fallen_pos,
                                              "name": "ponder.starrailexpress.label.victim",
                                              "corpse": False}))
            builder.add(custom("role_currency", {
                "progress": [0.0, 1.0],
                "lines": [tr(("复活机会 +1", "One revival charge gained", "復活機會 +1"))]}))
        elif "右键尸体" in zh:
            builder.add({"type": "show_controls", "action": "right",
                         "point": [t[0], t[1] + 1.2, t[2]], "direction": "down",
                         "duration": 70, "attachKeyFrame": True})
            builder.add(custom("role_teleport", {"linkId": fallen, "pos": list(t)}))
            builder.add(custom("role_outline", {"linkId": fallen, "color": -65536}))
            builder.add(custom("role_timer", {
                "progress": [1.0, 0.0],
                "lines": [tr(("冷却 3 分钟", "3-minute cooldown", "冷卻 3 分鐘"))]}))
        elif "开锁器" in zh:
            shown = item_for_line(ctx["facts"], zh)
            builder.add(item_entity([p[0] - 1.0, p[1] + 0.55, p[2]], shown, rid + "_lockpick"))
            builder.add(custom("role_hold", {"linkId": main, "item": shown}))
        elif "图腾音效" in zh:
            builder.add(scene_sound("minecraft:totem_use"))
            builder.add(custom("role_panel", {
                "title": tr(("全服通报", "Server-wide announcement", "全服通報")),
                "lines": [tr(("死者已复活为普通杀手", "The dead returns as a plain killer",
                              "死者已復活為普通殺手"))]}))
        builder.add({"type": "idle", "duration": 25})
        builder.say(line)


def override_warlock_skill(builder, ctx):
    """咒术师：G 窃取咒物 → Y 切蚀骨之咒 → 诅咒标记扣 SAN → 背包开灰髓之境。"""
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    target = rid + "_curse_target"
    builder.add(player_step(t, target, outline=ctx["outline_color"]))
    lines = skill_lines(ctx, ["窃取发肤", "蚀骨之咒", "领域展开", "破界"])
    for line in lines:
        zh = line.get("zh_cn", "")
        if "窃取发肤" in zh:
            builder.add(key_cap([p[0], p[1] + 2.0, p[2]]))
            builder.add(custom("role_walk", {"linkId": main,
                                             "to": [t[0] - 0.9, t[1], t[2]], "duration": 36}))
            builder.add(scene_particle("minecraft:soul", [t[0], t[1] + 1.0, t[2]],
                                       count=14, duration=60))
            builder.add(custom("role_outline", {"linkId": target, "color": 0xAA55FF}))
            builder.add(custom("role_panel", {
                "title": tr(("咒物", "Cursed object", "咒物")),
                "lines": [tr(("获得一份咒物 · 每人整局一次",
                              "Gain a cursed object - once per player per match",
                              "獲得一份咒物 · 每人整局一次"))]}))
        elif "蚀骨之咒" in zh:
            builder.add(key_cap([p[0], p[1] + 2.0, p[2]], switch=True))
            builder.add(custom("role_mood", {
                "before": 0.9, "after": 0.6,
                "lines": [tr(("扣除 30% SAN", "Lose 30% SAN", "扣除 30% SAN"))]}))
            builder.add(custom("role_effects", {
                "lines": [tr(("隔离 · 缓慢 II · 被诅咒 45 秒",
                              "Isolated - slowness II - cursed for 45 seconds",
                              "隔離 · 緩慢 II · 被詛咒 45 秒"))]}))
            builder.add(custom("role_outline", {"linkId": target, "color": -65536}))
            builder.add(custom("role_timer", {
                "progress": [1.0, 0.0],
                "lines": [tr(("诅咒 45 秒 · 冷却 45 秒",
                              "Curse lasts 45 seconds - 45-second cooldown",
                              "詛咒 45 秒 · 冷卻 45 秒"))]}))
            builder.add(scene_particle("minecraft:soul", [t[0], t[1] + 1.1, t[2]],
                                       count=12, duration=60))
            builder.add(custom("role_death", {"linkId": target, "pos": t,
                                              "name": "ponder.starrailexpress.label.victim"}))
            builder.add(custom("role_currency", {
                "progress": [0.0, 1.0],
                "lines": [tr(("诅咒期间死亡 +40 金币", "Target dies cursed - gain 40 coins",
                              "詛咒期間死亡 +40 金幣"))]}))
        elif "领域展开" in zh:
            builder.add(custom("role_screen", {
                "title": tr(("选择被诅咒的玩家", "Choose the cursed player", "選擇被詛咒的玩家")),
                "hint": tr(("打开背包点选一名已被诅咒且存活的目标",
                            "Pick a living cursed target in the inventory",
                            "開啟背包點選一名已被詛咒且存活的目標")),
                "columns": 6}))
            builder.add(custom("role_ambience", {"color": 0x888888, "alpha": 0.55,
                                                 "mode": "fog"}))
            builder.add(custom("role_timer", {
                "progress": [0.0, 1.0],
                "lines": [tr(("灰髓之境 25 秒 · 冷却 60 秒",
                              "Ashen Marrow for 25 seconds - 60-second cooldown",
                              "灰髓之境 25 秒 · 冷卻 60 秒"))]}))
            builder.add(custom("role_effects", {
                "lines": [tr(("目标黑暗 / 失明 / 禁技 · 自身速度提升",
                              "Target blinded and silenced - you gain speed",
                              "目標黑暗 / 失明 / 禁技 · 自身速度提升"))]}))
            builder.add(custom("role_teleport", {"linkId": target,
                                                 "pos": [t[0], t[1] + 2.0, t[2]]}))
        else:
            builder.add(custom("role_death", {"linkId": main, "pos": p,
                                              "name": "ponder.starrailexpress.label.victim",
                                              "corpse": False}))
            builder.add(player_step(p, main))
            builder.add(custom("role_teleport", {"linkId": target, "pos": list(t)}))
            builder.add(custom("role_timer", {
                "progress": [1.0, 0.0],
                "lines": [tr(("时间到 · 幸存者原路返回",
                              "Time up - survivors return where they came from",
                              "時間到 · 倖存者原路返回"))]}))
        builder.add({"type": "idle", "duration": 25})
        builder.say(line)


ROLE_SEGMENT_OVERRIDES = {
    ("kafu_chino", "skill"): override_kafu_skill,
    ("noisemaker", "skill"): override_noisemaker_skill,
    ("broadcaster", "skill"): override_broadcaster_skill,
    ("thief", "skill"): override_thief_skill,
    ("creeper", "skill"): override_creeper_skill,
    ("candlebearer", "skill"): override_candlebearer_skill,
    ("medium", "skill"): override_medium_skill,
    ("photographer", "skill"): override_photographer_skill,
    ("pooler", "skill"): override_pooler_skill,
    ("doctor", "skill"): override_doctor_skill,
    ("vulture", "skill"): override_vulture_skill,
    ("locksmith", "skill"): override_locksmith_skill,
    ("houjuu_nue", "skill"): override_nue_skill,
    ("houjuu_nue", "form"): override_nue_form,
    ("arsonist", "items"): override_arsonist_items,
    ("arsonist", "skill"): override_arsonist_skill,
    ("arsonist", "shop"): override_arsonist_shop,
    ("arsonist", "body"): override_arsonist_body,
    ("arsonist", "vision"): override_arsonist_vision,
    ("baka", "items"): override_baka_items,
    ("baka", "panel"): override_baka_panel,
    ("baka", "shop"): override_baka_shop,
    ("baka", "mood"): override_baka_mood,
    ("baka", "details"): override_baka_details,
    ("disc_master", "shop"): override_disc_master_shop,
    ("mortician_bodymaker", "items"): override_mortician_bodymaker_items,
    ("mortician_bodymaker", "skill"): override_mortician_bodymaker_skill,
    ("ibaraki_kasen", "shop"): override_kasen_shop,
    ("jade_general", "skill"): override_jade_general_skill,
    ("telegrapher", "skill"): override_telegrapher_skill,
    ("agent", "skill"): override_agent_skill,
    ("fortuneteller", "skill"): override_fortuneteller_skill,
    ("diviner", "skill"): override_diviner_skill,
    ("necromancer", "skill"): override_necromancer_skill,
    ("warlock", "skill"): override_warlock_skill,
    ("rabbit_wansui", "form"): override_rabbit_form,
    ("snow_golem", "form"): override_snow_golem_form,
    ("metal_monster", "form"): override_metal_monster_form,
    ("morphling", "form"): override_morphling_form,
    ("tomato_head", "form"): override_tomato_form,
    ("embalmer", "form"): override_embalmer_form,
    ("skincrawler", "form"): override_skincrawler_form,
    ("doomed_sinner", "form"): override_doomed_sinner_form,
    ("amon", "form"): override_amon_form,
    ("leather_pig", "form"): override_leather_pig_form,
    ("nature_spirit", "form"): override_nature_spirit_form,
    ("pumpkin_spirit", "form"): override_pumpkin_spirit_form,
    ("magician", "form"): override_magician_form,
    ("vulture", "form"): override_vulture_form,
    ("gambler", "form"): override_gambler_form,
    ("roulette_devil", "form"): override_roulette_devil_form,
    ("dreadlord", "form"): override_dreadlord_form,
    ("entity_303", "form"): override_entity303_form,
    ("monokuma", "form"): override_monokuma_form,
    ("purple_monster", "form"): override_purple_monster_form,
    ("raven", "form"): override_raven_form,
}


def build_role_scene(rid, facts, intros, names, goals):
    """生成一个职业的思索场景。"""
    facts = dict(facts)
    if rid in ROLE_ITEMS:
        facts["initialItems"] = list(ROLE_ITEMS[rid])
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

    all_trios = [trio(i) for i in range(len(zh_lines))]

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
    outline_color = INSTINCT_COLOR_BY_ROLE.get(rid)
    if outline_color is None:
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
    opening_lines = [faction_line]
    if relation_index is not None:
        opening_lines.append(trio(relation_index))
    if goal_text:
        opening_lines.append(goal_text)
    for index, line in enumerate(opening_lines):
        zh = line.get("zh_cn", "")
        if is_death_line(zh) and not is_immune_death(zh):
            for step in death_act_steps(zh, rid, player_pos, second_pos,
                                        index, outline_color):
                builder.add(step)
        builder.say(line)
    ensure_particles(builder, opening_lines, rid)
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
        if key == "shop" and any(word in "".join(zh_lines)
                                 for word in ("商店", "可购买", "购买")):
            # 介绍提到商店就要有商店页面，即使分类器没把这句分到 shop。
            forced = True
        if key == "domain" and rid in DOMAIN_BY_ROLE:
            forced = True
        if key == "form" and (rid, "form") in ROLE_SEGMENT_OVERRIDES:
            # 介绍里明确写了变身 / 伪装的职业，即使没有被自动分类到 form 段也要演出来。
            forced = True
        if key == "skill" and (rid, "skill") in ROLE_SEGMENT_OVERRIDES:
            # 广播员的介绍只写「花 50 金币广播」，没有「技能」二字，但游戏里确实是
            # 按技能键打开广播编辑页；有专属 override 就强制建这一段。
            forced = True
        if not lines and not forced:
            continue
        lines = lines or []
        builder = SceneBuilder(player_pos)
        builder.add(show_structure(nbt))
        builder.add(player_step(player_pos, rid + "_main"))

        override = ROLE_SEGMENT_OVERRIDES.get((rid, key))
        if override is not None:
            override(builder, {"rid": rid, "facts": facts, "lines": lines, "key": key,
                               "player_pos": player_pos, "second_pos": second_pos,
                               "outline_color": outline_color, "all_lines": all_trios})
            ensure_particles(builder, all_trios, rid)
            builder.finish()
            scenes.append({"id": key, "title": title, "steps": builder.steps})
            continue

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
                lead = item_for_line(facts, lines[0].get("zh_cn", "") if lines else "")
                if lead not in shop:
                    lead = shop[0]
                builder.add({"type": "create_item_entity", "attachKeyFrame": True,
                             "pos": [player_pos[0] - 1.4, player_pos[1] + 0.55,
                                     player_pos[2] - 1.4],
                             "motion": [0.0, 0.0, 0.0], "count": 1,
                             "item": lead, "linkId": rid + "_item"})
        elif key == "skill":
            # 右上角只放冷却 / 代价等元信息，具体机制由下面逐句的动作演绎。
            meta = [dict(line) for line in lines
                    if any(word in line.get("zh_cn", "") for word in ("冷却", "金币", "消耗", "秒"))][:2]
            if not meta and lines:
                meta = [dict(lines[0])]
            if meta:
                builder.add(custom("role_skill", {"label": SEGMENT_TITLE_KEY["skill"],
                                                  "lines": meta}))
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
            vision_colors = VISION_COLORS_BY_ROLE.get(rid)
            if vision_colors:
                for index, color in enumerate(vision_colors[:3]):
                    pos = (second_pos if index == 0 else
                           [second_pos[0], second_pos[1], round(second_pos[2] + 1.2 * index, 2)])
                    builder.add(player_step(pos, rid + "_target_" + str(index), outline=color))
            else:
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
        state_steps = {"starrailexpress:role_vitals", "starrailexpress:role_timer",
                       "starrailexpress:role_currency", "starrailexpress:role_panel",
                       "starrailexpress:role_bond", "starrailexpress:role_mood",
                       "starrailexpress:role_effects"}
        state_used = any(step.get("type") in state_steps for step in builder.steps)
        shop_items = [item for item in (facts.get("shopItems") or []) if item][:4] if key == "shop" else []
        for index, line in enumerate(lines):
            zh_line = line.get("zh_cn", "")
            death_before = sum(1 for step in builder.steps
                               if step["type"] == "starrailexpress:role_death")
            item_steps_before = sum(1 for step in builder.steps
                                    if step["type"] in ("create_item_entity", "show_controls",
                                                        "starrailexpress:role_hold"))
            if shop_items:
                shown = item_for_line(facts, zh_line)
                builder.add(item_entity(
                    [player_pos[0] - 1.4 + (index % 4) * 0.75, player_pos[1] + 0.55,
                     player_pos[2] - 1.2], shown,
                    rid + "_shop_line_" + str(index)))
            if key in ACT_SEGMENTS and has_action(zh_line):
                acts, _ = build_skill_act(zh_line, rid, facts,
                                          player_pos, second_pos, outline_color, str(index), line)
                if state_used:
                    # 左上角覆盖层只会画在最上层，同段保留第一块即可。
                    acts = [step for step in acts if step.get("type") not in state_steps]
                if any(step.get("type") in state_steps for step in acts):
                    state_used = True
                for step in acts:
                    builder.add(step)
            elif key not in ACT_SEGMENTS and is_switch_line(zh_line):
                # shop / details 等段落里讲到切换键时也要显示正确的 Y 键帽。
                builder.add(key_cap([player_pos[0], player_pos[1] + 1.9, player_pos[2]],
                                    switch=True))
            # 只讲道具、没有触发词的句子也要把道具摆出来，否则介绍「药丸」时
            # 画面里还停着上一句的心脏起搏器。
            if key in ACT_SEGMENTS and zh_line and item_steps_before == sum(
                    1 for step in builder.steps
                    if step["type"] in ("create_item_entity", "show_controls",
                                        "starrailexpress:role_hold")):
                shown = item_for_line(facts, zh_line)
                shown_ids = set()
                for step in builder.steps:
                    if step["type"] in ("create_item_entity", "show_controls"):
                        shown_ids.add(step.get("item"))
                    elif step["type"] == "starrailexpress:role_hold":
                        shown_ids.add(step.get("parameters", {}).get("item"))
                if shown not in shown_ids:
                    builder.add(item_entity(
                        [player_pos[0] - 0.8, player_pos[1] + 0.5, player_pos[2]], shown,
                        rid + "_line_item_" + str(index)))
                    for effect in item_effect_steps(
                            shown, [second_pos[0], second_pos[1] + 0.8, second_pos[2]]):
                        builder.add(effect)
            # 讲死亡的句子必须在这一幕演一次死亡；build_skill_act 若已经演过
            # 击杀就不重复。body 段开头已有受害者死亡，不再逐句叠尸体。
            if (key != "body" and zh_line and
                    death_before == sum(1 for step in builder.steps
                                        if step["type"] == "starrailexpress:role_death")):
                for step in death_act_steps(zh_line, rid, player_pos, second_pos,
                                            index, outline_color):
                    builder.add(step)
            builder.say(line)
        ensure_particles(builder, lines, rid)
        builder.finish()
        scenes.append({"id": key, "title": title, "steps": builder.steps})

    # 没有主动技能的职业不能被通用演绎塞进技能键帽与走动。
    if rid in NO_SKILL_KEY_ROLES:
        for scene in scenes:
            scene["steps"] = [step for step in scene["steps"]
                              if step.get("type") not in ("starrailexpress:role_key",
                                                          "starrailexpress:role_walk")]

    items = []
    for candidate in ROLE_ITEMS.get(rid, []) + facts.get("initialItems", []) + facts.get("shopItems", []):
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
                # 万圣节介绍明确提到「右键打开商店」，对应这一幕就要有商店页面。
                builder.add({"type": "starrailexpress:role_shop", "attachKeyFrame": True,
                             "parameters": {"role": rid, "columns": 4,
                                            "items": [{"item": "minecraft:pumpkin", "price": 0,
                                                       "currency": "money"}]}})
            elif segment_id == "combat":
                builder.add({"type": "starrailexpress:role_render", "attachKeyFrame": True,
                             "parameters": {"label": "万圣节亡灵"}})

        for line_index in line_indices:
            line = trio(line_index)
            zh = line.get("zh_cn", "")
            if is_death_line(zh) and not is_immune_death(zh):
                for step in death_act_steps(zh, rid, player_pos, second_pos,
                                            line_index, -65536):
                    builder.add(step)
            builder.say(line)
        ensure_particles(builder, [trio(i) for i in line_indices], rid)
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
