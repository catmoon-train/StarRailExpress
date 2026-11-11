package org.agmas.noellesroles.mixin.roles.ma_chen_xu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.game.roles.killer.ma_chen_xu.SackGhostItems;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 原版拾取和直接背包插入均校验诡物归属、四件上限及法器真实阵营。 */
@Mixin(Inventory.class)
public abstract class GhostArtInventoryMixin {
    @Shadow @Final public Player player;
    @Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void noellesroles$guardGhostItems(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!player.level().isClientSide() && !SackGhostItems.canReceive(player, stack)) cir.setReturnValue(false);
    }
}
