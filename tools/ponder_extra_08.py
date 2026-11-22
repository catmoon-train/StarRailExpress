# -*- coding: utf-8 -*-
def _g():
    import gen_ponder_scenes as g
    return g


# 依据：src/main/java/org/agmas/noellesroles/role_data/innocence/TomatoHeadRoleData.java:55-62,158-174,294-310；src/main/resources/assets/role_modifier_intro/lang/zh_cn.json:228
def override_tomato_head_skill(builder, ctx):
    g = _g()
    p, rid = ctx["player_pos"], ctx["rid"]
    main = rid + "_main"
    lines = g.skill_lines(ctx, ["变成一颗西红柿", "100金币", "摔倒"])
    builder.add(g.custom("role_currency", {"progress": [1.0, 0.0], "lines": lines[:1]}))
    builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
    builder.add(g.custom("role_timer", {"progress": [1.0, 0.0], "lines": lines[:1]}))
    builder.add(g.custom("role_scale", {"linkId": main, "scale": 0.35}))
    builder.add(g.custom("role_hold", {"linkId": main, "item": "noellesroles:tomato"}))
    builder.add(g.scene_sound("noellesroles:tomato_splat"))
    builder.add(g.item_entity([p[0] + 0.8, p[1] + 0.55, p[2]], "noellesroles:tomato", rid + "_tomato"))
    builder.add(g.custom("role_scale", {"linkId": main, "scale": 1.0}))
    for line in lines:
        builder.say(line)


# 依据：src/main/java/org/agmas/noellesroles/role_data/killer/TrapperRoleData.java:61-104,231-292,411-415,465,490；zh_cn.json:68
def override_trapper_skill(builder, ctx):
    g = _g()
    p, t, rid = ctx["player_pos"], ctx["second_pos"], ctx["rid"]
    main = rid + "_main"
    lines = g.skill_lines(ctx, ["绊线", "泥沼", "捕网", "前摇", "技能切换键"])
    builder.add(g.player_step(t, rid + "_trap_target", outline=ctx["outline_color"]))
    builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
    builder.add(g.key_cap([p[0], p[1] + 2.45, p[2]], switch=True))
    builder.add(g.custom("role_timer", {"progress": [0.0, 1.0], "lines": lines[:1]}))
    builder.add(g.custom("role_walk", {"linkId": main, "to": [t[0] - 0.9, t[1], t[2]], "duration": 40}))
    builder.add(g.scene_particle("minecraft:wax_on", [t[0], t[1] + 0.4, t[2]], count=10))
    builder.add(g.scene_particle("minecraft:electric_spark", [t[0], t[1] + 0.8, t[2]], count=10))
    builder.add(g.item_entity([p[0] - 1.0, p[1] + 0.55, p[2]], "noellesroles:trapper_net_gun", rid + "_netgun"))
    builder.add(g.custom("role_currency", {"progress": [1.0, 0.3], "lines": lines[-1:]}))
    for line in lines:
        builder.say(line)


# 依据：src/main/java/org/agmas/noellesroles/role_data/killer/UndeadLordRoleData.java:52-125,489,574；zh_cn.json:412
def override_undead_lord_skill(builder, ctx):
    g = _g()
    p, t, rid = ctx["player_pos"], ctx["second_pos"], ctx["rid"]
    lines = g.skill_lines(ctx, ["灵魂", "亡者复苏", "亡魂号令", "冥火招魂", "感染"])
    builder.add(g.player_step(t, rid + "_undead_target", outline=ctx["outline_color"]))
    builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
    builder.add(g.custom("role_currency", {"progress": [0.3, 1.0], "lines": lines[:1]}))
    builder.add(g.scene_particle("minecraft:witch", [t[0], t[1] + 0.9, t[2]], count=12))
    builder.add(g.custom("role_death", {"linkId": rid + "_undead_target", "pos": t}))
    builder.add(g.custom("role_vitals", {"bars": [{"label": "ponder.starrailexpress.label.vitals", "before": 1.0, "after": 0.0, "color": g.ORANGE}]}))
    builder.add(g.item_entity([p[0] - 1.0, p[1] + 0.55, p[2]], "noellesroles:bone_staff", rid + "_staff"))
    builder.add(g.custom("role_timer", {"progress": [1.0, 0.0], "lines": lines[1:2]}))
    for line in lines:
        builder.say(line)


# 依据：src/main/java/org/agmas/noellesroles/game/roles/innocence/veteran/VeteranKnifeHandler.java:126-135；packet/VeteranDashC2SPacket.java:24-30；zh_cn.json:72
def override_veteran_skill(builder, ctx):
    g = _g()
    p, t, rid = ctx["player_pos"], ctx["second_pos"], ctx["rid"]
    main = rid + "_main"
    lines = g.skill_lines(ctx, ["初始军刀", "向前冲刺", "持刀时按技能键", "小丑", "军刀"])
    builder.add(g.player_step(t, rid + "_veteran_target", outline=ctx["outline_color"]))
    builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
    builder.add(g.custom("role_hold", {"linkId": main, "item": "noellesroles:sp_knife"}))
    builder.add(g.custom("role_walk", {"linkId": main, "to": [p[0] + 1.6, p[1], p[2]], "duration": 24}))
    builder.add(g.show_right_click([p[0] + 1.2, p[1] + 1.0, p[2]], "noellesroles:sp_knife"))
    builder.add(g.custom("role_timer", {"progress": [1.0, 0.0], "lines": lines[:1]}))
    for line in lines:
        builder.say(line)


# 依据：src/main/java/org/agmas/noellesroles/role_data/neutral/VoiceChangerRoleData.java:57-71,105-144,159-198,204-223；zh_cn.json:161
def override_voice_changer_skill(builder, ctx):
    g = _g()
    p, t, rid = ctx["player_pos"], ctx["second_pos"], ctx["rid"]
    lines = g.skill_lines(ctx, ["标记准星玩家", "变声效果", "技能切换键", "变声等级", "开锁器"])
    builder.add(g.player_step(t, rid + "_voice_target", outline=ctx["outline_color"]))
    builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
    builder.add(g.key_cap([p[0], p[1] + 2.45, p[2]], switch=True))
    builder.add(g.custom("role_outline", {"linkId": rid + "_voice_target", "color": 0xFF9400D3}))
    builder.add(g.custom("role_bond", {"lines": lines[:1], "color": 0xFF9400D3}))
    builder.add(g.custom("role_effects", {"lines": lines[1:2]}))
    builder.add(g.custom("role_broadcast", {"title": g.tr(("变声通知", "Voice notice", "變聲通知")), "lines": lines[2:3], "cost": 0, "duration": 110}))
    builder.add(g.custom("role_timer", {"progress": [1.0, 0.0], "lines": lines[3:4]}))
    for line in lines:
        builder.say(line)


# 依据：src/main/java/org/agmas/noellesroles/role_data/killer/WatcherRoleData.java:42-80,109；zh_cn.json:214
def override_watcher_skill(builder, ctx):
    g = _g()
    p, t, rid = ctx["player_pos"], ctx["second_pos"], ctx["rid"]
    main = rid + "_main"
    lines = g.skill_lines(ctx, ["宁静", "愤怒", "护盾", "速度 I", "杀死玩家"])
    builder.add(g.player_step(t, rid + "_watcher_target", outline=ctx["outline_color"]))
    builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
    builder.add(g.custom("role_hold", {"linkId": main, "item": "trainmurdermystery:revolver"}))
    builder.add(g.custom("role_outline", {"linkId": main, "color": g.GRAY}))
    builder.add(g.custom("role_vitals", {"bars": [{"label": "ponder.starrailexpress.label.vitals", "before": 1.0, "after": 0.0, "color": g.GRAY}]}))
    builder.add(g.custom("role_effects", {"lines": lines[:1]}))
    builder.add(g.custom("role_outline", {"linkId": main, "color": g.ORANGE}))
    builder.add(g.custom("role_timer", {"progress": [0.0, 1.0], "lines": lines[1:2]}))
    builder.add(g.custom("role_death", {"linkId": rid + "_watcher_target", "pos": t}))
    for line in lines:
        builder.say(line)


# 依据：src/main/java/org/agmas/noellesroles/role_data/innocence/WatchmanRoleData.java:47-54,100-119,132-156,199,273；zh_cn.json:358
def override_watchman_skill(builder, ctx):
    g = _g()
    p, rid = ctx["player_pos"], ctx["rid"]
    lines = g.skill_lines(ctx, ["敲钟", "锣", "梆", "游戏时间", "入梦"])
    builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
    builder.add(g.scene_sound("minecraft:block.bell.use"))
    builder.add(g.custom("role_timer", {"progress": [0.0, 1.0], "lines": lines[:1]}))
    builder.add(g.custom("role_outline", {"linkId": rid + "_main", "color": 0xFFD700}))
    builder.add(g.item_entity([p[0] - 1.0, p[1] + 0.55, p[2]], g.item_for_line(ctx["facts"], "锣"), rid + "_gong"))
    builder.add(g.item_entity([p[0] - 0.2, p[1] + 0.55, p[2]], g.item_for_line(ctx["facts"], "梆"), rid + "_bang"))
    builder.add(g.show_right_click([p[0] + 1.2, p[1] + 1.0, p[2]], g.item_for_line(ctx["facts"], "锣")))
    builder.add(g.custom("role_panel", {"title": g.tr(("剩余时间", "Time left", "剩餘時間")), "lines": lines[1:3]}))
    for line in lines:
        builder.say(line)


# 依据：src/main/java/org/agmas/noellesroles/role_data/killer/WaterGhostRoleData.java:49-52,239-265；packet/WaterGhostUseSkillC2SPacket.java；zh_cn.json:244
def override_water_ghost_skill(builder, ctx):
    g = _g()
    p, t, rid = ctx["player_pos"], ctx["second_pos"], ctx["rid"]
    main = rid + "_main"
    lines = g.skill_lines(ctx, ["激流", "海豚的恩惠", "三叉戟", "干涸而死", "左轮手枪"])
    builder.add(g.player_step(t, rid + "_water_target", outline=ctx["outline_color"]))
    builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
    builder.add(g.custom("role_hold", {"linkId": main, "item": "minecraft:trident"}))
    builder.add(g.scene_sound("minecraft:entity.dolphin.play"))
    builder.add(g.custom("role_effects", {"lines": lines[:1]}))
    builder.add(g.custom("role_walk", {"linkId": main, "to": [t[0] - 0.8, t[1], t[2]], "duration": 34}))
    builder.add(g.custom("role_death", {"linkId": rid + "_water_target", "pos": t}))
    builder.add(g.item_entity([t[0], t[1] + 0.6, t[2]], "trainmurdermystery:revolver", rid + "_revolver"))
    builder.add(g.custom("role_timer", {"progress": [1.0, 0.0], "lines": lines[2:3]}))
    for line in lines:
        builder.say(line)


# 依据：src/main/java/org/agmas/noellesroles/role_data/neutral/WayfarerRoleData.java:154-156,309-319；init/ModRolesInitialEventRegister.java:245-246；zh_cn.json:208
def override_wayfarer_skill(builder, ctx):
    g = _g()
    p, t, rid = ctx["player_pos"], ctx["second_pos"], ctx["rid"]
    main = rid + "_main"
    lines = g.skill_lines(ctx, ["尸体", "一次性刀", "击杀", "死法", "入睡"])
    builder.add(g.player_step(t, rid + "_wayfarer_corpse", outline=ctx["outline_color"]))
    builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
    builder.add(g.custom("role_hold", {"linkId": main, "item": "noellesroles:fake_knife"}))
    builder.add(g.item_entity([t[0], t[1] + 0.6, t[2]], "trainmurdermystery:note", rid + "_note"))
    builder.add(g.item_entity([t[0] + 0.4, t[1] + 0.6, t[2]], "trainmurdermystery:key", rid + "_key"))
    builder.add(g.custom("role_outline", {"linkId": rid + "_wayfarer_corpse", "color": g.ORANGE}))
    builder.add(g.scene_sound("minecraft:block.end_portal_frame.fill"))
    builder.add(g.custom("role_death", {"linkId": rid + "_wayfarer_corpse", "pos": t}))
    builder.add(g.custom("role_teleport", {"linkId": main, "pos": [t[0], t[1], t[2]]}))
    builder.add(g.custom("role_timer", {"progress": [1.0, 0.0], "lines": lines[:1]}))
    for line in lines:
        builder.say(line)


# 依据：src/main/java/org/agmas/noellesroles/game/command_block/WhiteEyesRole.java:32-47,50-77,83；zh_cn.json:524
def override_white_eyes_skill(builder, ctx):
    g = _g()
    p, t, rid = ctx["player_pos"], ctx["second_pos"], ctx["rid"]
    main = rid + "_main"
    lines = g.skill_lines(ctx, ["灼瞎", "发光", "失明", "瞳针", "3.5秒"])
    builder.add(g.player_step(t, rid + "_white_eyes_target", outline=ctx["outline_color"]))
    builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
    builder.add(g.custom("role_hide", {"linkId": main, "visible": False}))
    builder.add(g.custom("role_outline", {"linkId": rid + "_white_eyes_target", "color": 0xFFFFFF66}))
    builder.add(g.scene_sound("minecraft:block.beacon.activate"))
    builder.add(g.custom("role_hold", {"linkId": main, "item": "noellesroles:eye_needle"}))
    builder.add(g.show_right_click([p[0] + 1.2, p[1] + 1.0, p[2]], "noellesroles:eye_needle"))
    builder.add(g.custom("role_effects", {"lines": lines[:1]}))
    for line in lines:
        builder.say(line)


# 依据：src/main/java/org/agmas/noellesroles/init/ModRolesInitialEventRegister.java:250-252；zh_cn.json:38
def override_wind_yaose_skill(builder, ctx):
    g = _g()
    p, t, rid = ctx["player_pos"], ctx["second_pos"], ctx["rid"]
    main = rid + "_main"
    lines = g.skill_lines(ctx, ["永久隐身", "风精灵透视", "音量调至", "风弹", "弹开"])
    builder.add(g.player_step(t, rid + "_wind_target", outline=ctx["outline_color"]))
    builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
    builder.add(g.custom("role_hide", {"linkId": main, "visible": False}))
    builder.add(g.custom("role_outline", {"linkId": rid + "_wind_target", "color": g.GRAY}))
    builder.add(g.custom("role_effects", {"lines": lines[:1]}))
    builder.add(g.item_entity([p[0] - 0.9, p[1] + 0.55, p[2]], g.item_for_line(ctx["facts"], "风弹"), rid + "_windball"))
    builder.add(g.show_right_click([p[0] + 1.2, p[1] + 1.0, p[2]], g.item_for_line(ctx["facts"], "风弹")))
    builder.add(g.custom("role_knockback", {"linkId": rid + "_wind_target", "from": t, "to": [t[0] + 2.0, t[1], t[2]], "duration": 14, "arc": 0.3}))
    for line in lines:
        builder.say(line)


# 依据：src/main/java/org/agmas/noellesroles/role_data/killer/WizardRoleData.java:229-231,276-277,312-315,391,444-446；game/roles/killer/wizard/WizardSpells.java；zh_cn.json:418
def override_wizard_skill(builder, ctx):
    g = _g()
    p, t, rid = ctx["player_pos"], ctx["second_pos"], ctx["rid"]
    main = rid + "_main"
    lines = g.skill_lines(ctx, ["火焰箭", "魔素", "切换技能", "盔甲护身", "闪影步", "Explosion"])
    builder.add(g.player_step(t, rid + "_wizard_target", outline=ctx["outline_color"]))
    builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
    builder.add(g.key_cap([p[0], p[1] + 2.45, p[2]], switch=True))
    builder.add(g.custom("role_hold", {"linkId": main, "item": "noellesroles:wizard_staff"}))
    builder.add(g.scene_particle("minecraft:lava", [t[0], t[1] + 1.0, t[2]], count=12))
    builder.add(g.scene_particle("minecraft:enchant", [t[0], t[1] + 1.4, t[2]], count=10))
    builder.add(g.scene_particle("minecraft:snowflake", [t[0], t[1] + 1.8, t[2]], count=10))
    builder.add(g.custom("role_death", {"linkId": rid + "_wizard_target", "pos": t}))
    builder.add(g.custom("role_timer", {"progress": [1.0, 0.0], "lines": lines[1:2]}))
    for line in lines:
        builder.say(line)


# 依据：src/main/java/org/agmas/noellesroles/role_data/killer/WraithAssassinRoleData.java:188-207,403-411,425-445；zh_cn.json:436
def override_wraith_assassin_skill(builder, ctx):
    g = _g()
    p, t, rid = ctx["player_pos"], ctx["second_pos"], ctx["rid"]
    main = rid + "_main"
    lines = g.skill_lines(ctx, ["冤魂突袭", "厉鬼嚎叫", "冤魂显现", "能量", "漂浮"])
    builder.add(g.player_step(t, rid + "_wraith_target", outline=ctx["outline_color"]))
    builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
    builder.add(g.custom("role_hide", {"linkId": main, "visible": False}))
    builder.add(g.scene_particle("minecraft:dust", [t[0], t[1] + 1.0, t[2]], count=12))
    builder.add(g.scene_particle("minecraft:soul_fire_flame", [p[0], p[1] + 1.0, p[2]], count=10))
    builder.add(g.scene_sound("minecraft:entity.warden.sonic_boom"))
    builder.add(g.custom("role_walk", {"linkId": main, "to": [t[0] - 0.8, t[1], t[2]], "duration": 30}))
    builder.add(g.custom("role_death", {"linkId": rid + "_wraith_target", "pos": t}))
    builder.add(g.custom("role_effects", {"lines": lines[:1]}))
    builder.add(g.custom("role_timer", {"progress": [1.0, 0.0], "lines": lines[2:3]}))
    for line in lines:
        builder.say(line)


# 依据：src/main/java/org/agmas/noellesroles/role/touhou/roles/THYukariRole.java:40-117；handler/utils/THYukariPortalManager.java；zh_cn.json:292
def override_yakumo_yukari_skill(builder, ctx):
    g = _g()
    p, t, rid = ctx["player_pos"], ctx["second_pos"], ctx["rid"]
    main = rid + "_main"
    lines = g.skill_lines(ctx, ["锚点", "传送门", "启用", "关闭", "环境伤害"])
    builder.add(g.player_step(t, rid + "_yukari_target", outline=ctx["outline_color"]))
    builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
    builder.add(g.custom("role_outline", {"linkId": rid + "_yukari_target", "color": 0x800080}))
    builder.add(g.custom("role_teleport", {"linkId": main, "pos": [p[0], p[1], p[2] + 0.9]}))
    builder.add(g.custom("role_teleport", {"linkId": rid + "_yukari_target", "pos": [p[0], p[1], p[2] - 0.9]}))
    builder.add(g.custom("role_effects", {"lines": lines[1:2]}))
    builder.add(g.custom("role_timer", {"progress": [1.0, 0.0], "lines": lines[:1]}))
    for line in lines:
        builder.say(line)


# 依据：src/main/java/org/agmas/noellesroles/role_data/killer/YouluRoleData.java:104-131,165-177；content/entity/YouluSmokeBallEntity.java；zh_cn.json:212
def override_youlu_skill(builder, ctx):
    g = _g()
    p, t, rid = ctx["player_pos"], ctx["second_pos"], ctx["rid"]
    main = rid + "_main"
    lines = g.skill_lines(ctx, ["魂游", "自由模式", "球烟", "不请自来", "遮天闭目"])
    builder.add(g.player_step(t, rid + "_youlu_target", outline=ctx["outline_color"]))
    builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
    builder.add(g.item_entity([p[0] - 0.9, p[1] + 0.55, p[2]], g.item_for_line(ctx["facts"], "锚点"), rid + "_anchor"))
    builder.add(g.custom("role_teleport", {"linkId": main, "pos": [p[0], p[1], p[2] + 1.0]}))
    builder.add(g.key_cap([p[0], p[1] + 2.45, p[2]]))
    builder.add(g.item_entity([t[0], t[1] + 0.55, t[2]], g.item_for_line(ctx["facts"], "烟"), rid + "_smoke"))
    builder.add(g.custom("role_hide", {"linkId": rid + "_youlu_target", "visible": False}))
    builder.add(g.scene_sound("minecraft:entity.generic.extinguish_fire"))
    builder.add(g.custom("role_timer", {"progress": [1.0, 0.0], "lines": lines[:1]}))
    for line in lines:
        builder.say(line)


# 依据：src/main/java/org/agmas/noellesroles/role_data/neutral/ZhangAngelRoleData.java:58,121,217,298-300,320；zh_cn.json:444
def override_zhang_angel_skill(builder, ctx):
    g = _g()
    p, t, rid = ctx["player_pos"], ctx["second_pos"], ctx["rid"]
    main = rid + "_main"
    lines = g.skill_lines(ctx, ["召雷", "吞电", "闪电", "残疾", "100金币"])
    builder.add(g.player_step(t, rid + "_zhang_target", outline=ctx["outline_color"]))
    builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
    builder.add(g.scene_particle("minecraft:electric_spark", [t[0], t[1] + 1.0, t[2]], count=14))
    builder.add(g.scene_sound("minecraft:entity.lightning_bolt.thunder"))
    builder.add(g.custom("role_outline", {"linkId": rid + "_zhang_target", "color": 0xFFFF00}))
    builder.add(g.custom("role_currency", {"progress": [1.0, 0.0], "lines": lines[:1]}))
    builder.add(g.custom("role_effects", {"lines": lines[1:2]}))
    builder.add(g.custom("role_timer", {"progress": [1.0, 0.0], "lines": lines[2:3]}))
    for line in lines:
        builder.say(line)


OVERRIDES = {
    ("tomato_head", "skill"): override_tomato_head_skill,
    ("trapper", "skill"): override_trapper_skill,
    ("undead_lord", "skill"): override_undead_lord_skill,
    ("veteran", "skill"): override_veteran_skill,
    ("voice_changer", "skill"): override_voice_changer_skill,
    ("watcher", "skill"): override_watcher_skill,
    ("watchman", "skill"): override_watchman_skill,
    ("water_ghost", "skill"): override_water_ghost_skill,
    ("wayfarer", "skill"): override_wayfarer_skill,
    ("white_eyes", "skill"): override_white_eyes_skill,
    ("wind_yaose", "skill"): override_wind_yaose_skill,
    ("wizard", "skill"): override_wizard_skill,
    ("wraith_assassin", "skill"): override_wraith_assassin_skill,
    ("yakumo_yukari", "skill"): override_yakumo_yukari_skill,
    ("youlu", "skill"): override_youlu_skill,
    ("zhang_angel", "skill"): override_zhang_angel_skill,
}