package org.agmas.noellesroles.mixin.client.echo;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import org.agmas.noellesroles.game.modes.echo.client.EchoHotbar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 回声固定九格分组显示，滚轮仍遍历完整 0..8。 */
@Mixin(Inventory.class)
public abstract class EchoHotbarScrollMixin {
    @Inject(method = "swapPaint", at = @At("HEAD"), cancellable = true)
    private void noellesroles$echoSwapPaint(double direction, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        if (client.player == null || client.player.getInventory() != (Inventory) (Object) this) return;
        if (!EchoHotbar.active()) return;
        Inventory inventory = (Inventory) (Object) this;
        inventory.selected = Math.floorMod(inventory.selected - (int) Math.signum(direction), 9);
        ci.cancel();
    }
}
