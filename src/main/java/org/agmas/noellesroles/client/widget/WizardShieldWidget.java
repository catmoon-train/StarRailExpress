package org.agmas.noellesroles.client.widget;

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
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.game.roles.killer.wizard.WizardPlayerComponent;
import org.agmas.noellesroles.packet.WizardShieldC2SPacket;
import org.jetbrains.annotations.NotNull;

/**
 * 巫师“盔甲护身”玩家选择组件：点击玩家头像，请求为其赋予护盾。
 */
public class WizardShieldWidget extends Button {
    public final LimitedInventoryScreen screen;
    public final PlayerInfo targetPlayer;

    public WizardShieldWidget(LimitedInventoryScreen screen, int x, int y, @NotNull PlayerInfo targetPlayer) {
        super(x, y, 16, 16, Component.literal(targetPlayer.getProfile().getName()), (button) -> {
            AbstractClientPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                WizardPlayerComponent comp = WizardPlayerComponent.KEY.get(player);
                if (comp.selectedSpell == WizardPlayerComponent.Spell.ARMOR) {
                    ClientPlayNetworking.send(new WizardShieldC2SPacket(targetPlayer.getProfile().getId()));
                }
            }
        }, DEFAULT_NARRATION);
        this.screen = screen;
        this.targetPlayer = targetPlayer;
    }

    @Override
    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.renderWidget(context, mouseX, mouseY, delta);
        context.blitSprite(ShopEntry.Type.TOOL.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
        PlayerFaceRenderer.draw(context, targetPlayer.getSkin().texture(), this.getX(), this.getY(), 16);
        if (this.isHovered()) {
            int color = -1862287543;
            context.fillGradient(RenderType.guiOverlay(), this.getX(), this.getY(), this.getX() + 16, this.getY() + 16,
                    color, color, 0);
            context.renderTooltip(Minecraft.getInstance().font,
                    Component.nullToEmpty(targetPlayer.getProfile().getName()),
                    this.getX() - 4 - Minecraft.getInstance().font.width(targetPlayer.getProfile().getName()) / 2,
                    this.getY() - 9);
        }
    }

    @Override
    public void renderString(GuiGraphics context, Font textRenderer, int color) {
        // 自定义渲染，无需默认文字
    }
}
