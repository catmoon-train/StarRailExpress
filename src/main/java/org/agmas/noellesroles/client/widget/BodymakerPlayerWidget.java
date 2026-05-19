package org.agmas.noellesroles.client.widget;

import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
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
import org.agmas.noellesroles.game.roles.neutral.mortician.MorticianPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * 葬仪选择玩家Widget
 * 显示在线玩家列表供葬仪选择
 */
public class BodymakerPlayerWidget extends Button {

    public final LimitedInventoryScreen screen;
    public final UUID targetUUID;
    public final PlayerInfo targetPlayerInfo;
    public final MorticianScreenCallback callback;

    public BodymakerPlayerWidget(@NotNull LimitedInventoryScreen screen, int x, int y, 
                                 @NotNull UUID targetUUID, @NotNull PlayerInfo targetPlayerInfo, 
                                 @NotNull MorticianScreenCallback callback) {
        super(x, y, 16, 16, Component.nullToEmpty(targetPlayerInfo.getProfile().getName()), (button) -> {
            if (Minecraft.getInstance().player == null) return;
            
            // 检查葬仪是否有冷却
            MorticianPlayerComponent component = ModComponents.MORTICIAN_BODYMAKER.get(Minecraft.getInstance().player);
            if (component == null) return;
            
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
        
        MorticianPlayerComponent component = ModComponents.MORTICIAN_BODYMAKER.get(Minecraft.getInstance().player);
        if (component == null) return;
        
        if (component.cooldown <= 0) {
            // 可以使用技能 - 正常显示
            context.blitSprite(ShopEntry.Type.POISON.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
            PlayerFaceRenderer.draw(context, targetPlayerInfo.getSkin().texture(), this.getX(), this.getY(), 16);
            if (this.isHovered()) {
                this.drawShopSlotHighlight(context, this.getX(), this.getY());
                context.renderTooltip(Minecraft.getInstance().font, Component.nullToEmpty(targetPlayerInfo.getProfile().getName()), 
                    this.getX() - 4 - Minecraft.getInstance().font.width(targetPlayerInfo.getProfile().getName()) / 2, 
                    this.getY() - 9);
            }
        } else {
            // 冷却中 - 显示灰色
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
            // 显示冷却时间
            context.drawString(Minecraft.getInstance().font, (component.cooldown / 20) + "", this.getX(), this.getY(), Color.RED.getRGB(), true);
        }
    }

    private void drawShopSlotHighlight(@NotNull GuiGraphics context, int x, int y) {
        int color = 0x80000000;
        context.fillGradient(net.minecraft.client.renderer.RenderType.guiOverlay(), x, y, x + 16, y + 14, color, color, 0);
        context.fillGradient(net.minecraft.client.renderer.RenderType.guiOverlay(), x, y + 14, x + 15, y + 15, color, color, 0);
        context.fillGradient(net.minecraft.client.renderer.RenderType.guiOverlay(), x, y + 15, x + 14, y + 16, color, color, 0);
    }
}
