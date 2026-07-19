package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.compat.TrainVoicePlugin;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.component.FamilyPosition;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 掉线备份/重连恢复系统：游戏进行中玩家掉线时自动快照其<b>背包 + 全部 60s 状态</b>；
 * 若其在<b>本局结束前</b>重连，恢复背包与状态并重新入队（队伍成员表本就保留 UUID）。
 * <p>
 * 需要它的原因：{@code SixtySecondsStatsComponent} 是局内状态、刻意不落盘（重登即重置），
 * 掉线重连会丢失 队伍/身份/五值/倒地 等一切。备份表按局清空（{@code begin}/{@code stopGame}），
 * 不会把上一局的快照恢复到下一局。管理员也可用 {@code /sre:60s backup} 手动 保存/恢复/查看。
 */
public final class SixtySecondsReconnect {
    private static final Map<UUID, Snapshot> BACKUPS = new HashMap<>();

    private SixtySecondsReconnect() {
    }

    /** 一名玩家的完整局内快照：背包 NBT + 组件全部字段（含纯服务端字段）。 */
    private static final class Snapshot {
        ListTag inventory;
        int hunger;
        int thirst;
        int sanity;
        int sanityMax = 100;
        int pollution;
        int health;
        int teamId;
        FamilyPosition familyPosition;
        boolean sick;
        boolean downed;
        boolean monster;
        int downedCountToday;
        boolean downedFromInjury;
        long bleedOutEndTick;
        long exploreCooldownEndTick;
        boolean recovering;
        long sanZeroTick;
        long reviveEndTick;
    }

    /** 模组初始化时注册一次。 */
    public static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            if (SixtySecondsMod.RUNNING && SixtySecondsMod.isActive(player.level())) {
                save(player);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            if (SixtySecondsMod.RUNNING && SixtySecondsMod.isActive(player.level())
                    && BACKUPS.containsKey(player.getUUID())) {
                // 推迟一 tick：等玩家完全初始化（背包/组件就绪）再恢复
                server.execute(() -> {
                    ServerPlayer online = server.getPlayerList().getPlayer(player.getUUID());
                    if (online != null && SixtySecondsMod.RUNNING) {
                        restore(online);
                    }
                });
            }
        });
    }

    /** 快照玩家背包与全部 60s 状态（重复调用覆盖旧快照）。仅对已入队玩家有效。 */
    public static boolean save(ServerPlayer player) {
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
        if (stats.teamId < 0) {
            return false; // 未入队（旁观等）无需备份
        }
        Snapshot s = new Snapshot();
        s.inventory = player.getInventory().save(new ListTag());
        s.hunger = stats.hunger;
        s.thirst = stats.thirst;
        s.sanity = stats.sanity;
        s.sanityMax = stats.sanityMax;
        s.pollution = stats.pollution;
        s.health = stats.health;
        s.teamId = stats.teamId;
        s.familyPosition = stats.familyPosition;
        s.sick = stats.sick;
        s.downed = stats.downed;
        s.monster = stats.monster;
        s.downedCountToday = stats.downedCountToday;
        s.downedFromInjury = stats.downedFromInjury;
        s.bleedOutEndTick = stats.bleedOutEndTick;
        s.exploreCooldownEndTick = stats.exploreCooldownEndTick;
        s.recovering = stats.recovering;
        s.sanZeroTick = stats.sanZeroTick;
        s.reviveEndTick = stats.reviveEndTick;
        BACKUPS.put(player.getUUID(), s);
        return true;
    }

    /** 恢复背包与状态（快照消耗掉）；dayNumber/phaseEndTick 用当前世界值刷新（离线期间可能换日）。 */
    public static boolean restore(ServerPlayer player) {
        Snapshot s = BACKUPS.remove(player.getUUID());
        if (s == null) {
            return false;
        }
        player.getInventory().load(s.inventory);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();

        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
        stats.hunger = s.hunger;
        stats.thirst = s.thirst;
        stats.sanity = s.sanity;
        stats.sanityMax = s.sanityMax;
        stats.pollution = s.pollution;
        stats.health = s.health;
        stats.teamId = s.teamId;
        stats.familyPosition = s.familyPosition;
        stats.sick = s.sick;
        stats.downed = s.downed;
        stats.monster = s.monster;
        stats.downedCountToday = s.downedCountToday;
        stats.downedFromInjury = s.downedFromInjury;
        stats.bleedOutEndTick = s.bleedOutEndTick;
        stats.exploreCooldownEndTick = s.exploreCooldownEndTick;
        stats.recovering = s.recovering;
        stats.sanZeroTick = s.sanZeroTick;
        stats.reviveEndTick = s.reviveEndTick;
        // 换日相关用当前值刷新，避免离线期间过期
        if (player.level() instanceof ServerLevel level) {
            SixtySecondsState.Data data = SixtySecondsState.get(level);
            stats.dayNumber = data.dayNumber;
            stats.totalDays = SixtySecondsManager.totalDays(level); // HUD「第 X/N 天」的 N（可配置）
            stats.phaseEndTick = data.phaseEndTick;
            // 重发区域地图范围（客户端断线时已清空）
            SixtySecondsState.TeamData team = data.teams.get(stats.teamId);
            if (team != null) {
                net.exmo.sre.sixtyseconds.network.SixtySecondsMapZoneS2CPacket.send(
                        player, team.shelterBox, team.shelterSpawn, true);
            }

            // 重连死亡状态处理：
            // 1. 已死亡等待复活（reviveEndTick > 0）：保持旁观者，等待自动复活到期
            // 2. 存活时掉线（reviveEndTick == 0）：视为死亡惩罚，进入旁观并等待自动复活
            if (stats.reviveEndTick > 0L) {
                player.setGameMode(GameType.SPECTATOR);
            } else if (!stats.monster) {
                // 怪物身份的玩家已经死了（不算幸存者），不需要再判死
                player.setGameMode(GameType.SPECTATOR);
                TrainVoicePlugin.addPlayer(player.getUUID());
                if (SixtySecondsAutoRevive.enabled(level)) {
                    stats.reviveEndTick = level.getGameTime() + SixtySecondsAutoRevive.intervalTicks(level);
                }
            }
        }
        stats.sync();
        player.displayClientMessage(Component.translatable(
                "message.noellesroles.sixty_seconds.reconnect_restored").withStyle(ChatFormatting.GREEN), false);
        return true;
    }

    public static boolean hasBackup(UUID uuid) {
        return BACKUPS.containsKey(uuid);
    }

    public static int backupCount() {
        return BACKUPS.size();
    }

    /** 换局清空：上一局的快照不得泄漏到下一局。 */
    public static void reset() {
        BACKUPS.clear();
    }
}
