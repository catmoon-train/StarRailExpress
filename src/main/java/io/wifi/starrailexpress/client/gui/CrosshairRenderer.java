package io.wifi.starrailexpress.client.gui;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import io.wifi.starrailexpress.SRE;
// import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

// import net.minecraft.world.item.ItemCooldowns;
// import net.minecraft.world.item.ItemStack;
// import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;

public class CrosshairRenderer {
    private static final ResourceLocation CROSSHAIR = SRE.watheId("hud/crosshair");
    // private static final ResourceLocation KNIFE_ATTACK =
    // SRE.watheId("hud/knife_attack");
    // private static final ResourceLocation KNIFE_PROGRESS =
    // SRE.watheId("hud/knife_progress");
    // private static final ResourceLocation KNIFE_BACKGROUND =
    // SRE.watheId("hud/knife_background");
    // private static final ResourceLocation BAT_ATTACK =
    // SRE.watheId("hud/bat_attack");
    // private static final ResourceLocation BAT_PROGRESS =
    // SRE.watheId("hud/bat_progress");
    // private static final ResourceLocation BAT_BACKGROUND =
    // SRE.watheId("hud/bat_background");

    public static void renderCrosshair(@NotNull Minecraft client, @NotNull LocalPlayer player, GuiGraphics context,
            DeltaTracker tickCounter) {
        if (!client.options.getCameraType().isFirstPerson())
            return;
        RenderSystem.enableBlend();

        context.pose().pushPose();
        {
            RenderSystem.blendFuncSeparate(SourceFactor.ONE_MINUS_DST_COLOR, DestFactor.ONE_MINUS_SRC_COLOR,
                    SourceFactor.ONE, DestFactor.ZERO);
            context.pose().pushPose();
            context.pose().translate(context.guiWidth() / 2f - 1.5f, context.guiHeight() / 2f - 1.5f, 0);
            context.blitSprite(CROSSHAIR, 0, 0, 3, 3);
            context.pose().popPose();
            {
                float totalCooldownTicks = player.getCurrentItemAttackStrengthDelay();
                // 2. 攻击指示器（仅当蓄力中时显示）
                float f = player.getAttackStrengthScale(0.0F); // 0~1
                if (totalCooldownTicks < 4.0 && f < 1.0F) { // 非空手才显示
                    // 指示器位置（与原版一致）
                    int barX = context.guiWidth() / 2 - 8;
                    int barY = context.guiHeight() / 2 - 7 + 16;
                    int barWidth = 16;
                    int barHeight = 2;

                    // 2.1 黑色半透明背景（50% 透明度）
                    int bgColor = 0x80000000; // ARGB: 0x80 = 128/255 ≈ 50%
                    context.fill(barX, barY, barX + barWidth, barY + barHeight, bgColor);

                    // 2.2 白色半透明进度条（80% 不透明度）
                    int progressWidth = (int) (f * barWidth);
                    progressWidth = Math.max(progressWidth, 1); // 至少 1 像素，确保可见
                    int progressColor = 0x80FFFFFF; // 0xCC ≈ 80% 不透明
                    context.fill(barX, barY, barX + progressWidth, barY + barHeight, progressColor);
                }
                // 当 f >= 1.0 时，不绘制任何指示器，符合“满蓄力不显示条”的要求
            }
        }
        context.pose().popPose();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }
}