package org.agmas.noellesroles.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.agmas.noellesroles.client.roulette.RouletteEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 只对受击参与者叠加短暂角度扰动，不移动相机，不改变玩家朝向或准星射线。 */
@Mixin(Camera.class)
public abstract class RouletteCameraMixin {
    @Shadow public abstract float getYRot();
    @Shadow public abstract float getXRot();
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "setup", at = @At("RETURN"))
    private void roulette$shake(BlockGetter level, Entity entity, boolean detached, boolean reverse,
                                 float partialTick, CallbackInfo ci) {
        var mc = Minecraft.getInstance();
        if (entity != mc.player || mc.level == null) return;
        float strength = RouletteEffects.shake(partialTick);
        if (strength <= 0) return;
        double time = mc.level.getGameTime() + partialTick;
        setRotation(getYRot() + (float) Math.sin(time * 3.7) * strength,
                getXRot() + (float) Math.cos(time * 4.3) * strength * .7F);
    }
}
