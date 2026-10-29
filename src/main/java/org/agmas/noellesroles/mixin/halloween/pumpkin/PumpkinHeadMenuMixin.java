package org.agmas.noellesroles.mixin.halloween.pumpkin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.agmas.noellesroles.game.halloween.pumpkin.PumpkinHeads;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 覆盖普通点击、shift、数字键、丢弃与拖拽，防止把临时头拿走复制。 */
@Mixin(AbstractContainerMenu.class)
public abstract class PumpkinHeadMenuMixin {
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void pumpkin$headSlot(int slot, int button, ClickType type, Player player, CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (PumpkinHeads.blocksClick(menu, slot, player)) {
            if (player instanceof ServerPlayer) menu.broadcastFullState();
            ci.cancel();
        }
    }

    @Inject(method = "canDragTo", at = @At("HEAD"), cancellable = true)
    private void pumpkin$noDrag(Slot slot, CallbackInfoReturnable<Boolean> cir) {
        if (PumpkinHeads.blocksDrag(slot)) cir.setReturnValue(false);
    }
}
