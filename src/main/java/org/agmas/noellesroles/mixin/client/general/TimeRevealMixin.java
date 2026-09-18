package org.agmas.noellesroles.mixin.client.general;

import io.wifi.starrailexpress.client.gui.TimeRenderer;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import org.agmas.noellesroles.init.ModEffects;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 更夫敲钟的「时间透视」：拥有 {@link ModEffects#TIME_REVEAL} 的客户端玩家，暂时也能看到游戏时间。
 *
 * <p>
 * 主模组 {@link TimeRenderer#renderHud} 里决定是否绘制时间的逻辑写死在
 * {@code role.canSeeTime() || 旁观/创造 || SREClient.cachedCanSeeTime}，没有提供事件钩子，
 * 因此这里在 {@code cachedCanSeeTime} 被写入之后补一次「或」运算。
 */
@Mixin(TimeRenderer.class)
public class TimeRevealMixin {

    @Shadow
    private static boolean cachedCanSeeTime;

    @Inject(method = "renderHud", at = @At(value = "FIELD", target = "Lio/wifi/starrailexpress/client/gui/TimeRenderer;cachedCanSeeTime:Z", opcode = Opcodes.PUTSTATIC, shift = At.Shift.AFTER))
    private static void noellesroles$applyTimeReveal(Font renderer, LocalPlayer player, FakeGuiGraphics context,
            float delta, CallbackInfo ci) {
        if (!cachedCanSeeTime && player != null && player.hasEffect(ModEffects.TIME_REVEAL)) {
            cachedCanSeeTime = true;
        }
    }
}
