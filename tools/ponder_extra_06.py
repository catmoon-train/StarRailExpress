# -*- coding: utf-8 -*-
def _g():
    import gen_ponder_scenes as g
    return g


# 依据：src/main/java/org/agmas/noellesroles/role_data/innocence/PainterRoleData.java:44-48,152-195,298-360（画板获取三场景与每4分钟求索）
def override_painter_skill(builder, ctx):
    g = _g()
    p, t, rid = ctx["player_pos"], ctx["second_pos"], ctx["rid"]
    lines = g.skill_lines(ctx, ["画板", "绘画灵感", "求索", "挚友"])
    main = rid + "_main"
    board = "starrailexpress:drawing_board"
    builder.add(g.player_step(t, rid + "_writer", outline=ctx["outline_color"]))
    builder.add(g.item_entity([p[0] - 1.2, p[1] + 0.55, p[2]], board, rid + "_board"))
    builder.add(g.show_right_click([p[0] + 1.2, p[1] + 1.0, p[2]], board))
    builder.add(g.custom("role_hold", {"linkId": main, "item": board}))
    builder.add(g.custom("role_panel", {"title": g.tr(("绘画", "Canvas", "繪畫")), "lines": lines[:2]}))
    builder.add(g.key_cap([p[0], p[1] + 2.2, p[2]]))
    builder.add(g.custom("role_timer", {"progress": [0.0, 1.0], "lines": lines[2:3]}))
    builder.add(g.item_entity([t[0], t[1] + 0.55, t[2]], board, rid + "_friend_board"))
    builder.add(g.custom("role_bond", {"lines": lines[3:4], "color": g.ORANGE}))
    builder.add(g.custom("role_effects", {"lines": lines[4:5]}))
    for line in lines:
        builder.say(line)


# 依据：src/main/java/org/agmas/noellesroles/role_data/killer/PaperDollRoleData.java:48-118（暂缓半秒、闪现4格、虚弱发光、CLOUD粒子）
def override_paper_doll_skill(builder, ctx):
    g = _g()
    p, t, rid = ctx["player_pos"], ctx["second_pos"], ctx["rid"]
    lines = g.skill_lines(ctx, ["替身", "乱向", "纸刃", "切换键", "免除此击"])
    main = rid + "_main"
    builder.add(g.player_step(t, rid + "_attacker", outline=g.ORANGE))
    builder.add(g.key_cap([p[0], p[1] + 2.25, p[2]], switch=True))
    builder.add(g.custom("role_timer", {"progress": [1.0, 0.0], "lines": lines[:1]}))
    builder.add(g.custom("role_teleport", {"linkId": main, "pos": [p[0] - 4.0, p[1], p[2]]}))
    builder.add(g.scene_particle("minecraft:cloud", [p[0], p[1] + 1.0, p[2]], 16))
    builder.add(g.scene_sound("minecraft:entity.enderman.teleport"))
    builder.add(g.custom("role_death", {"linkId": main, "pos": p,
                                        "name": "ponder.starrailexpress.label.victim"}))
    builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
    builder.add(g.custom("role_outline", {"linkId": rid + "_attacker", "color": g.ORANGE}))
    builder.add(g.custom("role_walk", {"linkId": rid + "_attacker",
                                       "to": [t[0] + 0.8, t[1], t[2]], "duration": 30}))
    builder.add(g.custom("role_effects", {"lines": lines[1:3]}))
    for line in lines:
        builder.say(line)


# 依据：src/main/java/org/agmas/noellesroles/role_data/killer/PartyRoleData.java:72-80,116-131,153-201；init/NRSounds.java:38（阈值、延迟派对音效、烟花与金币）
def override_party_killer_skill(builder, ctx):
    g = _g()
    p, t, rid = ctx["player_pos"], ctx["second_pos"], ctx["rid"]
    lines = g.skill_lines(ctx, ["氦气", "变声", "派对音效", "派对狂欢", "烟花火箭"])
    builder.add(g.player_step(t, rid + "_guest", outline=ctx["outline_color"]))
    builder.add(g.custom("role_screen", {
        "title": g.tr(("选择玩家", "Pick a player", "選擇玩家")),
        "hint": g.tr(("点击头像变声", "Click a head to change voice", "點擊頭像變聲")), "columns": 6}))
    builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
    builder.add(g.custom("role_outline", {"linkId": rid + "_guest", "color": g.ORANGE}))
    builder.add(g.custom("role_effects", {"lines": lines[:1]}))
    builder.add(g.custom("role_timer", {"progress": [1.0, 0.0], "lines": lines[1:2]}))
    builder.add(g.scene_sound("noellesroles:party_skill"))
    builder.add(g.item_entity([t[0], t[1] + 1.2, t[2]], "minecraft:firework_rocket", rid + "_rocket"))
    builder.add(g.custom("role_broadcast", {
        "title": g.tr(("派对狂欢", "Party Time", "派對狂歡")),
        "lines": lines[2:3], "cost": 0, "duration": 140}))
    builder.add(g.custom("role_currency", {"progress": [0.0, 1.0], "lines": lines[3:]}))
    for line in lines:
        builder.say(line)


# 依据：src/main/java/org/agmas/noellesroles/role_data/vigilante/PatrollerRoleData.java:107-133（30格内死亡触发巡警手枪与乘务员钥匙）
def override_patroller_skill(builder, ctx):
    g = _g()
    p, t, rid = ctx["player_pos"], ctx["second_pos"], ctx["rid"]
    lines = g.skill_lines(ctx, ["30格", "死亡", "巡警手枪", "乘务员钥匙", "左轮手枪"])
    main = rid + "_main"
    builder.add(g.player_step(t, rid + "_victim", outline=g.ORANGE))
    builder.add(g.custom("role_death", {"linkId": rid + "_victim", "pos": t,
                                        "name": "ponder.starrailexpress.label.victim"}))
    builder.add(g.custom("role_walk", {"linkId": main,
                                       "to": [t[0] - 1.0, t[1], t[2]], "duration": 35}))
    builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
    builder.add(g.show_right_click([p[0] + 1.3, p[1] + 1.0, p[2]], "noellesroles:patroller_revolver"))
    builder.add(g.item_entity([p[0] + 0.6, p[1] + 0.55, p[2]], "noellesroles:patroller_revolver", rid + "_gun"))
    builder.add(g.item_entity([p[0] - 0.6, p[1] + 0.55, p[2]], "noellesroles:master_key_p", rid + "_key"))
    builder.add(g.custom("role_hold", {"linkId": main, "item": "noellesroles:master_key_p"}))
    builder.add(g.custom("role_timer", {"progress": [1.0, 0.0], "lines": lines[2:4]}))
    builder.add(g.custom("role_effects", {"lines": lines[4:]}))
    for line in lines:
        builder.say(line)


# 依据：src/main/java/org/agmas/noellesroles/role_data/neutral/PelicanRoleData.java:102-181,194-243（2.15格吞噬、BURP音效、蹲下吐出、票出后死亡）
def override_pelican_skill(builder, ctx):
    g = _g()
    p, t, rid = ctx["player_pos"], ctx["second_pos"], ctx["rid"]
    lines = g.skill_lines(ctx, ["吞噬", "吐出", "透视", "死亡", "胜利条件"])
    builder.add(g.player_step(t, rid + "_prey", outline=g.ORANGE))
    builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
    builder.add(g.custom("role_walk", {"linkId": rid + "_main",
                                       "to": [t[0] - 0.7, t[1], t[2]], "duration": 30}))
    builder.add(g.custom("role_hide", {"linkId": rid + "_prey", "visible": False}))
    builder.add(g.custom("role_bond", {"lines": lines[2:3], "color": g.ORANGE}))
    builder.add(g.scene_sound("minecraft:entity.player.burp"))
    builder.add(g.custom("role_pose", {"linkId": rid + "_main", "pose": "crouching"}))
    builder.add(g.key_cap([p[0], p[1] + 2.25, p[2]]))
    builder.add(g.custom("role_hide", {"linkId": rid + "_prey", "visible": True}))
    builder.add(g.custom("role_death", {"linkId": rid + "_prey", "pos": t,
                                        "name": "ponder.starrailexpress.label.victim"}))
    builder.add(g.custom("role_timer", {"progress": [0.0, 1.0], "lines": lines[4:]}))
    for line in lines:
        builder.say(line)


# 依据：src/main/java/org/agmas/noellesroles/game/command_block/PendulumRole.java:32-38,51-59,141-164（秒针/分针/钟锤、定钟回溯、定摆拉回击杀）
def override_pendulum_skill(builder, ctx):
    g = _g()
    p, t, rid = ctx["player_pos"], ctx["second_pos"], ctx["rid"]
    lines = g.skill_lines(ctx, ["定钟", "定摆", "秒针", "分针", "钟锤"])
    main = rid + "_main"
    builder.add(g.player_step(t, rid + "_marked", outline=g.ORANGE))
    builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
    builder.add(g.custom("role_timer", {"progress": [1.0, 0.0], "lines": lines[:1]}))
    builder.add(g.custom("role_teleport", {"linkId": main, "pos": [p[0] - 1.5, p[1], p[2]]}))
    builder.add(g.key_cap([p[0], p[1] + 2.25, p[2]]))
    builder.add(g.custom("role_telegraph", {
        "title": g.tr(("定摆", "Pendulum mark", "定擺")),
        "hint": g.tr(("4秒后摆回原点", "Pulled back after 4s", "4秒後擺回原點")),
        "message": g.tr(("已标记", "Marked", "已標記")), "remaining": 4, "duration": 90}))
    builder.add(g.custom("role_teleport", {"linkId": rid + "_marked",
                                           "pos": [t[0] - 2.0, t[1], t[2]]}))
    builder.add(g.item_entity([p[0] + 0.8, p[1] + 0.55, p[2]], "noellesroles:second_needle", rid + "_needle"))
    builder.add(g.show_right_click([p[0] + 1.3, p[1] + 1.0, p[2]], "noellesroles:minute_needle"))
    builder.add(g.custom("role_death", {"linkId": rid + "_marked", "pos": t,
                                        "name": "ponder.starrailexpress.label.victim"}))
    builder.add(g.custom("role_currency", {"progress": [0.0, 1.0], "lines": lines[3:]}))
    for line in lines:
        builder.say(line)


# 依据：src/main/java/org/agmas/noellesroles/role_data/neutral/PhantomSpiritRoleData.java:64-67,201-213（附身冷却60秒、再按键灵视）
def override_phantom_spirit_skill(builder, ctx):
    g = _g()
    p, t, rid = ctx["player_pos"], ctx["second_pos"], ctx["rid"]
    lines = g.skill_lines(ctx, ["附身", "幻灵", "透视", "击杀", "悬浮"])
    builder.add(g.player_step(t, rid + "_host", outline=ctx["outline_color"]))
    builder.add(g.key_cap([p[0], p[1] + 2.9, p[2]]))
    builder.add(g.custom("role_teleport", {"linkId": rid + "_main", "pos": [t[0], t[1] + 1.8, t[2]]}))
    builder.add(g.custom("role_bond", {"lines": lines[:1], "color": g.ORANGE}))
    builder.add(g.custom("role_scale", {"linkId": rid + "_main", "scale": 0.75}))
    builder.add(g.custom("role_walk", {"linkId": rid + "_host",
                                       "to": [t[0] + 1.2, t[1], t[2]], "duration": 40}))
    builder.add(g.key_cap([t[0], t[1] + 2.0, t[2]]))
    builder.add(g.custom("role_outline", {"linkId": rid + "_host", "color": g.ORANGE}))
    builder.add(g.custom("role_effects", {"lines": lines[2:3]}))
    builder.add(g.custom("role_death", {"linkId": rid + "_host", "pos": t,
                                        "name": "ponder.starrailexpress.label.victim"}))
    builder.add(g.custom("role_timer", {"progress": [1.0, 0.0], "lines": lines[3:]}))
    for line in lines:
        builder.say(line)


# 依据：src/main/java/org/agmas/noellesroles/role_data/innocence/PilotRoleData.java:55-134（技能键脱下喷气背包与鞘翅）
def override_pilot_skill(builder, ctx):
    g = _g()
    p, t, rid = ctx["player_pos"], ctx["second_pos"], ctx["rid"]
    lines = g.skill_lines(ctx, ["喷气背包", "鞘翅", "技能键", "漂浮", "绑定"])
    main = rid + "_main"
    builder.add(g.player_step(t, rid + "_falcon", outline=g.ORANGE))
    builder.add(g.custom("role_bond", {"lines": lines[:1], "color": g.ORANGE}))
    builder.add(g.custom("role_hold", {"linkId": main, "item": "noellesroles:jetpack"}))
    builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
    builder.add(g.item_entity([p[0] + 0.8, p[1] + 0.55, p[2]], "noellesroles:jetpack", rid + "_pack"))
    builder.add(g.custom("role_pose", {"linkId": main, "pose": "crouching"}))
    builder.add(g.item_entity([p[0] - 0.8, p[1] + 0.55, p[2]], "minecraft:elytra", rid + "_elytra"))
    builder.add(g.item_entity([p[0], p[1] + 1.6, p[2]], "minecraft:firework_rocket", rid + "_rocket"))
    builder.add(g.custom("role_effects", {"lines": lines[3:4]}))
    builder.add(g.custom("role_timer", {"progress": [1.0, 0.0], "lines": lines[4:]}))
    for line in lines:
        builder.say(line)


# 依据：src/main/java/org/agmas/noellesroles/role_data/innocence/PsychologistRoleData.java:136-154,239-244,287,452-454（san≥0.7、静止10秒回满、治愈负面、4格光环）
def override_psychologist_skill(builder, ctx):
    g = _g()
    p, t, rid = ctx["player_pos"], ctx["second_pos"], ctx["rid"]
    lines = g.skill_lines(ctx, ["san值", "静止不动", "治愈", "光环", "薄荷糖", "维生素"])
    builder.add(g.player_step(t, rid + "_patient", outline=ctx["outline_color"]))
    builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
    builder.add(g.custom("role_telegraph", {
        "title": g.tr(("静止10秒", "Stand still 10s", "靜止10秒")),
        "hint": g.tr(("对准目标", "Aim at target", "對準目標")),
        "message": g.tr(("san值回满", "Sanity restored", "san值回滿")),
        "remaining": 10, "duration": 150}))
    builder.add(g.custom("role_vitals", {"bars": [
        {"label": "ponder.starrailexpress.label.vitals", "before": 0.2,
         "after": 1.0, "color": g.ORANGE}]}))
    builder.add(g.custom("role_outline", {"linkId": rid + "_patient", "color": g.ORANGE}))
    builder.add(g.custom("role_bond", {"lines": lines[2:3], "color": g.ORANGE}))
    builder.add(g.item_entity([p[0] - 1.0, p[1] + 0.55, p[2]], "noellesroles:mint_candies", rid + "_mint"))
    builder.add(g.show_right_click([p[0] + 1.3, p[1] + 1.0, p[2]], "noellesroles:alchemist_buff_potion"))
    builder.add(g.custom("role_walk", {"linkId": rid + "_main",
                                       "to": [p[0] + 1.2, p[1], p[2]], "duration": 30}))
    builder.add(g.custom("role_effects", {"lines": lines[3:]}))
    for line in lines:
        builder.say(line)


# 依据：src/main/java/org/agmas/noellesroles/game/halloween/pumpkin/PumpkinSpiritRoleData.java:35-56（体型-80%、蝙蝠伪装15秒与飞行）
def override_pumpkin_spirit_skill(builder, ctx):
    g = _g()
    p, t, rid = ctx["player_pos"], ctx["second_pos"], ctx["rid"]
    lines = g.skill_lines(ctx, ["南瓜", "蝙蝠", "南瓜头", "死亡", "糖果"])
    main = rid + "_main"
    builder.add(g.custom("role_scale", {"linkId": main, "scale": 0.2}))
    builder.add(g.player_step(t, rid + "_victim", outline=g.ORANGE))
    builder.add(g.custom("role_death", {"linkId": rid + "_victim", "pos": t,
                                        "name": "ponder.starrailexpress.label.victim"}))
    builder.add(g.key_cap([p[0], p[1] + 1.6, p[2]]))
    builder.add(g.custom("role_pose", {"linkId": main, "pose": "crouching"}))
    builder.add(g.item_entity([t[0], t[1] + 0.6, t[2]], "noellesroles:halloween_pumpkin_helmet", rid + "_head"))
    builder.add(g.custom("role_hide", {"linkId": main, "visible": False}))
    builder.add(g.custom("role_hide", {"linkId": main, "visible": True}))
    builder.add(g.item_entity([p[0] + 1.0, p[1] + 0.55, p[2]], "noellesroles:halloween_candy", rid + "_candy"))
    builder.add(g.custom("role_broadcast", {
        "title": g.tr(("南瓜计数", "Pumpkin count", "南瓜計數")),
        "lines": lines[:1], "cost": 0, "duration": 120}))
    builder.add(g.custom("role_currency", {"progress": [0.0, 1.0], "lines": lines[4:]}))
    for line in lines:
        builder.say(line)


# 依据：src/main/java/org/agmas/noellesroles/game/roles/neutral/puppeteer/PuppeteerPlayerComponent.java:63-65,78,191-250,305-340（一阶段收尸10秒冷却、二阶段制造操控60秒）
def override_puppeteer_skill(builder, ctx):
    g = _g()
    p, t, rid = ctx["player_pos"], ctx["second_pos"], ctx["rid"]
    lines = g.skill_lines(ctx, ["尸体", "傀儡", "操控", "一阶段", "二阶段", "死亡"])
    builder.add(g.player_step(t, rid + "_corpse", outline=g.ORANGE))
    builder.add(g.custom("role_death", {"linkId": rid + "_corpse", "pos": t,
                                        "name": "ponder.starrailexpress.label.victim"}))
    builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
    builder.add(g.custom("role_walk", {"linkId": rid + "_main",
                                       "to": [t[0] - 0.8, t[1], t[2]], "duration": 35}))
    builder.add(g.custom("role_hide", {"linkId": rid + "_corpse", "visible": False}))
    builder.add(g.custom("role_timer", {"progress": [1.0, 0.0], "lines": lines[1:2]}))
    builder.add(g.custom("role_bond", {"lines": lines[2:3], "color": g.ORANGE}))
    builder.add(g.player_step([t[0] + 1.2, t[1], t[2]], rid + "_doll", outline=g.ORANGE))
    builder.add(g.custom("role_teleport", {"linkId": rid + "_main",
                                           "pos": [t[0] + 1.2, t[1], t[2]]}))
    builder.add(g.custom("role_panel", {
        "title": g.tr(("独立物品栏", "Separate inventory", "獨立物品欄")), "lines": lines[6:8]}))
    builder.add(g.custom("role_teleport", {"linkId": rid + "_main", "pos": list(p)}))
    for line in lines:
        builder.say(line)


# 依据：src/main/java/org/agmas/noellesroles/role/bouns/roles/RabbitWansuiRole.java:45,93-113,129（25金币变兔、5秒禁行动30秒禁言、死后复原）
def override_rabbit_wansui_skill(builder, ctx):
    g = _g()
    p, t, rid = ctx["player_pos"], ctx["second_pos"], ctx["rid"]
    lines = g.skill_lines(ctx, ["兔兔", "25金币", "30s", "死后", "技能"])
    builder.add(g.player_step(t, rid + "_bunny", outline=ctx["outline_color"]))
    builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
    builder.add(g.custom("role_currency", {"progress": [0.0, 1.0], "lines": lines[1:2]}))
    builder.add(g.custom("role_scale", {"linkId": rid + "_bunny", "scale": 0.6}))
    builder.add(g.custom("role_pose", {"linkId": rid + "_bunny", "pose": "crouching"}))
    builder.add(g.custom("role_timer", {"progress": [1.0, 0.0], "lines": lines[2:3]}))
    builder.add(g.key_cap([p[0], p[1] + 2.25, p[2]]))
    builder.add(g.custom("role_scale", {"linkId": rid + "_main", "scale": 0.6}))
    builder.add(g.custom("role_death", {"linkId": rid + "_main", "pos": p,
                                        "name": "ponder.starrailexpress.label.victim"}))
    builder.add(g.custom("role_broadcast", {
        "title": g.tr(("全员兔兔", "All rabbits", "全員兔兔")),
        "lines": lines[3:4], "cost": 0, "duration": 130}))
    for line in lines:
        builder.say(line)


# 依据：src/main/java/org/agmas/noellesroles/role_data/neutral/RavenRoleData.java:282-285,320-348（击杀正确目标充能、120秒猎杀、按技能键返回本体）
def override_raven_skill(builder, ctx):
    g = _g()
    p, t, rid = ctx["player_pos"], ctx["second_pos"], ctx["rid"]
    lines = g.skill_lines(ctx, ["本能透视", "充能", "猎杀态", "本体", "击杀", "冷却"])
    main = rid + "_main"
    builder.add(g.player_step(t, rid + "_target", outline=g.ORANGE))
    builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
    builder.add(g.custom("role_outline", {"linkId": rid + "_target", "color": g.GRAY}))
    builder.add(g.custom("role_timer", {"progress": [0.0, 1.0], "lines": lines[1:2]}))
    builder.add(g.player_step([p[0] - 1.2, p[1], p[2]], rid + "_body", outline=g.ORANGE))
    builder.add(g.custom("role_hide", {"linkId": main, "visible": False}))
    builder.add(g.custom("role_outline", {"linkId": rid + "_target", "color": g.ORANGE}))
    builder.add(g.custom("role_walk", {"linkId": main,
                                       "to": [t[0] - 0.7, t[1], t[2]], "duration": 45}))
    builder.add(g.custom("role_death", {"linkId": rid + "_target", "pos": t,
                                        "name": "ponder.starrailexpress.label.victim"}))
    builder.add(g.custom("role_death", {"linkId": rid + "_body", "pos": [p[0] - 1.2, p[1], p[2]],
                                        "name": "ponder.starrailexpress.label.victim"}))
    builder.add(g.custom("role_teleport", {"linkId": main, "pos": [p[0] - 1.2, p[1], p[2]]}))
    builder.add(g.custom("role_hide", {"linkId": main, "visible": True}))
    builder.add(g.custom("role_currency", {"progress": [1.0, 0.0], "lines": lines[5:]}))
    for line in lines:
        builder.say(line)


# 依据：src/main/java/org/agmas/noellesroles/role_data/neutral/ReasonerRoleData.java:52,124-128,172-204,221-227（2分钟发罗盘、右键五问、35秒冷却）
def override_reasoner_skill(builder, ctx):
    g = _g()
    p, t, rid = ctx["player_pos"], ctx["second_pos"], ctx["rid"]
    lines = g.skill_lines(ctx, ["罗盘", "夜视", "回答", "推理", "分钟后"])
    compass = "noellesroles:reasoner_compass"
    builder.add(g.custom("role_timer", {"progress": [0.0, 1.0], "lines": lines[1:2]}))
    builder.add(g.item_entity([p[0] - 1.0, p[1] + 0.55, p[2]], compass, rid + "_compass"))
    builder.add(g.custom("role_hold", {"linkId": rid + "_main", "item": compass}))
    builder.add(g.show_right_click([p[0] + 1.3, p[1] + 1.0, p[2]], compass))
    builder.add(g.custom("role_panel", {
        "title": g.tr(("五个问题", "Five questions", "五個問題")), "lines": lines[2:3]}))
    builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
    builder.add(g.custom("role_effects", {"lines": lines[:1]}))
    builder.add(g.custom("role_broadcast", {
        "title": g.tr(("推理正确", "Reasoning correct", "推理正確")),
        "lines": lines[3:4], "cost": 0, "duration": 120}))
    for line in lines:
        builder.say(line)


# 依据：src/main/java/org/agmas/noellesroles/role_data/innocence/RecallerRoleData.java:65-111（标记坐标、100金币返回、末地传送音效与传送门粒子）
def override_recaller_skill(builder, ctx):
    g = _g()
    p, t, rid = ctx["player_pos"], ctx["second_pos"], ctx["rid"]
    lines = g.skill_lines(ctx, ["标记", "地点", "100金币", "回到"])
    main = rid + "_main"
    builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
    builder.add(g.custom("role_telegraph", {
        "title": g.tr(("标记地点", "Marked spot", "標記地點")),
        "hint": g.tr(("此处已记录", "Spot recorded", "此處已記錄")),
        "message": g.tr(("再次使用返回", "Use again to return", "再次使用返回")),
        "remaining": 0, "duration": 70}))
    builder.add(g.custom("role_walk", {"linkId": main,
                                       "to": [p[0] + 2.5, p[1], p[2] + 1.5], "duration": 50}))
    builder.add(g.key_cap([p[0] + 2.5, p[1] + 2.0, p[2] + 1.5]))
    builder.add(g.custom("role_currency", {"progress": [0.0, 1.0], "lines": lines[2:3]}))
    builder.add(g.scene_sound("minecraft:entity.enderman.teleport"))
    builder.add(g.custom("role_teleport", {"linkId": main, "pos": list(p)}))
    builder.add(g.custom("role_hide", {"linkId": main, "visible": False}))
    builder.add(g.custom("role_hide", {"linkId": main, "visible": True}))
    builder.add(g.custom("role_timer", {"progress": [1.0, 0.0], "lines": lines[3:]}))
    for line in lines:
        builder.say(line)


# 依据：src/main/java/org/agmas/noellesroles/role/touhou/roles/THRemiliaRole.java:37（仆从冷却120秒）；resources/assets/role_modifier_intro/lang/zh_cn.json:332（改死因/仆从/高亮）
def override_remilia_skill(builder, ctx):
    g = _g()
    p, t, rid = ctx["player_pos"], ctx["second_pos"], ctx["rid"]
    lines = g.skill_lines(ctx, ["死因", "仆从", "杀手透视", "水", "高亮", "杀死"])
    builder.add(g.player_step(t, rid + "_victim", outline=g.ORANGE))
    builder.add(g.custom("role_death", {"linkId": rid + "_victim", "pos": t,
                                        "name": "ponder.starrailexpress.label.victim"}))
    builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
    builder.add(g.custom("role_panel", {
        "title": g.tr(("更改死因", "Change cause", "更改死因")), "lines": lines[:1]}))
    builder.add(g.custom("role_bond", {"lines": lines[1:2], "color": g.ORANGE}))
    builder.add(g.custom("role_teleport", {"linkId": rid + "_victim", "pos": [t[0] + 1.0, t[1], t[2]]}))
    builder.add(g.custom("role_outline", {"linkId": rid + "_victim", "color": g.ORANGE}))
    builder.add(g.custom("role_walk", {"linkId": rid + "_main",
                                       "to": [p[0] + 1.0, p[1], p[2] + 1.0], "duration": 35}))
    builder.add(g.custom("role_effects", {"lines": lines[3:5]}))
    builder.add(g.custom("role_timer", {"progress": [1.0, 0.0], "lines": lines[5:]}))
    for line in lines:
        builder.say(line)


# 依据：src/main/java/org/agmas/noellesroles/role_data/innocence/ReturnTravelerRoleData.java:61-88,184-209,216-230（旧日渡口2秒前摇4格2人、末班车12格、技能切换）
def override_return_traveler_skill(builder, ctx):
    g = _g()
    p, t, rid = ctx["player_pos"], ctx["second_pos"], ctx["rid"]
    lines = g.skill_lines(ctx, ["旧日渡口", "末班车", "切换", "里世界", "半透明", "杀死"])
    main = rid + "_main"
    builder.add(g.player_step(t, rid + "_ferry_a", outline=ctx["outline_color"]))
    builder.add(g.player_step([t[0] + 1.2, t[1], t[2]], rid + "_ferry_b", outline=ctx["outline_color"]))
    builder.add(g.key_cap([p[0], p[1] + 2.25, p[2]], switch=True))
    builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
    builder.add(g.custom("role_timer", {"progress": [1.0, 0.0], "lines": lines[3:4]}))
    builder.add(g.custom("role_death", {"linkId": main, "pos": p,
                                        "name": "ponder.starrailexpress.label.victim"}))
    builder.add(g.scene_particle("minecraft:reverse_portal", [p[0], p[1] + 1.0, p[2]], 16))
    builder.add(g.custom("role_teleport", {"linkId": rid + "_ferry_a", "pos": [p[0] + 1.4, p[1], p[2]]}))
    builder.add(g.custom("role_teleport", {"linkId": rid + "_ferry_b", "pos": [p[0] - 1.4, p[1], p[2]]}))
    builder.add(g.custom("role_hide", {"linkId": main, "visible": False}))
    builder.add(g.custom("role_effects", {"lines": lines[4:5]}))
    builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
    builder.add(g.custom("role_broadcast", {
        "title": g.tr(("末班车", "Last Train", "末班車")),
        "lines": lines[1:2], "cost": 0, "duration": 130}))
    for line in lines:
        builder.say(line)


# 依据：src/main/java/org/agmas/noellesroles/role_data/innocence/RiftWalkerRoleData.java:46-51,60-97,124-137（125金币、2秒前摇、15秒无敌隔离隐身速度IV）
def override_rift_walker_skill(builder, ctx):
    g = _g()
    p, t, rid = ctx["player_pos"], ctx["second_pos"], ctx["rid"]
    lines = g.skill_lines(ctx, ["裂隙", "金币", "前摇", "无敌", "杀死"])
    main = rid + "_main"
    builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
    builder.add(g.custom("role_currency", {"progress": [0.0, 1.0], "lines": lines[1:2]}))
    builder.add(g.custom("role_timer", {"progress": [1.0, 0.0], "lines": lines[2:3]}))
    builder.add(g.scene_sound("minecraft:block.portal.trigger"))
    builder.add(g.custom("role_death", {"linkId": main, "pos": p,
                                        "name": "ponder.starrailexpress.label.victim"}))
    builder.add(g.custom("role_hide", {"linkId": main, "visible": False}))
    builder.add(g.custom("role_effects", {"lines": lines[3:4]}))
    builder.add(g.custom("role_walk", {"linkId": main,
                                       "to": [p[0] + 2.0, p[1], p[2]], "duration": 30}))
    builder.add(g.scene_sound("minecraft:entity.enderman.teleport"))
    builder.add(g.custom("role_hide", {"linkId": main, "visible": True}))
    builder.add(g.custom("role_timer", {"progress": [1.0, 0.0], "lines": lines[4:]}))
    for line in lines:
        builder.say(line)


# 依据：src/main/java/org/agmas/noellesroles/role_data/neutral/RouletteDevilRoleData.java:19-49（种子成熟、同目标冷却、胜场与免死次数）
def override_roulette_devil_skill(builder, ctx):
    g = _g()
    p, t, rid = ctx["player_pos"], ctx["second_pos"], ctx["rid"]
    lines = g.skill_lines(ctx, ["恶魔种子", "欲望显现", "轮盘赌局", "免死", "黑色烟雾", "死亡"])
    main = rid + "_main"
    builder.add(g.custom("role_hide", {"linkId": main, "visible": False}))
    builder.add(g.scene_particle("wathe:black_smoke", [p[0], p[1] + 1.0, p[2]], 12))
    builder.add(g.player_step(t, rid + "_challenger", outline=g.ORANGE))
    builder.add(g.custom("role_outline", {"linkId": rid + "_challenger", "color": 0x3B82F6}))
    builder.add(g.key_cap([p[0], p[1] + 2.0, p[2]]))
    builder.add(g.custom("role_bond", {"lines": lines[:1], "color": g.ORANGE}))
    builder.add(g.custom("role_timer", {"progress": [1.0, 0.0], "lines": lines[1:2]}))
    builder.add(g.custom("role_hide", {"linkId": main, "visible": True}))
    builder.add(g.custom("role_telegraph", {
        "title": g.tr(("欲望显现", "Manifest Desire", "欲望顯現")),
        "hint": g.tr(("面向你", "Face the devil", "面向你")),
        "message": g.tr(("理智流失", "Sanity drains", "理智流失")),
        "remaining": 20, "duration": 140}))
    builder.add(g.custom("role_screen", {
        "title": g.tr(("轮盘赌局", "Russian Roulette", "輪盤賭局")),
        "hint": g.tr(("点击低理智头像", "Click a low-sanity head", "點擊低理智頭像")), "columns": 6}))
    builder.add(g.custom("role_vitals", {"bars": [
        {"label": "ponder.starrailexpress.label.vitals", "before": 1.0,
         "after": 0.25, "color": g.ORANGE}]}))
    builder.add(g.custom("role_death", {"linkId": rid + "_challenger", "pos": t,
                                        "name": "ponder.starrailexpress.label.victim"}))
    builder.add(g.custom("role_currency", {"progress": [1.0, 0.0], "lines": lines[3:5]}))
    for line in lines:
        builder.say(line)


OVERRIDES = {
    ("painter", "skill"): override_painter_skill,
    ("paper_doll", "skill"): override_paper_doll_skill,
    ("party_killer", "skill"): override_party_killer_skill,
    ("patroller", "skill"): override_patroller_skill,
    ("pelican", "skill"): override_pelican_skill,
    ("pendulum", "skill"): override_pendulum_skill,
    ("phantom_spirit", "skill"): override_phantom_spirit_skill,
    ("pilot", "skill"): override_pilot_skill,
    ("psychologist", "skill"): override_psychologist_skill,
    ("pumpkin_spirit", "skill"): override_pumpkin_spirit_skill,
    ("puppeteer", "skill"): override_puppeteer_skill,
    ("rabbit_wansui", "skill"): override_rabbit_wansui_skill,
    ("raven", "skill"): override_raven_skill,
    ("reasoner", "skill"): override_reasoner_skill,
    ("recaller", "skill"): override_recaller_skill,
    ("remilia", "skill"): override_remilia_skill,
    ("return_traveler", "skill"): override_return_traveler_skill,
    ("rift_walker", "skill"): override_rift_walker_skill,
    ("roulette_devil", "skill"): override_roulette_devil_skill,
}