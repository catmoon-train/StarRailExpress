package org.agmas.noellesroles.mixin.client.roles.command_block;

import org.agmas.noellesroles.client.CommandBlockSkyLight;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 天空穹顶和雾用同一抹褪色紫，地平线不出现遮罩边。 */
@Mixin(ClientLevel.class)
public abstract class CommandBlockSkyColorMixin {
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void nr$purpleSky(Vec3 pos, float partialTick, CallbackInfoReturnable<Vec3> cir) {
        ClientLevel level = (ClientLevel) (Object) this;
        if (!CommandBlockSkyLight.overworldSky(level) || !CommandBlockSkyLight.active()) {
            return;
        }
        cir.setReturnValue(CommandBlockSkyLight.tintDome(cir.getReturnValue()));
    }
}
