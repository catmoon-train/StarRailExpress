# -*- coding: utf-8 -*-
"""往 starrailexpress 语言文件里补齐 Ponder 扩展步骤的翻译键（三语）。

用法：python tools/update_ponder_lang.py
"""

import io
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LANG = os.path.join(ROOT, "src", "main", "resources", "assets", "starrailexpress", "lang")

KEYS = {
    "ponder.starrailexpress.step.role_items": {
        "zh_cn": "职业初始物品",
        "en_us": "Role starting items",
        "zh_tw": "職業初始物品",
    },
    "ponder.starrailexpress.step.role_death": {
        "zh_cn": "死亡动画",
        "en_us": "Death animation",
        "zh_tw": "死亡動畫",
    },
    "ponder.starrailexpress.step.role_screen": {
        "zh_cn": "技能界面",
        "en_us": "Skill interface",
        "zh_tw": "技能介面",
    },
    "ponder.starrailexpress.step.role_ambience": {
        "zh_cn": "环境染色",
        "en_us": "Ambience tint",
        "zh_tw": "環境染色",
    },
    "ponder.starrailexpress.screen.select_player": {
        "zh_cn": "选择目标玩家",
        "en_us": "Select a target player",
        "zh_tw": "選擇目標玩家",
    },
    "ponder.starrailexpress.screen.search_hint": {
        "zh_cn": "可输入玩家名搜索",
        "en_us": "Type a player name to search",
        "zh_tw": "可輸入玩家名搜尋",
    },
    "ponder.starrailexpress.screen.pick_hint": {
        "zh_cn": "点击头像即可施放",
        "en_us": "Click a head to apply",
        "zh_tw": "點擊頭像即可施放",
    },
    "ponder.starrailexpress.label.binding": {
        "zh_cn": "绑定对象",
        "en_us": "Bound target",
        "zh_tw": "綁定對象",
    },
    "ponder.starrailexpress.label.cooldown": {
        "zh_cn": "冷却",
        "en_us": "Cooldown",
        "zh_tw": "冷卻",
    },
    "ponder.starrailexpress.label.duration": {
        "zh_cn": "持续时间",
        "en_us": "Duration",
        "zh_tw": "持續時間",
    },
    "ponder.starrailexpress.label.effect": {
        "zh_cn": "效果",
        "en_us": "Effect",
        "zh_tw": "效果",
    },
    "ponder.starrailexpress.label.psycho": {
        "zh_cn": "疯狂形态",
        "en_us": "Psycho form",
        "zh_tw": "瘋狂形態",
    },
    "ponder.starrailexpress.label.disguise": {
        "zh_cn": "伪装外观",
        "en_us": "Disguise",
        "zh_tw": "偽裝外觀",
    },
    "ponder.starrailexpress.label.items": {
        "zh_cn": "随身物品",
        "en_us": "Carried items",
        "zh_tw": "隨身物品",
    },
    "ponder.starrailexpress.label.skill": {
        "zh_cn": "技能",
        "en_us": "Skill",
        "zh_tw": "技能",
    },
    "ponder.starrailexpress.label.body": {
        "zh_cn": "尸体",
        "en_us": "Body",
        "zh_tw": "屍體",
    },
    "ponder.starrailexpress.label.victim": {
        "zh_cn": "受害者",
        "en_us": "Victim",
        "zh_tw": "受害者",
    },
    "ponder.starrailexpress.label.notes": {
        "zh_cn": "记录",
        "en_us": "Notes",
        "zh_tw": "記錄",
    },
    "ponder.starrailexpress.label.vitals": {
        "zh_cn": "虚拟血量",
        "en_us": "Virtual health",
        "zh_tw": "虛擬血量",
    },
    "ponder.starrailexpress.label.shield": {
        "zh_cn": "护盾",
        "en_us": "Shield",
        "zh_tw": "護盾",
    },
    "ponder.starrailexpress.label.sanity": {
        "zh_cn": "心情值",
        "en_us": "Sanity",
        "zh_tw": "心情值",
    },
    "ponder.starrailexpress.label.coins": {
        "zh_cn": "金币",
        "en_us": "Coins",
        "zh_tw": "金幣",
    },
    "ponder.starrailexpress.label.progress": {
        "zh_cn": "进度",
        "en_us": "Progress",
        "zh_tw": "進度",
    },
    "ponder.starrailexpress.label.vision": {
        "zh_cn": "视野",
        "en_us": "Vision",
        "zh_tw": "視野",
    },
    "ponder.starrailexpress.label.summon": {
        "zh_cn": "召唤物",
        "en_us": "Summons",
        "zh_tw": "召喚物",
    },
    "ponder.starrailexpress.label.stage": {
        "zh_cn": "阶段",
        "en_us": "Stage",
        "zh_tw": "階段",
    },
}


def main():
    for locale in ("zh_cn", "en_us", "zh_tw"):
        path = os.path.join(LANG, locale + ".json")
        with io.open(path, encoding="utf-8-sig") as f:
            data = json.load(f)
        added = 0
        for key, values in KEYS.items():
            if key not in data:
                data[key] = values[locale]
                added += 1
        with io.open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(json.dumps(data, ensure_ascii=False, indent=2, sort_keys=False))
            f.write("\n")
        print(locale, "added", added)


if __name__ == "__main__":
    main()
