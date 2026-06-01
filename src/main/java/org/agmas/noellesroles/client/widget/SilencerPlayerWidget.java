package org.agmas.noellesroles.client.widget;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
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
import org.agmas.noellesroles.game.roles.killer.silencer.SilencerPlayerComponent;
import org.agmas.noellesroles.packet.SilencerC2SPacket;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class SilencerPlayerWidget extends Button {
    public final LimitedInventoryScreen screen;
    public final PlayerInfo skillTarget;
    private Component displayText = Component.empty();
    private java.util.List<net.minecraft.util.FormattedCharSequence> cachedLines = new java.util.ArrayList<>();

    public SilencerPlayerWidget(LimitedInventoryScreen screen, int x, int y, @NotNull PlayerInfo skillTarget) {
        super(x, y, 16, 16, Component.nullToEmpty(skillTarget.getProfile().getName()), (a) -> {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                SilencerPlayerComponent component = SilencerPlayerComponent.KEY.get(player);
                if (component.skillCooldownTicks == 0 && component.phase == 0) {
                    ClientPlayNetworking.send(new SilencerC2SPacket(skillTarget.getProfile().getId()));
                }
            }
        }, DEFAULT_NARRATION);
        this.screen = screen;
        this.skillTarget = skillTarget;
        if (skillTarget.getGameMode() != GameType.ADVENTURE) {
            setDisplayText(Component.translatable("hud.general.dead").withStyle(ChatFormatting.DARK_RED));
        } else {
            if (SREClient.gameComponent != null
                    && SREClient.gameComponent.getRole(skillTarget.getProfile().getId()) != null
                    && org.agmas.noellesroles.role.ModRoles.isVisibleKillerTeammate(
                        SREClient.gameComponent.getRole(skillTarget.getProfile().getId()))) {
                setDisplayText(Component.translatable("hud.general.killer_friend").withStyle(ChatFormatting.GOLD));
            }
        }
    }

    @Override
    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;

        SilencerPlayerComponent component = SilencerPlayerComponent.KEY.get(player);

        if (component.skillCooldownTicks == 0 && component.phase == 0) {
            // Ready to use
            super.renderWidget(context, mouseX, mouseY, delta);
            context.blitSprite(io.wifi.starrailexpress.util.ShopEntry.Type.POISON.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
            PlayerFaceRenderer.draw(context, skillTarget.getSkin().texture(), this.getX(), this.getY(), 16);
            if (this.isHovered()) {
                this.drawShopSlotHighlight(context, this.getX(), this.getY(), 0);
                context.renderTooltip(Minecraft.getInstance().font,
                        Component.nullToEmpty(skillTarget.getProfile().getName()),
                        this.getX() - 4 - Minecraft.getInstance().font.width(skillTarget.getProfile().getName()) / 2,
                        this.getY() - 9);
            }
        } else if (component.skillCooldownTicks < 0) {
            // On cooldown
            super.renderWidget(context, mouseX, mouseY, delta);
            context.setColor(0.25f, 0.25f, 0.25f, 0.5f);
            context.blitSprite(io.wifi.starrailexpress.util.ShopEntry.Type.POISON.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
            PlayerFaceRenderer.draw(context, skillTarget.getSkin().texture(), this.getX(), this.getY(), 16);
            if (this.isHovered()) {
                this.drawShopSlotHighlight(context, this.getX(), this.getY(), 0);
                context.renderTooltip(Minecraft.getInstance().font,
                        Component.nullToEmpty(skillTarget.getProfile().getName()),
                        this.getX() - 4 - Minecraft.getInstance().font.width(skillTarget.getProfile().getName()) / 2,
                        this.getY() - 9);
            }
            context.setColor(1f, 1f, 1f, 1f);
            context.drawString(Minecraft.getInstance().font, String.valueOf(-component.skillCooldownTicks / 20),
                    this.getX(), this.getY(), Color.RED.getRGB(), true);
        }

        renderDisplayText(context);
    }

    private void drawShopSlotHighlight(GuiGraphics context, int x, int y, int z) {
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
        if (displayText == null || displayText.getString().isEmpty()) return;

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
    }
}
