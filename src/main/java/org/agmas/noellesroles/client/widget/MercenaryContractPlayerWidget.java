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

package org.agmas.noellesroles.client.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.client.screen.MercenaryContractScreen;

import java.awt.*;
import java.util.UUID;

public class MercenaryContractPlayerWidget extends Button {
    private final MercenaryContractScreen screen;
    private final UUID playerUuid;
    private final String playerName;
    private final ResourceLocation skinTexture;
    private final int size;

    public MercenaryContractPlayerWidget(
            MercenaryContractScreen screen,
            int x,
            int y,
            int size,
            UUID playerUuid,
            String playerName,
            ResourceLocation skinTexture
    ) {
        super(x, y, size, size, Component.literal(playerName), b -> screen.onPlayerSelected(playerUuid, playerName), DEFAULT_NARRATION);
        this.screen = screen;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.skinTexture = skinTexture;
        this.size = size;
    }

    @Override
    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        int bgColor = this.isHovered() ? new Color(110, 75, 40, 210).getRGB() : new Color(65, 45, 25, 170).getRGB();
        context.fill(getX() - 2, getY() - 2, getX() + size + 2, getY() + size + 2, bgColor);

        int borderColor = this.isHovered() ? new Color(220, 180, 120).getRGB() : new Color(155, 115, 70).getRGB();
        context.renderOutline(getX() - 2, getY() - 2, size + 4, size + 4, borderColor);

        if (skinTexture != null) {
            PlayerFaceRenderer.draw(context, skinTexture, getX(), getY(), size);
        }

        if (this.isHovered()) {
            drawHighlight(context, getX(), getY(), 0);
            Font font = Minecraft.getInstance().font;
            context.renderTooltip(font, Component.literal(playerName), getX(), getY() - 12);
        }
    }

    private void drawHighlight(GuiGraphics context, int x, int y, int z) {
        int color = new Color(255, 215, 120, 100).getRGB();
        context.fillGradient(RenderType.guiOverlay(), x, y, x + size, y + size, color, color, z);
    }

    @Override
    public void renderString(GuiGraphics context, Font textRenderer, int color) {
    }
}
