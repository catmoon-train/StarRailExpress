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

package org.agmas.noellesroles.client.hud.roles;

import com.mojang.blaze3d.systems.RenderSystem;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import io.wifi.utils.client.betterrender.FakeHudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModEffects;

public class TomatoSauceHud {

    public static final ResourceLocation TEXTURE = Noellesroles.id("textures/gui/tomato_sauce.png");
    private static final int TEX_WIDTH = 800;
    private static final int TEX_HEIGHT = 450;
    private static final int FADE_IN_TICKS = 12;
    private static final int FADE_OUT_TICKS = 16;
    private static final float MAX_ALPHA = 0.88F;

    /** 本次糊脸的总时长（剩余时间上涨时视为重新糊上）。 */
    private static int sessionTotalTicks;

    public static void register() {
        FakeHudRenderCallback.EVENT.register(TomatoSauceHud::render);
    }

    private static void render(FakeGuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            sessionTotalTicks = 0;
            return;
        }
        MobEffectInstance effect = client.player.getEffect(ModEffects.TOMATO_SAUCE);
        if (effect == null) {
            sessionTotalTicks = 0;
            return;
        }
        int remainingTicks = Math.max(effect.getDuration(), 0);
        if (remainingTicks > sessionTotalTicks) {
            sessionTotalTicks = remainingTicks;
        }
        float alpha = overlayAlpha(sessionTotalTicks, remainingTicks,
                deltaTracker.getGameTimeDeltaPartialTick(true));
        if (alpha <= 0.01F) {
            return;
        }
        GuiGraphics real = graphics.getDefaultGuiGraphics();
        int width = real.guiWidth();
        int height = real.guiHeight();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        real.setColor(1.0F, 1.0F, 1.0F, alpha);
        real.blit(TEXTURE, 0, 0, width, height, 0.0F, 0.0F, TEX_WIDTH, TEX_HEIGHT, TEX_WIDTH, TEX_HEIGHT);
        real.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    /**
     * 按本次实际总时长做渐显/渐隐，互不抢占：开头淡入、结尾淡出，中间保持满透明度。
     */
    private static float overlayAlpha(int totalTicks, int remainingTicks, float partialTick) {
        if (totalTicks <= 0 || remainingTicks <= 0) {
            return 0.0F;
        }
        float remaining = Mth.clamp(remainingTicks - partialTick, 0.0F, totalTicks);
        float elapsed = totalTicks - remaining;
        float fadeIn = Mth.clamp(elapsed / FADE_IN_TICKS, 0.0F, 1.0F);
        float fadeOut = Mth.clamp(remaining / FADE_OUT_TICKS, 0.0F, 1.0F);
        return Math.min(fadeIn, fadeOut) * MAX_ALPHA;
    }
}
