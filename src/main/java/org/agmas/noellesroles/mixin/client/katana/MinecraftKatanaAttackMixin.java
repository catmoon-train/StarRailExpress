package org.agmas.noellesroles.mixin.client.katana;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.agmas.noellesroles.init.ModItems;
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
 * <li>左键命中实体时<b>不拦下</b>原版攻击：照常走 {@code Player#attack} →
 * {@code KatanaItem#onServerAttack}，这是「瞄到人时突刺」的老路径，也是可靠退路。</li>
 * <li>额外 always 发一个 {@link KatanaThrustC2SPacket}：原版左键<b>空挥时不会调
 * {@code Player#attack}</b>（只发挥手包），而突刺完全不看准星目标，所以空挥必须靠
 * 这个自定义通道。服务端 {@code KatanaCombat#handleThrust} 以服务端的
 * {@code nextMove} 为准判定，不是第二招就丢弃；已经在突刺中也会丢弃，
 * 因此与老路径不会重复出刀。</li>
 * <li>顺带以当前预测的招式立即播放动画（零延迟），服务端事件到达时若为同一招式
 * 则不重启动画（见 {@link KatanaClientState}）。</li>
 * </ul>
 *
 * <p>做法参考下界合金矛的 {@code MinecraftSpearAttackMixin}。
 */
@Mixin(Minecraft.class)
public class MinecraftKatanaAttackMixin {

    @Shadow
    @Nullable
    public LocalPlayer player;

    @Inject(method = "startAttack", at = @At("HEAD"))
    private void katana$sendThrustPacket(CallbackInfoReturnable<Boolean> cir) {
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
        // 补上「空挥」这条路：命中实体时这条包会被服务端丢弃，不会重复出刀
        ClientPlayNetworking.send(new KatanaThrustC2SPacket());
        KatanaClientState.startOptimisticAnim(player);
    }
}

