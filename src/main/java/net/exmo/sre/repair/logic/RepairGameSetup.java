package net.exmo.sre.repair.logic;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.exmo.sre.repair.arena.RepairArenaBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;

import java.util.HashSet;
import java.util.List;

/**
 * 修机模式开局前的世界与玩家准备。
 * 不走通用 baseInitialize，避免加载普通模式的 Areas 配置。
 */
public final class RepairGameSetup {
    private RepairGameSetup() {
    }

    public static void prepareWorld(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        gameWorldComponent.setPlayerCount(players.size());
        applyGameRules(serverWorld);
        applyMapEnvironment(serverWorld);
        serverWorld.getServer().setDifficulty(Difficulty.PEACEFUL, true);

        // 参战玩家统一冒险模式
        for (ServerPlayer player : players) {
            player.removeVehicle();
            player.setGameMode(GameType.ADVENTURE);
        }

        // 非参战玩家旁观并传送到庄园上方观察位
        BlockPos manorBase = RepairArenaBuilder.defaultMansionBase(serverWorld);
        for (ServerPlayer player : serverWorld.getServer().getPlayerList().getPlayers()) {
            if (players.contains(player))
                continue;
            player.setGameMode(GameType.SPECTATOR);
            player.teleportTo(serverWorld,
                    manorBase.getX() + 32.5D, manorBase.getY() + 26.0D, manorBase.getZ() + 56.5D,
                    player.getYRot(), player.getXRot());
        }

        // 清空背包、情绪/商店组件与物品冷却
        for (ServerPlayer player : players) {
            player.getInventory().clearContent();
            SREPlayerMoodComponent.KEY.get(player).init();
            SREPlayerShopComponent.KEY.get(player).init();
            HashSet<Item> copy = new HashSet<>(player.getCooldowns().cooldowns.keySet());
            for (Item item : copy)
                player.getCooldowns().removeCooldown(item);
        }
        gameWorldComponent.clearRoleMap(true);
        SREGameTimeComponent.KEY.get(serverWorld).reset();
    }

    private static void applyGameRules(ServerLevel serverWorld) {
        var server = serverWorld.getServer();
        serverWorld.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
        serverWorld.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        serverWorld.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, server);
        serverWorld.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        serverWorld.getGameRules().getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, server);
        serverWorld.getGameRules().getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(false, server);
    }

    private static void applyMapEnvironment(ServerLevel serverWorld) {
        var areas = AreasWorldComponent.KEY.get(serverWorld);
        serverWorld.setDayTime(areas.areasSettings.time);
        serverWorld.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(areas.areasSettings.weatherCycle,
                serverWorld.getServer());
        switch (areas.areasSettings.weather) {
            case rain:
                serverWorld.setWeatherParameters(0, 120000, true, false);
                break;
            case thunder:
                serverWorld.setWeatherParameters(0, 120000, true, true);
                break;
            default: // clear
                serverWorld.setWeatherParameters(120000, 0, false, false);
                break;
        }
    }
}
