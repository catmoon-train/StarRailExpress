package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * san 归零变怪物：
 * <ul>
 *   <li>目睹他人死亡（范围内）→ 损失 san。</li>
 *   <li>san 归零 → 倒计时 {@link SixtySecondsBalance#MONSTER_DELAY_TICKS} 后变怪；san 回升 &gt;0 则取消。</li>
 *   <li>变怪：发光 + 力量 + 速度（可见威胁），被枪一击即死（{@code SixtySecondsHealthSystem}）。</li>
 *   <li>自我解脱：在变怪倒计时中可选择牺牲（不变怪），换取队伍安全。</li>
 * </ul>
 */
public final class SixtySecondsMonsterSystem {
    private SixtySecondsMonsterSystem() {
    }

    public static void registerEvents() {
        OnPlayerDeath.EVENT.register((victim, deathReason) -> {
            if (!SixtySecondsMod.isActive(victim.level()) || !(victim.level() instanceof ServerLevel level)) {
                return;
            }
            for (ServerPlayer other : level.players()) {
                if (other == victim || GameUtils.isPlayerEliminated(other)) {
                    continue;
                }
                if (other.distanceToSqr(victim) <= SixtySecondsBalance.DEATH_SAN_RANGE_SQR) {
                    SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(other);
                    if (stats.sanity > 0) {
                        stats.sanity = Math.max(0, stats.sanity - SixtySecondsBalance.SAN_LOSS_ON_DEATH);
                        stats.sync();
                    }
                }
            }
        });
    }

    public static void tick(ServerLevel level) {
        long now = level.getGameTime();
        for (ServerPlayer player : level.players()) {
            if (GameUtils.isPlayerEliminated(player)) {
                continue;
            }
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
            if (stats.monster) {
                applyMonsterEffects(player);
                continue;
            }
            if (stats.downed) {
                continue;
            }
            if (stats.sanity <= 0) {
                if (stats.sanZeroTick == 0) {
                    stats.sanZeroTick = now;
                    stats.sync();
                    player.displayClientMessage(
                            Component.translatable("message.noellesroles.sixty_seconds.monster_warning",
                                    SixtySecondsBalance.MONSTER_DELAY_TICKS / 20).withStyle(ChatFormatting.DARK_RED),
                            false);
                } else if (now - stats.sanZeroTick >= SixtySecondsBalance.MONSTER_DELAY_TICKS) {
                    transform(level, player, stats);
                }
            } else if (stats.sanZeroTick != 0) {
                stats.sanZeroTick = 0;
                stats.sync();
            }
        }
    }

    private static void transform(ServerLevel level, ServerPlayer player, SixtySecondsStatsComponent stats) {
        stats.monster = true;
        stats.sanZeroTick = 0;
        stats.health = 1; // 怪物血量为 1，被枪一击即死（匹配设计文档：发光+力量+速度的可见威胁，脆皮高伤）
        stats.sync();
        applyMonsterEffects(player);
        // 立即将玩家的原版生命值扣至 1，确保客户端与服务端状态一致，怪物可被一击击杀
        player.setHealth(1.0F);
        broadcast(level, Component.translatable("message.noellesroles.sixty_seconds.monster_transform",
                player.getGameProfile().getName()).withStyle(ChatFormatting.DARK_RED));
    }

    /** 给玩家施加怪物视觉/移动效果（公开供妹妹变异等外部调用）。 */
    public static void applyMonsterEffects(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, 0, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 0, false, false, false));
    }

    /** 自我解脱：仅在 san 归零变怪倒计时中可用；牺牲（不变怪）。 */
    public static boolean sacrifice(ServerPlayer player) {
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
        if (stats.monster || stats.sanZeroTick == 0) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.sixty_seconds.sacrifice_unavailable"), true);
            return false;
        }
        broadcast(player.serverLevel(), Component.translatable("message.noellesroles.sixty_seconds.sacrifice",
                player.getGameProfile().getName()).withStyle(ChatFormatting.GOLD));
        SixtySecondsHealthSystem.die(player, null);
        return true;
    }

    private static void broadcast(ServerLevel level, Component message) {
        for (ServerPlayer player : level.players()) {
            player.displayClientMessage(message, false);
        }
    }
}
