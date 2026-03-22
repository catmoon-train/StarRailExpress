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
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import org.agmas.noellesroles.packet.MorphC2SPacket;
import org.agmas.noellesroles.roles.morphling.MorphlingPlayerComponent;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * 变形者玩家选择组件
 * @author YourName
 */
public class MorphlingPlayerWidget extends Button{
    public final LimitedInventoryScreen screen;
    public final PlayerInfo disguiseTarget;
    private Component displayText = Component.empty();
    private java.util.List<net.minecraft.util.FormattedCharSequence> cachedLines = new java.util.ArrayList<>();

    public MorphlingPlayerWidget(LimitedInventoryScreen screen, int x, int y, @NotNull PlayerInfo disguiseTarget) {
        super(x, y, 16, 16, Component.nullToEmpty(disguiseTarget.getProfile().getName()), (a) -> {
            AbstractClientPlayer player = Minecraft.getInstance().player;
            if (player != null && (MorphlingPlayerComponent.KEY.get(player)).getMorphTicks() == 0) {
                ClientPlayNetworking.send(new MorphC2SPacket(disguiseTarget.getProfile().getId()));
            }
        }, DEFAULT_NARRATION);
        this.screen = screen;
        this.disguiseTarget = disguiseTarget;
        if (disguiseTarget.getGameMode() != GameType.ADVENTURE){
            setDisplayText(Component.translatable("hud.general.dead").withStyle(ChatFormatting.DARK_RED));
        }else {
            if (SREClient.gameComponent!=null && SREClient.gameComponent.getRole(disguiseTarget.getProfile().getId()) != null && SREClient.gameComponent.isKillerTeam(disguiseTarget.getProfile().getId())){
                setDisplayText(Component.translatable("hud.general.killer_friend").withStyle(ChatFormatting.GOLD));
            }
        }
    }

    @Override
    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        AbstractClientPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        
        MorphlingPlayerComponent component = MorphlingPlayerComponent.KEY.get(player);
        
        if (component.getMorphTicks() == 0) {
            super.renderWidget(context, mouseX, mouseY, delta);
            context.blitSprite(ShopEntry.Type.POISON.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
            PlayerFaceRenderer.draw(context, disguiseTarget.getSkin().texture(), this.getX(), this.getY(), 16);
            if (this.isHovered()) {
                this.drawShopSlotHighlight(context, this.getX(), this.getY(), 0);
                context.renderTooltip(Minecraft.getInstance().font, Component.nullToEmpty(disguiseTarget.getProfile().getName()), this.getX() - 4 - Minecraft.getInstance().font.width(disguiseTarget.getProfile().getName()) / 2, this.getY() - 9);
            }
        } else if (component.getMorphTicks() < 0) {
            super.renderWidget(context, mouseX, mouseY, delta);
            context.setColor(0.25f, 0.25f, 0.25f, 0.5f);
            context.blitSprite(ShopEntry.Type.POISON.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
            PlayerFaceRenderer.draw(context, disguiseTarget.getSkin().texture(), this.getX(), this.getY(), 16);
            if (this.isHovered()) {
                this.drawShopSlotHighlight(context, this.getX(), this.getY(), 0);
                context.renderTooltip(Minecraft.getInstance().font, Component.nullToEmpty(disguiseTarget.getProfile().getName()), this.getX() - 4 - Minecraft.getInstance().font.width(disguiseTarget.getProfile().getName()) / 2, this.getY() - 9);
            }
            context.setColor(1f, 1f, 1f, 1f);
            context.drawString(Minecraft.getInstance().font, String.valueOf(-component.getMorphTicks() / 20), this.getX(), this.getY(), Color.RED.getRGB(), true);
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
     * @param text 要显示的文本组件
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
    
    @Override
    public void renderString(GuiGraphics context, Font textRenderer, int color) {
        // 空实现，因为我们有自己的文本渲染逻辑
    }
}
