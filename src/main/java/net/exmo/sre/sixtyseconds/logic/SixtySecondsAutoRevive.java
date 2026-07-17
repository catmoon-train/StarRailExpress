package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.arena.SixtySecondsSearchZones;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore;
import net.exmo.sre.sixtyseconds.network.SixtySecondsCorpseMarkS2CPacket;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 自动复活（按图开关 {@code autoReviveEnabled}，默认<b>开</b>；间隔 {@code autoReviveIntervalSeconds}，默认 4 分钟）。
 * <ul>
 *   <li><b>死亡</b>（{@code SixtySecondsHealthSystem.die}）：登记复活时刻到
 *       {@link SixtySecondsStatsComponent#reviveEndTick}（同步一次，客户端 HUD 自己倒数），
 *       并在死亡处给<b>本人</b>打一个尸体标记（区域地图上的暗红点）。</li>
 *   <li><b>到期</b>（{@link #tick}）：在<b>本队避难所</b>复活（{@code GameUtils.revivePlayer}），
 *       清尸体标记，状态值恢复到 {@link SixtySecondsBalance#AUTO_REVIVE_STAT_PERCENT}。</li>
 * </ul>
 *
 * <p><b>尸体不清</b>：装着遗物的尸体箱（{@code SixtySecondsCorpseLoot}）照旧留在原地——
 * 标记的意义就是让你自己跑回去捡，也给别人留了先一步捡走的机会。复活在避难所而非原地，
 * 「跑尸」这段路就是死亡的代价。
 *
 * <p><b>与胜负判定的关系</b>：开启时「等待复活」计入<b>未阵亡</b>
 * （{@link #anyPendingRevive}，被 {@code SixtySecondsWinConditions.anySurvivorAlive} 采纳），
 * 因此一波团灭不会直接判负；胜负仍由「撑到最后一天 / 救援信标 / 幸存者阵营」决定。
 */
public final class SixtySecondsAutoRevive {

    /** 每世界：玩家 → 尸体坐标（复活时据此清标记）。 */
    private static final Map<ServerLevel, Map<UUID, BlockPos>> CORPSES = new WeakHashMap<>();

    private SixtySecondsAutoRevive() {
    }

    /** 按图配置：自动复活是否开启（默认开）。 */
    public static boolean enabled(ServerLevel level) {
        return SixtySecondsConfigStore.current(level)
                .map(config -> config.autoReviveEnabled)
                .orElse(true);
    }

    /** 按图配置：复活间隔（tick）。 */
    public static int intervalTicks(ServerLevel level) {
        int seconds = SixtySecondsConfigStore.current(level)
                .map(config -> config.autoReviveIntervalSeconds)
                .orElse(SixtySecondsBalance.AUTO_REVIVE_DEFAULT_SECONDS);
        return Math.max(1, seconds) * 20;
    }

    /**
     * 死亡钩子（{@code SixtySecondsHealthSystem.die} 在 forceKillPlayer 之后调）：
     * 登记复活时刻 + 打尸体标记。关掉开关时什么也不做——死了就是死了。
     *
     * @param corpsePos 死亡处坐标（须在 forceKillPlayer <b>之前</b>取，那之后玩家已转旁观）
     */
    public static void onDeath(ServerPlayer victim, BlockPos corpsePos) {
        ServerLevel level = victim.serverLevel();
        if (!enabled(level)) {
            return;
        }
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(victim);
        // 变怪的玩家不给复活：它已经不是幸存者了，复活等于把怪拉回队里
        if (stats.monster) {
            return;
        }
        long now = level.getGameTime();
        stats.reviveEndTick = now + intervalTicks(level);
        stats.sync();
        CORPSES.computeIfAbsent(level, ignored -> new HashMap<>())
                .put(victim.getUUID(), corpsePos.immutable());
        SixtySecondsCorpseMarkS2CPacket.mark(victim, corpsePos);
        victim.displayClientMessage(Component.translatable(
                "message.noellesroles.sixty_seconds.revive_pending",
                intervalTicks(level) / 20, corpsePos.getX(), corpsePos.getY(), corpsePos.getZ())
                .withStyle(ChatFormatting.GRAY), false);
    }

    /** DAY 相位每 tick 调（内部 20 tick 一次）：到期的玩家复活。 */
    public static void tick(ServerLevel level) {
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        long now = level.getGameTime();
        for (ServerPlayer player : level.players()) {
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
            if (stats.reviveEndTick <= 0L) {
                continue;
            }
            // 开关中途关掉：把在等的人的倒计时作废（不复活，也不让 HUD 一直挂着）
            if (!enabled(level)) {
                stats.reviveEndTick = 0L;
                stats.sync();
                clearCorpseMark(level, player);
                continue;
            }
            if (now >= stats.reviveEndTick) {
                revive(level, player, stats);
            }
        }
    }

    /** 在本队避难所复活：清倒计时与尸体标记，状态值恢复到半血，解除探索区/生病等残留状态。 */
    private static void revive(ServerLevel level, ServerPlayer player, SixtySecondsStatsComponent stats) {
        SixtySecondsState.TeamData team = SixtySecondsState.get(level).teams.get(stats.teamId);
        BlockPos home = team != null ? team.shelterSpawn : null;
        if (home == null) {
            // 没有队伍/避难所信息（散人、配置不全）：让倒计时空转到下一秒再试，
            // 而不是把人复活到 (0,0,0) 那种地方
            return;
        }
        BlockPos safe = SixtySecondsSearchZones.findSafeSpot(level, home);
        GameUtils.revivePlayer(player, safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D);

        stats.reviveEndTick = 0L;
        int revived = (int) (SixtySecondsStatsComponent.MAX * SixtySecondsBalance.AUTO_REVIVE_STAT_PERCENT);
        stats.health = revived;
        stats.hunger = revived;
        stats.thirst = revived;
        // 理智以「本局已被杀人代价永久压低的上限」为顶，别把上限惩罚洗掉
        stats.sanity = Math.min(stats.sanityMax,
                (int) (stats.sanityMax * SixtySecondsBalance.AUTO_REVIVE_STAT_PERCENT));
        stats.pollution = 0;
        stats.downed = false;
        stats.downedFromInjury = false;
        stats.bleedOutEndTick = 0L;
        stats.sanZeroTick = 0L;
        stats.sync();
        // 生病走既有治愈路径（它自己会清感染计时并同步）
        SixtySecondsSicknessSystem.cure(player);
        // 死在探索区/海岛的：复活已经把人拉回家了，清掉「在外」状态，免得还被限制在那片盒子里
        SixtySecondsSearchZones.clearReturnEntry(player);

        clearCorpseMark(level, player);
        level.playSound(null, safe, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.7F, 1.2F);
        player.displayClientMessage(Component.translatable(
                "message.noellesroles.sixty_seconds.revive_done").withStyle(ChatFormatting.GREEN), false);
    }

    /** 清掉该玩家的尸体标记（客户端地图 + 服务端记录）。 */
    private static void clearCorpseMark(ServerLevel level, ServerPlayer player) {
        Map<UUID, BlockPos> corpses = CORPSES.get(level);
        BlockPos pos = corpses == null ? null : corpses.remove(player.getUUID());
        if (pos != null) {
            SixtySecondsCorpseMarkS2CPacket.clear(player, pos);
        }
    }

    /**
     * 该玩家是否在等待自动复活。{@code SixtySecondsWinConditions} 用它把「待复活」算成未阵亡——
     * 否则开着自动复活时一波团灭会直接判负，复活也就没意义了。
     */
    public static boolean isPendingRevive(ServerPlayer player) {
        return SixtySecondsStatsComponent.KEY.get(player).reviveEndTick > 0L;
    }

    /** 全场是否还有人在等复活（全灭判定的豁免条件）。 */
    public static boolean anyPendingRevive(ServerLevel level) {
        if (!enabled(level)) {
            return false;
        }
        for (ServerPlayer player : level.players()) {
            if (isPendingRevive(player) && !SixtySecondsStatsComponent.KEY.get(player).monster) {
                return true;
            }
        }
        return false;
    }

    /** 局末清理（{@code SixtySecondsGameMode.stopGame}）：清尸体记录 + 抹掉所有人的复活倒计时。 */
    public static void reset(ServerLevel level) {
        CORPSES.remove(level);
        for (ServerPlayer player : level.players()) {
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
            if (stats.reviveEndTick > 0L) {
                stats.reviveEndTick = 0L;
                stats.sync();
            }
            SixtySecondsCorpseMarkS2CPacket.clear(player, BlockPos.ZERO);
        }
    }
}
