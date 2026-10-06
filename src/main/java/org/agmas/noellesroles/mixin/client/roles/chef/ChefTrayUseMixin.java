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
import org.spongepowered.asm.mixin.Unique;
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

    /**
     * 本次「按下—松开」是否还没发过交互包。
     *
     * <p>
     * 必须在 {@code startUseItem} 上做去重：原版 {@code runTick} 里是
     * {@code while (keyUse.isDown() && !player.isUsingItem())} 循环调用 {@code startUseItem}，
     * 每隔 4 tick 一次。本 mixin 把原版逻辑 cancel 掉后，{@code isUsingItem()} 永远不会变成
     * true，循环也就不会退出——于是「按住一次右键」会连发好几个 C2S 包：
     * <ul>
     * <li>厨师手持最后一个食物放入盘子 → 主手变空 → 紧接着的包命中「取出」分支，
     * 立刻把刚放进去的又取回来并进入 30 秒冷却（表现为「放入即冷却、什么都没拿到」）；</li>
     * <li>若厨师手上食物充足，一次右键会连续塞进多个，撑满整个盘子。</li>
     * </ul>
     * 所以同一次按住只允许发一个包，松开右键（{@code keyUse} 抬起）后才重新武装。
     */
    @Unique
    private boolean noe$chefTrayInteractArmed = true;

    /**
     * 每帧检查使用键：松开时重新武装，让下一次按下能够再次交互。
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void noe$rearmChefTrayInteract(CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        if (client.options != null && !client.options.keyUse.isDown()) {
            noe$chefTrayInteractArmed = true;
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void noe$interceptChefTrayUse(CallbackInfo ci) {
        if (!noe$chefTrayInteractArmed) {
            // 同一次按住右键期间 startUseItem 会被反复调用，这里只处理第一次
            return;
        }
        if (!(hitResult instanceof BlockHitResult blockHit)) {
            return;
        }
        BlockPos pos = blockHit.getBlockPos();
        if (!ClientChefTrayManager.isTrayAt(pos)) {
            return;
        }
        noe$chefTrayInteractArmed = false;
        ClientPlayNetworking.send(new ChefTrayInteractC2SPacket(pos));
        // 原版被 cancel 之后不会摆手，这里补一下，让放入/取出也有挥手反馈
        Minecraft client = (Minecraft) (Object) this;
        if (client.player != null) {
            client.player.swing(InteractionHand.MAIN_HAND);
        }
        ci.cancel();
    }
}
