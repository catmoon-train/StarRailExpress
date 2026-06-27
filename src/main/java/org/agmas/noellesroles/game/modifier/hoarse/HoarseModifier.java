package org.agmas.noellesroles.game.modifier.hoarse;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import org.agmas.noellesroles.init.ModEffects;

/**
 * 沙哑修饰符：让玩家的嗓音十分低沉。
 *
 * <p>复用仓库现成的 OpenAL 语音方案——持续给玩家挂上隐藏的
 * {@link ModEffects#HEAVY_METAL_VOICE} 效果，由
 * {@code VoiceEffectsOpenALPlugin} 在听者客户端把声源音调压低，
 * {@code VoiceEffectSync} 负责把该效果广播给其它玩家，
 * 因此其它人听到的也会是低沉的嗓音。</p>
 */
public final class HoarseModifier {

    private HoarseModifier() {
    }

    /** 重金属语音等级。0 级对应 1 级重金属药水效果，音调轻微压低（0.85×）。 */
    private static final int VOICE_AMPLIFIER = 0;

    /** 每次续期下发的效果时长（tick）。 */
    private static final int EFFECT_DURATION = 200;

    /** 剩余时长低于该值时续期，避免在两次刷新之间过期。 */
    private static final int REFRESH_THRESHOLD = 40;

    public static void serverTick(ServerPlayer player) {
        MobEffectInstance current = player.getEffect(ModEffects.HEAVY_METAL_VOICE);
        // 已有更深或等深且未临近过期的语音效果时无需重复下发，避免覆盖更强的来源。
        if (current != null
                && current.getAmplifier() >= VOICE_AMPLIFIER
                && current.getDuration() > REFRESH_THRESHOLD) {
            return;
        }
        // 隐藏粒子/图标，仅用于驱动 OpenAL 音调处理。
        player.addEffect(new MobEffectInstance(
                ModEffects.HEAVY_METAL_VOICE, EFFECT_DURATION, VOICE_AMPLIFIER, false, false, false));
    }
}
