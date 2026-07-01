package org.agmas.noellesroles.client.widget;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

import org.agmas.noellesroles.game.roles.innocence.voodoo.VoodooPlayerComponent;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.packet.MorphC2SPacket;

import java.awt.*;
import java.util.UUID;

public class LengxiaoPlayerWidget extends Button {
    public final LimitedInventoryScreen screen;
    public final UUID targetUUID;
    public final PlayerInfo targetPlayer;
    private Component displayText = Component.empty();
    private java.util.List<net.minecraft.util.FormattedCharSequence> cachedLines = new java.util.ArrayList<>();

    public LengxiaoPlayerWidget(LimitedInventoryScreen screen, int x, int y, UUID targetUUID,
            PlayerInfo targetPlayerEntry, Level world, int index) {
        super(x, y, 16, 16, Component.literal(""), (a) -> {
            ClientPlayNetworking.send(new MorphC2SPacket(targetUUID));
        }, DEFAULT_NARRATION);
        this.screen = screen;
        this.targetPlayer = targetPlayerEntry;
        this.targetUUID = targetUUID;
        if (targetPlayer.getGameMode() != GameType.ADVENTURE) {
            setDisplayText(Component.translatable("hud.general.dead").withStyle(ChatFormatting.DARK_RED));
        } else {
            if (SREClient.gameComponent != null
                    && SREClient.gameComponent.getRole(targetPlayer.getProfile().getId()) != null
                    && org.agmas.noellesroles.role.ModRoles.isVisibleKillerTeammate(
                            SREClient.gameComponent.getRole(targetPlayer.getProfile().getId()))) {
                setDisplayText(Component.translatable("hud.general.killer_friend").withStyle(ChatFormatting.GOLD));
            }
        }
    }

    /**
     * 设置要显示的文本
     * 
     * @param text 要显示的文本组件
     */
    public void setDisplayText(Component text) {
        this.displayText = text;
        this.cachedLines.clear();
    }

    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.renderWidget(context, mouseX, mouseY, delta);
        final var player = Minecraft.getInstance().player;
        if (player == null)
            return;
        if (targetPlayer == null)
            return;

        VoodooPlayerComponent voodooPlayerComponent = (VoodooPlayerComponent) VoodooPlayerComponent.KEY.get(player);

        final var abilityPlayerComponent = SREAbilityPlayerComponent.KEY.get(player);
        if (abilityPlayerComponent == null)
            return;
        final var target = voodooPlayerComponent.target;
        if (target == null)
            return;

        // 检查皮肤纹理是否存在，避免空指针异常
        var skinTextures = targetPlayer.getSkin();
        if (skinTextures == null || skinTextures.texture() == null)
            return;

        final var textRenderer = Minecraft.getInstance().font;
        if (textRenderer == null)
            return;
        if (abilityPlayerComponent.cooldown == 0 && !player.hasEffect(ModEffects.SAFE_TIME)) {
            context.blitSprite(ShopEntry.Type.TOOL.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
            PlayerFaceRenderer.draw(context, skinTextures.texture(), this.getX(), this.getY(), 16);
            if (this.isHovered()) {
                this.drawShopSlotHighlight(context, this.getX(), this.getY(), 0);
                final var displayName = targetPlayer.getProfile().getName();
                if (displayName != null) {
                    context.renderTooltip(textRenderer, Component.nullToEmpty(displayName), this.getX() - 4 - 10,
                            this.getY() - 9);
                }
            }

            if (target.equals(targetUUID)) {
                var text = Component.translatable("widget.general.select");
                context.renderTooltip(textRenderer, text,
                        this.getX() - 4 - textRenderer.width(text) / 2, this.getY() - 9);
                this.drawShopSlotHighlight(context, this.getX(), this.getY(), 0);
            }
        }

        if (abilityPlayerComponent.cooldown > 0) {
            context.setColor(0.25f, 0.25f, 0.25f, 0.5f);
            context.blitSprite(ShopEntry.Type.TOOL.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
            PlayerFaceRenderer.draw(context, skinTextures.texture(), this.getX(), this.getY(), 16);
            if (this.isHovered()) {
                this.drawShopSlotHighlight(context, this.getX(), this.getY(), 0);
            }

            if (target.equals(targetUUID)) {
                var text = Component.translatable("widget.general.selected");
                context.renderTooltip(textRenderer, text,
                        this.getX() - 4 - textRenderer.width(text) / 2, this.getY() - 9);
                this.drawShopSlotHighlight(context, this.getX(), this.getY(), 0);
            }
            context.setColor(1f, 1f, 1f, 1f);
            context.drawString(textRenderer, abilityPlayerComponent.cooldown / 20 + "", this.getX(), this.getY(),
                    Color.RED.getRGB(), true);

        }
        renderDisplayText(context);
    }

    /**
     * 渲染显示文本
     */
    private void renderDisplayText(GuiGraphics context) {
        if (displayText == null || displayText.getString().isEmpty()) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        int maxWidth = 50; // 最大宽度
        int lineHeight = font.lineHeight + 1; // 行高
        int yOffset = 4; // 距离widget的垂直偏移

        // 如果缓存为空，重新计算分行
        if (cachedLines.isEmpty()) {
            cachedLines = font.split(displayText, maxWidth);
        }

        // 计算起始Y位置（widget底部 + 偏移）
        int startY = this.getY() + this.getHeight() + yOffset;

        // 居中渲染每一行
        for (int i = 0; i < cachedLines.size(); i++) {
            net.minecraft.util.FormattedCharSequence line = cachedLines.get(i);
            int lineWidth = font.width(line);
            int x = this.getX() + (this.getWidth() - lineWidth) / 2; // 水平居中
            int y = startY + (i * lineHeight);

            // 绘制背景半透明矩形
            context.fill(x - 2, y - 1, x + lineWidth + 2, y + font.lineHeight + 1, 0x80000000);

            // 绘制文字
            context.drawString(font, line, x, y, 0xFFFFFF, true);
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
