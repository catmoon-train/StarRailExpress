# -*- coding: utf-8 -*-
"""生成《职业思索系统全覆盖规划》文档。

只读：role_modifier_intro / role_name_goals 的 zh_cn 语言文件。
输出：docs/职业思索系统全覆盖规划.md

用法：python tools/gen_ponder_plan.py
"""
import io
import json
import os
import re
from collections import Counter, OrderedDict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets")
OUT = os.path.join(ROOT, "docs", "职业思索系统全覆盖规划.md")

# 其它模式职业（Java 侧 setOtherModeRole(true)，含 GooseDuckRole 基类）
OTHER_MODE = OrderedDict([
    ("custom_pending", "自选职业占位（games 自选模式）"),
    ("discovery_civilian", "游客（发现模式）"),
    ("hide_and_seek_seeker", "躲猫猫寻找者"),
    ("super_loose_end", "特殊结局-超"),
    ("ghost_loose_end", "特殊结局-幽"),
    ("dirt_id", "泥土占位"),
    ("goose", "鹅鸭杀：鹅"),
    ("duck", "鹅鸭杀：鸭"),
    ("echo_carrier", "回声模式：携带者"),
    ("echo_interceptor", "回声模式：拦截者"),
    ("echo_broker", "回声模式：掮客"),
])

# 非职业条目（无 Java 注册 / 纯占位）
PLACEHOLDER = OrderedDict([
    ("error404_puppet", "错误码占位"),
    ("example_neutral", "示例占位"),
    ("null", "未选择"),
    ("unknown", "未知"),
])

# 事件条目（阵营为「杀手事件 / 中立事件」，不是可选职业）
EVENTS = OrderedDict([
    ("command_block", "杀手事件载体"),
    ("night_event", "黑夜事件"),
    ("halloween_event", "万圣节事件"),
])

# 已完成的思索场景（scenes/ 下实际存在）
DONE = OrderedDict([
    ("bartender", "酒保：物品/护盾/毒药三段，模板级参考"),
    ("jester", "小丑：阵营 + 疯狂模式皮肤切换"),
    ("recaller", "回溯者：技能/传送/时间轴"),
])

# 难度评分用词表
FEATURES = OrderedDict([
    ("items", ["开局自带", "开局获得", "开局持有", "右键", "手持", "道具", "收纳袋"]),
    ("shop", ["商店", "购买", "售价", "金币购买"]),
    ("body", ["尸体", "遗体"]),
    ("panel", ["界面", "查看", "终端", "笔记", "记录在", "纸条", "笔记本"]),
    ("overlay", ["透视", "直觉", "发光", "高亮", "san值", "护盾", "心情"]),
    ("skill", ["技能键", "主动技能", "被动", "冷却", "CD"]),
    ("multi", ["绑定", "追随", "寄宿", "潜入", "附身", "夺舍", "跟随", "队友", "另一名玩家", "被标记者"]),
    ("form", ["疯狂模式", "形态", "时刻", "阶段", "进化", "变身", "第二形态", "终幕"]),
    ("structure", ["地图", "房间", "井盖", "水下", "列车", "车厢", "机关", "铁门", "宿舍", "墓地", "商店门"]),
    ("domain", ["领域"]),
    ("unique", ["寄生", "夺舍", "做题", "投票", "复制", "伪装成", "交换", "回放", "记忆", "伪装"]),
])

WEIGHT = {
    "items": 1, "shop": 1, "body": 1, "panel": 1, "overlay": 1, "skill": 1,
    "multi": 1, "form": 2, "structure": 2, "domain": 2, "unique": 2,
}

LABEL = {
    "items": "物品", "shop": "商店", "body": "尸体", "panel": "面板",
    "overlay": "叠加", "skill": "技能", "multi": "多人", "form": "形态",
    "structure": "结构", "domain": "领域", "unique": "特殊",
}

WORK = {
    "items": "摆出初始/持用物品（create_item_entity + show_controls.item）",
    "shop": "商店陈列（role_shop + 实际 ShopEntry）",
    "body": "尸体实体（player_body + CustomName）",
    "panel": "文书/界面面板（role_panel 或 show_interface）",
    "overlay": "数值与透视覆盖层（role_vitals / role_effects / role_timer）",
    "skill": "技能演示与键帽（role_skill + role_key）",
    "multi": "第二个玩家模型与关系（role_player.linkId + role_bond）",
    "form": "多段落形态切换（皮肤替换，参考 jester）",
    "structure": "专用结构 NBT（generate_ponder_structures.py）",
    "domain": "领域场景切换（role_domain）",
    "unique": "现有步骤表达不了，需新增 Java 步骤或降级为文本",
}

# 逐职业表格里用的短标签
SHORT = {
    "items": "摆物品", "shop": "商店陈列", "body": "尸体实体", "panel": "面板",
    "overlay": "数值叠加", "skill": "技能+键帽", "multi": "第二玩家",
    "form": "形态切换", "structure": "新结构", "domain": "领域场景", "unique": "新步骤",
}

# 重点职业的人工备注（难度最高或机制特殊）
MANUAL = {
    "leader": "2186 字、招募/追随/效果分支极多；建议拆成「招募」「追随者效果」「终局」等 5+ 段落，先降级为 role_bond + role_panel。",
    "ma_chen_xu": "2122 字、布袋鬼多阶段形态与寄生逻辑；需要 2 个以上玩家模型与形态切换皮肤，建议最后做。",
    "roulette_devil": "972 字、轮盘赌随机分支，只能演示一条主线，其余用文本说明。",
    "undead_lord": "896 字、亡灵之主多召唤物 + 阶段，需要自定义实体与召唤演示。",
    "arsonist": "874 字、浇油→点火→纵火犯时刻三段式，且有 80 秒时刻与喷火枪；结构+形态+叠加全占。",
    "clone_doctor": "730 字、克隆与身份复制，需要第三个玩家模型模拟被克隆者。",
    "wizard": "705 字、多法术分支，建议只演示 2 个代表法术。",
    "swordsman": "644 字、剑客连招/格挡，需要 role_skill 多段与粒子。",
    "wraith_assassin": "641 字、冤魂穿墙与瞬移，需要 role_teleport + 粒子。",
    "dream": "634 字、Dream 有专属斧（noellesroles:dream_axe）与疯狂模式皮肤。",
    "amon": "593 字、寄生/夺舍/终幕三阶段，需要自定义寄生标记演示，属最难一档。",
    "painter": "581 字、颜料与画作生成，需要物品与方块混合展示。",
    "command_block": "511 字、事件型职业（杀手事件），场景是命令方块房间，需要专用结构。",
    "bartender": "已完成（role_tutorial_bartender.json），作为物品类模板。",
    "jester": "已完成（role_tutorial_jester.json），作为形态切换模板。",
    "recaller": "已完成（role_tutorial_recaller.json），作为技能/传送模板。",
}


def load(path):
    with io.open(path, encoding="utf-8-sig") as f:
        return json.load(f)


def main():
    intro = load(os.path.join(ASSETS, "role_modifier_intro", "lang", "zh_cn.json"))
    names = load(os.path.join(ASSETS, "role_name_goals", "lang", "zh_cn.json"))
    goals = {}
    for k, v in names.items():
        if k.startswith("announcement.star.goals."):
            goals[k[len("announcement.star.goals."):]] = v
    name = {k[len("announcement.star.role."):]: v for k, v in names.items()
            if k.startswith("announcement.star.role.")}

    all_ids = [k[len("info.screen.roleid."):] for k in intro
               if k.startswith("info.screen.roleid.") and not k.endswith(".simple")]

    en_intro = load(os.path.join(ASSETS, "role_modifier_intro", "lang", "en_us.json"))
    en_ids = {k[len("info.screen.roleid."):] for k in en_intro
              if k.startswith("info.screen.roleid.") and not k.endswith(".simple")}
    missing_en = sorted(set(all_ids) - en_ids)

    rows = []
    for key, text in intro.items():
        if not key.startswith("info.screen.roleid.") or key.endswith(".simple"):
            continue
        rid = key[len("info.screen.roleid."):]
        if rid in OTHER_MODE or rid in PLACEHOLDER or rid in EVENTS:
            continue
        lines = text.split("\n")
        feats = []
        for feat, words in FEATURES.items():
            if any(w in text for w in words):
                feats.append(feat)
        score = 1 + sum(WEIGHT[f] for f in feats)
        if len(text) > 600:
            score += 1
        rows.append({
            "id": rid,
            "name": name.get(rid, "?"),
            "faction": lines[0].strip() if lines else "",
            "goal": goals.get(rid, ""),
            "len": len(text),
            "feats": feats,
            "score": score,
            "done": rid in DONE,
        })

    rows.sort(key=lambda r: (r["done"], r["score"], r["len"], r["id"]))
    todo = [r for r in rows if not r["done"]]

    tiers = [("A", 1, 2, "极简（纯文本/单能力）", "0.5 小时"),
             ("B", 3, 4, "常规（技能 + 物品）", "2 小时"),
             ("C", 5, 6, "复杂（商店/尸体/多玩家/形态）", "4 小时"),
             ("D", 7, 99, "最难（多阶段/专用结构/无法直接表达）", "8 小时以上")]

    def tier_of(score):
        for t, lo, hi, _, _ in tiers:
            if lo <= score <= hi:
                return t
        return "D"

    counts = Counter(tier_of(r["score"]) for r in todo)
    hours = {"A": 0.5, "B": 2, "C": 4, "D": 8}
    total_hours = sum(hours[tier_of(r["score"])] for r in todo)

    L = []
    ap = L.append
    ap("# 职业思索系统全覆盖规划")
    ap("")
    ap("> 本文是规划文档，不是实现记录。生成脚本：`tools/gen_ponder_plan.py`（只读语言文件，可重跑）。")
    ap("> 数据源：`assets/role_modifier_intro/lang/zh_cn.json`、`assets/role_name_goals/lang/zh_cn.json`。")
    ap("")
    ap("## 1. 目标")
    ap("")
    ap("为所有正式职业补齐「思索（Ponder）」职业教程，并在 `RoleIntroduceScreen` 的入口按钮里可用。")
    ap("教程文案逐句取自 `info.screen.roleid.<id>` 原文，不自行改写（见 `docs/PONDERER_NOTES.md`）。")
    ap("")
    ap("## 2. 范围")
    ap("")
    ap("`info.screen.roleid.*` 详细介绍共 %d 条，按以下规则排除后进入规划。" % len(all_ids))
    ap("")
    ap("- 其它模式职业 %d 个（Java 侧 `setOtherModeRole(true)`）：" % len(OTHER_MODE))
    ap("")
    ap("| id | 说明 |")
    ap("| --- | --- |")
    for k, v in OTHER_MODE.items():
        ap("| `%s` | %s |" % (k, v))
    ap("")
    ap("- 占位条目 %d 个：%s" % (len(PLACEHOLDER), "、".join("`%s`" % k for k in PLACEHOLDER)))
    ap("- 事件条目 %d 个（不是可选职业，但同样需要思索；它们依赖整局世界状态，单列第 5 批，见第 10 节）：%s"
       % (len(EVENTS), "、".join("`%s`（%s）" % (k, v) for k, v in EVENTS.items())))
    ap("")
    ap("**本次范围：%d 个职业 + %d 个事件条目 = %d 条思索**；职业中已完成 %d 个（`%s`），"
       "待规划 %d 个职业与 %d 个事件（事件见第 10 节）。"
       % (len(rows), len(EVENTS), len(rows) + len(EVENTS), len(DONE), "`、`".join(DONE),
          len(todo), len(EVENTS)))
    ap("")
    ap("| 档位 | 数量 | 占比 |")
    ap("| --- | --- | --- |")
    for t, lo, hi, _, _ in tiers:
        ap("| %s | %d | %.0f%% |" % (t, counts[t], 100.0 * counts[t] / max(1, len(todo))))
    ap("")
    ap("## 3. 现有资产")
    ap("")
    ap("| 资产 | 位置 | 说明 |")
    ap("| --- | --- | --- |")
    ap("| 场景 JSON | `assets/starrailexpress/ponderer/scenes/role_tutorial_*.json` | 现有 3 个：bartender / jester / recaller |")
    ap("| 内容清单 | `assets/starrailexpress/ponderer/content/role_tutorials.json` | 场景索引，新增场景必须登记 |")
    ap("| 结构 NBT | `assets/starrailexpress/ponder/<name>.nbt` | `role_tutorial_common` 可复用作通用房间 |")
    ap("| 自定义步骤 | `PonderSreExtension.java` | 16 个：role_player / walk / teleport / key / skill / mood / render / disguise / vitals / currency / timer / effects / bond / panel / shop / domain |")
    ap("| 入口 | `RolePonderTutorial.java`、`RoleIntroduceScreen` | 场景 id 前缀 `starrailexpress:role_tutorial.` |")
    ap("")
    ap("入口按钮对所有职业常驻显示（`isPonderButtonVisible()` 只判断是否选中职业），点击时按 "
       "`role_tutorial.<职业路径>` 查场景；查不到只弹 `screen.roleintroduce.ponder.no_tutorial` 提示。"
       "因此**补齐场景 JSON + 登记清单即可自动生效，不需要为每个职业写 Java 入口**。")
    ap("")
    ap("现有三个场景的角色定位：")
    ap("")
    for k, v in DONE.items():
        ap("- `%s`：%s" % (k, v))
    ap("")
    ap("## 4. 难度评估模型")
    ap("")
    ap("按介绍文本命中的机制元素累加评分（基础 1 分），>600 字额外 +1：")
    ap("")
    ap("| 元素 | 权重 | 判定词（示例） | 需要的补充 |")
    ap("| --- | --- | --- | --- |")
    for feat, words in FEATURES.items():
        ap("| %s | %d | %s | %s |" % (LABEL[feat], WEIGHT[feat], "、".join(words[:5]),
                                      WORK[feat]))
    ap("")
    ap("分档：")
    ap("")
    ap("| 档位 | 分值 | 含义 | 单条预估 | 数量 |")
    ap("| --- | --- | --- | --- | --- |")
    for t, lo, hi, desc, est in tiers:
        rng = "%d" % lo if lo == hi else ("≥%d" % lo if hi >= 99 else "%d–%d" % (lo, hi))
        ap("| %s | %s | %s | %s | %d |" % (t, rng, desc, est, counts[t]))
    ap("")
    ap("合计预估 **%.1f 人日**（按 A 0.5h / B 2h / C 4h / D 8h+ 粗算，不含结构建模与 Java 新步骤）。" % (total_hours / 8.0))
    ap("")
    ap("## 5. 批次计划")
    ap("")
    ap("| 批次 | 档位 | 数量 | 预估 | 目标 | 验收 |")
    ap("| --- | --- | --- | --- | --- | --- |")
    ap("| 第 1 批 | A | %d | %.1f 人日 | 用 `role_tutorial_common` 结构 + 纯文本/单技能，跑通流水线与入口 | 全部能在介绍页点开并播放 |"
       % (counts["A"], counts["A"] * hours["A"] / 8.0))
    ap("| 第 2 批 | B | %d | %.1f 人日 | 加物品展示与键帽提示，沉淀 3 套段落模板 | 文本不重叠、物品不悬空 |"
       % (counts["B"], counts["B"] * hours["B"] / 8.0))
    ap("| 第 3 批 | C | %d | %.1f 人日 | 商店陈列、尸体实体、多玩家模型、形态切换 | 商店条目与实际 ShopEntry 一致 |"
       % (counts["C"], counts["C"] * hours["C"] / 8.0))
    ap("| 第 4 批 | D | %d | %.1f 人日 | 专用结构 NBT 与新步骤（寄生/夺舍/多阶段/事件房间） | 结构自检通过、日志无 unavailable |"
       % (counts["D"], counts["D"] * hours["D"] / 8.0))
    ap("| 第 5 批 | 事件 | %d | 约 19 人日 | 三个事件条目（命令方块 / 黑夜 / 万圣节），先补环境层等公共能力 | 概率与阶段与源码一致，见第 10 节 |"
       % len(EVENTS))
    ap("")
    ap("第 1 批建议先做这 10 个打样（介绍最短、只需通用房间）：%s。"
       % "、".join("`%s`" % r["id"] for r in todo[:10]))
    ap("每批完成后统一做一次运行验证：`loaded N scenes`、无 `scene ... unavailable`、逐场景实机播放。")
    ap("")
    ap("## 6. 逐职业清单（由易到难）")
    ap("")
    ap("元素列缩写：物品 / 商店 / 尸体 / 面板 / 叠加 / 技能 / 多人 / 形态 / 结构 / 领域 / 特殊。")
    ap("")
    for t, lo, hi, desc, est in tiers:
        group = [r for r in todo if tier_of(r["score"]) == t]
        ap("### %s 档 · %s（%d 个）" % (t, desc, len(group)))
        ap("")
        if not group:
            ap("无。")
            ap("")
            continue
        ap("| # | 职业 | id | 阵营 | 字数 | 元素 | 需要补充 | 备注 |")
        ap("| --- | --- | --- | --- | --- | --- | --- | --- |")
        for i, r in enumerate(group, 1):
            feats = "/".join(LABEL[f] for f in r["feats"]) or "无"
            work = "+".join(SHORT[f] for f in r["feats"]) or "纯文本 + 通用房间"
            note = MANUAL.get(r["id"], "")
            nm = r["name"] if r["name"] != "?" else "（缺中文名）"
            ap("| %d | %s | `%s` | %s | %d | %s | %s | %s |"
               % (i, nm, r["id"], r["faction"], r["len"], feats, work, note))
        ap("")
    ap("### 已完成")
    ap("")
    ap("| 职业 | id | 说明 |")
    ap("| --- | --- | --- |")
    for rid, why in DONE.items():
        ap("| %s | `%s` | %s |" % (name.get(rid, "?"), rid, why))
    ap("")
    ap("## 7. 公共缺口与基础设施补充")
    ap("")
    ap("按职业统计的共性缺口（做完前两批再动 D 档）：")
    ap("")
    ap("- **结构**：`role_tutorial_common` 只够演示「站着的玩家 + 桌面物品」。提到地图/房间/井盖/水下/铁门/车厢的职业共 %d 个，需要 3–5 套新结构（标准房间、列车车厢、门与走廊、墓地/尸体场景、水域）。"
       % sum(1 for r in todo if "structure" in r["feats"]))
    ap("- **多玩家**：需要第二个（必要时第三个）玩家模型的职业 %d 个，靠 `role_player.linkId` + `role_bond` 表达关系，模型要错开位置避免穿模。"
       % sum(1 for r in todo if "multi" in r["feats"]))
    ap("- **形态/皮肤**：%d 个职业涉及疯狂模式或形态切换，需要按 `getPsychoSkin` 取皮肤路径（参考 jester 场景）。"
       % sum(1 for r in todo if "form" in r["feats"]))
    ap("- **商店**：%d 个职业有商店，`role_shop` 直接读真实 ShopEntry，但要确认条目不被配置裁剪。"
       % sum(1 for r in todo if "shop" in r["feats"]))
    ap("- **尸体**：%d 个职业依赖尸体交互，统一用 `trainmurdermystery:player_body` + `CustomName`。"
       % sum(1 for r in todo if "body" in r["feats"]))
    ap("- **无法直接表达**：%d 个职业（寄生、夺舍、做题、轮盘、投票等）需要新增 Java 步骤，建议先降级为「文本 + 现有覆盖层」占位，再按热度补真步骤。"
       % sum(1 for r in todo if "unique" in r["feats"]))
    ap("- **文案三语**：所有 `text`/`title`/`hint` 必须同时写 `en_us` / `zh_cn` / `zh_tw`；`en_us` 缺 %d 条介绍（%s），"
       "涉及这些职业的批次要先补英文原文再引用。"
       % (len(missing_en), "、".join("`%s`" % x for x in missing_en[:12]) + ("…" if len(missing_en) > 12 else "")))

    ap("")
    ap("## 8. 单职业执行模板")
    ap("")
    ap("1. 读 `info.screen.roleid.<id>`（三语），确定段落划分（每段一个功能，id 用功能名）。")
    ap("2. 核对证据：`RoleInitialItems`、`ShopEntry`、`getPsychoSkin`、相关实体/方块 id。")
    ap("3. 写场景 JSON：第一段 `show_structure` 开场，后续段落每个功能一段。")
    ap("4. 时间轴自检：每条 `text` 后补足 `idle ≥ duration`，且每条文本用不同 `point`。")
    ap("5. 三语补齐，`tags: []`、`customTags: {}` 不写 null。")
    ap("6. 登记到 `content/role_tutorials.json`，跑 `verify_ponder_structures.py`。")
    ap("7. 实机播放，确认 `loaded N scenes` 且无 `unavailable`。")
    ap("")
    ap("## 9. 注意事项")
    ap("")
    ap("- 介绍文本有 6 条含无效 `§` 颜色码（voodoo / lengxiao / dio / kafu_chino / ibuki_suika / pachuri），教程引用前应先修介绍文本，否则会照抄乱码。")
    ap("- 介绍文本 47 条含 `%%`，引用进 Ponder 文本时按游戏内实际显示为准，避免教程里出现双百分号。")
    ap("- `leader`、`ma_chen_xu` 等 2000 字以上的介绍无法在一屏讲完，教程只演示主线，细节仍交给介绍页。")
    ap("- 其它模式职业（回声、鹅鸭杀、躲猫猫、特殊结局）不在本规划内，避免污染普通模式教程列表。")
    ap("")
    ap("## 10. 事件篇：三个事件条目的思索")
    ap("")
    ap("`command_block` / `night_event` / `halloween_event` 不是可选职业，介绍只有十几行，但机制散在 "
       "`CommandBlockDirector`、`NightEvent`、`HalloweenRuntime` 与多个 CCA 组件里，**必须把源码与介绍对着读**才能写出不编造的文案。")
    ap("")
    ap("三者共同点：改变的是「整局世界状态」（时间、灯光、天空、视野、职业池、刷怪），不是一次技能动作，"
       "而 Ponder 场景是离屏沙盒（`docs/PONDERER_NOTES.md:340-342` 已确认没有真实时间/雾/服务端组件），所以难度高于绝大多数职业。")
    ap("")
    ap("| 条目 | 性质 | 触发 | 演示段落 | 需新增能力 | 预估 |")
    ap("| --- | --- | --- | --- | --- | --- |")
    ap("| `command_block` | 杀手事件，占满杀手位 | 万分比 `commandBlockEnableChance`（代码默认 1000）+ `/sre:command_block next` | 5 段 | 环境层（紫天/紫雾）、双条 HUD、同段落职业改写 | 5 人日 |")
    ap("| `night_event` | 中立事件，只改环境 | 万分比 `nightEventEnableChance`（默认 200）+ `/sre:night next` | 4 段 | 环境层（夜/雾/灯灭瞬变）、亡灵走位 | 3 人日 |")
    ap("| `halloween_event` | 中立事件，地图开关常驻 | 地图配置 `halloweenEventEnabled`（默认 false），无概率 | 5 段 | 残月商店陈列步骤、橙色天空、概率分支叙述 | 5 人日 |")
    ap("")
    ap("三者都已在 `ModRoles` 以「事件载体职业」注册（`EggRole` + `setEventNeutral(true)` + `setDefaultMax(0)` + "
       "`setHiddenForRoleRotation(true)`，`ModRoles.java:2243-2291`），不分配给玩家；介绍页的思索按钮对它们同样显示，"
       "目前点开会落「无教程」。")
    ap("")
    ap("### 10.1 `command_block`（杀手事件）")
    ap("")
    ap("**触发**：`CommandBlockDirector.prepare()`（`game/command_block/CommandBlockDirector.java:301-326`）按万分比掷骰，"
       "`NoellesRolesConfig.commandBlockEnableChance` 代码默认 `1000`（10%）；管理员可用 `/sre:command_block next` 强制下一局"
       "（`commands/CommandBlockCommand.java:16-32`）。仅 `SREMurderGameMode` 生效，与躲猫猫 / 邪恶战争 / TNT / 赌徒 / 发现 / 鹅鸭杀互斥"
       "（`CommandBlockDirector.java:1027-1034`）。")
    ap("")
    ap("**流程**（`CommandBlockDirector`，会议冻结期间停表）：")
    ap("")
    ap("1. 开局 `start()`（`:409-434`）：占满杀手位；标记 `ceil(原杀手×1.5)` 名平民为异常（`markAbnormals()` `:794-818`）；"
       "把一名平民改写成警长 Steve（`assignSteve()` `:530-552`）；在游玩区中心列上方 18 格生成 `noellesroles:command_block_marker`"
       "（8×8 碰撞箱、客户端自绘）并接管开场镜头（`sendIntro()` `:363-407`，音效 `END_PORTAL_SPAWN`）；标题 + 广播 + `BEACON_ACTIVATE`。")
    ap("2. 每 60 秒改写一名异常：提前 1 分钟变中立空壳「异常」并只给本人发预览字幕（`beginPreview()` `:554-572`），"
       "到期正式转化（`convertNext()` `:591-621`），全场字幕 + `EVOKER_CAST_SPELL`（`announceBirth()` `:623-630`）。")
    ap("3. 每 120 秒随机一个场地事件（9 种：关灯 / 换位 / 信号干扰 / 时间回溯 / 缄默 / 金币补给 / 曝光 / 封锁 / 命令注入，"
       "`FieldEvent` 枚举 `:1036-1071`），触发前全场预告「即将发生：xxx」（`announceNextEvent()` `:824-833`）。")
    ap("4. 每 75–112.5 秒在任务点旁刷新一道 `noellesroles:command_block_crack`，只有杀手看得见发光；"
       "好人用近战武器击碎 → 破坏进度 +1（`:469-472`、`addDestruction()` `:835-844`）。")
    ap("5. 结束：破坏进度达到配额 `ceil(原杀手×1.5)+2`（`CommandBlockRules.destructionQuota` `:24-27`）乘客胜；"
       "否则时间耗尽或中立独立胜利（`filterEnd()` `:210-232`）。")
    ap("6. 表现层：破坏进度 + 事件倒计时双条 HUD（`CommandBlockProgressS2CPacket`，客户端 `CommandBlockProgressClient`）；"
       "天空 / 雾 / 天光 / 月亮染紫（`mixin/client/roles/command_block/CommandBlockSky*Mixin.java`）。")
    ap("")
    ap("**演示分段（建议 5 段）**：")
    ap("")
    ap("| 段落 id | 内容 | 关键元素 |")
    ap("| --- | --- | --- |")
    ap("| `event_start` | 紫天、屋顶上空的巨型命令方块、开场镜头与广播 | 新结构（开阔车顶/机房）+ 环境层染色 |")
    ap("| `abnormal` | 平民被标记异常 → 私发预览 → 全场字幕改写为池内杀手 | 两名玩家模型 + 顶部字幕 + 职业改写 |")
    ap("| `crack` | 裂缝刷新、只有杀手看得见发光、击碎后进度 +1 | `command_block_crack` 实体或粒子近似 + 双条 HUD |")
    ap("| `field_event` | 抽 2 个代表事件（关灯、换位）演示 | `set_block` / 模型换位 + 预告文本 |")
    ap("| `settle` | 配额与胜负判定 | `role_state` 进度条 |")
    ap("")
    ap("**改写池（15 个，`CommandBlockRules.pool()` `:89-95`）**：`stone_man`、`him`、`error_404`、`dream`、`entity_303`、"
       "`entity_null`、`farlands`、`white_eyes`、`stringer`、`fog_guest`、`paper_doll`、`mustard`、`pendulum`、"
       "`candle_shade`、`dreadlord`。其中 `pendulum` 及其余「Command_Role」职业**只能**由本事件产生"
       "（`ModRoles.java:2293-2501`），介绍文本与代码一致。")
    ap("")
    ap("**缺口**：无天色/雾/天光步骤；双条 HUD 只能用 `role_state` 近似；同段落内换职业需要多个 `role_player` + `linkId` 替换；"
       "裂缝是非方块几何体（先用 `spawn_particles` 降级）。")
    ap("")
    ap("### 10.2 `night_event`（黑夜事件）")
    ap("")
    ap("**触发**：`NightEvent.prepare()`（`game/night/NightEvent.java:143-154`）按万分比掷骰，"
       "`NoellesRolesConfig.nightEventEnableChance` 默认 `200`（2%）；`/sre:night next` 强制；模式门槛与命令方块相同"
       "（`supports()` `:131-136`）；轮抽/志愿模式推迟到选完职业再发装备（`defersEquipment` `:138-141`）。")
    ap("")
    ap("**流程**（开局一次性切换，整局维持，没有分阶段剧情）：")
    ap("")
    ap("1. 时间锁午夜：`level.setDayTime(18000)` + `SRETrainWorldComponent.TimeOfDay.MIDNIGHT`，每秒纠偏（`:179-180`、`:219-223`）。")
    ap("2. 全场永久关灯：`SREWorldBlackoutComponent.startNightEvent()`（`:181`）关掉所有带 `LIT`/`ACTIVE` 的灯并记录原值，"
       "禁止中途 `reset()` 复亮（`cca/SREWorldBlackoutComponent.java:63-96, 116-125`）。")
    ap("3. 视野压到 16 格（`NightRoundState.VIEW_DISTANCE = 16`、`fogStart/fogEnd` `:39-45`，客户端 `WorldRendererMixin:72-76`）。")
    ap("4. 每人发一把 `noellesroles:flashlight`（`equip()` `:197-210`，满包落地不吞装备）。")
    ap("5. 死亡原地化 `noellesroles:undead`（`onDeath()` `:236-252`，删真实尸体），警戒 12 格 / 漫游 8 格 / 牵引 20 格"
       "（`NightUndeadRules.java:7-10, 38-44`），与亡灵之主的亡灵是独立账本。")
    ap("6. 结束 `clear()`（`:254-268`）恢复时间与全部灯光、清掉本局亡灵。")
    ap("")
    ap("**演示分段（建议 4 段）**：`nightfall`（标题 + 副标题 + 灯逐盏熄灭）→ `flashlight`（手电筒开关与光锥、视野 16 格）→ "
       "`undead`（玩家死亡 → 亡灵出现并逼近幸存者）→ `restore`（结束恢复灯光与时间）。")
    ap("")
    ap("**缺口**：夜天与雾无法直接设置；「灯突然全灭」需要逐灯 `set_block` 或新步骤；亡灵 AI 逼近只能用 `role_walk` 代理。"
       "不涉及职业改写，是本篇三条里最轻的。")
    ap("")
    ap("### 10.3 `halloween_event`（万圣节事件）")
    ap("")
    ap("**触发**：没有概率，唯一门槛是地图配置 `AreasSettings.halloweenEventEnabled`（默认 false）+ `halloweenNpcSpawnPos`"
       "（`api/AreasSettings.java:332-340`，中文名「启用万圣节小镇活动」）；运行时判定 `HalloweenRuntime.isMapEnabled()`"
       "（`game/halloween/HalloweenRuntime.java:26-31`），`OnGameStarted` 建 session。")
    ap("")
    ap("**流程**（整局并行的多条子系统，不是线性剧情）：")
    ap("")
    ap("1. 残月 NPC（`noellesroles:halloween_village_npc`）生成在固定坐标，右键开 `VillageShopScreen`，用南瓜/金币换道具"
       "（`game/halloween/HalloweenVillage.java:122-147, 276-314`）。")
    ap("2. 敲门：潜行空手右键 `SmallDoorBlock`，奖励 60% 1–2 南瓜 / 30% 亡灵 / 10% 糖果袋，另有 20% 冒出发糖残月"
       "（`HalloweenVillage.java:215-256`）。")
    ap("3. 魂契：每 3 个真实任务 20% 掉 `halloween_soul_contract`，靠近残月交付；死亡后复活为中立 `halloween_pact`"
       "（不能攻击/用物品/拾取，跟随最近非依附玩家阵营获胜，`HalloweenPactRole.java:38-51`）。")
    ap("4. 开局把一名杀手改写成 `undead_lord`（`HalloweenRuntime.java:74-82`）。")
    ap("5. 道具：`halloween_blood_saw`（3 格蓄力）、`halloween_fright_gun`（30 格 + 冻结 + 召唤亡灵）；"
       "持有刀具的职业商店可 200 金币买 8 耐久血锯。")
    ap("6. 万圣亡灵：枪击/敲门/靠近残月击杀生成橙金色 `noellesroles:undead`（`HalloweenCombat.java:349-391`），上限 32。")
    ap("7. 南瓜精 `pumpkin_spirit`（独立胜利，收集南瓜）与南瓜据点（`minecraft:item_display` + `interaction`，不放方块）。")
    ap("8. 环境：橙色穹顶/雾/天光/月亮（`game/halloween/ambience/HalloweenAmbienceSky.java:39-54`）+ 音乐 `halloween:music.town`。")
    ap("9. **亡魂夜（Ghost Night）已移除**，配置字段仅兼容保留、不再读取（`HalloweenVillage.java:118, 172-176`，"
       "记录见 `docs/halloween-village.md:58-62`），教程不得演示定时关灯/暗闪/旁观者鬼脸。")
    ap("")
    ap("**演示分段（建议 5 段）**：`village`（门廊小镇 + 残月与商店）→ `knock`（潜行空手敲门，三档奖励分条标注概率）→ "
       "`contract`（魂契交付与复活为 pact）→ `combat`（血锯 / 惊魂枪 → 橙色亡灵）→ `pumpkin`（南瓜精与据点）。")
    ap("")
    ap("**缺口**：残月商店走 `VillagePackets.Offer`（`HalloweenVillage.java:278-292`），**不是** `ShopEntry` 体系，"
       "现有 `role_shop` 读不到，需要新增「残月商店陈列」步骤或降级为文本 + 物品实体；橙色天空无步骤；"
       "60/30/10 与 20% 的随机分支不能在同一条时间轴呈现，只能按段落叙述并写明概率。")
    ap("")
    ap("### 10.4 三条事件的共同难点")
    ap("")
    ap("1. 世界级状态：服务端写的是 CCA 组件、session tick 与整局开关，Ponder 沙盒里没有真实时间/雾/全局光照，"
       "所有环境效果都要靠「新步骤」或「搭场景近似」。")
    ap("2. 缺环境层步骤：现有 16 个自定义步骤全是职业/覆盖层类，内置步骤也没有时间、天气、天空、雾、全场光照入口；"
       "紫天、夜雾、橙天这三个最标志性的观感目前都表达不了。")
    ap("3. HUD 全是 S2C 包：破坏进度+倒计时、标题/字幕只能用 `role_state` / `role_info` 近似，样式与真实 HUD 不一致。")
    ap("4. 概率必须显式标注：万分比骰、60/30/10、20% 魂契、池内随机改写，线性时间轴会被误读成「必然发生」。")
    ap("5. 职业改写最难：异常 → 15 个池内杀手，需要同段落换职业/皮肤/商店 + 顶部字幕；`role_player` 的皮肤在步骤内固定，"
       "只能多个 `role_player` + `linkId` 替换展示。")
    ap("6. 时间轴：事件节奏是 60s / 120s / 75–112.5s，必须用 `idle` 压缩并加注释，否则文本重叠（`PONDERER_SCENE_AUTHORING.md:24-41`）。")
    ap("7. 无现成结构：命令方块机房/车顶开阔场地、夜间小镇、万圣门廊都要新建 NBT 并跑 `verify_ponder_structures.py`。")
    ap("")
    ap("### 10.5 事件篇的前置公共能力（先做这些，再写三条场景）")
    ap("")
    ap("| 能力 | 用途 | 影响范围 |")
    ap("| --- | --- | --- |")
    ap("| `role_ambience`（新步骤：天空/雾/天光/时间染色） | 紫天、夜雾、橙天 | 三条事件全部 |")
    ap("| `role_hud_bars`（双条进度 + 倒计时） | 破坏进度 + 事件倒计时 | `command_block` |")
    ap("| `role_rewrite`（同段落换职业：皮肤 + 名称 + 商店 + 字幕） | 异常 → 杀手、杀手 → 亡灵之主 | `command_block`、`halloween_event` |")
    ap("| `role_village_shop`（残月商店陈列，读 `VillagePackets.Offer`） | 南瓜/金币兑换 | `halloween_event` |")
    ap("| `role_blackout`（逐灯熄灭的批量 `set_block` + 音效） | 灯全灭的瞬变 | `night_event`、`command_block` 关灯事件 |")
    ap("| 结构：`event_command_room` / `event_night_town` / `event_halloween_porch` | 三条事件各自的舞台 | 三条事件全部 |")
    ap("")
    ap("建议顺序：先做 `night_event`（只需环境层 + 1 个结构，能顺带验证 `role_ambience`），"
       "再做 `halloween_event`（验证 `role_village_shop`），最后做 `command_block`（依赖环境层、双条 HUD 与职业改写三件套都就绪）。")
    ap("")
    ap("### 10.6 写事件教程前必须先核对的既有文档")
    ap("")
    ap("- `docs/命令方块杀手事件.md` 与本规划逐条对齐过：`commandBlockEnableChance` 默认值已按源码 "
       "`NoellesRolesConfig.commandBlockEnableChance = 1000`（10%）修正，旧值 80 作废。")
    ap("- 「亡魂夜（Ghost Night）」已从万圣节活动移除，`docs/halloween-village.md:58-62` 已注明「已移除」"
       "且旧字段 `nightStartTicks` / `nightDurationTicks` / `nightIntervalTicks` / `nightDarkThreshold` 仅为兼容保留；"
       "教程不得再演示定时关灯、暗闪或旁观者鬼脸。")
    ap("- 事件颜色优先级：命令方块紫色 > 万圣节橙色；液体、失明、黑暗、幽露黑雾 > 橙色雾/月亮"
       "（`docs/halloween-ambience.md:26`）。做 `role_ambience` 时必须照抄这个优先级，否则三条事件同时在场会演示错。")

    with io.open(OUT, "w", encoding="utf-8") as f:
        f.write("\n".join(L) + "\n")

    print("roles in scope:", len(rows), "todo:", len(todo))
    print("tiers:", dict(counts))
    print("est person-days: %.1f" % (total_hours / 8.0))
    print("todo ->", OUT)


if __name__ == "__main__":
    main()
