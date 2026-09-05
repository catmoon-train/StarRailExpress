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
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;
import org.agmas.noellesroles.init.ModEffects;

public class TomatoSauceHud {

    public static final ResourceLocation TEXTURE = Noellesroles.id("textures/gui/tomato_sauce.png");
    private static final int TEX_WIDTH = 800;
    private static final int TEX_HEIGHT = 450;
    private static final int TOTAL_TICKS = 60;
    private static final int FADE_IN_TICKS = 10;
    private static final int FADE_OUT_TICKS = 16;
    private static final float MAX_ALPHA = 0.88F;

    public static void register() {
        CommonHudRenderCallback.EVENT.register(TomatoSauceHud::render);
    }

    private static void render(FakeGuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        MobEffectInstance effect = client.player.getEffect(ModEffects.TOMATO_SAUCE);
        if (effect == null) {
            return;
        }
        float alpha = overlayAlpha(effect.getDuration(), deltaTracker.getGameTimeDeltaPartialTick(true));
        if (alpha <= 0.01F) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        graphics.blit(TEXTURE, 0, 0, width, height, 0.0F, 0.0F, TEX_WIDTH, TEX_HEIGHT, TEX_WIDTH, TEX_HEIGHT);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    private static float overlayAlpha(int remainingTicks, float partialTick) {
        float remaining = remainingTicks - partialTick;
        float elapsed = TOTAL_TICKS - remaining;
        float fade;
        if (elapsed < FADE_IN_TICKS) {
            fade = Mth.clamp(elapsed / FADE_IN_TICKS, 0.0F, 1.0F);
        } else if (remaining < FADE_OUT_TICKS) {
            fade = Mth.clamp(remaining / FADE_OUT_TICKS, 0.0F, 1.0F);
        } else {
            fade = 1.0F;
        }
        return fade * MAX_ALPHA;
    }
}
