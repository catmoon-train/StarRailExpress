package org.agmas.noellesroles.mixin.client.decree;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import org.agmas.noellesroles.client.decree.DecreeClient;
import org.agmas.noellesroles.game.roles.innocence.decree.Decree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 滚轮仅拦本地玩家背包；服务端仍必须校验选槽包。 */
@Mixin(Inventory.class)
public abstract class DecreeHotbarMixin {
    @Inject(method = "swapPaint", at = @At("HEAD"), cancellable = true)
    private void decree$scroll(double direction, CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.getInventory() == (Object) this && DecreeClient.isRestricted(Decree.HOTBAR)) {
            ci.cancel();
        }
    }
}
