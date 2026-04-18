package io.wifi.starrailexpress.client.fourthroom;

import io.wifi.starrailexpress.client.StaminaRenderer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.modes.fourthroom.block.FourthRoomTableBlock;
import org.agmas.noellesroles.game.modes.fourthroom.block.FourthRoomTableBlockEntity;
import org.agmas.noellesroles.game.modes.fourthroom.effect.TableEffectEvents;
import org.jetbrains.annotations.Nullable;

public final class FourthRoomCameraDirector {

    @Nullable
    private static ActiveFocus activeFocus;

    private FourthRoomCameraDirector() {
    }

    public static void focusTable(BlockPos origin, TableEffectEvents.TableAnchor anchor, long durationMs, float strength,
                                  boolean cinematic, int edgeColor) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        long now = System.currentTimeMillis();
        CameraType previousCameraType = activeFocus != null ? activeFocus.previousCameraType : minecraft.options.getCameraType();
        boolean restoreCameraType = activeFocus != null ? activeFocus.restoreCameraType : false;
        if (cinematic && minecraft.options.getCameraType() == CameraType.FIRST_PERSON) {
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            restoreCameraType = true;
        }
        activeFocus = new ActiveFocus(origin, anchor, now, Math.max(240L, durationMs), Mth.clamp(strength, 0.0F, 1.0F),
                cinematic, edgeColor, previousCameraType, restoreCameraType);
        if (edgeColor != 0) {
            StaminaRenderer.triggerScreenEdgeEffect(edgeColor, Math.min(durationMs, 850L), Math.max(0.10F, strength * 0.35F));
        }
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null || activeFocus == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now >= activeFocus.endTimeMs()) {
            clear();
            return;
        }
        BlockState state = minecraft.level.getBlockState(activeFocus.origin());
        Direction facing = state.hasProperty(FourthRoomTableBlock.FACING)
                ? state.getValue(FourthRoomTableBlock.FACING)
                : Direction.NORTH;
        Vec3 focusPos = activeFocus.anchor().worldPos(activeFocus.origin(), facing);
        LocalPlayer player = minecraft.player;
        Vec3 eyePos = player.getEyePosition();
        Vec3 delta = focusPos.subtract(eyePos);
        double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float targetYaw = (float) (Math.atan2(delta.z, delta.x) * (180.0D / Math.PI)) - 90.0F;
        float targetPitch = (float) (-(Math.atan2(delta.y, horizontalDistance) * (180.0D / Math.PI)));
        float blend = 0.12F + activeFocus.strength() * 0.16F;
        float yaw = Mth.rotLerp(blend, player.getYRot(), targetYaw);
        float pitch = Mth.lerp(blend, player.getXRot(), targetPitch);
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
    }

    public static void renderOverlay(GuiGraphics guiGraphics) {
        renderCinematicBars(guiGraphics);
    }

    public static void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        if (activeFocus != null && activeFocus.restoreCameraType) {
            minecraft.options.setCameraType(activeFocus.previousCameraType);
        }
        activeFocus = null;
    }

    @Nullable
    public static FourthRoomTableBlockEntity getLookedTable(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || !(minecraft.hitResult instanceof BlockHitResult hitResult)
                || hitResult.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos hitPos = hitResult.getBlockPos();
        BlockState state = minecraft.level.getBlockState(hitPos);
        if (!(state.getBlock() instanceof FourthRoomTableBlock)) {
            return null;
        }
        BlockPos corePos = FourthRoomTableBlock.getCore(state, hitPos);
        if (minecraft.level.getBlockEntity(corePos) instanceof FourthRoomTableBlockEntity table) {
            return table;
        }
        return null;
    }

    private static void renderCinematicBars(GuiGraphics guiGraphics) {
        if (activeFocus == null || !activeFocus.cinematic()) {
            return;
        }
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        float alpha = activeFocus.overlayAlpha(System.currentTimeMillis());
        if (alpha <= 0.0F) {
            return;
        }
        int barHeight = Math.max(18, Math.round(screenHeight * 0.085F * alpha));
        int color = ((int) (alpha * 220.0F) << 24);
        guiGraphics.fill(0, 0, screenWidth, barHeight, color);
        guiGraphics.fill(0, screenHeight - barHeight, screenWidth, screenHeight, color);
    }

    private record ActiveFocus(BlockPos origin, TableEffectEvents.TableAnchor anchor, long startTimeMs, long durationMs,
                               float strength, boolean cinematic, int edgeColor, CameraType previousCameraType,
                               boolean restoreCameraType) {
        long endTimeMs() {
            return startTimeMs + durationMs;
        }

        float overlayAlpha(long now) {
            float progress = Mth.clamp((now - startTimeMs) / (float) durationMs, 0.0F, 1.0F);
            float fadeIn = Mth.clamp(progress / 0.18F, 0.0F, 1.0F);
            float fadeOut = Mth.clamp((1.0F - progress) / 0.22F, 0.0F, 1.0F);
            return Math.min(fadeIn, fadeOut) * (0.45F + strength * 0.45F);
        }
    }
}