package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.compat.TrainVoicePlugin;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 自动复活（按图开关 {@code autoReviveEnabled}，默认<b>开</b>；间隔 {@code autoReviveIntervalSeconds}，默认 4 分钟；
 * 次数上限 {@code autoReviveMaxUses}，默认 -1=无限）。
 * <ul>
 *   <li><b>死亡</b>（{@code SixtySecondsHealthSystem.die}）：先检查是否已达本局次数上限——达到则不登记复活，
 *       玩家直接出局。否则登记复活时刻到 {@link SixtySecondsStatsComponent#reviveEndTick}（同步一次，
 *       客户端 HUD 自己倒数），记录尸体坐标与实体 UUID（用于复活后清理尸体实体，避免重复丢弃物品），
 *       并在死亡处给<b>本人</b>打一个尸体标记（区域地图上的暗红点）。</li>
 *   <li><b>到期</b>（{@link #tick}）：在<b>本队避难所</b>复活（{@code GameUtils.revivePlayer}），
 *       <b>物品不返还</b>——复活后玩家背包为空、尸体实体被 discard（其物品随之销毁），
 *       状态值恢复到 {@link SixtySecondsBalance#AUTO_REVIVE_STAT_PERCENT}，{@code reviveCount} +1。</li>
 * </ul>
 *
 * <p><b>尸体物资不继承</b>：复活时<b>不</b>从尸体背包恢复任何物品——玩家以空背包复活，
 * 尸体实体（若区块仍加载）也会被销毁、物品随之消失，避免物品复制或重复。死亡处的尸体在等待复活期间
 * 仍可被队友拾取/搜刮（普通尸体箱交互），但复活时刻一到即清理。
 *
 * <p><b>复活语音组</b>：{@code GameUtils.revivePlayer} 已内置清除
 * {@code DeathPenaltyComponent}（死亡语音隔离）和 {@code TrainVoicePlugin}
 * （旁观语音组）；复活后玩家回到正常近距离语音。
 *
 * <p><b>与胜负判定的关系</b>：开启时「等待复活」计入<b>未阵亡</b>
 * （{@link #anyPendingRevive}，被 {@code SixtySecondsWinConditions.anySurvivorAlive} 采纳），
 * 因此一波团灭不会直接判负；胜负仍由「撑到最后一天 / 救援信标 / 幸存者阵营」决定。
 */
public final class SixtySecondsAutoRevive {

    /** 尸体记录：坐标 + 实体 UUID（用于复活后销毁尸体实体，避免重复丢弃物品）。不再保存背包快照——复活不返还物品。 */
    private record CorpseRecord(BlockPos pos, UUID entityUuid) {}

    /** 每世界：玩家 UUID → 尸体记录（复活后清理）。 */
    private static final Map<ServerLevel, Map<UUID, CorpseRecord>> CORPSES = new WeakHashMap<>();

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
     * 按图配置：本局自动复活次数上限（-1=无限，0=禁用复活，正数=最多 N 次）。
     * 默认 -1（无限）保持旧行为。
     */
    public static int maxUses(ServerLevel level) {
        return SixtySecondsConfigStore.current(level)
                .map(config -> config.autoReviveMaxUses)
                .orElse(-1);
    }

    /** 玩家本局已用复活次数是否已达上限（{@code maxUses=-1} 视为无限）。供重连等外部调用判定。 */
    public static boolean reachedLimitPublic(ServerLevel level, SixtySecondsStatsComponent stats) {
        return reachedLimit(level, stats);
    }

    /** 玩家本局已用复活次数是否已达上限（{@code maxUses=-1} 视为无限）。 */
    private static boolean reachedLimit(ServerLevel level, SixtySecondsStatsComponent stats) {
        int max = maxUses(level);
        if (max < 0) {
            return false;
        }
        return stats.reviveCount >= max;
    }

    /**
     * 死亡钩子（{@code SixtySecondsHealthSystem.die} 在 forceKillPlayer 之后调）：
     * 先检查本局次数上限——达到则不登记复活（玩家直接出局）；否则登记复活时刻 + 打尸体标记 +
     * 记录尸体实体 UUID（复活时销毁尸体实体）。
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
        // 次数上限检查：已达上限则不登记复活，玩家直接出局（不进等待、不打尸体标记、不进全灭豁免）
        if (reachedLimit(level, stats)) {
            int max = maxUses(level);
            victim.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.autorevive_limit_reached",
                    stats.reviveCount, max < 0 ? "∞" : String.valueOf(max))
                    .withStyle(ChatFormatting.RED), false);
            return;
        }
        long now = level.getGameTime();
        stats.reviveEndTick = now + intervalTicks(level);
        stats.sync();

        // 查找尸体实体，仅记录实体 UUID（用于复活时销毁尸体实体，避免重复丢弃物品；不再保存背包快照）
        UUID entityUuid = null;
        AABB searchBox = new AABB(corpsePos).inflate(5);
        for (PlayerBodyEntity body : level.getEntitiesOfClass(PlayerBodyEntity.class, searchBox)) {
            if (victim.getUUID().equals(body.getPlayerUuid())) {
                entityUuid = body.getUUID();
                break;
            }
        }

        CorpseRecord record = new CorpseRecord(corpsePos.immutable(), entityUuid);
        CORPSES.computeIfAbsent(level, ignored -> new HashMap<>())
                .put(victim.getUUID(), record);
        SixtySecondsCorpseMarkS2CPacket.mark(victim, corpsePos);
        // 提示文案同时显示剩余次数（无限时不显示）
        int max = maxUses(level);
        net.minecraft.network.chat.MutableComponent pending = max < 0
                ? Component.translatable(
                        "message.noellesroles.sixty_seconds.revive_pending",
                        intervalTicks(level) / 20, corpsePos.getX(), corpsePos.getY(), corpsePos.getZ())
                : Component.translatable(
                        "message.noellesroles.sixty_seconds.revive_pending_with_remaining",
                        intervalTicks(level) / 20, corpsePos.getX(), corpsePos.getY(), corpsePos.getZ(),
                        Math.max(0, max - stats.reviveCount));
        victim.displayClientMessage(pending.withStyle(ChatFormatting.GRAY), false);
    }

    /** DAY 相位每 tick 调（内部 20 tick 一次）：到期的玩家复活；每 5 秒清理误留在死亡语音组的非旁观玩家。 */
    public static void tick(ServerLevel level) {
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        long now = level.getGameTime();

        // 每 5 秒（100 tick）清理：非旁观者不应留在死亡语音组
        if (now % 100 == 0) {
            for (ServerPlayer player : level.players()) {
                if (!player.isSpectator() && TrainVoicePlugin.isPlayerInGroup(player.getUUID())) {
                    TrainVoicePlugin.resetPlayer(player.getUUID());
                }
            }
        }

        for (ServerPlayer player : level.players()) {
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
            if (stats.reviveEndTick <= 0L) {
                continue;
            }
            // 开关中途关掉，或玩家已在等待时管理员把次数上限压到当前已用次数以下：
            // 把倒计时作废（不复活，也不让 HUD 一直挂着）
            if (!enabled(level) || reachedLimit(level, stats)) {
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

    /**
     * 在本队避难所复活：<b>物品不返还</b>（玩家以空背包复活）、销毁尸体实体（其物品随之消失）、
     * 清倒计时与尸体标记、状态值恢复到半血、{@code reviveCount} +1。
     */
    private static void revive(ServerLevel level, ServerPlayer player, SixtySecondsStatsComponent stats) {
        SixtySecondsState.TeamData team = SixtySecondsState.get(level).teams.get(stats.teamId);
        BlockPos home = team != null ? team.shelterSpawn : null;
        if (home == null) {
            // 没有队伍/避难所信息（散人、配置不全）：让倒计时空转到下一秒再试，
            // 而不是把人复活到 (0,0,0) 那种地方
            return;
        }
        BlockPos safe = SixtySecondsSearchZones.findSafeSpot(level, home);

        // 从记录中取出尸体实体 UUID（在 revivePlayer 之前取——revivePlayer 会改游戏模式）
        Map<UUID, CorpseRecord> corpses = CORPSES.get(level);
        CorpseRecord record = corpses != null ? corpses.remove(player.getUUID()) : null;

        // revivePlayer 内置：清除 DeathPenaltyComponent（死亡语音隔离）
        // + TrainVoicePlugin.resetPlayer（离开旁观语音组）→ 复活后回到正常近距离语音
        GameUtils.revivePlayer(player, safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D);

        // 物品不返还：复活后玩家背包为空（revivePlayer 不动背包，这里显式清空）
        player.getInventory().clearContent();

        // 销毁尸体实体（其物品随之消失，避免重复丢弃/被复制）；区块已卸载则跳过——
        // 反正玩家身上已无物品，尸体实体也找不到，物品不会回流到玩家。
        if (record != null && record.entityUuid() != null) {
            Entity e = level.getEntity(record.entityUuid());
            if (e instanceof PlayerBodyEntity body) {
                body.getComponent().getCorpseInventory().clearContent();
                body.discard();
            }
        }

        stats.reviveEndTick = 0L;
        // 计入本局已用次数（无限上限时也累加，供 HUD/查询显示）
        stats.reviveCount++;
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
        // 复活完成提示同时显示剩余次数（无限时不显示）
        int max = maxUses(level);
        net.minecraft.network.chat.MutableComponent done = max < 0
                ? Component.translatable("message.noellesroles.sixty_seconds.revive_done")
                : Component.translatable("message.noellesroles.sixty_seconds.revive_done_with_remaining",
                        Math.max(0, max - stats.reviveCount));
        player.displayClientMessage(done.withStyle(ChatFormatting.GREEN), false);
    }

    /** 清掉该玩家的尸体标记（客户端地图 + 服务端记录）。 */
    private static void clearCorpseMark(ServerLevel level, ServerPlayer player) {
        Map<UUID, CorpseRecord> corpses = CORPSES.get(level);
        CorpseRecord record = corpses == null ? null : corpses.remove(player.getUUID());
        if (record != null) {
            SixtySecondsCorpseMarkS2CPacket.clear(player, record.pos());
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

    /** 局末清理（{@code SixtySecondsGameMode.stopGame}）：清尸体记录 + 抹掉所有人的复活倒计时与已用次数。 */
    public static void reset(ServerLevel level) {
        CORPSES.remove(level);
        for (ServerPlayer player : level.players()) {
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
            boolean dirty = false;
            if (stats.reviveEndTick > 0L) {
                stats.reviveEndTick = 0L;
                dirty = true;
            }
            if (stats.reviveCount != 0) {
                stats.reviveCount = 0;
                dirty = true;
            }
            if (dirty) {
                stats.sync();
            }
            SixtySecondsCorpseMarkS2CPacket.clear(player, BlockPos.ZERO);
        }
    }
}
