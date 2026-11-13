package org.agmas.noellesroles.mixin.client.decree;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.agmas.noellesroles.client.decree.DecreeClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** setup 每帧还原相机后叠加衰减位移，不改玩家朝向、准星命中或持久设置。 */
@Mixin(Camera.class)
public abstract class DecreeCameraMixin {
    @Shadow protected abstract void move(float forward, float up, float sideways);

    @Inject(method = "setup", at = @At("RETURN"))
    private void decree$pressure(BlockGetter level, Entity entity, boolean thirdPerson,
                                  boolean reverse, float partialTick, CallbackInfo ci) {
        if (entity != Minecraft.getInstance().player) return;
        float pressure = DecreeClient.cameraPressure();
        if (pressure <= 0F) return;
        double time = System.nanoTime() / 1_000_000_000D;
        move(0F, (float) Math.sin(time * 75D) * pressure, (float) Math.cos(time * 62D) * pressure);
    }
}
