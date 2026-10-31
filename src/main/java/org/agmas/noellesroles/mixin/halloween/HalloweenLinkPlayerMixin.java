package org.agmas.noellesroles.mixin.halloween;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.game.halloween.HalloweenSoulLinks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 镜像物品不可丢到世界里复制；正常使用、数量和耐久消耗共享发起者真实栈。 */
@Mixin(Player.class)
public abstract class HalloweenLinkPlayerMixin {
    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void halloween$linkDrop(ItemStack stack, boolean random, boolean retainOwner,
            CallbackInfoReturnable<ItemEntity> cir) {
        if (HalloweenSoulLinks.isItemBorrower((Player) (Object) this)) cir.setReturnValue(null);
    }
}
