package org.agmas.noellesroles.mixin.halloween.ambience;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.halloween.ambience.HalloweenAmbienceSky;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 活动局染真正的天空穹顶，其它世界逐值保留原返回值。 */
@Mixin(ClientLevel.class)
public abstract class HalloweenSkyColorMixin {
    @Inject(method = "getSkyColor(Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;",
            at = @At("RETURN"), cancellable = true)
    private void halloween$orangeSky(Vec3 position, float partialTick, CallbackInfoReturnable<Vec3> cir) {
        if (HalloweenAmbienceSky.active((ClientLevel) (Object) this)) {
            cir.setReturnValue(HalloweenAmbienceSky.tintDome(cir.getReturnValue()));
        }
    }
}
