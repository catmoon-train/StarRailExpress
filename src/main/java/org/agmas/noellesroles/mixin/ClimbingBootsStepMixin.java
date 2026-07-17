package org.agmas.noellesroles.mixin;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.item.ClimbingBootsItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 攀登靴步高提升：穿着攀登靴且在海上时，
 * 将 maxUpStep 提升至 1.25，允许登上比海平面高一格的方块。
 */
@Mixin(LivingEntity.class)
public class ClimbingBootsStepMixin {

    /** Entity.maxUpStep（父类字段）；默认 0.6，提升后 = 1.25（可迈上 1 格高）。 */


    @Inject(method = "maxUpStep", at = @At("RETURN"), cancellable = true)
    private void climbingBootsBoostStep(CallbackInfoReturnable<Float> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Player player)) return;
        if (player.level().isClientSide) return;

        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        boolean wearingBoots = !boots.isEmpty() && boots.getItem() instanceof ClimbingBootsItem;
        boolean atSeaEdge = player.getBlockY() <= player.level().getSeaLevel()
                && player.isInWater();

        if (wearingBoots && atSeaEdge) {
            cir.setReturnValue(2.2f);

        }
    }
}
