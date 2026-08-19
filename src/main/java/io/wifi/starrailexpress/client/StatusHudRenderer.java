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

package io.wifi.starrailexpress.client;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public class StatusHudRenderer {
    private static final ResourceLocation AIR_SPRITE = ResourceLocation.withDefaultNamespace("hud/air");
    private static final ResourceLocation AIR_BURSTING_SPRITE = ResourceLocation
            .withDefaultNamespace("hud/air_bursting");

    public static void renderOxygen(GuiGraphics context, LocalPlayer self, float delta) {
        if (SREClient.areaComponent == null)
            return;
        if (!SREClient.areaComponent.areasSettings.enableOxygenDrowning)
            return;
        Minecraft client = Minecraft.getInstance();
        Player player = client.gui.getCameraPlayer();
        if (player != null) {
            if (player.isCreative() || player.isSpectator())
                return;
            int m = context.guiWidth() / 2 - 91;
            int n = context.guiHeight() - 39;
            int r = n - 10;

            client.getProfiler().popPush("air");
            int u = player.getMaxAirSupply();
            int v = Math.min(player.getAirSupply(), u);
            if (player.isEyeInFluid(FluidTags.WATER) || v < u) {
                int x = Mth.ceil((double) (v - 2) * (double) 10.0F / (double) u);
                int y = Mth.ceil((double) v * (double) 10.0F / (double) u) - x;
                RenderSystem.enableBlend();

                for (int z = 0; z < x + y; ++z) {
                    if (z < x) {
                        context.blitSprite(AIR_SPRITE, m + z * 8, r, 9, 9);
                    } else {
                        context.blitSprite(AIR_BURSTING_SPRITE, m + z * 8, r, 9, 9);
                    }
                }

                RenderSystem.disableBlend();
            }

            client.getProfiler().pop();
        }
    }
}
