package org.agmas.noellesroles.mixin.client.katana;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.katana.client.KatanaClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.jetbrains.annotations.Nullable;

/**
 * 本地玩家手持武士刀左键攻击瞬间：以当前预测的招式立即开始播放动画（零延迟），
 * 服务端的招式事件随后到达时若为同一招式则不重启动画（见 {@code KatanaClientState}）。
 *
 * <p>纯客户端表现，<b>不拦下也不改动原版攻击</b>：突刺的判定与位移全在服务端
 * （见 {@code ServerGamePacketListenerImplKatanaMixin} 捕获挥手包）。
 */
@Mixin(Minecraft.class)
public class MinecraftKatanaAttackMixin {

    @Shadow
    @Nullable
    public LocalPlayer player;

    @Inject(method = "startAttack", at = @At("HEAD"))
    private void katana$optimisticMoveAnim(CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer player = this.player;
        if (player == null || !player.getMainHandItem().is(ModItems.KATANA)) {
            return;
        }
        if (player.isUsingItem() || player.getCooldowns().isOnCooldown(ModItems.KATANA)) {
            return;
        }
        // 与服务端一致的满蓄力要求（留一点容差）
        if (player.getAttackStrengthScale(0.0F) < 0.95F) {
            return;
        }
        KatanaClientState.startOptimisticAnim(player);
    }
}
