/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pro.fazeclan.river.stupid_express.mixin.client.modifier.twin_children;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.fazeclan.river.stupid_express.modifier.twin_children.TwinChildrenHandler;

/**
 * Tracker interpolation of a player-rider fights {@code positionRider} and
 * leaves the upper twin frozen on the lower twin's client.
 */
@Mixin(LivingEntity.class)
public abstract class TwinChildrenLerpMixin {

    @Inject(method = "lerpTo", at = @At("HEAD"), cancellable = true)
    private void stupidExpress$skipStackedTwinLerp(double x, double y, double z, float yRot, float xRot, int steps,
            CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Player player && TwinChildrenHandler.isStackedUpper(player)) {
            ci.cancel();
        }
    }
}
