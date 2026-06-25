package org.agmas.noellesroles.scene;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.agmas.noellesroles.init.NRSounds;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * 场景方块的瞬态运行状态管理器（按维度划分，回合内有效，不持久化）。
 *
 * <p>提供两类能力：
 * <ul>
 *   <li><b>驻留计时</b>：方块每 tick 调用 {@link #reportDwell}，自动处理“多个方块同 tick 报告同一玩家不重复计数”
 *       与“离开区域（出现 tick 间隙）后重置”。用于有毒区域(20s)、井盖窒息(10s)等。</li>
 *   <li><b>破坏任务/sabotage 状态</b>：场景大门、喷火装置、反应堆、滚石等在破坏任务激活时改变行为。</li>
 * </ul>
 */
public final class SceneEventManager {
    private SceneEventManager() {
    }

    // ───────────────────────── 驻留计时 ─────────────────────────

    private static final class Dwell {
        int ticks;
        long lastTick = Long.MIN_VALUE;
    }

    private static final Map<ResourceKey<Level>, Map<String, Dwell>> DWELL = new HashMap<>();

    private static String dwellKey(String channel, Player player) {
        return channel + "|" + player.getUUID();
    }

    /**
     * 报告玩家在某通道（区域类型）内停留了一 tick，返回累计连续停留的 tick 数。
     * 同一 tick 多次报告（多个方块）不会重复计数；出现 ≥2 tick 间隙视为离开并重置。
     */
    public static int reportDwell(ServerLevel level, Player player, String channel) {
        long now = level.getGameTime();
        Map<String, Dwell> map = DWELL.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        Dwell d = map.computeIfAbsent(dwellKey(channel, player), k -> new Dwell());
        if (d.lastTick == now) {
            // 本 tick 已计数，跳过
        } else if (d.lastTick == now - 1) {
            d.ticks++;
        } else {
            d.ticks = 1;
        }
        d.lastTick = now;
        return d.ticks;
    }

    /** 主动重置某玩家在某通道的驻留计时。 */
    public static void resetDwell(ServerLevel level, Player player, String channel) {
        Map<String, Dwell> map = DWELL.get(level.dimension());
        if (map != null) {
            map.remove(dwellKey(channel, player));
        }
    }

    /** 查询当前连续停留 tick 数（若上一 tick 未报告则视为 0）。 */
    public static int getDwell(ServerLevel level, Player player, String channel) {
        Map<String, Dwell> map = DWELL.get(level.dimension());
        if (map == null) {
            return 0;
        }
        Dwell d = map.get(dwellKey(channel, player));
        if (d == null) {
            return 0;
        }
        return (d.lastTick >= level.getGameTime() - 1) ? d.ticks : 0;
    }

    // ───────────────────────── 破坏任务 / sabotage 状态 ─────────────────────────

    private static final Map<ResourceKey<Level>, Long> SABOTAGE_UNTIL = new HashMap<>();
    /** 记录已在超时后处理过的维度，防止重复扣除心情 */
    private static final Set<ResourceKey<Level>> SABOTAGE_TIMEOUT_HANDLED = new HashSet<>();

    /** 警报音效防重复：记录每个维度上次播放的 tick */
    private static final Map<ResourceKey<Level>, Long> ALARM_LAST_TICK = new HashMap<>();
    /** 警报间隔（tick），约 2 秒播放一次 */
    private static final long ALARM_INTERVAL = 40;

    /** 开启破坏任务状态。durationTicks < 0 表示持续到手动停止。 */
    public static void startSabotage(ServerLevel level, int durationTicks) {
        long until = durationTicks < 0 ? Long.MAX_VALUE : level.getGameTime() + durationTicks;
        SABOTAGE_UNTIL.put(level.dimension(), until);
    }

    public static void stopSabotage(ServerLevel level) {
        SABOTAGE_UNTIL.remove(level.dimension());
        SABOTAGE_TIMEOUT_HANDLED.remove(level.dimension());  // 手动停止时清除超时标记
        ALARM_LAST_TICK.remove(level.dimension());
    }

    public static boolean isSabotageActive(ServerLevel level) {
        Long until = SABOTAGE_UNTIL.get(level.dimension());
        return until != null && level.getGameTime() < until;
    }

    /** 破坏任务剩余 tick 数（不限时返回一个很大的值，未激活返回 0）。 */
    public static long sabotageRemaining(ServerLevel level) {
        Long until = SABOTAGE_UNTIL.get(level.dimension());
        if (until == null) {
            return 0;
        }
        return Math.max(0, until - level.getGameTime());
    }

    /**
     * 检查破坏任务是否已自然超时。若超时且尚未处理过，扣除所有玩家心情并返回 true。
     * 应由破坏任务相关 BlockEntity 的 serverTick 每 tick 调用。
     */
    public static boolean checkAndHandleSabotageTimeout(ServerLevel level) {
        ResourceKey<Level> dim = level.dimension();
        Long until = SABOTAGE_UNTIL.get(dim);
        // 无破坏任务、任务仍激活、或已处理过超时 → 跳过
        if (until == null || level.getGameTime() < until || SABOTAGE_TIMEOUT_HANDLED.contains(dim)) {
            return false;
        }
        // 标记已处理，防止重复
        SABOTAGE_TIMEOUT_HANDLED.add(dim);
        SABOTAGE_UNTIL.remove(dim);
        ALARM_LAST_TICK.remove(dim);

        // 扣除所有在线玩家 0.5 心情值（假心情不受影响，最低降至 0，旁观者除外）
        for (var player : level.players()) {
            if (player.isSpectator()) continue;
            var mood = player.getComponent(io.wifi.starrailexpress.cca.SREPlayerMoodComponent.KEY);
            if (mood != null) {
                mood.addMood(-0.5f);
            }
            var gameWorld = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(level);
            var role = gameWorld.getRole(player);
            boolean shouldCooldown = !gameWorld.isKillerTeam(player)
                    || gameWorld.isRole(player, org.agmas.noellesroles.role.ModRoles.MAGICIAN);
            if (shouldCooldown && role != null) {
                applyHotbarCooldown(player, 15 * 20);
            }
        }
        return true;
    }

    private static void applyHotbarCooldown(net.minecraft.server.level.ServerPlayer player, int ticks) {
        var cooldowns = player.getCooldowns();
        for (int slot = 0; slot < 9; slot++) {
            var stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            var item = stack.getItem();
            var current = cooldowns.cooldowns.get(item);
            if (current != null && current.endTime - cooldowns.tickCount > ticks) {
                continue;
            }
            cooldowns.addCooldown(item, ticks);
        }
    }

    /**
     * 破坏任务激活时循环播放 BROKEN_ALARM 警报音效（约每 2 秒一次）。
     * 应在任意一个破坏任务相关 BlockEntity 的 serverTick 中调用。
     */
    public static void tickSabotageAlarm(ServerLevel level) {
        if (!isSabotageActive(level)) return;

        long now = level.getGameTime();
        Long last = ALARM_LAST_TICK.get(level.dimension());
        if (last != null && now - last < ALARM_INTERVAL) return;

        ALARM_LAST_TICK.put(level.dimension(), now);
        if (!level.players().isEmpty()) {
            var player = level.players().get(0);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    NRSounds.BROKEN_ALARM, SoundSource.MASTER, 0.8F, 1.0F);
        }
    }

    /** 清空指定维度的所有瞬态状态（回合重置时可调用）。 */
    public static void clear(ServerLevel level) {
        DWELL.remove(level.dimension());
        SABOTAGE_UNTIL.remove(level.dimension());
        SABOTAGE_TIMEOUT_HANDLED.remove(level.dimension());
        ALARM_LAST_TICK.remove(level.dimension());
    }
}
