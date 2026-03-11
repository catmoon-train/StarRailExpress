package org.agmas.noellesroles.mixin.client.roles.detective;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.client.Listen;
import org.agmas.noellesroles.role.ModRoles;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.agmas.noellesroles.client.Listen.*;

@Mixin(Gui.class)
public class DetectivePassiveUiMixin {
    @Inject(method = "render", at = @At("TAIL"))
    public void renderDetectivePassiveUi(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        if (Minecraft.getInstance() == null || Minecraft.getInstance().player == null) {
            return;
        }
        if (Minecraft.getInstance().player.isSpectator())
            return;
        
        if (SREClient.gameComponent == null) {
            return;
        }
        if (!SREClient.gameComponent.isRole(mc.player, ModRoles.DETECTIVE)) {
            return;
        }

        Vec3 playerPos = mc.player.getPosition(deltaTracker.getGameTimeDeltaPartialTick(true));

        // 当前帧索引
        int frameIndex = (tick / FRAME_DURATION) % FRAME_COUNT;

        float minScale = 0.1f;
        float maxScale = 1f;
        double maxDis = 25;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        PoseStack poseStack = guiGraphics.pose();

        // 遍历所有实体
        for (int i = soundInfos.size() - 1; i >= 0; i--) {
            Listen.SoundInfo info = soundInfos.get(i);

            // 世界坐标 → 屏幕坐标
            Vector3f screen = worldToScreen(info.pos.x, info.pos.y, info.pos.z);

            // 不可见（背后）
            if (Float.isNaN(screen.x))
                continue;

            poseStack.pushPose();

            int x = (int) screen.x;
            int y = (int) screen.y;

            int drawX = -FRAME_WIDTH / 2; // 左移半宽
            int drawY = -FRAME_HEIGHT / 2; // 上移半高

            double distance = playerPos.distanceTo(info.pos);
            float dynamicScale;
            if (distance <= 0.1d) {
                dynamicScale = maxScale;
            } else {
                dynamicScale = minScale + (maxScale - minScale) * (float) Math.pow(1 - distance / maxDis, 2);
            }

            poseStack.translate(x, y, 0);
            poseStack.scale(dynamicScale, dynamicScale, 1.0f);

            // 绘制当前帧
            guiGraphics.blit(
                    ECHO_TEX,
                    drawX, drawY,
                    frameIndex * FRAME_WIDTH, 0,
                    FRAME_WIDTH, FRAME_HEIGHT,
                    FRAME_WIDTH * FRAME_COUNT, FRAME_HEIGHT);

            poseStack.popPose();

            if (mc.level.getGameTime() - info.time > 20) {
                soundInfoPool.push(soundInfos.remove(i));
            }
        }

        RenderSystem.disableBlend();

        tick++;
    }
}
