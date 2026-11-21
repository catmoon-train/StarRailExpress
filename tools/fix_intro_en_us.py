# -*- coding: utf-8 -*-
"""补齐 role_modifier_intro 的 en_us 介绍原文（缺失的在册职业）。

用法：python tools/fix_intro_en_us.py

规划文档《职业思索系统全覆盖规划》第 7 节要求：所有 `text` / `title` / `hint`
必须同时写 en_us / zh_cn / zh_tw，缺英文原文的条目要先补英文再引用。
"""

import io
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PATH = os.path.join(ROOT, "src", "main", "resources", "assets",
                    "role_modifier_intro", "lang", "en_us.json")

TRANSLATIONS = {
    "command_block": """Killer event
Not assigned to players.
Takes every killer slot when it spawns, and is mutually exclusive with roles such as Jotaro.
After the match starts, a crack appears at a random suitable empty spot beside a task point every 75 to 112.5 seconds; only the killers can see the crack glowing.
The passenger team can break the crack with weapons such as a pistol, a baton or nunchucks to add 1 to the destruction progress.
When the command block event starts, it randomly marks 1.5 times the original number of killers among the passengers as abnormal and attaches a hidden abnormal modifier.
One minute before becoming a killer, you turn into the neutral role "Abnormal" with no abilities and see the role you are about to become.
Rewrites prefer enabled roles that have not appeared this round; once all of them have appeared they prefer roles with fewer accumulated uses and fewer living players, random among equals.
When a killer is born, everyone sees a subtitle and hears a sound. A destruction progress bar sits at the top left of the screen.
Every two minutes one of the following events is drawn at random from the event pool: blackout, position swap, signal interference, time rewind, silence, coin supply, exposure, lockdown or command injection.
Rewinds save a node one minute before triggering and do not roll back the destruction progress or the role rewrites. The coin supply gives every online player in the event world 25 extra coins, including spectators, and no longer records positions or pulls players back.
The game only ends when the time runs out, the neutral side wins independently, or the destruction progress reaches its quota.
Destruction progress: when the good side breaks a crack or kills a command block event killer the destruction progress goes up by 1; once it reaches its quota the good side wins""",

    "daywalker": """Neutral faction
Has an independent victory condition
Only appears in matches with 12 or more players.
Other players can only see through you while they are within 20 blocks of you.
You need to complete "starting player count / 3" tasks (rounded down, at least 6); when time runs out, if you completed the required number of tasks, you win independently for the good side (you must survive yourself).
Every task you complete makes the in-match timer run 0.075 seconds faster per second (up to 0.6 seconds) and stores 20 seconds of "day-walk time" (up to 120 seconds).
While the Daywalker lives, world time flows faster.
Press the skill key to release the skill: it immediately consumes all stored "day-walk time" and fast-forwards the match time accordingly; the skill then enters a 150 second cooldown.
The skill can fast-forward the match time until only 5 seconds remain at most.
If the match time is 5 seconds or less and the Daywalker has not completed the required number of tasks, the Daywalker dies.
Every two tasks you complete, a sound is played around the spot where you completed a task, 2.5 seconds later.
When only one task is left to reach your victory goal, the whole match is notified and you keep glowing until the match ends.
After the Daywalker dies, all accelerated time is returned and the time flow returns to normal.
The shop sells "skip task" for 200 coins; buying it refreshes the current task immediately, and it cannot be bought while no task is pending.""",

    "tailor": """Passenger faction
Starts with a sewing needle and a ball of yarn.

Sewing needle: hold right-click on a player for 5 seconds; if you have a ball of yarn in your hotbar, one ball of yarn is consumed to weave a "Tailor's Clothes" for that player.
The clothes are automatically equipped on the target if possible, otherwise they go into the target's free hotbar slots; if the target already wears another chestplate and the hotbar is full, you cannot weave clothes for them.

While wearing "Tailor's Clothes":
Warmth no longer drops naturally.
When taking damage (not a forced death), the attacker's main-hand item gains 5 extra seconds of cooldown, and there is a 5% chance to block the hit - when the block succeeds the clothes break and disappear.

Ball of yarn: right-click to throw it; hitting a player gives them 8 seconds of "Limp".

Shop: Ball of yarn: each purchase costs 50 more coins; Sewing needle: can only be bought when you have no sewing needle in your inventory.""",

    "stone_man": """Killer faction
Only produced by the command block event.
You can nullify one attack that is neither a forced death nor a train crush, and gain 15 seconds of invulnerability; cooldown 360 seconds.
Each kill grows your body size by 5% and reduces movement speed by 5%.
Stone spear: hold right-click to charge, then throw it; it pierces the target and flies back into your hand; cooldown 20 seconds.""",

    "him": """Killer faction
Only produced by the command block event.
You can only kill players who are looking at you and are not cloaked.
Your skill toggles cloaking, cooldown 15 seconds. While cloaked you are invulnerable except to forced deaths.
While you are not cloaked, every player except him stares at you and cannot move their view.""",

    "error_404": """Killer faction
Only produced by the command block event.
While on the ground, after aiming at a visible player within 24 blocks for 5 seconds you can assimilate for 15 seconds; moving the crosshair away does not reset the assimilation.
There are at most 2 error puppets in the "assimilating" or "finished" state.
When the assimilation finishes, the original player dies immediately and enters spectator, and an independent error puppet entity spawns in their place.
Dimension switch: 1 second of wind-up, cooldown 20 seconds. Entering the higher dimension lasts until you leave it. The higher dimension nullifies all damage; while there your body is fixed and cannot attack or interact directly.
Entering the higher dimension automatically opens the tactical terminal, always in top-down view, overlooking every living player in the dimension. With a puppet selected, right-click a map position to send it in a straight line through walls, left-click a player to make it chase (it only follows, never attacks and deals no damage); you can also make it stand still or detonate it, and puppets can only deal damage through detonators.
Movement pathfinding is capped at 64 blocks. ESC hides the interface; right-clicking the terminal or sneaking plus the skill key reopens it.
Error puppets cannot self-detonate with the skill: only 404 can detonate them from the higher dimension terminal, each detonation costing one detonator.
A detonation unfolds a 7x7x7 error space and destroys breakable blocks after 1 second; anyone caught inside is annihilated after staying for 1 second continuously. A 404 inside the higher dimension is immune to this effect.
All block and container data is restored after 30 seconds.
Shop: free terminal refill, detonator 50, acceleration chip 80 (each chip shortens the assimilation by 5 seconds and is not consumed on failure).""",

    "entity_303": """Killer faction
Only produced by the command block event.
Skill "steal face": copies the appearance and inventory of a player in your line of sight for 18 seconds, cooldown 40 seconds; your own inventory is restored when the steal ends, and the target's inventory is unaffected.
You can deal damage while disguised. If you die during the steal, the current stolen target dies as well.
The stolen player sees through you when they look at you within 12 blocks; your disguise then shatters, you start glowing, and you cannot steal again for 12 seconds.
You can only damage players whose face you have stolen.
Skinning blade: tears off a known face at close range and can be used while disguised, cooldown 20 seconds; killing a player makes you glow and prevents another steal for 15 seconds.""",

    "entity_null": """Killer faction
Only produced by the command block event.
You cannot pick up items; you move slower in areas with light level 12 or above and gain a speed boost during blackouts.
Erase: aim at a player and release; there is a 1.25 second wind-up (1 second during a blackout), the target gains Slowness IV, and the target must be within 14 blocks in your line of sight when it ends; cooldown 45 seconds, at most 2 charges, recovering 1 charge every 60 seconds. On success one random item of yours is deleted; with no items you suffer a backlash.
Blackout: turns off lights and cameras within 20 blocks for 6 seconds.
Self erase: after selecting it with the skill switch key, release it within 0.5 seconds of being hit by a player. Once per match, the blast deletes players and dropped items within 4 blocks and kills you (it cannot be released when your death is forced).
Item eraser: 100 coins in the shop, purchase cooldown 120 seconds. A single right-click deletes the nearest pile of dropped items within 8 blocks, and is not consumed when there is no target.
Void step: shifts you 7 blocks forward, blinding you for 2 seconds and preventing item use for 3 seconds.""",

    "farlands": """Killer faction
Only produced by the command block event.
Skill "displace": swaps positions with the player in your line of sight, cooldown 28 seconds. After the swap you get 4 seconds of dizziness, cannot use items for 2 seconds and cannot use melee attacks for 5 seconds; the other player gains 2 seconds of speed.
Rift: can be opened 3 blocks in front of you and swallows anyone still standing inside after 1.5 seconds, including yourself.""",

    "white_eyes": """Killer faction
Only produced by the command block event.
Skill "white flash": blinds for 3.5 seconds everyone within 14 blocks who is looking at you and makes them glow. You are blinded for 1 second as well, cooldown 30 seconds.
Every 12 seconds your eyes glow on their own for 2 seconds.
The pupil needle can only kill players who are blinded.""",

    "paper_doll": """Killer faction
Only produced by the command block event.
Skill "stand-in": while the skill is ready, non-forced damage or death is delayed by 0.5 seconds. Using the stand-in inside that window cancels the hit and blinks you 4 blocks backwards. Forced deaths cannot be negated.
On success you get 3 seconds of weakness and 2 seconds of glowing; the stand-in has a 32 second cooldown. Being hit again inside the window does not extend the window.
Skill "random turn": randomly turns the horizontal view of a single unobstructed living player within 8 blocks of your crosshair; cooldown 60 seconds.
Use the skill switch key to switch skills.
Paper blade: hold right-click for at least 0.05 seconds and release; it can only kill players within 5 blocks from behind an unobstructed target and cannot go through walls. A hit has a 12 second cooldown and a miss does not trigger the cooldown.""",

    "candle_shade": """Killer faction
Only produced by the command block event.
Skill "light up": lights up every unobstructed living player within 14 blocks in front of you for 8 seconds. When the target looks at you within 10 blocks they blow out their own candle and you are blinded for 1 second. Cooldown 18 seconds.
After hitting a player: you become cloaked for 10 seconds and gain a brief Speed V.
Candle oil: extends the remaining candle glow by 4 seconds.
Candle shears: can only kill players who are lit up and are not looking at you.""",

    "abnormal": """Neutral faction
Does not spawn naturally.
You have no abilities.
You only appear inside the command block event, temporarily replacing your role during the minute before you become a killer.""",

    "pooler": """Killer faction
Takes two killer slots, spawns bound with the Star Gatherer, and is mutually exclusive with the Water Ghost and the Fool.
Starts with a trident and the Moon's Blessing.
"Moon's Blessing": right-click to gain the Dolphin grace, Speed II and 15 seconds of no collision.
Use the skill switch key to cycle between the four skills.

Surge: switches all enchantments on your tridents between Riptide III and Loyalty III; releasing it again switches back to Loyalty III.
Moon Devour: pulls every player except yourself within an 8 block radius onto the moon for 30 seconds.
Aquarium Mirror: traps everyone except killers and the Magician (including yourself) within a 6 block radius inside a big tank; while inside the tank the Moon Devourer no longer goes on cooldown for trident kills.
Hymn: grants the Dolphin grace and water breathing to every killer-faction player (including the Magician) for 20 seconds.
Psycho mode: the Moon Devourer uses a trident while in psycho mode and the kill cooldown drops to 5 seconds.""",

    "star_gatherer": """Vigilante faction
Takes two vigilante slots and spawns bound with the Moon Devourer.
Permanently has water breathing and night vision.
The Star Gatherer can use the trident and special vanilla weapons (the dream series), but cannot pick up a revolver.
Passive "star halo": grants tidal energy and domain sanctuary to every player within a 6 block radius; the sanctuary is removed immediately when you die.
Domain sanctuary: a player with the sanctuary cannot be pulled into any domain except the Fool's Assembly.
Starts with the Star Scepter: aim at a target within 35 blocks and after 2 seconds a wall-piercing homing star is released at them, with the cause of death "star".
After the Star Gatherer dies the Star Scepter drops as a revolver.
The skill switch key cycles between three skills:

Sanctuary boost: the halo's protection expands to a 10 block radius for 30 seconds.
Star gravity: usable only twice per match; after channelling for 5 seconds it pulls all metal items out of the hotbars of non-passenger players within a 16 block radius (each pull discards surplus duplicates).
Alms: aim at a player and release to hand your metal items over to them (tridents and the dream iron axe are excluded).

The following items count as metal items: knife, revolver, standard revolver, trident, dream iron axe, throwing axe, throwing knife, lockpick, poor lockpick, crowbar, short shotgun.""",
}


def main():
    with io.open(PATH, encoding="utf-8-sig") as f:
        data = json.load(f)
    added = []
    for rid, text in TRANSLATIONS.items():
        key = "info.screen.roleid." + rid
        if key in data:
            continue
        data[key] = text
        added.append(rid)
    with io.open(PATH, "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(data, ensure_ascii=False, indent=2, sort_keys=False))
        f.write("\n")
    print("added:", ", ".join(added) if added else "(nothing)")


if __name__ == "__main__":
    main()
