package org.agmas.noellesroles.client.widget;

import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.widget.MorticianScreenCallback;

import java.util.UUID;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.neutral.mortician.MorticianBodyMakerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * 葬仪选择玩家Widget（参考变形者模式：冷却时也显示头像+倒计时）
 */
public class BodymakerPlayerWidget extends Button {

    public final LimitedInventoryScreen screen;
    public final UUID targetUUID;
    public final PlayerInfo targetPlayerInfo;
    public final MorticianScreenCallback callback;

    public BodymakerPlayerWidget(@NotNull LimitedInventoryScreen screen, int x, int y, 
                                 @NotNull UUID targetUUID, @NotNull PlayerInfo targetPlayerInfo, 
                                 @NotNull MorticianScreenCallback callback) {
        super(x, y, 16, 16, Component.empty(), (button) -> {
            if (Minecraft.getInstance().player == null) return;
            
            MorticianBodyMakerPlayerComponent component = ModComponents.MORTICIAN_BODYMAKER.get(Minecraft.getInstance().player);
            if (component == null) return;
            
            // 技能冷却时不允许选择
            if (component.cooldown <= 0) {
                callback.setSelectedPlayer(targetUUID);
            }
        }, DEFAULT_NARRATION);
        this.screen = screen;
        this.targetUUID = targetUUID;
        this.targetPlayerInfo = targetPlayerInfo;
        this.callback = callback;
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (Minecraft.getInstance().player == null) return;
        super.renderWidget(context, mouseX, mouseY, delta);
        
        MorticianBodyMakerPlayerComponent component = ModComponents.MORTICIAN_BODYMAKER.get(Minecraft.getInstance().player);
        if (component == null) return;

        // 造尸冷却时显示灰色 + 倒计时（完全类似变形者）
        if (component.bodyCreationCooldown > 0) {
            context.setColor(0.25f, 0.25f, 0.25f, 0.5f);
            context.blitSprite(ShopEntry.Type.POISON.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
            PlayerFaceRenderer.draw(context, targetPlayerInfo.getSkin().texture(), this.getX(), this.getY(), 16);
            if (this.isHovered()) {
                this.drawShopSlotHighlight(context, this.getX(), this.getY());
                context.renderTooltip(Minecraft.getInstance().font, Component.nullToEmpty(targetPlayerInfo.getProfile().getName()), 
                    this.getX() - 4 - Minecraft.getInstance().font.width(targetPlayerInfo.getProfile().getName()) / 2, 
                    this.getY() - 9);
            }
            context.setColor(1f, 1f, 1f, 1f);
            // 显示造尸冷却倒计时
            context.drawString(Minecraft.getInstance().font, String.valueOf(component.bodyCreationCooldown / 20),
                    this.getX(), this.getY(), Color.RED.getRGB(), true);
        }
        // 技能冷却时显示灰色 + 倒计时
        else if (component.cooldown > 0) {
            context.setColor(0.25f, 0.25f, 0.25f, 0.5f);
            context.blitSprite(ShopEntry.Type.POISON.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
            PlayerFaceRenderer.draw(context, targetPlayerInfo.getSkin().texture(), this.getX(), this.getY(), 16);
            if (this.isHovered()) {
                this.drawShopSlotHighlight(context, this.getX(), this.getY());
                context.renderTooltip(Minecraft.getInstance().font, Component.nullToEmpty(targetPlayerInfo.getProfile().getName()), 
                    this.getX() - 4 - Minecraft.getInstance().font.width(targetPlayerInfo.getProfile().getName()) / 2, 
                    this.getY() - 9);
            }
            context.setColor(1f, 1f, 1f, 1f);
            context.drawString(Minecraft.getInstance().font, String.valueOf(component.cooldown / 20),
                    this.getX(), this.getY(), Color.RED.getRGB(), true);
        }
        // 无冷却 - 正常显示
        else {
            context.blitSprite(ShopEntry.Type.POISON.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
            PlayerFaceRenderer.draw(context, targetPlayerInfo.getSkin().texture(), this.getX(), this.getY(), 16);
            if (this.isHovered()) {
                this.drawShopSlotHighlight(context, this.getX(), this.getY());
                context.renderTooltip(Minecraft.getInstance().font, Component.nullToEmpty(targetPlayerInfo.getProfile().getName()), 
                    this.getX() - 4 - Minecraft.getInstance().font.width(targetPlayerInfo.getProfile().getName()) / 2, 
                    this.getY() - 9);
            }
        }
    }

    private void drawShopSlotHighlight(@NotNull GuiGraphics context, int x, int y) {
        int color = 0x80000000;
        context.fillGradient(net.minecraft.client.renderer.RenderType.guiOverlay(), x, y, x + 16, y + 14, color, color, 0);
        context.fillGradient(net.minecraft.client.renderer.RenderType.guiOverlay(), x, y + 14, x + 15, y + 15, color, color, 0);
        context.fillGradient(net.minecraft.client.renderer.RenderType.guiOverlay(), x, y + 15, x + 14, y + 16, color, color, 0);
    }
}
