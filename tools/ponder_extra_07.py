# -*- coding: utf-8 -*-
def _g():
    import gen_ponder_scenes as g
    return g


# 源码依据：src/main/java/org/agmas/noellesroles/role_data/innocence/SaltedFishRoleData.java:52-57,116,212-228；src/main/java/org/agmas/noellesroles/init/ModRolesInitialEventRegister.java:1511-1519
def override_salted_fish_skill(builder, ctx):
    g = _g()
    p = ctx["player_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    lines = g.skill_lines(ctx, ["躺下", "面朝太阳", "80 秒", "20 秒", "翻", "40 秒", "冷却"])
    for line in lines:
        zh = line.get("zh_cn", "")
        builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
        if "翻" in zh or "20 秒" in zh:
            builder.add(g.custom("role_pose", {"linkId": main, "pose": "sleeping"}))
            builder.add(g.custom("role_effects", {"lines": [g.tr(("每 20 秒蹦起翻面", "Flip over every 20 seconds", "每 20 秒蹦起翻面"))]}))
            builder.add(g.custom("role_timer", {"progress": [0.0, 1.0], "lines": [line]}))
        elif "40 秒" in zh or "冷却" in zh:
            builder.add(g.custom("role_timer", {"progress": [1.0, 0.0], "lines": [line]}))
        else:
            builder.add(g.custom("role_pose", {"linkId": main, "pose": "sleeping"}))
            builder.add(g.custom("role_effects", {"lines": [g.tr(("面朝太阳 · 期间无法移动转向", "Lie facing the sun, locked in place", "面朝太陽 · 期間無法移動轉向"))]}))
        builder.say(line)


# 源码依据：src/main/java/org/agmas/noellesroles/role/bouns/roles/ScoutRole.java:56-64,146-149,622-649
def override_scout_skill(builder, ctx):
    g = _g()
    p = ctx["player_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    lines = g.skill_lines(ctx, ["攀爬", "体力", "右键", "姿态", "站立", "匍匐", "技能"])
    wall = [int(p[0]) + 1, int(p[1]), int(p[2])]
    for line in lines:
        zh = line.get("zh_cn", "")
        builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
        if "右键" in zh or "攀爬" in zh:
            builder.add({"type": "set_block", "block": "minecraft:stone", "blockPos": wall, "blockPos2": [wall[0], wall[1] + 3, wall[2]]})
            builder.add(g.scene_sound("minecraft:item.armor.equip_generic"))
            builder.add(g.custom("role_outline", {"linkId": main, "color": g.GRAY}))
        if "姿态" in zh or "站立" in zh or "匍匐" in zh:
            builder.add(g.custom("role_pose", {"linkId": main, "pose": "crouching"}))
            builder.add(g.custom("role_effects", {"lines": [g.tr(("站立 / 匍匐姿态切换", "Stand / prone stance switch", "站立 / 匍匐姿態切換"))]}))
        if "体力" in zh:
            builder.add(g.custom("role_vitals", {"bars": [{"label": "ponder.starrailexpress.label.vitals", "before": 1.0, "after": 0.3, "color": g.ORANGE}]}))
        builder.say(line)


# 源码依据：src/main/java/org/agmas/noellesroles/role/bouns/roles/ScoutCaptainRole.java:24-27,48-66；src/main/java/org/agmas/noellesroles/role/bouns/roles/ScoutRole.java:638-649
def override_scout_captain_skill(builder, ctx):
    g = _g()
    p = ctx["player_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    lines = g.skill_lines(ctx, ["体力", "攀爬", "吃", "50", "左轮", "姿态", "任务"])
    for line in lines:
        zh = line.get("zh_cn", "")
        builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
        if "吃" in zh or "50" in zh:
            builder.add(g.item_entity([p[0] - 1.0, p[1] + 0.55, p[2] - 0.8], "minecraft:cooked_beef", rid + "_food"))
            builder.add(g.custom("role_vitals", {"bars": [{"label": "ponder.starrailexpress.label.vitals", "before": 0.4, "after": 1.0, "color": g.ORANGE}]}))
        if "左轮" in zh:
            revolver = g.item_for_line(ctx["facts"], zh)
            builder.add(g.item_entity([p[0] - 1.0, p[1] + 0.55, p[2]], revolver, rid + "_revolver"))
            builder.add(g.custom("role_hold", {"linkId": main, "item": revolver}))
        if "姿态" in zh:
            builder.add(g.custom("role_pose", {"linkId": main, "pose": "crouching"}))
            builder.add(g.scene_sound("minecraft:entity.player.small_fall"))
        builder.say(line)


# 源码依据：src/main/java/org/agmas/noellesroles/init/ModRolesInitialEventRegister.java:963-1007
def override_sea_king_skill(builder, ctx):
    g = _g()
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid = ctx["rid"]
    lines = g.skill_lines(ctx, ["技能键", "20格", "束缚", "发光", "三叉戟", "死亡", "海豚"])
    builder.add(g.player_step(t, rid + "_water_target", outline=g.ORANGE))
    builder.add(g.item_entity([p[0] - 1.0, p[1] + 0.55, p[2] - 0.9], "minecraft:trident", rid + "_trident"))
    for line in lines:
        zh = line.get("zh_cn", "")
        if "技能键" in zh or "束缚" in zh or "发光" in zh:
            builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
            builder.add(g.custom("role_outline", {"linkId": rid + "_water_target", "color": 0x55FFFF}))
            builder.add(g.scene_sound("minecraft:item.trident.return"))
            builder.add(g.custom("role_effects", {"lines": [g.tr(("水中目标：束缚 · 发光 · 5 秒", "Water targets bound and glowing for 5s", "水中目標：束縛 · 發光 · 5 秒"))]}))
        if "死亡" in zh:
            builder.add(g.custom("role_death", {"linkId": rid + "_water_target", "pos": t, "name": "ponder.starrailexpress.label.victim"}))
            builder.add(g.custom("role_teleport", {"linkId": rid + "_water_target", "pos": [t[0], t[1], t[2] - 1.0]}))
        builder.say(line)


# 源码依据：src/main/java/org/agmas/noellesroles/role_data/killer/ShadowFalconRoleData.java:43-50,106-159,167-185；src/main/java/org/agmas/noellesroles/client/GKeyRoleSkill.java
def override_shadow_falcon_skill(builder, ctx):
    g = _g()
    p = ctx["player_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    lines = g.skill_lines(ctx, ["喷气背包", "蹲下", "掠食", "创造飞行", "临时护盾", "死亡", "冷却"])
    builder.add(g.item_entity([p[0] - 1.0, p[1] + 0.55, p[2] - 0.9], "noellesroles:jetpack", rid + "_jetpack"))
    for line in lines:
        zh = line.get("zh_cn", "")
        builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
        if "蹲下" in zh:
            builder.add(g.custom("role_pose", {"linkId": main, "pose": "crouching"}))
            builder.add(g.item_entity([p[0] + 1.0, p[1] + 0.55, p[2]], "noellesroles:jetpack", rid + "_dropped"))
        elif "掠食" in zh or "创造飞行" in zh:
            builder.add(g.custom("role_effects", {"lines": [g.tr(("创造飞行 · 双击空格起飞", "Creative flight, double space to lift off", "創造飛行 · 雙擊空格起飛"))]}))
            builder.add(g.custom("role_timer", {"progress": [0.0, 1.0], "lines": [line]}))
        elif "护盾" in zh:
            builder.add(g.custom("role_vitals", {"bars": [{"label": "ponder.starrailexpress.label.vitals", "before": 1.0, "after": 0.5, "color": g.ORANGE}]}))
        elif "死亡" in zh:
            builder.add(g.custom("role_death", {"linkId": main, "pos": p, "corpse": False}))
            builder.add(g.item_entity([p[0] + 1.0, p[1] + 0.55, p[2]], "noellesroles:jetpack", rid + "_drop"))
        builder.say(line)


# 源码依据：src/main/java/org/agmas/noellesroles/role_data/killer/SilencerRoleData.java:58-64,95-120,172,209-220,296-317
def override_silencer_skill(builder, ctx):
    g = _g()
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid = ctx["rid"]
    lines = g.skill_lines(ctx, ["技能页面", "禁言", "求助", "惩罚", "120金币", "冷却", "头像"])
    builder.add(g.player_step(t, rid + "_cursed", outline=g.ORANGE))
    for line in lines:
        zh = line.get("zh_cn", "")
        if "头像" in zh or "技能页面" in zh:
            builder.add(g.custom("role_screen", {"title": g.tr(("选择玩家", "Choose a player", "選擇玩家")), "hint": g.tr(("技能页面点击头像", "Click the avatar on the skill page", "技能頁面點擊頭像")), "columns": 6}))
        if "禁言" in zh:
            builder.add(g.custom("role_outline", {"linkId": rid + "_cursed", "color": g.GRAY}))
            builder.add(g.custom("role_timer", {"progress": [0.0, 1.0], "lines": [line]}))
        if "求助" in zh:
            builder.add(g.custom("role_bond", {"lines": [line], "color": g.ORANGE}))
        if "惩罚" in zh or "120" in zh:
            builder.add(g.custom("role_broadcast", {"title": "skill.noellesroles.silent_killer", "lines": ["skill.noellesroles.silencer.punish"], "duration": 120}))
            builder.add(g.custom("role_currency", {"progress": [0.0, 1.0], "lines": [line]}))
        builder.say(line)


# 源码依据：src/main/java/org/agmas/noellesroles/init/ModRolesInitialEventRegister.java:732-769
def override_silent_killer_skill(builder, ctx):
    g = _g()
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid = ctx["rid"]
    lines = g.skill_lines(ctx, ["华丽登场", "余额", "死亡", "2格半", "5人", "冷却"])
    builder.add(g.player_step(t, rid + "_victim", outline=g.ORANGE))
    for line in lines:
        zh = line.get("zh_cn", "")
        builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
        if "余额" in zh or "5" in zh:
            builder.add(g.custom("role_currency", {"progress": [1.0, 0.0], "lines": [line]}))
        if "死亡" in zh or "2格半" in zh:
            builder.add(g.custom("role_death", {"linkId": rid + "_victim", "pos": t, "name": "ponder.starrailexpress.label.victim"}))
            builder.add(g.scene_sound("minecraft:entity.player.levelup"))
            builder.add(g.custom("role_broadcast", {"title": "skill.noellesroles.silent_killer", "lines": ["skill.noellesroles.silent_killer"], "duration": 90}))
        builder.say(line)


# 源码依据：src/main/java/org/agmas/noellesroles/init/ModRolesInitialEventRegister.java:1699-1721；src/main/java/org/agmas/noellesroles/role_data/neutral/SilverWingRoleData.java:139-154
def override_silver_wing_skill(builder, ctx):
    g = _g()
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    lines = g.skill_lines(ctx, ["电磁脉冲", "右键", "缓慢III", "机械小鸟", "致盲", "击退", "冷却"])
    builder.add(g.player_step(t, rid + "_emp_target", outline=g.GRAY))
    for line in lines:
        zh = line.get("zh_cn", "")
        builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
        if "电磁脉冲" in zh:
            builder.add(g.custom("role_hold", {"linkId": main, "item": "noellesroles:emp_bomb"}))
            builder.add(g.item_entity([p[0] - 1.0, p[1] + 0.55, p[2] - 0.9], "noellesroles:emp_bomb", rid + "_emp"))
            builder.add(g.show_right_click([p[0] - 1.4, p[1] + 0.9, p[2] - 1.1], "noellesroles:emp_bomb"))
        if "缓慢" in zh or "无法使用物品" in zh:
            builder.add(g.custom("role_effects", {"lines": [g.tr(("缓慢 III · 禁用物品 4 秒", "Slowness III and item ban for 4s", "緩慢 III · 禁用物品 4 秒"))]}))
            builder.add(g.custom("role_knockback", {"linkId": rid + "_emp_target", "from": t, "to": [t[0] + 1.4, t[1], t[2]], "duration": 14, "arc": 0.3}))
        if "机械小鸟" in zh or "爆炸" in zh:
            builder.add(g.custom("role_timer", {"progress": [0.0, 1.0], "lines": [line]}))
            builder.add(g.custom("role_mood", {"before": 0.9, "after": 0.5, "lines": [line]}))
        builder.say(line)


# 源码依据：src/main/java/org/agmas/noellesroles/role_data/killer/SkincrawlerRoleData.java:30-35,59-63；src/main/java/org/agmas/noellesroles/client/GKeyRoleSkill.java:264-270
def override_skincrawler_skill(builder, ctx):
    g = _g()
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid = ctx["rid"]
    lines = g.skill_lines(ctx, ["尸体", "皮肤", "免疫", "左轮手枪", "60秒", "技能键"])
    builder.add(g.player_step(t, rid + "_corpse", outline=g.GRAY))
    builder.add(g.custom("role_death", {"linkId": rid + "_corpse", "pos": t, "name": "ponder.starrailexpress.label.victim"}))
    for line in lines:
        zh = line.get("zh_cn", "")
        builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
        if "尸体" in zh or "皮肤" in zh:
            builder.add(g.custom("role_walk", {"linkId": rid + "_main", "to": [t[0] - 0.8, t[1], t[2]], "duration": 36}))
            builder.add(g.custom("role_outline", {"linkId": rid + "_corpse", "color": g.ORANGE}))
            builder.add(g.custom("role_timer", {"progress": [0.0, 1.0], "lines": [line]}))
        if "免疫" in zh:
            builder.add(g.custom("role_effects", {"lines": [g.tr(("消耗抵挡次数 · 免疫本次死亡 · 禁移动 5 秒", "Consume a charge to block death, root 5s", "消耗抵擋次數 · 免疫本次死亡 · 禁移動 5 秒"))]}))
            builder.add(g.custom("role_vitals", {"bars": [{"label": "ponder.starrailexpress.label.vitals", "before": 1.0, "after": 1.0, "color": g.ORANGE}]}))
        builder.say(line)


# 源码依据：src/main/java/org/agmas/noellesroles/role_data/neutral/SnowGolemRoleData.java:56-70；src/main/java/org/agmas/noellesroles/init/ModRolesInitialEventRegister.java:545-556
def override_snow_golem_skill(builder, ctx):
    g = _g()
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    lines = g.skill_lines(ctx, ["融化", "滚雪", "推出雪球", "雪球", "致死", "缓慢"])
    builder.add(g.player_step(t, rid + "_snowball_target", outline=g.ORANGE))
    for line in lines:
        zh = line.get("zh_cn", "")
        builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
        if "滚雪" in zh or "雪球" in zh:
            builder.add({"type": "set_block", "block": "minecraft:snow_block", "blockPos": [int(p[0]) + 1, int(p[1]), int(p[2])], "blockPos2": [int(p[0]) + 2, int(p[1]), int(p[2])]})
            builder.add(g.custom("role_scale", {"linkId": main, "scale": 1.2}))
        if "推出" in zh:
            builder.add(g.custom("role_knockback", {"linkId": rid + "_snowball_target", "from": [p[0], p[1] + 0.5, p[2]], "to": t, "duration": 22, "arc": 0.25}))
            builder.add(g.scene_sound("minecraft:block.snow.place"))
        if "致死" in zh or "融化" in zh:
            builder.add(g.custom("role_death", {"linkId": rid + "_snowball_target", "pos": t, "name": "snowball"}))
            builder.add(g.custom("role_effects", {"lines": [g.tr(("缓慢 I~III · 击退", "Slowness I-III and knockback", "緩慢 I~III · 擊退"))]}))
        builder.say(line)


# 源码依据：src/main/java/org/agmas/noellesroles/role_data/killer/SpellbreakerRoleData.java:44-48,103-125,130-142
def override_spellbreaker_skill(builder, ctx):
    g = _g()
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid = ctx["rid"]
    lines = g.skill_lines(ctx, ["破法力场", "50格", "破法药剂", "沉默图腾", "失明", "仙境滤镜"])
    builder.add(g.player_step(t, rid + "_silenced", outline=g.GRAY))
    for line in lines:
        zh = line.get("zh_cn", "")
        builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
        if "破法力场" in zh or "50格" in zh:
            builder.add(g.scene_sound("minecraft:block.enchantment_table.use"))
            builder.add(g.custom("role_effects", {"lines": [g.tr(("50 格内非杀手：禁技 · 禁背包 · 仙境滤镜", "Non-killers within 50 blocks lose skills and inventory", "50 格內非殺手：禁技 · 禁背包 · 仙境濾鏡"))]}))
            builder.add(g.custom("role_timer", {"progress": [0.0, 1.0], "lines": [line]}))
        if "破法药剂" in zh:
            builder.add(g.show_right_click([p[0] - 1.3, p[1] + 0.9, p[2] - 1.0], "minecraft:splash_potion"))
            builder.add(g.scene_sound("minecraft:block.glass.break"))
        if "沉默图腾" in zh or "失明" in zh:
            builder.add(g.custom("role_ambience", {"color": 0x222222, "alpha": 0.7, "mode": "fog"}))
            builder.add(g.custom("role_timer", {"progress": [1.0, 0.0], "lines": [line]}))
        builder.say(line)


# 源码依据：src/main/java/org/agmas/noellesroles/role_data/killer/StalkerRoleData.java:71-93,133-145,239-265；src/main/java/org/agmas/noellesroles/init/ModRolesInitialEventRegister.java:377-379
def override_stalker_skill(builder, ctx):
    g = _g()
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    lines = g.skill_lines(ctx, ["吸取能量", "二阶段", "狩猎匕首", "刺客形态", "冲刺", "挂墙", "击倒"])
    builder.add(g.player_step(t, rid + "_hunted", outline=g.ORANGE))
    for line in lines:
        zh = line.get("zh_cn", "")
        builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
        if "吸取能量" in zh or "二阶段" in zh:
            builder.add(g.custom("role_vitals", {"bars": [{"label": "ponder.starrailexpress.label.vitals", "before": 0.3, "after": 1.0, "color": g.ORANGE}]}))
            builder.add(g.custom("role_walk", {"linkId": main, "to": [t[0] - 1.0, t[1], t[2]], "duration": 40}))
        if "狩猎匕首" in zh or "刺客形态" in zh:
            dagger = g.item_for_line(ctx["facts"], "狩猎匕首")
            builder.add(g.item_entity([p[0] - 1.0, p[1] + 0.55, p[2] - 0.8], dagger, rid + "_dagger"))
            builder.add(g.custom("role_hold", {"linkId": main, "item": dagger}))
            builder.add(g.custom("role_timer", {"progress": [0.0, 1.0], "lines": [line]}))
        if "冲刺" in zh or "击倒" in zh:
            builder.add(g.custom("role_knockback", {"linkId": rid + "_hunted", "from": [p[0], p[1], p[2] - 1.0], "to": [t[0], t[1], t[2] + 2.0], "duration": 16, "arc": 0.35}))
            builder.add(g.custom("role_death", {"linkId": rid + "_hunted", "pos": t}))
        builder.say(line)


# 源码依据：src/main/java/org/agmas/noellesroles/role_data/innocence/StarGathererRoleData.java:74-115,210；src/main/java/org/agmas/noellesroles/init/ModRolesInitialEventRegister.java:1783-1822
def override_star_gatherer_skill(builder, ctx):
    g = _g()
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid = ctx["rid"]
    lines = g.skill_lines(ctx, ["切换键", "庇护强化", "星之引力", "施舍", "星之权杖", "阵亡", "星星"])
    builder.add(g.player_step(t, rid + "_star_target", outline=g.ORANGE))
    for line in lines:
        zh = line.get("zh_cn", "")
        builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]], switch="切换键" in zh))
        if "庇护强化" in zh:
            builder.add(g.custom("role_domain", {"title": g.tr(("庇护强化", "Aura enhanced", "庇護強化")), "hint": g.tr(("光环扩至 10 格 · 30 秒", "Aura out to 10 blocks for 30s", "光環擴至 10 格 · 30 秒")), "remaining": 30}))
            builder.add(g.scene_particle("minecraft:end_rod", [p[0], p[1] + 1.0, p[2]], count=12, duration=60))
        elif "星之引力" in zh:
            builder.add(g.scene_particle("minecraft:electric_spark", [p[0], p[1] + 1.0, p[2]], count=14, duration=70))
            builder.add(g.custom("role_timer", {"progress": [0.0, 1.0], "lines": [line]}))
        elif "施舍" in zh:
            builder.add(g.player_step(t, rid + "_alms_receiver", held="minecraft:trident"))
        elif "权杖" in zh or "星星" in zh or "阵亡" in zh:
            builder.add(g.item_entity([p[0] - 1.0, p[1] + 0.55, p[2] - 0.9], "noellesroles:star_scepter", rid + "_scepter"))
            builder.add(g.scene_particle("minecraft:portal", [t[0], t[1] + 1.0, t[2]], count=10, duration=60))
            builder.add(g.custom("role_death", {"linkId": rid + "_star_target", "pos": t}))
        builder.say(line)


# 源码依据：src/main/java/org/agmas/noellesroles/game/command_block/SteveRole.java:33-55；src/main/java/org/agmas/noellesroles/init/ModItems.java:1633-1636
def override_steve_skill(builder, ctx):
    g = _g()
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    lines = g.skill_lines(ctx, ["铁镐", "材料", "建造一堵墙", "技能键", "击杀", "钻石剑", "商店"])
    builder.add(g.player_step(t, rid + "_mine_target", outline=g.ORANGE))
    builder.add(g.item_entity([p[0] - 1.0, p[1] + 0.55, p[2] - 0.9], "noellesroles:steve_pickaxe", rid + "_pick"))
    for line in lines:
        zh = line.get("zh_cn", "")
        builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
        if "材料" in zh or "铁镐" in zh:
            builder.add(g.custom("role_hold", {"linkId": main, "item": "noellesroles:steve_pickaxe"}))
            builder.add(g.custom("role_currency", {"progress": [0.0, 1.0], "lines": [line]}))
        if "墙" in zh:
            builder.add({"type": "set_block", "block": "minecraft:stone", "blockPos": [int(p[0]) + 1, int(p[1]), int(p[2])], "blockPos2": [int(p[0]) + 1, int(p[1]) + 2, int(p[2])]})
            builder.add(g.custom("role_timer", {"progress": [0.0, 1.0], "lines": [line]}))
        if "击杀" in zh or "钻石剑" in zh:
            builder.add(g.custom("role_death", {"linkId": rid + "_mine_target", "pos": t}))
        if "商店" in zh:
            builder.add(g.custom("role_shop", {"title": g.tr(("材料商店", "Material shop", "材料商店")), "lines": [line]}))
        builder.say(line)


# 源码依据：src/main/java/org/agmas/noellesroles/game/command_block/StoneManRole.java:14-33；src/main/java/org/agmas/noellesroles/game/command_block/CommandBlockCombat.java
def override_stone_man_skill(builder, ctx):
    g = _g()
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    lines = g.skill_lines(ctx, ["免疫", "击杀", "体型", "石枪", "蓄力", "冷却"])
    builder.add(g.player_step(t, rid + "_stone_target", outline=g.ORANGE))
    for line in lines:
        zh = line.get("zh_cn", "")
        builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
        if "免疫" in zh or "无敌" in zh:
            builder.add(g.custom("role_effects", {"lines": [g.tr(("免疫一次攻击 · 15 秒无敌", "Block one hit, invincible for 15s", "免疫一次攻擊 · 15 秒無敵"))]}))
            builder.add(g.custom("role_timer", {"progress": [1.0, 0.0], "lines": [line]}))
        if "击杀" in zh or "体型" in zh:
            builder.add(g.custom("role_scale", {"linkId": main, "scale": 1.05}))
            builder.add(g.custom("role_death", {"linkId": rid + "_stone_target", "pos": t}))
        if "石枪" in zh or "蓄力" in zh:
            builder.add(g.item_entity([p[0] - 1.0, p[1] + 0.55, p[2] - 0.9], "noellesroles:stone_spear", rid + "_spear"))
            builder.add(g.show_right_click([p[0] - 1.4, p[1] + 0.9, p[2] - 1.1], "noellesroles:stone_spear", 90))
            builder.add(g.custom("role_knockback", {"linkId": rid + "_stone_target", "from": p, "to": t, "duration": 18, "arc": 0.2}))
        builder.say(line)


# 源码依据：src/main/java/org/agmas/noellesroles/game/command_block/StringerRole.java:40-52,60-109；src/main/java/org/agmas/noellesroles/init/ModItems.java:1599-1601
def override_stringer_skill(builder, ctx):
    g = _g()
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid = ctx["rid"]
    lines = g.skill_lines(ctx, ["缠丝", "牵线", "线轴", "丝刃", "击杀", "丝线团", "开锁器"])
    builder.add(g.player_step(t, rid + "_bound", outline=g.ORANGE))
    for line in lines:
        zh = line.get("zh_cn", "")
        builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
        if "缠丝" in zh:
            builder.add(g.custom("role_outline", {"linkId": rid + "_bound", "color": 0xFFFF55}))
            builder.add(g.custom("role_effects", {"lines": [g.tr(("发光 · 缓慢 II · 10 秒", "Glowing and slowness II for 10s", "發光 · 緩慢 II · 10 秒"))]}))
            builder.add(g.custom("role_timer", {"progress": [0.0, 1.0], "lines": [line]}))
        if "牵线" in zh:
            builder.add(g.custom("role_screen", {"title": g.tr(("选择两名玩家", "Choose two players", "選擇兩名玩家")), "hint": g.tr(("60 秒内相距不过 10 格", "Stay within 10 blocks for 60s", "60 秒內相距不過 10 格")), "columns": 6}))
            builder.add(g.custom("role_bond", {"lines": [line], "color": g.ORANGE}))
        if "线轴" in zh or "丝刃" in zh:
            builder.add(g.item_entity([p[0] - 1.0, p[1] + 0.55, p[2] - 0.9], "noellesroles:thread_spool", rid + "_spool"))
            builder.add(g.custom("role_walk", {"linkId": rid + "_bound", "to": [p[0] + 1.0, p[1], p[2]], "duration": 30}))
        if "击杀" in zh:
            builder.add(g.custom("role_death", {"linkId": rid + "_bound", "pos": t}))
        builder.say(line)


# 源码依据：src/main/java/org/agmas/noellesroles/role_data/killer/SwapperRoleData.java:45-50,84-120；src/main/java/org/agmas/noellesroles/client/GKeyRoleSkill.java:160-167
def override_swapper_skill(builder, ctx):
    g = _g()
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    lines = g.skill_lines(ctx, ["背包", "交换", "2.5s", "无法移动", "下落", "提示"])
    builder.add(g.player_step(t, rid + "_swap_target", outline=g.ORANGE))
    for line in lines:
        zh = line.get("zh_cn", "")
        if "背包" in zh and "交换" in zh:
            builder.add(g.custom("role_screen", {"title": g.tr(("选择玩家", "Choose a player", "選擇玩家")), "hint": g.tr(("背包中选择两名玩家", "Pick two players in the inventory", "背包中選擇兩名玩家")), "columns": 6}))
        if "技能" in zh or "正前方" in zh:
            builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
        if "2.5" in zh or "延迟" in zh:
            builder.add(g.custom("role_timer", {"progress": [0.0, 1.0], "lines": [line]}))
            builder.add(g.custom("role_effects", {"lines": [g.tr(("延迟 2.5 秒 · 期间无法移动", "2.5s delay, cannot move", "延遲 2.5 秒 · 期間無法移動"))]}))
        if "交换" in zh and ("位置" in zh or "正前方" in zh):
            builder.add(g.custom("role_teleport", {"linkId": main, "pos": [t[0], t[1], t[2]]}))
            builder.add(g.custom("role_teleport", {"linkId": rid + "_swap_target", "pos": [p[0], p[1], p[2]]}))
        if "提示" in zh:
            builder.add(g.custom("role_broadcast", {"title": "message.swapper.swapped", "lines": ["message.swapper.swapped"], "duration": 80}))
        builder.say(line)


# 源码依据：src/main/java/org/agmas/noellesroles/role/vigilante/SwordsmanRole.java:47-64,91-115；src/main/java/org/agmas/noellesroles/init/ModRolesInitialEventRegister.java:535-543
def override_swordsman_skill(builder, ctx):
    g = _g()
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    lines = g.skill_lines(ctx, ["淬血", "武士刀", "三连招", "格挡", "击杀", "虚拟血量", "死亡"])
    builder.add(g.player_step(t, rid + "_duel", outline=g.ORANGE))
    builder.add(g.item_entity([p[0] - 1.0, p[1] + 0.55, p[2] - 0.9], "noellesroles:katana", rid + "_katana"))
    for line in lines:
        zh = line.get("zh_cn", "")
        builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
        if "三连招" in zh or "横扫" in zh:
            builder.add(g.custom("role_hold", {"linkId": main, "item": "noellesroles:katana"}))
            builder.add(g.custom("role_walk", {"linkId": main, "to": [t[0] - 0.8, t[1], t[2]], "duration": 34}))
            builder.add(g.custom("role_vitals", {"bars": [{"label": "ponder.starrailexpress.label.vitals", "before": 1.0, "after": 0.5, "color": g.ORANGE}]}))
        if "格挡" in zh:
            builder.add(g.custom("role_effects", {"lines": [g.tr(("格挡 1.2 秒 · 消耗耐久", "Parry for 1.2s, costs durability", "格擋 1.2 秒 · 消耗耐久"))]}))
        if "淬血" in zh:
            builder.add(g.scene_sound("minecraft:entity.blaze.ambient"))
            builder.add(g.custom("role_timer", {"progress": [0.0, 1.0], "lines": [line]}))
        if "击杀" in zh or "死亡" in zh:
            builder.add(g.custom("role_death", {"linkId": rid + "_duel", "pos": t}))
        builder.say(line)


# 源码依据：src/main/java/org/agmas/noellesroles/game/roles/innocence/tailor/TailorRole.java:34-39,46-70；src/main/java/org/agmas/noellesroles/content/item/SewingNeedleItem.java
def override_tailor_skill(builder, ctx):
    g = _g()
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid = ctx["rid"]
    lines = g.skill_lines(ctx, ["缝纫针", "毛线球", "投掷", "裁缝的衣服", "保暖值", "抵挡", "商店"])
    builder.add(g.player_step(t, rid + "_client", outline=g.GRAY))
    for line in lines:
        zh = line.get("zh_cn", "")
        builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
        if "缝纫针" in zh and "编织" in zh:
            builder.add(g.item_entity([p[0] - 1.0, p[1] + 0.55, p[2]], "noellesroles:sewing_needle", rid + "_needle"))
            builder.add(g.show_right_click([p[0] - 1.4, p[1] + 1.0, p[2] - 0.9], "noellesroles:sewing_needle", 110))
            builder.add(g.custom("role_timer", {"progress": [0.0, 1.0], "lines": [line]}))
        if "毛线球" in zh and "投掷" in zh:
            builder.add(g.item_entity([p[0] - 1.0, p[1] + 0.55, p[2] - 0.8], "noellesroles:yarn_ball", rid + "_yarn"))
            builder.add(g.scene_particle("minecraft:end_rod", [t[0], t[1] + 0.8, t[2]], count=8, duration=55))
        if "裁缝的衣服" in zh or "保暖" in zh:
            builder.add(g.custom("role_effects", {"lines": [g.tr(("保暖不降 · 主手冷却 +5 秒 · 5% 抵挡", "No warmth decay, attacker +5s cooldown, 5% block", "保暖不降 · 主手冷卻 +5 秒 · 5% 抵擋"))]}))
        if "商店" in zh:
            builder.add(g.custom("role_currency", {"progress": [1.0, 0.5], "lines": [line]}))
        builder.say(line)


# 源码依据：src/main/java/org/agmas/noellesroles/game/roles/vigilante/genshin/TartagliaRole.java:61,70-73,143-184,186-265；src/main/java/org/agmas/noellesroles/init/ModRolesInitialEventRegister.java:1684-1689
def override_tartaglia_skill(builder, ctx):
    g = _g()
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    lines = g.skill_lines(ctx, ["弓", "极恶技", "尽灭闪", "杀死", "毒箭", "弩", "冷却"])
    builder.add(g.player_step(t, rid + "_dash_victim", outline=g.ORANGE))
    for line in lines:
        zh = line.get("zh_cn", "")
        builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
        if "弓" in zh and "开局" in zh:
            builder.add(g.item_entity([p[0] - 1.0, p[1] + 0.55, p[2] - 0.9], "minecraft:bow", rid + "_bow"))
            builder.add(g.custom("role_hold", {"linkId": main, "item": "minecraft:bow"}))
        if "尽灭闪" in zh or "冲刺" in zh:
            builder.add(g.custom("role_walk", {"linkId": main, "to": [t[0] - 0.8, t[1], t[2]], "duration": 30}))
            builder.add(g.scene_sound("minecraft:entity.player.splash"))
        if "杀死" in zh or "2名" in zh:
            builder.add(g.custom("role_death", {"linkId": rid + "_dash_victim", "pos": t}))
            builder.add(g.custom("role_knockback", {"linkId": rid + "_dash_victim", "from": [p[0], p[1], p[2] - 1.0], "to": [t[0], t[1], t[2] + 2.0], "duration": 14, "arc": 0.4}))
        if "毒箭" in zh or "弩" in zh:
            builder.add(g.custom("role_shop", {"title": g.tr(("箭与弩", "Arrows and crossbow", "箭與弩")), "lines": [line]}))
            builder.add(g.custom("role_timer", {"progress": [0.0, 1.0], "lines": [line]}))
        builder.say(line)


OVERRIDES = {
    ("salted_fish", "skill"): override_salted_fish_skill,
    ("scout", "skill"): override_scout_skill,
    ("scout_captain", "skill"): override_scout_captain_skill,
    ("sea_king", "skill"): override_sea_king_skill,
    ("shadow_falcon", "skill"): override_shadow_falcon_skill,
    ("silencer", "skill"): override_silencer_skill,
    ("silent_killer", "skill"): override_silent_killer_skill,
    ("silver_wing", "skill"): override_silver_wing_skill,
    ("skincrawler", "skill"): override_skincrawler_skill,
    ("snow_golem", "skill"): override_snow_golem_skill,
    ("spellbreaker", "skill"): override_spellbreaker_skill,
    ("stalker", "skill"): override_stalker_skill,
    ("star_gatherer", "skill"): override_star_gatherer_skill,
    ("steve", "skill"): override_steve_skill,
    ("stone_man", "skill"): override_stone_man_skill,
    ("stringer", "skill"): override_stringer_skill,
    ("swapper", "skill"): override_swapper_skill,
    ("swordsman", "skill"): override_swordsman_skill,
    ("tailor", "skill"): override_tailor_skill,
    ("tartaglia", "skill"): override_tartaglia_skill,
}