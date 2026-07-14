package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;

import java.util.HashSet;
import java.util.List;

/**
 * 末日60秒开局前的世界与玩家准备（仿 {@code RepairGameSetup}）。
 * <p>
 * <b>不走通用 {@code GameUtils.baseInitialize}</b>——它会按「列车房间」发钥匙/信封并把玩家传送到
 * {@code getSpawnPos(areas, room)}；60s 专用图没有列车房间出生点，fallback 会把玩家平移进<b>虚空</b>。
 * 这里只做必要的世界/玩家重置，<b>不传送任何参战玩家</b>——他们留在原地等异步建图完成，
 * 由 {@code SixtySecondsManager.onBuildComplete} 统一传送到各队住宅（模板建完才真正开始游戏）。
 */
public final class SixtySecondsGameSetup {
    private SixtySecondsGameSetup() {
    }

    public static void prepareWorld(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        gameWorldComponent.setPlayerCount(players.size());
        applyGameRules(serverWorld);
        applyMapEnvironment(serverWorld);
        serverWorld.getServer().setDifficulty(Difficulty.PEACEFUL, true);

        // 参战玩家统一冒险模式，原地待命（建图完成后再传送）
        for (ServerPlayer player : players) {
            player.removeVehicle();
            player.setGameMode(GameType.ADVENTURE);
        }

        // 非参战玩家旁观并送到旁观观察位（与 baseInitialize 一致）
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(serverWorld);
        for (ServerPlayer player : serverWorld.getServer().getPlayerList().getPlayers()) {
            if (players.contains(player)) {
                continue;
            }
            player.setGameMode(GameType.SPECTATOR);
            AreasWorldComponent.PosWithOrientation spectatorSpawn = areas.getSpectatorSpawnPos();
            if (spectatorSpawn != null) {
                player.teleportTo(serverWorld, spectatorSpawn.pos.x(), spectatorSpawn.pos.y(),
                        spectatorSpawn.pos.z(), spectatorSpawn.yaw, spectatorSpawn.pitch);
            }
        }

        // 清空背包、局内组件（金币/代币/情绪/动态价格）与物品冷却
        for (ServerPlayer player : players) {
            player.getInventory().clearContent();
            SREPlayerMoodComponent.KEY.get(player).init();
            SREPlayerShopComponent.KEY.get(player).init();
            io.wifi.starrailexpress.cca.DynamicShopComponent.KEY.get(player).init();
            io.wifi.starrailexpress.cca.SREPlayerMinigameTaskComponent.KEY.get(player).init();
            HashSet<Item> copy = new HashSet<>(player.getCooldowns().cooldowns.keySet());
            for (Item item : copy) {
                player.getCooldowns().removeCooldown(item);
            }
        }
        gameWorldComponent.clearRoleMap(true);
        SREGameTimeComponent.KEY.get(serverWorld).reset();
    }

    private static void applyGameRules(ServerLevel serverWorld) {
        var server = serverWorld.getServer();
        serverWorld.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
        serverWorld.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, server);
        serverWorld.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        serverWorld.getGameRules().getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, server);
        serverWorld.getGameRules().getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(false, server);
        serverWorld.getGameRules().getRule(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE).set(9999, server);
    }

    private static void applyMapEnvironment(ServerLevel serverWorld) {
        var areas = AreasWorldComponent.KEY.get(serverWorld);
        serverWorld.setDayTime(areas.areasSettings.time);
        serverWorld.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(areas.areasSettings.weatherCycle,
                serverWorld.getServer());
        switch (areas.areasSettings.weather) {
            case rain -> serverWorld.setWeatherParameters(0, 120000, true, false);
            case thunder -> serverWorld.setWeatherParameters(0, 120000, true, true);
            default -> serverWorld.setWeatherParameters(120000, 0, false, false);
        }
    }
}
