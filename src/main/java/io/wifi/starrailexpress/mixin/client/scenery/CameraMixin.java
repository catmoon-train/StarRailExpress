package io.wifi.starrailexpress.mixin.client.scenery;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public class CameraMixin {
    @Unique
    private static final ImprovedNoise sampler = new ImprovedNoise(RandomSource.create());

    @Unique
    private static float randomizeOffset(int offset) {
        float intensity = 0.2f;

        float min = -intensity * 2;
        float max = intensity * 2;
        float sampled = (float) sampler.noise((Minecraft.getInstance().level.getGameTime() % 24000L
                + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)) / intensity, offset, 0) * 1.5f;
        return min >= max ? min : sampled * max;
    }

    @Inject(method = "setup", at = @At("RETURN"))
    private void tmm$doScreenshake(BlockGetter area, Entity focusedEntity, boolean thirdPerson, boolean inverseView,
            float tickDelta, CallbackInfo ci) {
        if (SREClient.isTrainMoving() && !SREConfig.instance().disableScreenShake && SREClient.gameComponent.isRunning()
                && SREClient.gameComponent.isOutsideSoundsAvailable()) {
            Camera camera = (Camera) (Object) this;

            LocalPlayer player = Minecraft.getInstance().player;
            int age = player.tickCount;
            float v = (1 + (1 - SREClient.moodComponent.getMood())) * 2.5f;
            float amplitude = .0025f;
            float strength = 0.5f;

            float yawOffset = 0;
            float pitchOffset = 0;

            if (SRE.isSkyVisible(player)) {
                amplitude = .004f;
                strength = 1f;

                if (SRE.isExposedToWind(player)) {
                    yawOffset = 1.5f * randomizeOffset(10);
                    pitchOffset = 1.5f * randomizeOffset(-10);
                }
            }

            amplitude *= v;

            camera.setRotation(camera.getYRot() + yawOffset, camera.getXRot() + pitchOffset);
            camera.setPosition(camera.getPosition().add(0, Math.sin((age + tickDelta) * strength) / 2f * amplitude,
                    Math.cos((age + tickDelta) * strength) * amplitude));
        }
    }
}
