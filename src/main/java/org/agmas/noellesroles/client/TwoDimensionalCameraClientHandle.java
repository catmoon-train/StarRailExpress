package org.agmas.noellesroles.client;

import net.exmo.sre.camera.client.AdvancedCameraDirector;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEffects;

public final class TwoDimensionalCameraClientHandle {
    private static final double CAMERA_DISTANCE = 28.0D;
    private static final double CAMERA_HEIGHT = 6.0D;
    private static final float CAMERA_FOV = 35.0F;

    private TwoDimensionalCameraClientHandle() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(TwoDimensionalCameraClientHandle::tick);
    }

    private static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            AdvancedCameraDirector.clearFixedOverride();
            return;
        }

        MobEffectInstance effect = player.getEffect(ModEffects.TWO_DIMENSIONAL_CAMERA);
        if (effect == null) {
            AdvancedCameraDirector.clearFixedOverride();
            return;
        }

        Vec3 lookAt = player.getEyePosition(1.0F).add(0.0D, 0.5D, 0.0D);
        Vec3 side = sideVector(effect.getAmplifier());
        Vec3 cameraPos = lookAt.add(side.scale(CAMERA_DISTANCE)).add(0.0D, CAMERA_HEIGHT, 0.0D);
        Vec3 delta = lookAt.subtract(cameraPos);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) (Math.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) (-(Math.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG));
        AdvancedCameraDirector.setFixedOverride(cameraPos, yaw, pitch, CAMERA_FOV);
    }

    private static Vec3 sideVector(int amplifier) {
        return switch (Mth.clamp(amplifier, 0, 3)) {
            case 0 -> new Vec3(-1.0D, 0.0D, 0.0D); // 西边
            case 1 -> new Vec3(1.0D, 0.0D, 0.0D);  // 东边
            case 2 -> new Vec3(0.0D, 0.0D, -1.0D); // 北边
            default -> new Vec3(0.0D, 0.0D, 1.0D); // 南边
        };
    }
}
