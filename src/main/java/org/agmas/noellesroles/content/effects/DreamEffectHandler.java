package org.agmas.noellesroles.content.effects;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.agmas.noellesroles.init.ModEffects;

/**
 * 入梦药水效果的处理逻辑。
 *
 * <p>
 * 拥有「入梦」的玩家一旦进入睡眠，就会：
 * <ul>
 * <li>恢复 {@link #SANITY_RESTORE_PER_LEVEL} × 等级 的理智（按理智上限比例）；</li>
 * <li>获得迅捷（等级 = 入梦等级，I 级入梦 → 迅捷 I）；</li>
 * <li>清除身上的入梦效果（一次性，触发一次后消失）。</li>
 * </ul>
 *
 * <p>
 * 之所以不在 {@link net.minecraft.world.effect.MobEffect#applyEffectTick} 里做这些事，是因为
 * 在原版 {@code LivingEntity#tickEffects()} 迭代效果表的过程中增删效果会抛
 * {@link java.util.ConcurrentModificationException}。这里改为独立的服务端 tick 扫描。
 */
public final class DreamEffectHandler {

    /** 每级入梦恢复的理智（占理智上限 1.0 的比例）。 */
    public static final float SANITY_RESTORE_PER_LEVEL = 0.5f;

    /** 触发后给予的迅捷持续时间。 */
    public static final int SPEED_DURATION_TICKS = 60 * 20;

    private DreamEffectHandler() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!player.isSleeping()) {
                    continue;
                }
                MobEffectInstance dream = player.getEffect(ModEffects.ENTER_DREAM);
                if (dream == null) {
                    continue;
                }
                if (!SREGameWorldComponent.KEY.get(player.level()).isRunning()) {
                    continue;
                }
                trigger(player, dream.getAmplifier());
            }
        });
    }

    private static void trigger(ServerPlayer player, int amplifier) {
        int level = Math.max(0, amplifier) + 1;
        // 先清除，避免同一 tick 或后续 tick 重复触发
        player.removeEffect(ModEffects.ENTER_DREAM);
        SREPlayerMoodComponent.KEY.get(player).addMood(SANITY_RESTORE_PER_LEVEL * level);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, SPEED_DURATION_TICKS,
                Math.max(0, amplifier), false, false, true));
        player.displayClientMessage(
                Component.translatable("message.noellesroles.watchman.dream_triggered", level)
                        .withStyle(ChatFormatting.LIGHT_PURPLE),
                true);
    }
}
