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
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import org.agmas.noellesroles.game.roles.killer.party.PartyPlayerComponent;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.packet.PartyKillerC2SPacket;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 派对狂玩家选择组件 - 点击玩家头像释放氦气变声技能
 */
public class PartyKillerPlayerWidget extends Button {
    public final LimitedInventoryScreen screen;
    public final PlayerInfo targetInfo;
    private Component displayText = Component.empty();
    private List<net.minecraft.util.FormattedCharSequence> cachedLines = new ArrayList<>();

    public PartyKillerPlayerWidget(LimitedInventoryScreen screen, int x, int y, @NotNull PlayerInfo targetInfo) {
        super(x, y, 16, 16, Component.nullToEmpty(targetInfo.getProfile().getName()), (btn) -> {
            AbstractClientPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                // 检查冷却
                PartyPlayerComponent comp = PartyPlayerComponent.KEY.get(player);
                SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(player);
                if (ability.canUseAbility()) {
                    ClientPlayNetworking.send(new PartyKillerC2SPacket(targetInfo.getProfile().getId()));
                }
            }
        }, DEFAULT_NARRATION);
        this.screen = screen;
        this.targetInfo = targetInfo;
        updateDisplayText();
    }

    private void updateDisplayText() {
        if (targetInfo.getGameMode() != GameType.ADVENTURE) {
            setDisplayText(Component.translatable("hud.general.dead").withStyle(ChatFormatting.DARK_RED));
        } else {
            if (SREClient.gameComponent != null
                    && SREClient.gameComponent.getRole(targetInfo.getProfile().getId()) != null) {
                if (org.agmas.noellesroles.role.ModRoles.isVisibleKillerTeammate(
                        SREClient.gameComponent.getRole(targetInfo.getProfile().getId()))) {
                    setDisplayText(Component.translatable("hud.general.killer_friend").withStyle(ChatFormatting.GOLD));
                }
            }
        }
    }

    @Override
    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        AbstractClientPlayer player = Minecraft.getInstance().player;
        if (player == null)
            return;

        SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(player);
        boolean canUse = ability.canUseAbility();
        boolean onCooldown = ability.cooldown > 0 || player.hasEffect(ModEffects.SAFE_TIME);

        // 渲染背景
        context.blitSprite(ShopEntry.Type.POISON.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);

        // 渲染玩家头像
        PlayerFaceRenderer.draw(context, targetInfo.getSkin().texture(), this.getX(), this.getY(), 16);

        if (canUse) {
            // 技能可用时的悬浮提示
            if (this.isHovered()) {
                this.drawShopSlotHighlight(context, this.getX(), this.getY(), 0);
                context.renderTooltip(Minecraft.getInstance().font,
                        Component.nullToEmpty(targetInfo.getProfile().getName()),
                        this.getX() - 4 - Minecraft.getInstance().font.width(targetInfo.getProfile().getName()) / 2,
                        this.getY() - 9);
            }
        } else {
            // 冷却中时的效果
            context.setColor(0.25f, 0.25f, 0.25f, 0.5f);
            context.fill(this.getX(), this.getY(), this.getX() + 16, this.getY() + 16, 0x80000000);
            context.setColor(1f, 1f, 1f, 1f);

            if (this.isHovered()) {
                this.drawShopSlotHighlight(context, this.getX(), this.getY(), 0);
                Component tooltip = onCooldown ? Component.translatable("hud.general.cooldown", ability.cooldown / 20)
                        : Component.translatable("hud.general.dead");
                context.renderTooltip(Minecraft.getInstance().font, tooltip,
                        this.getX() - 4 - Minecraft.getInstance().font.width(tooltip) / 2, this.getY() - 9);
            }

            // 显示冷却秒数
            if (onCooldown) {
                context.drawString(Minecraft.getInstance().font, String.valueOf(ability.cooldown / 20),
                        this.getX(), this.getY(), Color.RED.getRGB(), true);
            }
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
        // 空实现，使用自定义渲染
    }
}
