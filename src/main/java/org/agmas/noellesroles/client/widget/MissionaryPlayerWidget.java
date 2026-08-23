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
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.util.ShopEntry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import org.agmas.noellesroles.role_data.killer.MissionaryRoleData;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.packet.MissionaryConvertC2SPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * 传教士玩家选择组件 — 视觉与变形者一致：背景、头像、冷却变暗+倒计时。
 * 冷却使用传教士自身的 MissionaryRoleData#getCooldownRemaining()。
 */
public class MissionaryPlayerWidget extends Button {
    public final LimitedInventoryScreen screen;
    public final PlayerInfo convertTarget;
    private Component displayText = Component.empty();
    private java.util.List<net.minecraft.util.FormattedCharSequence> cachedLines = new java.util.ArrayList<>();

    public MissionaryPlayerWidget(LimitedInventoryScreen screen, int x, int y, @NotNull PlayerInfo convertTarget) {
        super(x, y, 16, 16, Component.nullToEmpty(convertTarget.getProfile().getName()), (a) -> {
            AbstractClientPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                var comp = RoleData.getOptional(MissionaryRoleData.class, player);
                if (comp.isPresent() && comp.get().getCooldownRemaining() <= 0 && !player.hasEffect(ModEffects.SAFE_TIME)) {
                    ClientPlayNetworking.send(new MissionaryConvertC2SPacket(convertTarget.getProfile().getId()));
                }
            }
        }, DEFAULT_NARRATION);
        this.screen = screen;
        this.convertTarget = convertTarget;
        if (convertTarget.getGameMode() != GameType.ADVENTURE) {
            setDisplayText(Component.translatable("hud.general.dead").withStyle(ChatFormatting.DARK_RED));
        } else {
            if (SREClient.gameComponent != null
                    && SREClient.gameComponent.getRole(convertTarget.getProfile().getId()) != null
                    && ModRoles.isVisibleKillerTeammate(
                        SREClient.gameComponent.getRole(convertTarget.getProfile().getId()))) {
                setDisplayText(Component.translatable("hud.general.killer_friend").withStyle(ChatFormatting.GOLD));
            }
        }
    }

    @Override
    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        AbstractClientPlayer player = Minecraft.getInstance().player;
        if (player == null)
            return;

        var component = RoleData.getOptional(MissionaryRoleData.class, player);
        long cooldown = component.map(MissionaryRoleData::getCooldownRemaining).orElse(Long.MAX_VALUE);

        if (cooldown <= 0 && !player.hasEffect(ModEffects.SAFE_TIME)) {
            // 可用状态
            super.renderWidget(context, mouseX, mouseY, delta);
            context.blitSprite(ShopEntry.Type.POISON.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
            PlayerFaceRenderer.draw(context, convertTarget.getSkin().texture(), this.getX(), this.getY(), 16);
            if (this.isHovered()) {
                this.drawShopSlotHighlight(context, this.getX(), this.getY(), 0);
                context.renderTooltip(Minecraft.getInstance().font,
                        Component.nullToEmpty(convertTarget.getProfile().getName()),
                        this.getX() - 4 - Minecraft.getInstance().font.width(convertTarget.getProfile().getName()) / 2,
                        this.getY() - 9);
            }
        } else if (cooldown > 0) {
            // 冷却状态：头像变暗 + 红色秒数倒计时
            super.renderWidget(context, mouseX, mouseY, delta);
            context.setColor(0.25f, 0.25f, 0.25f, 0.5f);
            context.blitSprite(ShopEntry.Type.POISON.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
            PlayerFaceRenderer.draw(context, convertTarget.getSkin().texture(), this.getX(), this.getY(), 16);
            if (this.isHovered()) {
                this.drawShopSlotHighlight(context, this.getX(), this.getY(), 0);
                context.renderTooltip(Minecraft.getInstance().font,
                        Component.nullToEmpty(convertTarget.getProfile().getName()),
                        this.getX() - 4 - Minecraft.getInstance().font.width(convertTarget.getProfile().getName()) / 2,
                        this.getY() - 9);
            }
            context.setColor(1f, 1f, 1f, 1f);
            int secs = (int) Math.ceil(cooldown / 20.0);
            context.drawString(Minecraft.getInstance().font, String.valueOf(secs),
                    this.getX(), this.getY(), Color.RED.getRGB(), true);
        }

        // 渲染下方的文字
        renderDisplayText(context);
    }

    private void drawShopSlotHighlight(GuiGraphics context, int x, int y, int z) {
        int color = -1862287543;
        context.fillGradient(RenderType.guiOverlay(), x, y, x + 16, y + 14, color, color, 0);
        context.fillGradient(RenderType.guiOverlay(), x, y + 14, x + 15, y + 15, color, color, 0);
        context.fillGradient(RenderType.guiOverlay(), x, y + 15, x + 14, y + 16, color, color, 0);
    }

    /**
     * 设置要显示的文本
     */
    public void setDisplayText(Component text) {
        this.displayText = text;
        this.cachedLines.clear();
    }

    /**
     * 渲染显示文本
     */
    private void renderDisplayText(GuiGraphics context) {
        if (displayText == null || displayText.getString().isEmpty()) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        int maxWidth = 50;
        int lineHeight = font.lineHeight + 1;
        int yOffset = 4;

        if (cachedLines.isEmpty()) {
            cachedLines = font.split(displayText, maxWidth);
        }

        int startY = this.getY() + this.getHeight() + yOffset;

        for (int i = 0; i < cachedLines.size(); i++) {
            net.minecraft.util.FormattedCharSequence line = cachedLines.get(i);
            int lineWidth = font.width(line);
            int x = this.getX() + (this.getWidth() - lineWidth) / 2;
            int y = startY + (i * lineHeight);

            context.fill(x - 2, y - 1, x + lineWidth + 2, y + font.lineHeight + 1, 0x80000000);
            context.drawString(font, line, x, y, 0xFFFFFF, true);
        }
    }

    @Override
    public void renderString(GuiGraphics context, Font textRenderer, int color) {
        // 空实现，因为我们有自己的文本渲染逻辑
    }
}
