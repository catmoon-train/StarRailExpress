package io.wifi.starrailexpress.client.gui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.SRE;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;

public class CrosshairRenderer {
    private static final ResourceLocation CROSSHAIR = SRE.watheId("hud/crosshair");
    private static final ResourceLocation KNIFE_ATTACK = SRE.watheId("hud/knife_attack");
    private static final ResourceLocation KNIFE_PROGRESS = SRE.watheId("hud/knife_progress");
    private static final ResourceLocation KNIFE_BACKGROUND = SRE.watheId("hud/knife_background");
    private static final ResourceLocation BAT_ATTACK = SRE.watheId("hud/bat_attack");
    private static final ResourceLocation BAT_PROGRESS = SRE.watheId("hud/bat_progress");
    private static final ResourceLocation BAT_BACKGROUND = SRE.watheId("hud/bat_background");

    public static void renderCrosshair(@NotNull Minecraft client, @NotNull LocalPlayer player, GuiGraphics context,
            DeltaTracker tickCounter) {
        if (!client.options.getCameraType().isFirstPerson())
            return;
        context.pose().pushPose();
        context.pose().translate(context.guiWidth() / 2f, context.guiHeight() / 2f, 0);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        ItemStack mainHandStack = player.getMainHandItem();
        if (mainHandStack.is(ModItems.SP_KNIFE) || mainHandStack.is(ModItems.THROWING_KNIFE) || mainHandStack.is(TMMItems.KNIFE)) {
            ItemCooldowns manager = player.getCooldowns();
            if (!manager.isOnCooldown(TMMItems.KNIFE)) {
                context.blitSprite(KNIFE_ATTACK, -5, 5, 10, 7);
            } else {
                float f = 1 - manager.getCooldownPercent(TMMItems.KNIFE, tickCounter.getGameTimeDeltaPartialTick(true));
                context.blitSprite(KNIFE_BACKGROUND, -5, 5, 10, 7);
                context.blitSprite(KNIFE_PROGRESS, 10, 7, 0, 0, -5, 5, (int) (f * 10.0f), 7);
            }
        } else if (mainHandStack.is(TMMItems.BAT)) {
            if (player.getAttackStrengthScale(tickCounter.getGameTimeDeltaPartialTick(true)) >= 1f) {
                context.blitSprite(BAT_ATTACK, -5, 5, 10, 7);
            } else {
                float f = player.getAttackStrengthScale(tickCounter.getGameTimeDeltaPartialTick(true));
                context.blitSprite(BAT_BACKGROUND, -5, 5, 10, 7);
                context.blitSprite(BAT_PROGRESS, 10, 7, 0, 0, -5, 5, (int) (f * 10.0f), 7);
            }
        }
        context.pose().pushPose();
        context.pose().translate(-1.5f, -1.5f, 0);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
                GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        {
            context.blitSprite(CROSSHAIR, 0, 0, 3, 3);
        }
        context.pose().popPose();
        context.pose().popPose();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }
}