package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.SixtySecondsPhase;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 末日60秒的健康/倒地/死亡模型：
 * <ul>
 *   <li>所有受伤统一造成 {@link #INJURY_DAMAGE} 点健康值伤害（拦截模组击杀路径与原版伤害）。</li>
 *   <li>受伤致健康归零 → 首次倒地；同日第二次倒地 → 直接死亡；对倒地者再受伤 → 处决。</li>
 *   <li>非受伤致健康归零（饥渴等）→ 直接死亡（不进倒地）。见 {@link #onHealthZero}。</li>
 *   <li>倒地 {@link #BLEED_OUT_TICKS} 后流血而死；队友近身累计 {@link #REVIVE_TICKS} 救起。</li>
 * </ul>
 * 参照 {@code repair} 的倒地/救援与 {@code GameMode.killPlayer} 事件门。
 */
public final class SixtySecondsHealthSystem {
    public static final int INJURY_DAMAGE = 50;
    public static final int BLEED_OUT_TICKS = 20 * 150;   // 倒地 2.5 分钟流血死
    public static final int REVIVE_TICKS = 20 * 15;       // 队友近身 15s 救起
    private static final double REVIVE_RANGE_SQR = 3.0 * 3.0;
    private static final ResourceLocation DEATH_REASON = Noellesroles.id("sixty_seconds_death");

    /** 倒地玩家 UUID → 已累计救援 tick。 */
    private static final Map<UUID, Integer> REVIVE_PROGRESS = new HashMap<>();

    private SixtySecondsHealthSystem() {
    }

    public static void register() {
        // 只在带 killer 的事件里拦截（总在 AllowPlayerDeath 之后触发，可拿到击杀者）
        AllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> handleLethal(victim, killer));
        // 原版环境伤害（坠落/火/生物等）改为健康值伤害
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer player && SixtySecondsMod.isActive(player.level())
                    && GameUtils.isPlayerAliveAndSurvival(player)) {
                ServerPlayer attacker = source.getEntity() instanceof ServerPlayer sp ? sp : null;
                // 早晨/准备阶段：玩家对玩家攻击无效，且不转成健康伤害（怪物攻击不受限）
                if (attacker != null && !SixtySecondsStatsComponent.KEY.get(attacker).monster
                        && player.level() instanceof ServerLevel serverLevel && isPvpBlocked(serverLevel)) {
                    attacker.displayClientMessage(
                            Component.translatable("message.noellesroles.sixty_seconds.pvp_blocked"), true);
                    return false;
                }
                applyInjury(player, attacker);
                return false;
            }
            return true;
        });
    }

    private static boolean handleLethal(Player victim, Player killer) {
        if (!SixtySecondsMod.isActive(victim.level()) || !(victim instanceof ServerPlayer player)) {
            return true;
        }
        ServerPlayer serverKiller = killer instanceof ServerPlayer sk ? sk : null;
        // 早晨/准备阶段：玩家对玩家攻击无效（环境伤害仍生效；怪物攻击不受限）
        if (serverKiller != null && !SixtySecondsStatsComponent.KEY.get(serverKiller).monster
                && victim.level() instanceof ServerLevel serverLevel && isPvpBlocked(serverLevel)) {
            serverKiller.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.pvp_blocked"), true);
            return false;
        }
        applyInjury(player, serverKiller);
        return false; // 否决模组默认死亡，改由本系统接管
    }

    /** 准备阶段 / 每日早晨（前 {@link SixtySecondsBalance#MORNING_TICKS}）禁止玩家互相攻击。 */
    public static boolean isPvpBlocked(ServerLevel level) {
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        if (data.phase != SixtySecondsPhase.DAY) {
            return true; // 准备/结算阶段一律禁 PvP
        }
        long dayStart = data.phaseEndTick - SixtySecondsManager.DAY_TICKS;
        return level.getGameTime() - dayStart < SixtySecondsBalance.MORNING_TICKS;
    }

    /** 一次受伤：扣 50 健康；对倒地者受伤=处决。 */
    public static void applyInjury(ServerPlayer victim, @Nullable ServerPlayer attacker) {
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(victim);
        if (stats.monster) {
            die(victim, attacker); // 怪物被击即死
            return;
        }
        if (stats.downed) {
            die(victim, attacker);
            return;
        }
        stats.health = Math.max(0, stats.health - INJURY_DAMAGE);
        stats.sync();
        if (stats.health <= 0) {
            onHealthZero(victim, true, attacker);
        }
    }

    /** 健康归零处理。fromInjury=false（饥渴等）直接死亡。 */
    public static void onHealthZero(ServerPlayer victim, boolean fromInjury, @Nullable ServerPlayer attacker) {
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(victim);
        if (!fromInjury) {
            die(victim, null);
            return;
        }
        if (stats.downedCountToday >= 1) {
            die(victim, attacker);
            return;
        }
        setDowned(victim, stats);
    }

    private static void setDowned(ServerPlayer victim, SixtySecondsStatsComponent stats) {
        stats.downed = true;
        stats.downedFromInjury = true;
        stats.downedCountToday++;
        stats.health = 0;
        stats.bleedOutEndTick = victim.serverLevel().getGameTime() + BLEED_OUT_TICKS;
        stats.sync();
        REVIVE_PROGRESS.remove(victim.getUUID());
        victim.displayClientMessage(Component.translatable("message.noellesroles.sixty_seconds.downed"), false);
    }

    public static void die(ServerPlayer victim, @Nullable ServerPlayer killer) {
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(victim);
        stats.downed = false;
        stats.bleedOutEndTick = 0L;
        stats.sync();
        REVIVE_PROGRESS.remove(victim.getUUID());
        GameUtils.forceKillPlayer(victim, true, killer, DEATH_REASON);
    }

    public static void tick(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
            if (!stats.downed) {
                continue;
            }
            // 倒地定身
            player.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, 40, 0, false, false, false));
            if (level.getGameTime() >= stats.bleedOutEndTick) {
                die(player, null);
                continue;
            }
            tickRevive(level, player, stats);
        }
    }

    private static void tickRevive(ServerLevel level, ServerPlayer downed, SixtySecondsStatsComponent stats) {
        boolean rescuerNear = false;
        for (ServerPlayer other : level.players()) {
            if (other == downed || GameUtils.isPlayerEliminated(other)) {
                continue;
            }
            SixtySecondsStatsComponent otherStats = SixtySecondsStatsComponent.KEY.get(other);
            if (otherStats.downed || stats.teamId < 0 || otherStats.teamId != stats.teamId) {
                continue;
            }
            if (other.distanceToSqr(downed) <= REVIVE_RANGE_SQR) {
                rescuerNear = true;
                break;
            }
        }
        UUID id = downed.getUUID();
        if (rescuerNear) {
            int progress = REVIVE_PROGRESS.merge(id, 1, Integer::sum);
            if (progress % 20 == 0) {
                downed.displayClientMessage(Component.translatable("message.noellesroles.sixty_seconds.reviving",
                        progress / 20, REVIVE_TICKS / 20), true);
            }
            if (progress >= REVIVE_TICKS) {
                revive(downed, stats);
            }
        } else {
            REVIVE_PROGRESS.remove(id);
        }
    }

    /** 救起：清倒地，所有状态值 ×0.33，附缓慢。感染风险/生病判定交由 sickness 系统（P1 TODO）。 */
    public static void revive(ServerPlayer player, SixtySecondsStatsComponent stats) {
        stats.downed = false;
        stats.downedFromInjury = false;
        stats.bleedOutEndTick = 0L;
        stats.health = Math.max(1, (int) (SixtySecondsStatsComponent.MAX * 0.33));
        stats.hunger = (int) (stats.hunger * 0.33);
        stats.thirst = (int) (stats.thirst * 0.33);
        stats.sanity = (int) (stats.sanity * 0.33);
        // 未使用医疗包 → 感染风险：每 2 分钟 33% 生病（SixtySecondsSicknessSystem）；吃药/医疗包 cure() 可解除。
        stats.recovering = true;
        stats.sync();
        REVIVE_PROGRESS.remove(player.getUUID());
        player.removeEffect(ModEffects.MOVE_BANED);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 20, 1, false, false, true));
        player.displayClientMessage(Component.translatable("message.noellesroles.sixty_seconds.revived"), false);
    }

    public static void reset() {
        REVIVE_PROGRESS.clear();
    }
}
