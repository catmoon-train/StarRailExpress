package org.agmas.noellesroles.mixin.roles.clone_doctor;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.game.roles.killer.clone_doctor.CloneDoctorRole;
import org.agmas.noellesroles.game.roles.killer.clone_doctor.CloneDoctorSessionManager;
import org.agmas.noellesroles.init.ModItems;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 服务端拾取与插入也遵守临时物品归属、粘液弹最多一枚的约束。 */
@Mixin(Inventory.class)
public abstract class CloneDoctorInventoryMixin {
    @Shadow @Final public Player player;

    @Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void cloneDoctor$validateInsertion(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (player.level().isClientSide) return;
        if (CloneDoctorSessionManager.isTemporary(stack)
                && !CloneDoctorSessionManager.canReceiveTemporary(player, stack)) {
            cir.setReturnValue(false);
            return;
        }
        if (!CloneDoctorRole.isClone(player) || !stack.is(ModItems.SLIME_GRENADE)) return;
        Inventory inventory = (Inventory) (Object) this;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(ModItems.SLIME_GRENADE)) {
                cir.setReturnValue(false);
                return;
            }
        }
        if (stack.getCount() > 1) cir.setReturnValue(false);
    }
}
