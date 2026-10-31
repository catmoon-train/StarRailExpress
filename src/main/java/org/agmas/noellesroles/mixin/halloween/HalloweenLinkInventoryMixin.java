package org.agmas.noellesroles.mixin.halloween;

import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.game.halloween.HalloweenLinkItems;
import org.agmas.noellesroles.game.halloween.HalloweenSoulLinks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 借用者不接收新物品插入，槽位替换只允许完成真实消耗后的结果。 */
@Mixin(Inventory.class)
public abstract class HalloweenLinkInventoryMixin {
    @Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void halloween$linkAdd(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        Inventory self = (Inventory) (Object) this;
        if (HalloweenSoulLinks.isItemBorrower(self.player)) cir.setReturnValue(false);
    }

    @Inject(method = "setItem", at = @At("HEAD"), cancellable = true)
    private void halloween$linkSet(int slot, ItemStack stack, CallbackInfo ci) {
        if (HalloweenLinkItems.replaceConsumedSlot((Inventory) (Object) this, slot, stack)) ci.cancel();
    }

    @Inject(method = "dropAll", at = @At("HEAD"), cancellable = true)
    private void halloween$linkDeathDrop(CallbackInfo ci) {
        Inventory self = (Inventory) (Object) this;
        if (HalloweenSoulLinks.isItemBorrower(self.player)) ci.cancel();
    }
    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void halloween$linkSave(ListTag tag, CallbackInfoReturnable<ListTag> cir) {
        Inventory self = (Inventory) (Object) this;
        if (HalloweenSoulLinks.isItemBorrower(self.player)) cir.setReturnValue(tag);
    }
}
