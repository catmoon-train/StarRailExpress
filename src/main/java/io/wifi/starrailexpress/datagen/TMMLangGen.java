package io.wifi.starrailexpress.datagen;

import dev.doctor4t.ratatouille.util.TextUtils;
import io.wifi.starrailexpress.index.TMMBlocks;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.index.TMMItems;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.minecraft.core.HolderLookup;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class TMMLangGen extends FabricLanguageProvider {

    public TMMLangGen(FabricDataOutput dataOutput, CompletableFuture<HolderLookup.Provider> registryLookup) {
        super(dataOutput, registryLookup);
    }

    @Override
    public void generateTranslations(HolderLookup.Provider wrapperLookup, @NotNull TranslationBuilder builder) {
        TMMBlocks.registrar.generateLang(wrapperLookup, builder);
        TMMItems.registrar.generateLang(wrapperLookup, builder);
        TMMEntities.registrar.generateLang(wrapperLookup, builder);

//        builder.add(TMMItems.LETTER.getTranslationKey() + ".instructions", "Instructions");
//        builder.add("tip.letter.killer.tooltip1", "Thank you for taking this job. Please eliminate the following targets:");
//        builder.add("tip.letter.killer.tooltip.target", "- %s");
//        builder.add("tip.letter.killer.tooltip2", "Please do so with the utmost discretion and do not get caught. Good luck.");
//        builder.add("tip.letter.killer.tooltip3", "");
//        builder.add("tip.letter.killer.tooltip4", "P.S.: Don't forget to use your instinct [Left Alt] and use the train's exterior to relocate.");
//
//        builder.add(TMMItems.LETTER.getTranslationKey() + ".notes", "Notes");
//        builder.add("tip.letter.detective.tooltip1", "Multiple homicides, several wealthy victims.");
//        builder.add("tip.letter.detective.tooltip2", "Have to be linked... Serial killer? Assassin? Killer?");
//        builder.add("tip.letter.detective.tooltip3", "Potential next victims frequent travelers of the Harpy Express.");
//        builder.add("tip.letter.detective.tooltip4", "Perfect situation to corner but need to keep targets safe.");

        builder.add("lobby.players.count", "Players boarded: %s / %s");
        builder.add("lobby.autostart.active", "Game will start once 6+ players are boarded");
        builder.add("lobby.autostart.time", "Game starting in %ss");
        builder.add("lobby.autostart.starting", "Game starting");

        builder.add("announcement.star.role.civilian", "Civilian!");
        builder.add("announcement.star.role.vigilante", "Vigilante!");
        builder.add("announcement.star.role.killer", "Killer!");
        builder.add("announcement.star.role.loose_end", "Loose End!");
        builder.add("announcement.star.title.civilian", "Civilians");
        builder.add("announcement.star.title.vigilante", "Vigilantes");
        builder.add("announcement.star.title.killer", "Killers");
        builder.add("announcement.star.title.loose_end", "Loose Ends");

        builder.add("announcement.star.welcome", "Welcome aboard %s");
        builder.add("announcement.star.premise", "There is a killer aboard the train.");
        builder.add("announcement.star.premises", "There are %s killers aboard the train.");
        builder.add("announcement.star.goal.civilian", "Stay safe and survive till the end of the ride.");
        builder.add("announcement.star.goal.vigilante", "Eliminate any murderers and protect the civilians.");
        builder.add("announcement.star.goal.killer", "Eliminate a passenger to succeed, before time runs out.");
        builder.add("announcement.star.goals.civilian", "Stay safe and survive till the end of the ride.");
        builder.add("announcement.star.goals.vigilante", "Eliminate any murderers and protect the civilians.");
        builder.add("announcement.star.goals.killer", "Eliminate all civilians before time runs out.");
        builder.add("announcement.star.win.civilian", "Passengers Win!");
        builder.add("announcement.star.win.vigilante", "Passengers Win!");
        builder.add("announcement.star.win.killer", "Killers Win!");
        builder.add("announcement.star.win.loose_end", "%s Wins!");
        builder.add("announcement.star.loose_ends.welcome", "Welcome aboard... Loose End.");
        builder.add("announcement.star.loose_ends.premise", "Everybody on the train has a derringer and a knife.");
        builder.add("announcement.star.loose_ends.goal", "Tie all loose ends before they tie you. Good luck.");
        builder.add("announcement.star.loose_ends.winner", "%s Wins!");

        builder.add("tip.letter.name", "Dear %s, welcome aboard the Harpy Express!");
        builder.add("tip.letter.room", "Please find attached your ticket as well as the key for accessing");
        builder.add("tip.letter.room.grand_suite", "the Grand Suite");
        builder.add("tip.letter.room.cabin_suite", "your Cabin Suite");
        builder.add("tip.letter.room.twin_cabin", "your Twin Cabin");
        builder.add("tip.letter.tooltip1", "%s for your trip on the 1st of January 1923.");
        builder.add("tip.letter.tooltip2", "La Sirène wishes you a pleasant and safe voyage.");

        builder.add("itemGroup.starrailexpress.building", "starrailexpress: Building Blocks");
        builder.add("itemGroup.starrailexpress.decoration", "starrailexpress: Decoration & Functional");
        builder.add("itemGroup.starrailexpress.equipment", "starrailexpress: Equipment");

        builder.add("container.cargo_box", "Cargo Box");
        builder.add("container.cabinet", "Cabinet");
        builder.add("subtitles.block.cargo_box.close", "Cargo Box closes");
        builder.add("subtitles.block.cargo_box.open", "Cargo Box opens");
        builder.add("subtitles.block.door.toggle", "Door operates");
        builder.add("subtitles.item.crowbar.pry", "Crowbar pries door");

        builder.add("tip.door.locked", "This door is locked and cannot be opened.");
        builder.add("tip.door.requires_key", "This door is locked and requires a key to be opened.");
        builder.add("tip.door.requires_different_key", "This door is locked and requires a different key to be opened.");
        builder.add("tip.door.jammed", "This door is jammed and cannot be opened at the moment!");
        builder.add("tip.derringer.used", "Used: cannot be shot anymore, get a kill for another chance!");

        builder.add("tip.cooldown", "On cooldown: %s");
        builder.add(TextUtils.getItemTranslationKey(TMMItems.KNIFE) + ".tooltip", "Right-click, hold for a second and get close to your victim\nAfter a kill, cannot be used for 1 minute\nAttack to knock back / push a player (no cooldown)");
        builder.add(TextUtils.getItemTranslationKey(TMMItems.REVOLVER) + ".tooltip", "Point, right-click and shoot\nDrops if you kill an innocent");
        builder.add(TextUtils.getItemTranslationKey(TMMItems.DERRINGER) + ".tooltip", "Point, right-click and shoot\nCan only be shot once, so make it count!\nShot is replenished after a kill");
        builder.add(TextUtils.getItemTranslationKey(TMMItems.GRENADE) + ".tooltip", "Right-click to throw, explodes on impact\nGood to clear groups of people, but be wary of the blast radius!\nSingle use, 5 minute cooldown");
        builder.add(TextUtils.getItemTranslationKey(TMMItems.PSYCHO_MODE) + ".tooltip", "\"Do you like hurting other people?\"\nHides your identity and allows you to go crazy with a bat for 30 seconds\nBat kills on full swing and cannot be unselected for the duration of the ability\nActivated instantly upon purchase, 5 minute cooldown");
        builder.add(TextUtils.getItemTranslationKey(TMMItems.POISON_VIAL) + ".tooltip", "Slip in food or drinks to poison the next pickup");
        builder.add(TextUtils.getItemTranslationKey(TMMItems.FIRECRACKER) + ".tooltip", "Detonates 15 seconds after being placed on ground\nGood to simulate gunshots and lure people");
        builder.add(TextUtils.getItemTranslationKey(TMMItems.SCORPION) + ".tooltip", "Slip in a bed to poison the next person looking for a rest");
        builder.add(TextUtils.getItemTranslationKey(TMMItems.LOCKPICK) + ".tooltip", "Use on any locked door to open it (no cooldown)\nSneak-use on a door to jam it for 1 minute (3 minute cooldown)");
        builder.add(TextUtils.getItemTranslationKey(TMMItems.CROWBAR) + ".tooltip", "Use on any door to open it permanently");
        builder.add(TextUtils.getItemTranslationKey(TMMItems.BODY_BAG) + ".tooltip", "Use on a dead body to bag it up and remove it\nSingle use, 5 minute cooldown");
        builder.add(TextUtils.getItemTranslationKey(TMMItems.BLACKOUT) + ".tooltip", "Turn off all lights aboard for 15 to 20 seconds\nUse your instinct [left-alt] to see your targets in the dark\nActivated instantly on purchase, 5 minute cooldown");
        builder.add(TextUtils.getItemTranslationKey(TMMItems.NOTE) + ".tooltip", "Write a message and pin it for others to see\nSneak-use to write a message, then use on a wall or floor to place\nInvisible in hand");

        builder.add("game.win.killers", "The killers reached their kill count, they win!");
        builder.add("game.win.passengers", "All killers were eliminated: the passengers win!");
        builder.add("game.win.time", "The killers ran out of time: the passengers win!");
        builder.add("game.win.loose_end", "They tied all of their loose ends!");

        builder.add("key.starrailexpress.instinct", "Instinct");
        builder.add("key.starrailexpress.stats", "统计面板");
        builder.add("screen.starrailexpress.player_stats.title", "玩家统计数据");
        builder.add("screen.starrailexpress.player_stats.general_stats", "通用统计");
        builder.add("screen.starrailexpress.player_stats.total_play_time", "总游玩时长: %s");
        builder.add("screen.starrailexpress.player_stats.total_games_played", "总游玩局数: %d");
        builder.add("screen.starrailexpress.player_stats.total_kills", "总击杀玩家次数: %d");
        builder.add("screen.starrailexpress.player_stats.total_deaths", "总死亡次数: %d");
        builder.add("screen.starrailexpress.player_stats.total_wins", "总胜利次数: %d");
        builder.add("screen.starrailexpress.player_stats.total_losses", "总失败次数: %d");
        builder.add("screen.starrailexpress.player_stats.kd_ratio", "击杀/死亡比: %.2f");
        builder.add("screen.starrailexpress.player_stats.win_rate", "总胜率: %.2f%%");
        builder.add("screen.starrailexpress.player_stats.role_stats", "角色特定统计");
        builder.add("screen.starrailexpress.player_stats.role_name", "角色: %s");
        builder.add("screen.starrailexpress.player_stats.role_times_played", "使用次数: %d");
        builder.add("screen.starrailexpress.player_stats.role_wins", "胜利次数: %d");
        builder.add("screen.starrailexpress.player_stats.role_losses", "失败次数: %d");
        builder.add("screen.starrailexpress.player_stats.role_kd_ratio", "K/D比: %.2f");
        builder.add("screen.starrailexpress.player_stats.role_win_rate", "胜率: %.2f%%");
        builder.add("screen.starrailexpress.player_stats.time.days_hours_minutes", "%d天 %d小时 %d分钟");
        builder.add("screen.starrailexpress.player_stats.time.hours_minutes", "%d小时 %d分钟");
        builder.add("screen.starrailexpress.player_stats.time.minutes_seconds", "%d分钟 %d秒");
        builder.add("screen.starrailexpress.player_stats.time.seconds", "%d秒");
        builder.add("category.starrailexpress.keybinds", "Train Murder Mystery");

        // Player Stats GUI translations
        builder.add("gui.sre.stats.title", "Player Statistics");
        builder.add("gui.sre.stats.total_play_time", "Total Play Time: %s");
        builder.add("gui.sre.stats.total_games_played", "Total Games Played: %s");
        builder.add("gui.sre.stats.total_kills", "Total Kills: %s");
        builder.add("gui.sre.stats.total_deaths", "Total Deaths: %s");
        builder.add("gui.sre.stats.total_wins", "Total Wins: %s");
        builder.add("gui.sre.stats.total_losses", "Total Losses: %s");
        builder.add("gui.sre.stats.kd_ratio", "K/D Ratio: %s");
        builder.add("gui.sre.stats.win_rate", "Win Rate: %s");
        builder.add("gui.sre.stats.role_stats_header", "Role Statistics");
        builder.add("gui.sre.stats.role_name", "Role: %s");
        builder.add("gui.sre.stats.times_played", "Times Played: %s");
        builder.add("gui.sre.stats.kills_as_role", "Kills as Role: %s");
        builder.add("gui.sre.stats.deaths_as_role", "Deaths as Role: %s");
        builder.add("gui.sre.stats.wins_as_role", "Wins as Role: %s");
        builder.add("gui.sre.stats.losses_as_role", "Losses as Role: %s");
        builder.add("gui.sre.stats.role_win_rate", "Win Rate: %s");
        builder.add("gui.sre.stats.role_kd_ratio", "K/D Ratio: %s");

        builder.add("task.feel", "You feel like ");
        builder.add("task.fake", "You could fake ");
        builder.add("task.sleep", "getting some sleep.");
        builder.add("task.outside", "getting some fresh air.");
        builder.add("task.drink", "getting a drink.");
        builder.add("task.eat", "getting a snack.");
        builder.add("game.player.stung", "You feel something stinging you in your sleep.");
        builder.add("game.psycho_mode.time", "Psycho Mode: %s");
        builder.add("game.psycho_mode.text", "Kill them all!");
        builder.add("game.psycho_mode.over", "Psycho Mode Over!");
        builder.add("game.tip.cohort", "Killer Cohort");
        builder.add("game.start_error.not_enough_players", "Game cannot start: %s players minimum are required.");
        builder.add("game.start_error.game_running", "Game cannot start: a game is already running. Please try again from the lobby.");

        builder.add("sre.gui.reset", "Clear");

        builder.add("commands.supporter_only", "Super silly supporter commands are reserved for Ko-Fi and YouTube members; if you wanna try them out, please consider supporting! <3");

        // 配置翻译
        builder.add("starrailexpress.midnightconfig.title", "The Last Voyage of the Harpy Express - Config");
        builder.add("starrailexpress.midnightconfig.ultraPerfMode", "Ultra Performance Mode");
        builder.add("starrailexpress.midnightconfig.ultraPerfMode.tooltip", "Disables scenery for a worse visual experience but maximum performance. Lowers render distance to 2.");
        builder.add("starrailexpress.midnightconfig.disableScreenShake", "Disable Screen Shake");

        // 配置分类
        builder.add("starrailexpress.midnightconfig.category.shop", "Shop");
        builder.add("starrailexpress.midnightconfig.category.cooldowns", "Cooldowns");
        builder.add("starrailexpress.midnightconfig.category.game", "Game");
        builder.add("starrailexpress.midnightconfig.category.client", "Client");

        builder.add("starrailexpress.midnightconfig.clientConfigComment", "Client");
        // 商店价格配置
        builder.add("starrailexpress.midnightconfig.shopPricesComment", "Shop Item Prices");
        builder.add("starrailexpress.midnightconfig.knifePrice", "Knife Price");
        builder.add("starrailexpress.midnightconfig.revolverPrice", "Revolver Price");
        builder.add("starrailexpress.midnightconfig.grenadePrice", "Grenade Price");
        builder.add("starrailexpress.midnightconfig.psychoModePrice", "Psycho Mode Price");
        builder.add("starrailexpress.midnightconfig.poisonVialPrice", "Poison Vial Price");
        builder.add("starrailexpress.midnightconfig.scorpionPrice", "Scorpion Price");
        builder.add("starrailexpress.midnightconfig.firecrackerPrice", "Firecracker Price");
        builder.add("starrailexpress.midnightconfig.lockpickPrice", "Lockpick Price");
        builder.add("starrailexpress.midnightconfig.crowbarPrice", "Crowbar Price");
        builder.add("starrailexpress.midnightconfig.bodyBagPrice", "Body Bag Price");
        builder.add("starrailexpress.midnightconfig.blackoutPrice", "Blackout Price");
        builder.add("starrailexpress.midnightconfig.notePrice", "Note Price");

        // 冷却时间配置
        builder.add("starrailexpress.midnightconfig.cooldownsComment", "Item Cooldowns (seconds)");
        builder.add("starrailexpress.midnightconfig.knifeCooldown", "Knife Cooldown");
        builder.add("starrailexpress.midnightconfig.revolverCooldown", "Revolver Cooldown");
        builder.add("starrailexpress.midnightconfig.derringerCooldown", "Derringer Cooldown");
        builder.add("starrailexpress.midnightconfig.grenadeCooldown", "Grenade Cooldown");
        builder.add("starrailexpress.midnightconfig.lockpickCooldown", "Lockpick Cooldown");
        builder.add("starrailexpress.midnightconfig.crowbarCooldown", "Crowbar Cooldown");
        builder.add("starrailexpress.midnightconfig.bodyBagCooldown", "Body Bag Cooldown");
        builder.add("starrailexpress.midnightconfig.psychoModeCooldown", "Psycho Mode Cooldown");
        builder.add("starrailexpress.midnightconfig.blackoutCooldown", "Blackout Cooldown");

        // 游戏配置
        builder.add("starrailexpress.midnightconfig.gameConfigComment", "Game Configuration");
        builder.add("starrailexpress.midnightconfig.startingMoney", "Starting Money");
        builder.add("starrailexpress.midnightconfig.passiveMoneyAmount", "Passive Money Amount");
        builder.add("starrailexpress.midnightconfig.passiveMoneyInterval", "Passive Money Interval");
        builder.add("starrailexpress.midnightconfig.moneyPerKill", "Money Per Kill");
        builder.add("starrailexpress.midnightconfig.psychoModeArmor", "Psycho Mode Armor");
        builder.add("starrailexpress.midnightconfig.psychoModeDuration", "Psycho Mode Duration");
        builder.add("starrailexpress.midnightconfig.firecrackerDuration", "Firecracker Duration");
        builder.add("starrailexpress.midnightconfig.blackoutMinDuration", "Blackout Min Duration");
        builder.add("starrailexpress.midnightconfig.blackoutMaxDuration", "Blackout Max Duration");

        // GUI 相关翻译
        builder.add("credits.starrailexpress.thank_you", "Thank you for playing The Last Voyage of the Harpy Express!\nMe and my team spent a lot of time working\non this mod and we hope you enjoy it.\nIf you do and wish to make a video or stream\nplease make sure to credit my channel,\nvideo and the mod page!\n - RAT / doctor4t");
        builder.add("I should write something first", "I should write something first");
        builder.add("Purchase Failed", "Purchase Failed");
        builder.add("Edit Note", "Edit Note");
        builder.add("Server is reserved to doctor4t supporters.", "Server is reserved to doctor4t supporters.");
        builder.add("Role Weights:", "Role Weights:");

        // 命令系统翻译
        builder.add("commands.sre.start", "Game started successfully!");
        builder.add("commands.sre.stop", "Game stopped successfully!");
        builder.add("commands.sre.forcerole", "Role set to %s for player %s");
        builder.add("commands.sre.forcerole.multiple", "Role set to %s for %s players");
        builder.add("commands.sre.setrolecount", "Role count set: %s = %s");
        builder.add("commands.sre.enableweights.enabled", "Weight system enabled");
        builder.add("commands.sre.enableweights.disabled", "Weight system disabled");
        builder.add("commands.sre.checkweights.header", "=== Player Weights ===");
        builder.add("commands.sre.checkweights.entry", "%s: Killer=%s, Vigilante=%s");
        builder.add("commands.sre.resetweights", "All weights have been reset");
        builder.add("commands.sre.setmoney", "Set %s's money to $%s");
        builder.add("commands.sre.setmoney.multiple", "Set money to $%s for %s players");
        builder.add("commands.sre.setmood", "Set %s's mood to %.2f");
        builder.add("commands.sre.setmood.multiple", "Set mood to %.2f for %s players");
        builder.add("commands.sre.settimer", "Game timer set to %s minutes");
        builder.add("commands.sre.givekey", "Gave key for room %s to %s");
        builder.add("commands.sre.givekey.multiple", "Gave key for room %s to %s players");
        builder.add("commands.sre.setbound.enabled", "Boundary restriction enabled");
        builder.add("commands.sre.setbound.disabled", "Boundary restriction disabled");
        builder.add("commands.sre.autostart.enabled", "Auto-start enabled");
        builder.add("commands.sre.autostart.disabled", "Auto-start disabled");
        builder.add("commands.sre.locktosupporters.enabled", "Server locked to supporters only");
        builder.add("commands.sre.locktosupporters.disabled", "Server unlocked");
        builder.add("commands.sre.config.reload", "✓ Configuration reloaded successfully");
        builder.add("commands.sre.config.reload.fail", "✗ Configuration reload failed: %s");
        builder.add("commands.sre.config.reset", "✓ Configuration reset to defaults");
        builder.add("commands.sre.config.reset.fail", "✗ Configuration reset failed: %s");
        builder.add("commands.sre.setvisual.snow", "Snow visual effect set to %s");
        builder.add("commands.sre.setvisual.fog", "Fog visual effect set to %s");
        builder.add("commands.sre.setvisual.hud", "HUD visual effect set to %s");
        builder.add("commands.sre.setvisual.trainspeed", "Train speed visual effect set to %s");
        builder.add("commands.sre.setvisual.time", "Time of day visual effect set to %s");
        builder.add("commands.sre.setvisual.reset", "Visual effects reset to defaults");
        builder.add("commands.sre.updatedoors", "Train doors updated successfully");

        // Additional command translations
        builder.add("commands.sre.setrolecount.error.too_many_killers", "Cannot set %s killers: only %s players online");
        builder.add("commands.sre.setrolecount.too_many_vigilantes", "Cannot set %s vigilantes: only %s players online");
        builder.add("commands.sre.setrolecount.killer", "Killer count set to %s");
        builder.add("commands.sre.setrolecount.vigilante", "Vigilante count set to %s");

        builder.add("commands.sre.giveroomkey", "Gave key for room %s");

        builder.add("commands.sre.forcerole.killer", "Forced %s to be killer");
        builder.add("commands.sre.forcerole.killer.multiple", "Forced %s players to be killers");
        builder.add("commands.sre.forcerole.vigilante", "Forced %s to be vigilante");
        builder.add("commands.sre.forcerole.vigilante.multiple", "Forced %s players to be vigilantes");

        // Config command display translations
        builder.add("commands.sre.config.show.header", "=== TMM Configuration ===");
        builder.add("commands.sre.config.show.shop_prices.header", "Shop Prices:");
        builder.add("commands.sre.config.show.shop_prices.knife", "  Knife: $%s");
        builder.add("commands.sre.config.show.shop_prices.revolver", "  Revolver: $%s");
        builder.add("commands.sre.config.show.shop_prices.grenade", "  Grenade: $%s");
        builder.add("commands.sre.config.show.shop_prices.psycho_mode", "  Psycho Mode: $%s");
        builder.add("commands.sre.config.show.shop_prices.poison_vial", "  Poison Vial: $%s");
        builder.add("commands.sre.config.show.shop_prices.scorpion", "  Scorpion: $%s");
        builder.add("commands.sre.config.show.shop_prices.firecracker", "  Firecracker: $%s");
        builder.add("commands.sre.config.show.shop_prices.lockpick", "  Lockpick: $%s");
        builder.add("commands.sre.config.show.shop_prices.crowbar", "  Crowbar: $%s");
        builder.add("commands.sre.config.show.shop_prices.body_bag", "  Body Bag: $%s");
        builder.add("commands.sre.config.show.shop_prices.blackout", "  Blackout: $%s");
        builder.add("commands.sre.config.show.shop_prices.note", "  Note: $%s");

        builder.add("commands.sre.config.show.cooldowns.header", "Item Cooldowns (seconds):");
        builder.add("commands.sre.config.show.cooldowns.knife", "  Knife: %ss");
        builder.add("commands.sre.config.show.cooldowns.revolver", "  Revolver: %ss");
        builder.add("commands.sre.config.show.cooldowns.derringer", "  Derringer: %ss");
        builder.add("commands.sre.config.show.cooldowns.grenade", "  Grenade: %ss");
        builder.add("commands.sre.config.show.cooldowns.lockpick", "  Lockpick: %ss");
        builder.add("commands.sre.config.show.cooldowns.crowbar", "  Crowbar: %ss");
        builder.add("commands.sre.config.show.cooldowns.body_bag", "  Body Bag: %ss");
        builder.add("commands.sre.config.show.cooldowns.psycho_mode", "  Psycho Mode: %ss");
        builder.add("commands.sre.config.show.cooldowns.blackout", "  Blackout: %ss");

        builder.add("commands.sre.config.show.game_settings.header", "Game Settings:");
        builder.add("commands.sre.config.show.game_settings.starting_money", "  Starting Money: $%s");
        builder.add("commands.sre.config.show.game_settings.passive_money_amount", "  Passive Money Amount: $%s");
        builder.add("commands.sre.config.show.game_settings.passive_money_interval", "  Passive Money Interval: %ss");
        builder.add("commands.sre.config.show.game_settings.money_per_kill", "  Money Per Kill: $%s");
        builder.add("commands.sre.config.show.game_settings.psycho_mode_armor", "  Psycho Mode Armor: %s");
        builder.add("commands.sre.config.show.game_settings.psycho_mode_duration", "  Psycho Mode Duration: %ss");
        builder.add("commands.sre.config.show.game_settings.firecracker_duration", "  Firecracker Duration: %ss");
        builder.add("commands.sre.config.show.game_settings.blackout_min_duration", "  Blackout Min Duration: %ss");
        builder.add("commands.sre.config.show.game_settings.blackout_max_duration", "  Blackout Max Duration: %ss");

        builder.add("commands.sre.config.show.footer", "==========================");
        builder.add("commands.sre.config.show.hint", "Use '/tmm:config reload' to reload or '/tmm:config reset' to reset to defaults");

        // Replay event translations
        builder.add("sre.replay.event.kill", "%s killed %s with %s");
        builder.add("sre.replay.event.poison", "%s poisoned %s with %s");
        builder.add("sre.replay.event.game_start", "Game started");
        builder.add("sre.replay.event.game_end", "Game ended. Winners: %s");
        builder.add("sre.replay.event.role_assignment", "%s was assigned the role: %s");
        builder.add("sre.replay.event.item_use", "%s used %s");
        builder.add("sre.replay.event.player_join", "%s joined the game");
        builder.add("sre.replay.event.player_leave", "%s left the game");
        
        // Replay command translations
        builder.add("sre.replay.error.no_manager", "Replay system not available");
        builder.add("sre.replay.error.no_data", "No replay data available");
        builder.add("sre.replay.header", "Game Replay");
        builder.add("sre.replay.player_count", "Total players: %s");
        builder.add("sre.replay.loose_ends", "Loose Ends: %s");
        builder.add("sre.replay.winning_team", "Winning team: %s");
        builder.add("sre.replay.timeline", "Game Timeline");
        builder.add("sre.replay.footer", "End of Replay");

    }
}
