package org.agmas.noellesroles.client.widget;

import io.wifi.starrailexpress.util.ShopEntry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.packet.ExecutionerSelectTargetC2SPacket;
import org.agmas.noellesroles.roles.executioner.ExecutionerPlayerComponent;
import org.jetbrains.annotations.NotNull;

/**
 * Executioner选择目标的UI Widget
 * 显示可选择的平民玩家，点击后发送选择请求
 */
public class ExecutionerPlayerWidget extends Button {
    public final AbstractClientPlayer targetCandidate;

    public ExecutionerPlayerWidget(int x, int y, @NotNull AbstractClientPlayer targetCandidate, int index) {
        super(x, y, 16, 16, targetCandidate.getName(), (a) -> {
            // 检查是否启用了手动选择目标功能
            if (!NoellesRolesConfig.HANDLER.instance().executionerCanSelectTarget) {
                return; // 如果未启用，则忽略点击事件
            }
            
            ExecutionerPlayerComponent component = ExecutionerPlayerComponent.KEY.get(Minecraft.getInstance().player);
            if (!component.targetSelected) {
                ClientPlayNetworking.send(new ExecutionerSelectTargetC2SPacket(targetCandidate.getUUID()));
            }
        }, DEFAULT_NARRATION);
        this.targetCandidate = targetCandidate;
    }

    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        ExecutionerPlayerComponent component = ExecutionerPlayerComponent.KEY.get(Minecraft.getInstance().player);
        
        // 如果还没有选择目标，显示可选择的玩家
        if (!component.targetSelected) {
            super.renderWidget(context, mouseX, mouseY, delta);
            context.blitSprite(ShopEntry.Type.POISON.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
            PlayerFaceRenderer.draw(context, targetCandidate.getSkin().texture(), this.getX(), this.getY(), 16);
            
            if (this.isHovered()) {
                this.drawShopSlotHighlight(context, this.getX(), this.getY(), 0);
                context.renderTooltip(Minecraft.getInstance().font, targetCandidate.getName(), 
                    this.getX() - 4 - Minecraft.getInstance().font.width(targetCandidate.getName()) / 2, 
                    this.getY() - 9);
            }
        }
        // 如果已经选择了目标，显示灰色
        else {
            super.renderWidget(context, mouseX, mouseY, delta);
            context.setColor(0.25f, 0.25f, 0.25f, 0.5f);
            context.blitSprite(ShopEntry.Type.POISON.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
            PlayerFaceRenderer.draw(context, targetCandidate.getSkin().texture(), this.getX(), this.getY(), 16);
            context.setColor(1f, 1f, 1f, 1f);
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