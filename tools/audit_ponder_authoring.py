# -*- coding: utf-8 -*-
"""Audit generated Ponder scenes against docs/PONDERER_SCENE_AUTHORING.md."""

import glob
import json
import math
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCENES = os.path.join(ROOT, "src", "main", "resources", "assets", "starrailexpress",
                      "ponderer", "scenes")
EXCLUDED = {"role_tutorial_bartender.json", "role_tutorial_jester.json",
            "role_tutorial_recaller.json"}
LOCALES = ("en_us", "zh_cn", "zh_tw")
BLOCKING = {"rotate_camera_y", "show_interface"}
VISUAL = {"spawn_particles", "highlight_section", "create_item_entity",
          "create_entity", "show_controls", "indicate_success",
          "indicate_redstone", "set_block"}
PARTICLE_WORDS = ("粒子", "释放", "投掷", "喷洒", "喷出", "泼洒")

# 源码里确认过的粒子。按职业分组，未列出的职业不应在场景里发射粒子。
# 依据：TMMParticles.java、各 role_data/*.java、stupid_express 客户端特效注册。
SOURCE_PARTICLES = {
    "alchemist": {"wathe:poison"},
    "arsonist": {"stupid_express:arsonist_flame", "stupid_express:arsonist_kill",
                 "stupid_express:arsonist_shot", "minecraft:flame", "minecraft:smoke"},
    "conspirator": {"minecraft:witch", "minecraft:portal", "minecraft:dragon_breath"},
    "delayer": {"minecraft:enchant"},
    "diviner": {"minecraft:soul_fire_flame", "minecraft:enchant"},
    "doctor": {"minecraft:heart"},
    "dreadlord": {"minecraft:squid_ink"},
    "dream": {"minecraft:dust"},
    "executioner": {"minecraft:flame", "minecraft:soul_fire_flame",
                    "minecraft:totem_of_undying", "minecraft:portal"},
    "ghost_eye": {"minecraft:sculk_soul"},
    "glitch_robot": {"minecraft:effect"},
    "great_detective": {"minecraft:smoke", "minecraft:enchant"},
    "ibuki_suika": {"minecraft:explosion_emitter"},
    "leather_pig": {"minecraft:cloud"},
    "loose_end": {"minecraft:portal"},
    "magician": {"minecraft:flame", "minecraft:soul_fire_flame"},
    "magic_apprentice": {"minecraft:flame", "minecraft:snowflake", "minecraft:cloud"},
    # BloodBottleItem -> BloodMain.BLOOD_PARTICLE = noellesroles:deathblood
    "mortician_bodymaker": {"noellesroles:deathblood"},
    "musician_phantom": {"minecraft:portal"},
    "nostalgist": {"minecraft:reverse_portal", "minecraft:portal"},
    "nurse": {"minecraft:heart"},
    "paper_doll": {"minecraft:cloud"},
    "pooler": {"minecraft:splash", "minecraft:bubble", "minecraft:end_rod"},
    "return_traveler": {"minecraft:reverse_portal", "minecraft:portal", "minecraft:soul"},
    "roulette_devil": {"wathe:black_smoke"},
    "star_gatherer": {"minecraft:end_rod", "minecraft:electric_spark", "minecraft:portal"},
    "tailor": {"minecraft:end_rod"},
    "trapper": {"minecraft:wax_on", "minecraft:electric_spark"},
    "undead_lord": {"minecraft:witch"},
    "warlock": {"minecraft:soul"},
    "wizard": {"minecraft:enchant", "minecraft:snowflake", "minecraft:smoke",
               "minecraft:large_smoke", "minecraft:witch", "minecraft:lava"},
    "wraith_assassin": {"minecraft:soul_fire_flame", "minecraft:smoke", "minecraft:dust"},
    "zhang_angel": {"minecraft:electric_spark"},
}

violations = []


def add(where, message):
    violations.append((where, message))


for path in sorted(glob.glob(os.path.join(SCENES, "*.json"))):
    filename = os.path.basename(path)
    if filename in EXCLUDED:
        continue
    rid = filename[len("role_tutorial_"):-len(".json")]
    with open(path, encoding="utf-8-sig") as handle:
        scene = json.load(handle)

    if not scene["id"].startswith("starrailexpress:role_tutorial."):
        add(filename, "bad scene id prefix")
    expected = "role_tutorial_" + scene["id"].split(".")[-1] + ".json"
    if expected != filename:
        add(filename, "scene id does not match filename")
    for locale in LOCALES:
        if not scene["title"].get(locale):
            add(filename, "title missing " + locale)

    segment_ids = set()
    for segment in scene["scenes"]:
        sid = segment["id"]
        where = filename + "#" + sid
        if sid in segment_ids:
            add(where, "duplicate segment id")
        segment_ids.add(sid)
        for locale in LOCALES:
            if not segment["title"].get(locale):
                add(where, "segment title missing " + locale)

        steps = segment["steps"]
        if not steps or steps[0]["type"] != "show_structure":
            add(where, "first step is not show_structure")

        now = 0
        texts = []
        texts_since_visual = 0
        first_player = None
        main_player = None
        for step in steps:
            kind = step["type"]
            if kind == "starrailexpress:role_player":
                pos = step.get("parameters", {}).get("pos")
                link_id = step.get("parameters", {}).get("linkId", "")
                if first_player is None:
                    first_player = pos
                if link_id.endswith("_main"):
                    main_player = pos
        reference_player = main_player or first_player
        for step in steps:
            kind = step["type"]
            if kind == "idle":
                now += int(step.get("duration", 0))
            elif kind in BLOCKING:
                now += int(step.get("duration", 0) or 0)
            elif kind == "text":
                duration = int(step.get("duration", 60))
                for locale in LOCALES:
                    if not step.get("text", {}).get(locale):
                        add(where, "text missing " + locale)
                point = step.get("point")
                if not point:
                    add(where, "text missing point")
                elif reference_player and math.dist(point, reference_player) > 2.6:
                    add(where, "text point too far from player model")
                texts.append((now, duration))
                texts_since_visual += 1
                if texts_since_visual >= 3:
                    add(where, "three consecutive text blocks without visual action")
            elif kind in VISUAL or (isinstance(kind, str) and kind.startswith("starrailexpress:")):
                texts_since_visual = 0

            if kind == "create_item_entity" and step.get("motion") != [0, 0, 0]:
                add(where, "create_item_entity motion is not [0,0,0]")
            if kind == "show_controls" and "action" in step and step["action"] not in ("left", "right"):
                add(where, "show_controls action is not left/right")

        key_points = [step.get("parameters", {}).get("point") for step in steps
                      if step["type"] == "starrailexpress:role_key"]
        control_points = [step.get("point") for step in steps
                          if step["type"] == "show_controls"]
        if any(kp and cp and math.dist(kp, cp) < 0.5
               for kp in key_points for cp in control_points):
            add(where, "role_key overlaps show_controls")

        for previous, following in zip(texts, texts[1:]):
            if following[0] < previous[0] + previous[1] + 10:
                add(where, "text gap shorter than 10 ticks")
        if texts:
            last_start, last_duration = texts[-1]
            if now < last_start + last_duration:
                add(where, "idle after last text is insufficient")

        body = "".join(step.get("text", {}).get("zh_cn", "")
                       for step in steps if step["type"] == "text")
        used_particles = [step.get("particle") for step in steps
                          if step["type"] == "spawn_particles"]
        allowed = SOURCE_PARTICLES.get(rid, set())
        for particle in used_particles:
            if particle not in allowed:
                add(where, "particle not backed by source: " + str(particle))
        if any(word in body for word in PARTICLE_WORDS) and allowed and not used_particles:
            add(where, "source has particle but scene emitted none")

print("audited scenes:", len([p for p in glob.glob(os.path.join(SCENES, "*.json"))
                              if os.path.basename(p) not in EXCLUDED]))
print("violations:", len(violations))
from collections import Counter
for message, count in Counter(message for _, message in violations).most_common():
    print("  %4d  %s" % (count, message))
for where, message in violations[:200]:
    print(" ", where, "-", message)
