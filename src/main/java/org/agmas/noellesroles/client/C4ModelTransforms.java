package org.agmas.noellesroles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.Direction;
import org.agmas.noellesroles.game.c4.C4PlacementPreset;
import org.joml.Quaternionf;

public final class C4ModelTransforms {
    private static final float SURFACE_CENTER_OFFSET = -0.09F;
    private static final float CHEST_CENTER_Z = -0.22F;

    private C4ModelTransforms() {}

    public static void rotateToSurface(PoseStack matrices, float yaw, float pitch) {
        rotateIfNeeded(matrices, com.mojang.math.Axis.YP, yaw);
        rotateIfNeeded(matrices, com.mojang.math.Axis.XP, pitch);
    }

    public static void rotateFrontToSurface(PoseStack matrices, float yaw, float pitch) {
        switch (surfaceFromStoredAngles(yaw, pitch)) {
            case UP -> rotateIfNeeded(matrices, com.mojang.math.Axis.ZP, -90.0F);
            case DOWN -> rotateIfNeeded(matrices, com.mojang.math.Axis.ZP, 90.0F);
            case NORTH -> rotateIfNeeded(matrices, com.mojang.math.Axis.YP, -90.0F);
            case SOUTH -> rotateIfNeeded(matrices, com.mojang.math.Axis.YP, 90.0F);
            case EAST -> rotateIfNeeded(matrices, com.mojang.math.Axis.YP, 180.0F);
            default -> {
            }
        }
    }

    public static void rotateFrontToPlayerChest(PoseStack matrices) {
        rotateIfNeeded(matrices, com.mojang.math.Axis.YP, -90.0F);
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
        matrices.translate(SURFACE_CENTER_OFFSET, 0.0F, 0.0F);
        rotateIfNeeded(matrices, com.mojang.math.Axis.ZP, preset.rotationZ());
        float scale = preset.scale();
        matrices.scale(scale, scale, scale);
    }

    public static void applyChestPlacement(PoseStack matrices, C4PlacementPreset preset) {
        if (preset == null) preset = C4PlacementPreset.DEFAULT;
        matrices.translate(preset.offsetX(), preset.offsetY(), CHEST_CENTER_Z);
        rotateFrontToPlayerChest(matrices);
        rotateIfNeeded(matrices, com.mojang.math.Axis.ZP, preset.rotationZ());
        float scale = preset.scale();
        matrices.scale(scale, scale, scale);
    }

    private static Direction surfaceFromStoredAngles(float yaw, float pitch) {
        if (pitch <= -45.0F) return Direction.UP;
        if (pitch >= 45.0F) return Direction.DOWN;
        float normalizedYaw = net.minecraft.util.Mth.wrapDegrees(yaw);
        if (normalizedYaw >= 135.0F || normalizedYaw <= -135.0F) {
            return Direction.NORTH;
        }
        if (normalizedYaw >= 45.0F) return Direction.WEST;
        if (normalizedYaw <= -45.0F) return Direction.EAST;
        return Direction.SOUTH;
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
