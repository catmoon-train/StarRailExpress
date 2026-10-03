package org.agmas.noellesroles.mixin.client.katana;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.katana.KatanaState;
import org.agmas.noellesroles.katana.client.KatanaClientState;
import org.agmas.noellesroles.packet.KatanaThrustC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.jetbrains.annotations.Nullable;

/**
 * 本地玩家手持武士刀左键瞬间的处理：
 *
 * <ul>
 * <li><b>招式为第二招（突刺）</b>：拦下原版攻击，改发
 * {@link KatanaThrustC2SPacket}，服务端据此突刺。原版左键只有命中实体才会调
 * {@code Player#attack}，而突刺完全不看准星目标，所以空挥必须走这条自定义通道
 * （做法与下界合金矛的 {@code MinecraftSpearAttackMixin} 一致）。</li>
 * <li><b>其他招式</b>：以当前预测的招式立即开始播放动画（零延迟），
 * 服务端的招式事件随后到达时若为同一招式则不重启动画（见 {@link KatanaClientState}）。</li>
 * </ul>
 */
@Mixin(Minecraft.class)
public class MinecraftKatanaAttackMixin {

    @Shadow
    @Nullable
    public LocalPlayer player;

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void katana$onStartAttack(CallbackInfoReturnable<Boolean> cir) {
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
        if (KatanaClientState.currentMove(player) == KatanaState.MOVE_THRUST) {
            // 突刺不依赖准星目标：空挥也要出刀
            ClientPlayNetworking.send(new KatanaThrustC2SPacket());
            player.swing(InteractionHand.MAIN_HAND);
            player.resetAttackStrengthTicker();
            // 手动开始突刺动画：服务端招式事件到达前零延迟（与
            // KatanaClientState#onMoveUsed 的去重逻辑配合，不会重启动画）
            KatanaClientState.startOptimisticAnim(player);
            cir.setReturnValue(true);
            return;
        }
        KatanaClientState.startOptimisticAnim(player);
    }
}
