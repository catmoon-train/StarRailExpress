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
import net.minecraft.world.entity.LivingEntity;

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
    private static final ResourceLocation CROSSHAIR_ATTACK_INDICATOR_FULL_SPRITE = ResourceLocation
            .withDefaultNamespace("hud/crosshair_attack_indicator_full");
    private static final ResourceLocation CROSSHAIR_ATTACK_INDICATOR_BACKGROUND_SPRITE = ResourceLocation
            .withDefaultNamespace("hud/crosshair_attack_indicator_background");
    private static final ResourceLocation CROSSHAIR_ATTACK_INDICATOR_PROGRESS_SPRITE = ResourceLocation
            .withDefaultNamespace("hud/crosshair_attack_indicator_progress");

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
                float f = client.player.getAttackStrengthScale(0.0F);
                boolean bl = false;
                if (client.crosshairPickEntity != null
                        && client.crosshairPickEntity instanceof LivingEntity && f >= 1.0F) {
                    bl = client.player.getCurrentItemAttackStrengthDelay() > 5.0F;
                    bl &= client.crosshairPickEntity.isAlive();
                }

                int j = context.guiHeight() / 2 - 7 + 16;
                int k = context.guiWidth() / 2 - 8;
                if (bl) {
                    context.blitSprite(CROSSHAIR_ATTACK_INDICATOR_FULL_SPRITE, k, j, 16, 16);
                } else if (f < 1.0F) {
                    int l = (int) (f * 17.0F);
                    context.blitSprite(CROSSHAIR_ATTACK_INDICATOR_BACKGROUND_SPRITE, k, j, 16, 4);
                    context.blitSprite(CROSSHAIR_ATTACK_INDICATOR_PROGRESS_SPRITE, 16, 4, 0, 0, k, j, l, 4);
                }
            }
        }
        context.pose().popPose();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }
}