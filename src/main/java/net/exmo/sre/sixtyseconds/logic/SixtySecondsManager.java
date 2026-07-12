package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.SixtySecondsPhase;
import net.exmo.sre.sixtyseconds.arena.SixtySecondsArena;
import net.exmo.sre.sixtyseconds.arena.SixtySecondsSearchZones;
import net.exmo.sre.sixtyseconds.component.FamilyPosition;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.agmas.noellesroles.Noellesroles;

import java.util.List;
import java.util.UUID;

/**
 * 末日60秒模式相位机的编排核心：家庭分队 → 按队克隆建图 → 60s 准备 → 7 个游戏日 → 结算。
 */
public final class SixtySecondsManager {
    public static final int PREP_TICKS = 20 * 60;          // 60s 准备
    public static final int DAY_TICKS = 20 * 60 * 8;       // 每游戏日 8 分钟
    public static final int TOTAL_DAYS = 7;
    public static final int COINS_PER_DAY = 80;            // 每天 80 金币
    public static final int COINS_PER_MINUTE = 3;          // 每分钟 3 金币

    private SixtySecondsManager() {
    }

    /** 开局：分队、建图、传住宅、进 PREPARATION。 */
    public static void begin(ServerLevel level, SREGameWorldComponent gameWorldComponent, List<ServerPlayer> players) {
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        data.teams.clear();
        net.exmo.sre.sixtyseconds.SixtySecondsMod.RUNNING = true;
        SixtySecondsHealthSystem.reset();
        SixtySecondsVisitSystem.reset();
        SixtySecondsVisitChat.reset();
        SixtySecondsTrade.reset();
        SixtySecondsEventSystem.reset(level);
        SixtySecondsMinigameRotation.reset(level);
        SixtySecondsDoorHighlight.reset(level);
        assignFamilies(level, data, players);
        SixtySecondsArena.build(level, data, SixtySecondsConfigStore.current(level).orElse(null));
        teleportTeams(level, data, true);
        data.phase = SixtySecondsPhase.PREPARATION;
        data.dayNumber = 0;
        data.phaseEndTick = level.getGameTime() + PREP_TICKS;
        broadcast(level, Component.translatable("message.noellesroles.sixty_seconds.prep_start"));
    }

    public static void tick(ServerLevel level, SREGameWorldComponent gameWorldComponent) {
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        SixtySecondsSearchZones.tick(level);
        SixtySecondsHealthSystem.tick(level);
        SixtySecondsInventoryLimit.tick(level);
        switch (data.phase) {
            case PREPARATION -> {
                SixtySecondsDoorHighlight.tick(level);
                if (level.getGameTime() >= data.phaseEndTick) {
                    startDay(level, data, 1);
                }
            }
            case DAY -> {
                SixtySecondsStatsSystem.tick(level);
                SixtySecondsSicknessSystem.tick(level);
                SixtySecondsMonsterSystem.tick(level);
                SixtySecondsSleepSystem.tick(level);
                SixtySecondsVisitSystem.tick(level);
                SixtySecondsEventSystem.tick(level);
                SixtySecondsMinigameRotation.tick(level);
                tickMinuteCoins(level, data);
                SixtySecondsWinConditions.tick(level, data); // 无存活幸存者→提前结束
                // 若已被 WinConditions 提前结束(相位变 FINISHED)则跳过换日/结算
                if (data.phase == SixtySecondsPhase.DAY && level.getGameTime() >= data.phaseEndTick) {
                    if (data.dayNumber >= TOTAL_DAYS) {
                        finish(level, data);
                    } else {
                        startDay(level, data, data.dayNumber + 1);
                    }
                }
            }
            default -> {
            }
        }
    }

    private static void startDay(ServerLevel level, SixtySecondsState.Data data, int day) {
        boolean firstDay = day == 1;
        data.phase = SixtySecondsPhase.DAY;
        data.dayNumber = day;
        data.phaseEndTick = level.getGameTime() + DAY_TICKS;
        if (firstDay) {
            teleportTeams(level, data, false);
            placeSupplyChests(level, data);
        }
        syncDayNumber(level, data, day);
        dailyPlayerUpdates(level, data);
        // 物资箱每日刷新为惰性：交互时按当前 dayNumber 自动重掷（见 SupplyBoxBlockEntity）。
        SixtySecondsRoleAwakening.awaken(level, data);
        broadcast(level, Component.translatable("message.noellesroles.sixty_seconds.day_start", day, TOTAL_DAYS));
    }

    private static void finish(ServerLevel level, SixtySecondsState.Data data) {
        // 第7天结束：有存活幸存者→幸存者胜，否则败（详见 SixtySecondsWinConditions）。
        SixtySecondsWinConditions.finish(level, data);
        Noellesroles.LOGGER.info("[60s] 第七天结束，游戏结算。");
    }

    /** 每 4 人一队，依次分配 父/母/妹/哥 身份。 */
    private static void assignFamilies(ServerLevel level, SixtySecondsState.Data data, List<ServerPlayer> players) {
        int teamId = 0;
        SixtySecondsState.TeamData current = null;
        int posInTeam = 0;
        for (int i = 0; i < players.size(); i++) {
            if (i % 4 == 0) {
                current = new SixtySecondsState.TeamData(teamId);
                data.teams.put(teamId, current);
                teamId++;
                posInTeam = 0;
            }
            ServerPlayer player = players.get(i);
            current.members.add(player.getUUID());
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
            stats.init();
            stats.teamId = current.teamId;
            stats.familyPosition = FamilyPosition.byIndex(posInTeam);
            stats.sync();
            posInTeam++;
        }
    }

    /** 换日：重置本日倒地次数，发放每日 80 金币。 */
    private static void dailyPlayerUpdates(ServerLevel level, SixtySecondsState.Data data) {
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            for (UUID uuid : team.members) {
                if (level.getPlayerByUUID(uuid) instanceof ServerPlayer player) {
                    SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
                    stats.downedCountToday = 0;
                    stats.sync();
                    SREPlayerShopComponent.KEY.get(player).addToBalance(COINS_PER_DAY);
                }
            }
        }
    }

    /** 每分钟给存活玩家发放 3 金币。 */
    private static void tickMinuteCoins(ServerLevel level, SixtySecondsState.Data data) {
        if (level.getGameTime() % (20 * 60) != 0) {
            return;
        }
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            for (UUID uuid : team.members) {
                if (level.getPlayerByUUID(uuid) instanceof ServerPlayer player
                        && GameUtils.isPlayerAliveAndSurvival(player)) {
                    SREPlayerShopComponent.KEY.get(player).addToBalance(COINS_PER_MINUTE);
                }
            }
        }
    }

    /** 准备结束：在各队避难所出生点旁放置箱子，装入本队记录的物资。 */
    private static void placeSupplyChests(ServerLevel level, SixtySecondsState.Data data) {
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            if (team.shelterSpawn == null || team.storedSupplies.isEmpty()) {
                continue;
            }
            BlockPos chestPos = team.shelterSpawn.offset(1, 0, 0);
            level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
                int i = 0;
                for (ItemStack stack : team.storedSupplies) {
                    if (i >= chest.getContainerSize()) {
                        break;
                    }
                    chest.setItem(i++, stack.copy());
                }
            }
        }
    }

    /** 换日时把 dayNumber 同步给各队成员，供客户端 HUD 显示（每日一次，低频）。 */
    private static void syncDayNumber(ServerLevel level, SixtySecondsState.Data data, int day) {
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            for (UUID uuid : team.members) {
                if (level.getPlayerByUUID(uuid) instanceof ServerPlayer player) {
                    SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
                    stats.dayNumber = day;
                    stats.sync();
                }
            }
        }
    }

    private static void teleportTeams(ServerLevel level, SixtySecondsState.Data data, boolean residential) {
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            BlockPos spawn = residential ? team.residentialSpawn : team.shelterSpawn;
            if (spawn == null) {
                continue;
            }
            for (UUID uuid : team.members) {
                if (level.getPlayerByUUID(uuid) instanceof ServerPlayer player) {
                    player.teleportTo(level, spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                            player.getYRot(), player.getXRot());
                }
            }
        }
    }

    private static void broadcast(ServerLevel level, Component message) {
        for (ServerPlayer player : level.players()) {
            player.displayClientMessage(message, false);
        }
    }
}
