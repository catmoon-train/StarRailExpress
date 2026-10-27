package org.agmas.noellesroles.mixin.client.echo;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.agmas.noellesroles.game.modes.echo.client.EchoAtmosphereClient;
import org.agmas.noellesroles.game.modes.echo.visual.EchoVisualState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 每帧相机 setup 后局部位移；不改玩家朝向/眼高，不改射线或服务端命中。 */
@Mixin(Camera.class)
public abstract class EchoFearCameraMixin {
    @Shadow protected abstract void move(float forward, float up, float sideways);

    @Inject(method = "setup", at = @At("RETURN"))
    private void echo$fearCamera(BlockGetter area, Entity focusedEntity, boolean thirdPerson,
                                 boolean inverseView, float partialTick, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        if (focusedEntity != client.player || client.level == null) return;
        float pressure = EchoAtmosphereClient.cameraPressure();
        if (pressure <= 0F) return;
        // setup 每帧重建位置，因此不保存偏移，也不会多帧/多杀手无限叠加。
        double time = client.level.getGameTime() + partialTick;
        move(0F, (float) EchoVisualState.shake(pressure, time, true), (float) EchoVisualState.shake(pressure, time, false));
    }
}
