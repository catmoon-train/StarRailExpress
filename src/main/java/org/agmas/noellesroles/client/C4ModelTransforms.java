package org.agmas.noellesroles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import org.agmas.noellesroles.game.c4.C4PlacementPreset;
import org.joml.Quaternionf;

public final class C4ModelTransforms {
    private C4ModelTransforms() {}

    public static void rotateToSurface(PoseStack matrices, float yaw, float pitch) {
        rotateIfNeeded(matrices, com.mojang.math.Axis.YP, yaw);
        rotateIfNeeded(matrices, com.mojang.math.Axis.XP, pitch);
    }

    public static void applyPlacement(PoseStack matrices, C4PlacementPreset preset) {
        if (preset == null) preset = C4PlacementPreset.DEFAULT;
        matrices.translate(preset.offsetX(), preset.offsetY(), preset.offsetZ());
        rotateIfNeeded(matrices, com.mojang.math.Axis.XP, preset.rotationX());
        rotateIfNeeded(matrices, com.mojang.math.Axis.YP, preset.rotationY());
        rotateIfNeeded(matrices, com.mojang.math.Axis.ZP, preset.rotationZ());
        slantIfNeeded(matrices, preset.slant());
        float scale = preset.scale();
        matrices.scale(scale, scale, scale);
    }

    public static void applySurfacePlacement(PoseStack matrices, C4PlacementPreset preset) {
        if (preset == null) preset = C4PlacementPreset.DEFAULT;
        float surfaceZ = preset.offsetZ() - C4PlacementPreset.DEFAULT.offsetZ() - 0.11F;
        matrices.translate(preset.offsetX(), preset.offsetY(), surfaceZ);
        rotateIfNeeded(matrices, com.mojang.math.Axis.ZP, preset.rotationZ());
        float scale = preset.scale();
        matrices.scale(scale, scale, scale);
    }

    private static void rotateIfNeeded(PoseStack matrices, com.mojang.math.Axis axis, float degrees) {
        if (degrees != 0.0F) {
            matrices.mulPose(axis.rotationDegrees(degrees));
        }
    }

    private static void slantIfNeeded(PoseStack matrices, float degrees) {
        if (degrees != 0.0F) {
            matrices.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(degrees),
                0.70710677F, 0.0F, 0.70710677F));
        }
    }
}
