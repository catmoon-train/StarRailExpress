package org.agmas.noellesroles.mixin.roles.clone_doctor;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.game.roles.killer.clone_doctor.CloneDoctorSessionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 临时初始物品不得通过丢弃转交给其他玩家。 */
@Mixin(Player.class)
public abstract class CloneDoctorItemMixin {
    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void cloneDoctor$preventTemporaryDrop(ItemStack stack, boolean around, boolean trace,
            CallbackInfoReturnable<ItemEntity> cir) {
        if (CloneDoctorSessionManager.isTemporary(stack)) cir.setReturnValue(null);
    }
}
