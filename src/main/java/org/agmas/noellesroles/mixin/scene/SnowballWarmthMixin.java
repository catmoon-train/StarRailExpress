package org.agmas.noellesroles.mixin.scene;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.phys.EntityHitResult;
import org.agmas.noellesroles.scene.MapStatusBarRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Snowball.class)
public class SnowballWarmthMixin {
    @Inject(method = "onHitEntity", at = @At("HEAD"))
    private void noellesroles$reduceWarmth(EntityHitResult result, CallbackInfo ci) {
        if (result.getEntity() instanceof ServerPlayer player) {
            MapStatusBarRuntime.addWarmth(player, -2);
        }
    }
}
