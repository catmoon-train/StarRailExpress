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
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.GameType;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.packet.EmbalmerVoiceSwapC2SPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.EmbalmerRoleData;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 嬉命人·语音互换 玩家选择组件。
 * <p>
 * 交互与交换者完全一致：第一段点击记录「第一个人」，第二段点击把两人一起发往服务端，
 * 服务端建立双向配对后，两人的语音发声位置互换。
 */
public class EmbalmerVoiceSwapWidget extends Button {
    public final LimitedInventoryScreen screen;
    public final PlayerInfo voiceTarget;
    private Component displayText = Component.empty();
    private List<FormattedCharSequence> cachedLines = new ArrayList<>();

    /** 第一段点击选中的玩家；为 null 表示尚未选择。 */
    public static UUID playerChoiceOne = null;

    public EmbalmerVoiceSwapWidget(LimitedInventoryScreen screen, int x, int y, @NotNull PlayerInfo voiceTarget) {
        super(x, y, 16, 16, Component.nullToEmpty(voiceTarget.getProfile().getName()), (a) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) {
                return;
            }
            var data = RoleData.getNullable(EmbalmerRoleData.class, client.player);
            // 本地数据未同步到时不要静默吞掉点击：照常发包，由服务端做权威校验
            // （冷却中服务端会回提示；只有本地明确处于冷却才本地拦截，避免刷屏）
            if (data != null && data.voiceSwapCooldown > 0) {
                return;
            }
            if (client.player.level().getPlayerByUUID(voiceTarget.getProfile().getId()) == null) {
                return;
            }
            if (playerChoiceOne != null) {
                ClientPlayNetworking.send(new EmbalmerVoiceSwapC2SPacket(
                        playerChoiceOne, voiceTarget.getProfile().getId()));
                playerChoiceOne = null;
            } else {
                playerChoiceOne = voiceTarget.getProfile().getId();
            }
        }, DEFAULT_NARRATION);
        this.screen = screen;
        this.voiceTarget = voiceTarget;
        if (voiceTarget.getGameMode() != GameType.ADVENTURE) {
            setDisplayText(Component.translatable("hud.general.dead").withStyle(ChatFormatting.DARK_RED));
        } else if (SREClient.gameComponent != null
                && SREClient.gameComponent.getRole(voiceTarget.getProfile().getId()) != null
                && ModRoles.isVisibleKillerTeammate(
                        SREClient.gameComponent.getRole(voiceTarget.getProfile().getId()))) {
            setDisplayText(Component.translatable("hud.general.killer_friend").withStyle(ChatFormatting.GOLD));
        }
    }

    @Override
    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        var data = RoleData.getNullable(EmbalmerRoleData.class, client.player);
        int voiceSwapCooldown = data == null ? 0 : data.voiceSwapCooldown;

        super.renderWidget(context, mouseX, mouseY, delta);
        if (voiceSwapCooldown == 0) {
            context.blitSprite(ShopEntry.Type.POISON.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
            PlayerFaceRenderer.draw(context, voiceTarget.getSkin().texture(), this.getX(), this.getY(), 16);
            if (this.isHovered()) {
                this.drawSlotHighlight(context, this.getX(), this.getY());
                context.renderTooltip(client.font,
                        Component.nullToEmpty(voiceTarget.getProfile().getName()),
                        this.getX() - 4 - client.font.width(voiceTarget.getProfile().getName()) / 2,
                        this.getY() - 9);
            }
        }

        if (voiceSwapCooldown > 0 || client.player.hasEffect(ModEffects.SAFE_TIME)) {
            context.setColor(0.25f, 0.25f, 0.25f, 0.5f);
            context.blitSprite(ShopEntry.Type.POISON.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
            PlayerFaceRenderer.draw(context, voiceTarget.getSkin().texture(), this.getX(), this.getY(), 16);
            if (this.isHovered()) {
                this.drawSlotHighlight(context, this.getX(), this.getY());
                context.renderTooltip(client.font,
                        Component.nullToEmpty(voiceTarget.getProfile().getName()),
                        this.getX() - 4 - client.font.width(voiceTarget.getProfile().getName()) / 2,
                        this.getY() - 9);
            }
            context.setColor(1f, 1f, 1f, 1f);
            context.drawString(client.font, String.valueOf(voiceSwapCooldown / 20), this.getX(),
                    this.getY(), Color.RED.getRGB(), true);
        }

        // 已经作为「第一个人」被选中时给出高亮
        if (voiceTarget.getProfile().getId().equals(playerChoiceOne)) {
            this.drawSlotHighlight(context, this.getX(), this.getY());
        }

        renderDisplayText(context);
    }

    private void drawSlotHighlight(GuiGraphics context, int x, int y) {
        int color = -1862287543;
        context.fillGradient(RenderType.guiOverlay(), x, y, x + 16, y + 14, color, color, 0);
        context.fillGradient(RenderType.guiOverlay(), x, y + 14, x + 15, y + 15, color, color, 0);
        context.fillGradient(RenderType.guiOverlay(), x, y + 15, x + 14, y + 16, color, color, 0);
    }

    public void setDisplayText(Component text) {
        this.displayText = text;
        this.cachedLines.clear();
    }

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
            FormattedCharSequence line = cachedLines.get(i);
            int lineWidth = font.width(line);
            int x = this.getX() + (this.getWidth() - lineWidth) / 2;
            int y = startY + (i * lineHeight);

            context.fill(x - 2, y - 1, x + lineWidth + 2, y + font.lineHeight + 1, 0x80000000);
            context.drawString(font, line, x, y, 0xFFFFFF, true);
        }
    }

    @Override
    public void renderString(GuiGraphics context, Font textRenderer, int color) {
        // 自带文本渲染逻辑，这里留空
    }
}
