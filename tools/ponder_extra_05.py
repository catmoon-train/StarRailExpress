# -*- coding: utf-8 -*-
def _g():
    import gen_ponder_scenes as g
    return g


# 源码依据：src/main/java/org/agmas/noellesroles/role_data/neutral/MetalMonsterRoleData.java:22-39；介绍 zh_cn.json:562
def override_metal_monster_skill(builder, ctx):
    g = _g()
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    lines = g.skill_lines(ctx, ["切换键", "钢卷形态", "致命强化", "冷却"])
    builder.add(g.player_step(t, rid + "_rammed", outline=g.ORANGE))
    builder.add(g.custom("role_death", {"linkId": rid + "_rammed", "pos": t,
                                        "name": "ponder.starrailexpress.label.victim"}))
    for line in lines:
        zh = line.get("zh_cn", "")
        builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]], switch="切换键" in zh))
        if "钢卷" in zh or "撞击" in zh:
            builder.add(g.custom("role_scale", {"linkId": main, "scale": 0.55}))
            builder.add(g.custom("role_knockback", {"linkId": rid + "_rammed",
                                                    "from": list(p), "to": list(t)}))
        elif "致命强化" in zh or "致死" in zh:
            builder.add(g.custom("role_death", {"linkId": rid + "_rammed", "pos": t}))
        elif "冷却" in zh:
            builder.add(g.custom("role_timer", {"progress": [1.0, 0.0], "lines": [line]}))
        builder.say(line)


# 源码依据：src/main/java/org/agmas/noellesroles/role_data/killer/MissionaryRoleData.java:76-97；介绍 zh_cn.json:456
def override_missionary_skill(builder, ctx):
    g = _g()
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    lines = g.skill_lines(ctx, ["传教", "投票权重", "头像", "切换传教"])
    builder.add(g.player_step(t, rid + "_flock", outline=g.GRAY))
    for line in lines:
        zh = line.get("zh_cn", "")
        builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]], switch="切换键" in zh))
        if "头像" in zh or "背包" in zh:
            builder.add(g.custom("role_panel", {
                "title": g.tr(("背包头像", "Backpack heads", "背包頭像")), "lines": [line]}))
        elif "切换" in zh:
            builder.add(g.custom("role_bond", {"linkId": rid + "_flock", "lines": [line]}))
        else:
            builder.add(g.custom("role_bond", {"linkId": rid + "_flock",
                                               "color": g.ORANGE, "lines": [line]}))
        builder.say(line)


# 源码依据：src/main/java/org/agmas/noellesroles/role_data/innocence/MonitorRoleData.java:49-55；介绍 zh_cn.json:210
def override_monitor_skill(builder, ctx):
    g = _g()
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    lines = g.skill_lines(ctx, ["标记", "透视", "照明弹", "右键"])
    builder.add(g.player_step(t, rid + "_marked", outline=g.GRAY))
    for line in lines:
        zh = line.get("zh_cn", "")
        builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
        if "照明弹" in zh or "右键" in zh:
            builder.add(g.item_entity([p[0] - 1.0, p[1] + 0.55, p[2] - 0.9],
                                      "minecraft:torch", rid + "_flare"))
            builder.add(g.show_right_click([p[0] - 1.3, p[1] + 0.9, p[2] - 1.2],
                                           "minecraft:torch"))
        elif "标记" in zh or "透视" in zh:
            builder.add(g.custom("role_outline", {"linkId": rid + "_marked", "color": 0xFF55FF}))
        builder.say(line)


# 源码依据：src/main/java/org/agmas/noellesroles/role_data/neutral/MonokumaRoleData.java:119-120,574-581；介绍 zh_cn.json:182
def override_monokuma_skill(builder, ctx):
    g = _g()
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    lines = g.skill_lines(ctx, ["阴阳剑", "AOE", "击杀", "黑白熊", "冲刺", "狂暴前奏"])
    builder.add(g.player_step(t, rid + "_bear_foe", outline=g.ORANGE))
    for line in lines:
        zh = line.get("zh_cn", "")
        builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
        if "阴阳剑" in zh or "AOE" in zh:
            builder.add(g.item_entity([p[0] - 1.0, p[1] + 0.55, p[2] - 0.9],
                                      "noellesroles:yin_yang_sword", rid + "_sword"))
            builder.add(g.show_right_click([p[0] - 1.3, p[1] + 0.9, p[2] - 1.2],
                                           "noellesroles:yin_yang_sword"))
        elif "击杀" in zh or "杀死" in zh or "死亡" in zh:
            builder.add(g.custom("role_death", {"linkId": rid + "_bear_foe", "pos": t}))
        elif "狂暴前奏" in zh or "黑白熊" in zh:
            builder.add(g.custom("role_scale", {"linkId": main, "scale": 1.5}))
        builder.say(line)


# 源码依据：src/main/java/org/agmas/noellesroles/role_data/killer/MorphlingRoleData.java:122-143；介绍 zh_cn.json:132
def override_morphling_skill(builder, ctx):
    g = _g()
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    lines = g.skill_lines(ctx, ["皮肤", "迷幻瓶", "假人", "闪光弹", "匕首"])
    builder.add(g.player_step(t, rid + "_decoy", outline=g.GRAY))
    for line in lines:
        zh = line.get("zh_cn", "")
        builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
        if "皮肤" in zh:
            builder.add(g.custom("role_outline", {"linkId": main, "color": 0xAA55FF}))
        elif "迷幻瓶" in zh:
            builder.add(g.item_entity([p[0] - 1.0, p[1] + 0.55, p[2] - 0.9],
                                      "noellesroles:hallucination_bottle", rid + "_bottle"))
        elif "假人" in zh or "闪光弹" in zh:
            builder.add(g.custom("role_walk", {"linkId": rid + "_decoy",
                                               "to": [t[0], t[1], t[2] + 2.0], "duration": 36}))
            builder.add(g.custom("role_hide", {"linkId": rid + "_decoy", "visible": False}))
        builder.say(line)


# 源码依据：src/main/java/org/agmas/noellesroles/role_data/innocence/MorticianRoleData.java:44-57,79-93；介绍 zh_cn.json:366
def override_mortician_skill(builder, ctx):
    g = _g()
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    lines = g.skill_lines(ctx, ["透视", "掉落物", "物品栏", "拿取", "冷却"])
    builder.add(g.item_entity([t[0], t[1] + 0.55, t[2]], "trainmurdermystery:revolver", rid + "_corpse"))
    for line in lines:
        zh = line.get("zh_cn", "")
        builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
        if "物品栏" in zh or "拿取" in zh:
            builder.add(g.custom("role_panel", {
                "title": g.tr(("尸体物品栏", "Corpse inventory", "屍體物品欄")), "lines": [line]}))
        elif "透视" in zh or "掉落物" in zh:
            builder.add(g.custom("role_outline", {"linkId": rid + "_corpse", "color": 0xFFAA00}))
        elif "冷却" in zh:
            builder.add(g.custom("role_timer", {"progress": [1.0, 0.0], "lines": [line]}))
        builder.say(line)


# 源码依据：src/main/java/org/agmas/noellesroles/role_data/neutral/MushroomScholarRoleData.java:15-45；介绍 zh_cn.json:502
def override_mushroom_scholar_skill(builder, ctx):
    g = _g()
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    lines = g.skill_lines(ctx, ["菌子", "培育", "蘑菇", "切换键", "蘑菇素", "Q键"])
    builder.add(g.player_step(t, rid + "_sample", outline=g.GRAY))
    for line in lines:
        zh = line.get("zh_cn", "")
        builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]], switch="切换键" in zh))
        if "培育" in zh or ("菌子" in zh and "样本" in zh):
            builder.add(g.custom("role_panel", {
                "title": g.tr(("培育菌子", "Cultivate", "培育菌子")), "lines": [line]}))
        elif "蘑菇素" in zh:
            builder.add(g.custom("role_hold", {"linkId": main, "item": "noellesroles:mushroom_essence"}))
        elif "蘑菇" in zh:
            builder.add(g.item_entity([p[0] - 1.0, p[1] + 0.55, p[2] - 0.9],
                                      "noellesroles:mushroom", rid + "_mushroom"))
        builder.say(line)


# 源码依据：src/main/java/org/agmas/noellesroles/role_data/neutral/PhantomMusicianRoleData.java:46-50,163-214；介绍 zh_cn.json:406
def override_musician_phantom_skill(builder, ctx):
    g = _g()
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    lines = g.skill_lines(ctx, ["幻音师透视", "幻音传送", "传送", "音效", "金币"])
    for line in lines:
        zh = line.get("zh_cn", "")
        builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
        if "传送" in zh:
            builder.add(g.scene_sound("minecraft:entity.enderman.teleport"))
            builder.add(g.scene_particle("minecraft:portal", [p[0], p[1] + 0.9, p[2]],
                                         count=14, duration=80))
            builder.add(g.custom("role_teleport", {"linkId": main, "pos": [t[0], t[1], t[2]]}))
            builder.add(g.custom("role_currency", {"progress": [0.9, 0.4], "lines": [line]}))
        else:
            builder.add(g.custom("role_effects", {"lines": [line]}))
        builder.say(line)


# 源码依据：src/main/java/org/agmas/noellesroles/role_data/killer/MustardRoleData.java:13-25；介绍 zh_cn.json:556
def override_mustard_skill(builder, ctx):
    g = _g()
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    lines = g.skill_lines(ctx, ["傀儡核心", "复活", "召回", "替伤", "傀儡", "死亡"])
    builder.add(g.player_step(t, rid + "_golem", outline=g.GRAY))
    for line in lines:
        zh = line.get("zh_cn", "")
        builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
        if "复活" in zh or "核心" in zh:
            builder.add(g.custom("role_hold", {"linkId": main, "item": "noellesroles:puppet_core"}))
        elif "召回" in zh or "替伤" in zh or "传送" in zh:
            builder.add(g.custom("role_teleport", {"linkId": rid + "_golem",
                                                   "pos": [p[0] + 1.0, p[1], p[2]]}))
        elif "死亡" in zh:
            builder.add(g.custom("role_death", {"linkId": rid + "_golem", "pos": t}))
        builder.say(line)


# 源码依据：src/main/java/org/agmas/noellesroles/role_data/killer/MustardPuppetRoleData.java:14-32；介绍 zh_cn.json:558
def override_mustard_puppet_skill(builder, ctx):
    g = _g()
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    lines = g.skill_lines(ctx, ["傀儡核心", "复活", "攻击", "僵直", "传送", "死亡"])
    builder.add(g.player_step(t, rid + "_puppet_victim", outline=g.GRAY))
    builder.add(g.custom("role_death", {"linkId": rid + "_puppet_victim", "pos": t,
                                        "name": "ponder.starrailexpress.label.victim"}))
    for line in lines:
        zh = line.get("zh_cn", "")
        builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
        if "攻击" in zh or "僵直" in zh:
            builder.add(g.custom("role_vitals", {"bars": [
                {"label": "virtual", "before": 1.0, "after": 0.5, "color": 0xFF5555}]}))
        elif "传送" in zh:
            builder.add(g.custom("role_teleport", {"linkId": main, "pos": [p[0] + 1.0, p[1], p[2]]}))
        elif "死亡" in zh:
            builder.add(g.custom("role_death", {"linkId": main, "pos": p}))
        builder.say(line)


# 源码依据：src/main/java/org/agmas/noellesroles/role/touhou/THMiscRoles.java:164-169；介绍 zh_cn.json:290
def override_mystia_lorelei_skill(builder, ctx):
    g = _g()
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    lines = g.skill_lines(ctx, ["音游", "奖励", "烤八目海鳗", "失明", "速度", "夜视"])
    for line in lines:
        zh = line.get("zh_cn", "")
        builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
        if "音游" in zh:
            builder.add(g.custom("role_panel", {
                "title": g.tr(("音游", "Rhythm game", "音遊")), "lines": [line]}))
        elif "海鳗" in zh or "夜视" in zh:
            builder.add(g.item_entity([p[0] - 1.0, p[1] + 0.55, p[2] - 0.9],
                                      "minecraft:cooked_cod", rid + "_eel"))
        elif "失明" in zh:
            builder.add(g.custom("role_ambience", {"color": 0x222222, "alpha": 0.7, "mode": "blind"}))
        elif "速度" in zh or "金币" in zh or "奖励" in zh:
            builder.add(g.custom("role_currency", {"progress": [0.1, 0.9], "lines": [line]}))
        builder.say(line)


# 源码依据：src/main/java/org/agmas/noellesroles/game/roles/killer/nature_spirit/NatureSpiritRole.java:81-95；介绍 zh_cn.json:486
def override_nature_spirit_skill(builder, ctx):
    g = _g()
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    lines = g.skill_lines(ctx, ["叶隐", "伪装", "脚下的方块", "通报", "竹子"])
    for line in lines:
        zh = line.get("zh_cn", "")
        builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
        if "叶隐" in zh or "伪装" in zh or "脚下的方块" in zh:
            builder.add(g.custom("role_hide", {"linkId": main, "visible": False}))
            builder.add(g.custom("role_pose", {"linkId": main, "pose": "crouching"}))
        elif "通报" in zh:
            builder.add(g.custom("role_broadcast", {
                "title": "hud.noellesroles.nature_spirit", "lines": [line]}))
        elif "竹子" in zh:
            builder.add(g.item_entity([p[0] - 1.0, p[1] + 0.55, p[2] - 0.9],
                                      "noellesroles:bamboo_spear", rid + "_bamboo"))
        builder.say(line)


# 源码依据：src/main/java/org/agmas/noellesroles/role_data/vigilante/NetCopRoleData.java:50-90；介绍 zh_cn.json:32
def override_net_cop_skill(builder, ctx):
    g = _g()
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    lines = g.skill_lines(ctx, ["主手", "副手", "释放技能", "击杀", "爆炸弩", "代币"])
    builder.add(g.player_step(t, rid + "_robbed", outline=g.GRAY))
    builder.add(g.custom("role_death", {"linkId": rid + "_robbed", "pos": t,
                                        "name": "ponder.starrailexpress.label.victim"}))
    for line in lines:
        zh = line.get("zh_cn", "")
        builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
        if "主手" in zh or "副手" in zh:
            builder.add(g.custom("role_hold", {"linkId": main, "item": "noellesroles:dream_axe"}))
        elif "爆炸弩" in zh or "烟花火箭" in zh:
            builder.add(g.show_right_click([p[0] - 1.3, p[1] + 0.9, p[2] - 1.2], "minecraft:crossbow"))
        elif "击杀" in zh:
            builder.add(g.custom("role_death", {"linkId": rid + "_robbed", "pos": t}))
        elif "代币" in zh or "金币" in zh:
            builder.add(g.custom("role_currency", {"progress": [0.2, 0.9], "lines": [line]}))
        builder.say(line)


# 源码依据：src/main/java/org/agmas/noellesroles/role_data/neutral/NianShouRoleData.java:95-111,219-250；介绍 zh_cn.json:224
def override_nianshou_skill(builder, ctx):
    g = _g()
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    lines = g.skill_lines(ctx, ["红包", "鞭炮", "散财", "恭喜发财", "护盾试剂", "死亡", "阵亡"])
    builder.add(g.player_step(t, rid + "_blessed", outline=g.GRAY))
    for line in lines:
        zh = line.get("zh_cn", "")
        builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
        if "红包" in zh or "散财" in zh:
            builder.add(g.item_entity([p[0] - 1.0, p[1] + 0.55, p[2] - 0.9],
                                      "noellesroles:red_packet", rid + "_packet"))
        elif "鞭炮" in zh:
            builder.add(g.custom("role_telegraph", {
                "title": g.tr(("除岁", "Firecrackers", "除歲")),
                "hint": g.tr(("5格内集齐即阵亡", "Enough nearby = death", "5格內集齊即陣亡")),
                "message": g.tr(("鞭炮散布", "Firecrackers spread", "鞭炮散佈")), "remaining": 4}))
        elif "死亡" in zh or "阵亡" in zh:
            builder.add(g.custom("role_death", {"linkId": rid + "_blessed", "pos": t}))
        elif "恭喜发财" in zh or "金币" in zh:
            builder.add(g.custom("role_currency", {"progress": [0.2, 1.0], "lines": [line]}))
        builder.say(line)


# 源码依据：src/main/java/org/agmas/noellesroles/role_data/killer/NiaoshoushouRoleData.java:59-66,130-168；介绍 zh_cn.json:462
def override_niaoshoushou_skill(builder, ctx):
    g = _g()
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    lines = g.skill_lines(ctx, ["超强耐力", "火焰", "燃烧", "掩体", "墙壁", "击杀", "全家福"])
    builder.add(g.player_step(t, rid + "_bird_foe", outline=g.ORANGE))
    for line in lines:
        zh = line.get("zh_cn", "")
        builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
        if "掩体" in zh or "墙壁" in zh:
            builder.add({"type": "set_block", "block": "minecraft:bricks",
                         "blockPos": [int(p[0]), int(p[1]), int(p[2]) + 1],
                         "blockPos2": [int(p[0]) + 2, int(p[1]) + 1, int(p[2]) + 1]})
        elif "速度" in zh or "耐力" in zh:
            builder.add(g.custom("role_effects", {"lines": [line]}))
        elif "击杀" in zh or "燃烧" in zh or "死亡" in zh:
            builder.add(g.custom("role_death", {"linkId": rid + "_bird_foe", "pos": t}))
        builder.say(line)


# 源码依据：src/main/java/org/agmas/noellesroles/role_data/killer/NinjaRoleData.java:39-46,92-137；介绍 zh_cn.json:250
def override_ninja_skill(builder, ctx):
    g = _g()
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    lines = g.skill_lines(ctx, ["开锁器", "苦无", "手里剑", "关灯", "格挡", "护盾", "黑暗"])
    for line in lines:
        zh = line.get("zh_cn", "")
        builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
        if "格挡" in zh or "护盾" in zh:
            builder.add(g.custom("role_vitals", {"bars": [
                {"label": "shield", "before": 0.0, "after": 1.0, "color": 0x55FFFF}]}))
        elif "苦无" in zh or "手里剑" in zh:
            builder.add(g.item_entity([p[0] - 1.0, p[1] + 0.55, p[2] - 0.9],
                                      "noellesroles:kunai", rid + "_blade"))
        elif "开锁器" in zh:
            builder.add(g.custom("role_hold", {"linkId": main, "item": "trainmurdermystery:lockpick"}))
        elif "关灯" in zh or "黑暗" in zh:
            builder.add(g.custom("role_ambience", {"color": 0x111111, "alpha": 0.65, "mode": "blind"}))
        builder.say(line)


# 源码依据：src/main/java/org/agmas/noellesroles/game/roles/killer/nostalgist/NostalgistRole.java:36-46；介绍 zh_cn.json:252
def override_nostalgist_skill(builder, ctx):
    g = _g()
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    lines = g.skill_lines(ctx, ["里世界", "隐身", "粒子", "灰白", "崩塌", "技能键", "金币", "潜行"])
    builder.add(g.custom("role_hide", {"linkId": main, "visible": False}))
    for line in lines:
        zh = line.get("zh_cn", "")
        builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
        if "灰白" in zh:
            builder.add(g.custom("role_ambience", {"color": 0xBBBBBB, "alpha": 0.55, "mode": "fog"}))
        elif "粒子" in zh or "声音" in zh:
            builder.add(g.scene_particle("minecraft:reverse_portal", [p[0], p[1] + 0.9, p[2]],
                                         count=12, duration=80))
        elif "金币" in zh:
            builder.add(g.custom("role_currency", {"progress": [0.2, 0.9], "lines": [line]}))
        elif "崩塌" in zh or "技能键" in zh:
            builder.add(g.custom("role_hide", {"linkId": main, "visible": True}))
        builder.say(line)


# 源码依据：src/main/java/org/agmas/noellesroles/role/touhou/roles/THKomachiRole.java:33-41；介绍 zh_cn.json:276
def override_onozuka_komachi_skill(builder, ctx):
    g = _g()
    p, t = ctx["player_pos"], ctx["second_pos"]
    rid, main = ctx["rid"], ctx["rid"] + "_main"
    lines = g.skill_lines(ctx, ["拉至身前", "突进", "金币", "杀死", "死后"])
    builder.add(g.player_step(t, rid + "_hauled", outline=g.GRAY))
    for line in lines:
        zh = line.get("zh_cn", "")
        builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
        if "拉至身前" in zh or "技能1" in zh:
            builder.add(g.custom("role_teleport", {"linkId": rid + "_hauled",
                                                   "pos": [p[0] + 1.0, p[1], p[2]]}))
        elif "突进" in zh or "技能2" in zh:
            builder.add(g.custom("role_walk", {"linkId": main,
                                               "to": [p[0], p[1], p[2] - 4.0], "duration": 34}))
        elif "杀死" in zh or "死后" in zh:
            builder.add(g.custom("role_death", {"linkId": rid + "_hauled", "pos": t}))
        elif "金币" in zh:
            builder.add(g.custom("role_currency", {"progress": [0.1, 0.9], "lines": [line]}))
        builder.say(line)


OVERRIDES = {
    ("metal_monster", "skill"): override_metal_monster_skill,
    ("missionary", "skill"): override_missionary_skill,
    ("monitor", "skill"): override_monitor_skill,
    ("monokuma", "skill"): override_monokuma_skill,
    ("morphling", "skill"): override_morphling_skill,
    ("mortician", "skill"): override_mortician_skill,
    ("mushroom_scholar", "skill"): override_mushroom_scholar_skill,
    ("musician_phantom", "skill"): override_musician_phantom_skill,
    ("mustard", "skill"): override_mustard_skill,
    ("mustard_puppet", "skill"): override_mustard_puppet_skill,
    ("mystia_lorelei", "skill"): override_mystia_lorelei_skill,
    ("nature_spirit", "skill"): override_nature_spirit_skill,
    ("net_cop", "skill"): override_net_cop_skill,
    ("nianshou", "skill"): override_nianshou_skill,
    ("niaoshoushou", "skill"): override_niaoshoushou_skill,
    ("ninja", "skill"): override_ninja_skill,
    ("nostalgist", "skill"): override_nostalgist_skill,
    ("onozuka_komachi", "skill"): override_onozuka_komachi_skill,
}