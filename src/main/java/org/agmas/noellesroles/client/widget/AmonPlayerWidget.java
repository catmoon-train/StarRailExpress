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
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.role_data.neutral.AmonRoleData;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.packet.AmonSelectTargetC2SPacket;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * 阿蒙背包选目标组件：在物品栏中以可点击头像列出成熟宿主。
 * 点击即把该宿主锁定为待夺舍目标（进入「操纵」），随后按 G 直接夺舍（夺取全部物品并杀死）。
 */
public class AmonPlayerWidget extends Button {
    public final LimitedInventoryScreen screen;
    public final PlayerInfo targetPlayer;

    public AmonPlayerWidget(LimitedInventoryScreen screen, int x, int y, @NotNull PlayerInfo targetPlayer) {
        super(x, y, 16, 16, Component.literal(targetPlayer.getProfile().getName()), (button) -> {
            AbstractClientPlayer player = Minecraft.getInstance().player;
            if (player != null && !player.hasEffect(ModEffects.SAFE_TIME)) {
                ClientPlayNetworking.send(new AmonSelectTargetC2SPacket(targetPlayer.getProfile().getId()));
            }
        }, DEFAULT_NARRATION);
        this.screen = screen;
        this.targetPlayer = targetPlayer;
    }

    @Override
    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        AbstractClientPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        AmonRoleData comp = RoleData.getNullable(AmonRoleData.class, player);
        boolean possessed = comp != null && comp.clientPossessTarget != null
                && comp.clientPossessTarget.equals(targetPlayer.getProfile().getId());

        super.renderWidget(context, mouseX, mouseY, delta);
        context.blitSprite(ShopEntry.Type.WEAPON.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
        PlayerFaceRenderer.draw(context, targetPlayer.getSkin().texture(), this.getX(), this.getY(), 16);

        // 当前附身的目标用红框高亮。
        if (possessed) {
            context.renderOutline(this.getX() - 1, this.getY() - 1, 18, 18, Color.RED.getRGB());
        }

        if (this.isHovered()) {
            context.renderTooltip(Minecraft.getInstance().font,
                    Component.nullToEmpty(targetPlayer.getProfile().getName()),
                    this.getX() - 4 - Minecraft.getInstance().font.width(targetPlayer.getProfile().getName()) / 2,
                    this.getY() - 9);
        }
    }

    @Override
    public void renderString(GuiGraphics context, Font textRenderer, int color) {
        // 使用自定义渲染，无需默认文本。
    }
}
