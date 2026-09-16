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

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.util.ShopEntry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import org.agmas.noellesroles.packet.MorphC2SPacket;
import org.agmas.noellesroles.role_data.innocence.DivinerRoleData;

import java.awt.*;
import java.util.UUID;

/**
 * 占卜家背包界面里的玩家头像：点击选中该玩家作为占卜目标。
 */
public class DivinerPlayerWidget extends Button {
    public final LimitedInventoryScreen screen;
    public final UUID targetUUID;
    public final PlayerInfo targetPlayerEntry;

    public DivinerPlayerWidget(LimitedInventoryScreen screen, int x, int y, UUID targetUUID,
            PlayerInfo targetPlayerEntry, Level world, int index) {
        super(x, y, 16, 16, Component.literal(""), (a) -> {
            ClientPlayNetworking.send(new MorphC2SPacket(targetUUID));
        }, DEFAULT_NARRATION);
        this.screen = screen;
        this.targetPlayerEntry = targetPlayerEntry;
        this.targetUUID = targetUUID;
    }

    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.renderWidget(context, mouseX, mouseY, delta);
        final var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (targetPlayerEntry == null) {
            return;
        }

        // 检查皮肤纹理是否存在，避免空指针异常
        var skinTextures = targetPlayerEntry.getSkin();
        if (skinTextures == null || skinTextures.texture() == null) {
            return;
        }

        final var textRenderer = Minecraft.getInstance().font;
        if (textRenderer == null) {
            return;
        }

        var divinerData = RoleData.getOptional(DivinerRoleData.class, player);
        final var selected = divinerData.map(DivinerRoleData::getTarget).orElse(null);

        context.blitSprite(ShopEntry.Type.TOOL.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
        PlayerFaceRenderer.draw(context, skinTextures.texture(), this.getX(), this.getY(), 16);
        if (this.isHovered()) {
            this.drawShopSlotHighlight(context, this.getX(), this.getY(), 0);
            final var displayName = targetPlayerEntry.getProfile().getName();
            if (displayName != null) {
                context.renderTooltip(textRenderer, Component.nullToEmpty(displayName), this.getX() - 4 - 10,
                        this.getY() - 9);
            }
        }

        if (selected != null && selected.equals(targetUUID)) {
            var text = Component.translatable("widget.general.select");
            context.renderTooltip(textRenderer, text,
                    this.getX() - 4 - textRenderer.width(text) / 2, this.getY() - 9);
            this.drawShopSlotHighlight(context, this.getX(), this.getY(), 0);
        }
    }

    private void drawShopSlotHighlight(GuiGraphics context, int x, int y, int z) {
        int color = -1862287543;
        context.fillGradient(RenderType.guiOverlay(), x, y, x + 16, y + 14, color, color, z);
        context.fillGradient(RenderType.guiOverlay(), x, y + 14, x + 15, y + 15, color, color, z);
        context.fillGradient(RenderType.guiOverlay(), x, y + 15, x + 14, y + 16, color, color, z);
    }

    public void renderString(GuiGraphics context, Font textRenderer, int color) {
    }
}
