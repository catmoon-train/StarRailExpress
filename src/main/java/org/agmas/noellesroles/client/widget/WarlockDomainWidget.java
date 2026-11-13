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
import net.minecraft.network.chat.Component;
import io.wifi.starrailexpress.api.data.RoleData;
import org.agmas.noellesroles.role_data.killer.WarlockRoleData;
import org.agmas.noellesroles.packet.WarlockDomainC2SPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;

/**
 * 咒术师·领域展开目标选择组件。
 * 显示"已被诅咒且存活"的候选玩家头像；点击即请求对其展开领域（60s 冷却）。
 */
public class WarlockDomainWidget extends Button {
    public final LimitedInventoryScreen screen;
    public final PlayerInfo targetPlayer;

    public WarlockDomainWidget(LimitedInventoryScreen screen, int x, int y, @NotNull PlayerInfo targetPlayer) {
        super(x, y, 16, 16, Component.literal(targetPlayer.getProfile().getName()), (button) -> {
            AbstractClientPlayer player = Minecraft.getInstance().player;
            if (player != null && isReady(player)) {
                ClientPlayNetworking.send(new WarlockDomainC2SPacket(targetPlayer.getProfile().getId()));
            }
        }, DEFAULT_NARRATION);
        this.screen = screen;
        this.targetPlayer = targetPlayer;
    }

    private static boolean isReady(AbstractClientPlayer player) {
        WarlockRoleData comp = RoleData.getOptional(WarlockRoleData.class, player).orElse(null);
        long now = SREClient.getTicksFromGameStart();
        return comp != null && now >= comp.domainCooldownEndTick;
    }

    @Override
    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        AbstractClientPlayer player = Minecraft.getInstance().player;
        if (player == null)
            return;
        WarlockRoleData comp = RoleData.getOptional(WarlockRoleData.class, player).orElse(null);
        if (comp == null)
            return;
        long now = SREClient.getTicksFromGameStart();
        boolean ready = now >= comp.domainCooldownEndTick;

        super.renderWidget(context, mouseX, mouseY, delta);
        if (!ready) {
            context.setColor(0.25f, 0.25f, 0.25f, 0.5f);
        }
        context.blitSprite(ShopEntry.Type.WEAPON.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
        PlayerFaceRenderer.draw(context, targetPlayer.getSkin().texture(), this.getX(), this.getY(), 16);
        context.setColor(1f, 1f, 1f, 1f);

        // 与交换者一致：只标记客户端可见的杀手队友，不泄露其他阵营职业。
        if (SREClient.gameComponent != null && ModRoles.isVisibleKillerTeammate(
                SREClient.gameComponent.getRole(targetPlayer.getProfile().getId()))) {
            Font font = Minecraft.getInstance().font;
            Component text = Component.translatable("hud.general.killer_friend").withStyle(ChatFormatting.GOLD);
            var lines = font.split(text, 50);
            for (int i = 0; i < lines.size(); i++) {
                var line = lines.get(i);
                int lineWidth = font.width(line);
                int x = getX() + (getWidth() - lineWidth) / 2;
                int y = getY() + getHeight() + 4 + i * (font.lineHeight + 1);
                context.fill(x - 2, y - 1, x + lineWidth + 2, y + font.lineHeight + 1, 0x80000000);
                context.drawString(font, line, x, y, 0xFFFFFF, true);
            }
        }

        if (this.isHovered()) {
            context.renderTooltip(Minecraft.getInstance().font,
                    Component.nullToEmpty(targetPlayer.getProfile().getName()),
                    this.getX() - 4 - Minecraft.getInstance().font.width(targetPlayer.getProfile().getName()) / 2,
                    this.getY() - 9);
        }

        if (!ready) {
            int cooldownSeconds = (int) ((comp.domainCooldownEndTick - now + 19) / 20);
            context.drawString(Minecraft.getInstance().font, cooldownSeconds + "s",
                    this.getX(), this.getY(), Color.RED.getRGB(), true);
        }
    }

    @Override
    public void renderString(GuiGraphics context, Font textRenderer, int color) {
        // 空实现
    }
}
