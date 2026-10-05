package org.agmas.noellesroles.mixin.client.roles.chef;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.client.ClientChefTrayManager;
import org.agmas.noellesroles.packet.ChefTrayInteractC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 厨师的「客户端」食物盘 / 饮料盘右键拦截。
 *
 * <p>盘子只画在客户端世界里，服务端根本没有这个方块，原版的 useItemOn 包过去也没有用。
 * 因此这里在右键开始处拦下来：命中的是客户端盘子时取消原版交互，改发
 * {@link ChefTrayInteractC2SPacket} 让服务端做放入 / 取出的记账。
 */
@Mixin(Minecraft.class)
public abstract class ChefTrayUseMixin {

    @Shadow
    public HitResult hitResult;

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void noe$interceptChefTrayUse(CallbackInfo ci) {
        if (!(hitResult instanceof BlockHitResult blockHit)) {
            return;
        }
        BlockPos pos = blockHit.getBlockPos();
        if (!ClientChefTrayManager.isTrayAt(pos)) {
            return;
        }
        ClientPlayNetworking.send(new ChefTrayInteractC2SPacket(pos));
        // 原版被 cancel 之后不会摆手，这里补一下，让放入/取出也有挥手反馈
        Minecraft client = (Minecraft) (Object) this;
        if (client.player != null) {
            client.player.swing(InteractionHand.MAIN_HAND);
        }
        ci.cancel();
    }
}
