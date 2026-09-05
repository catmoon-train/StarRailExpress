/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.client.gui;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import io.wifi.starrailexpress.SRE;
import net.minecraft.client.AttackIndicatorStatus;
// import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
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
            // 先提交反色准星。攻击条若跟反色混合画在一起，中灰背景上会抵消成看不见。
            context.flush();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableBlend();

            // 仅攻击指示器为crosshair下渲染蓄力条
            if (client.options.attackIndicator().get() == AttackIndicatorStatus.CROSSHAIR) {
                float f = player.getAttackStrengthScale(0.0F); // 0~1
                if (f < 1.0f) {
                    int barX = context.guiWidth() / 2 - 8;
                    int barY = context.guiHeight() / 2 - 7 + 16;
                    int barWidth = 16;
                    int barHeight = 2;
                    int progressWidth = Math.max(1, (int) (f * (float) barWidth));

                    context.pose().pushPose();
                    context.pose().translate(barX, barY + barHeight * 0.5f, 0);
                    context.pose().scale(1f, 0.7f, 1f);
                    context.pose().translate(0, -barHeight * 0.5f, 0);
                    context.fill(RenderType.guiOverlay(), 0, 0, barWidth, barHeight, 0xC0606060);
                    context.fill(RenderType.guiOverlay(), 0, 0, progressWidth, barHeight, 0xE0FFFFFF);
                    context.pose().popPose();
                }
            }
        }
        context.pose().popPose();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }
}