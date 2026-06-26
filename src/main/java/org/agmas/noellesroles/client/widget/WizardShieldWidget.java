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
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.game.roles.killer.wizard.WizardPlayerComponent;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.WizardShieldC2SPacket;
import org.jetbrains.annotations.NotNull;

/**
 * 巫师"盔甲护身"玩家选择组件：点击玩家头像，请求为其赋予护盾。
 * 当盔甲不可用时（已用完/魔素不足/法杖冷却），头像变暗并显示原因提示。
 */
public class WizardShieldWidget extends Button {
    public final LimitedInventoryScreen screen;
    public final PlayerInfo targetPlayer;

    public WizardShieldWidget(LimitedInventoryScreen screen, int x, int y, @NotNull PlayerInfo targetPlayer) {
        super(x, y, 16, 16, Component.literal(targetPlayer.getProfile().getName()), (button) -> {
            AbstractClientPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                WizardPlayerComponent comp = WizardPlayerComponent.KEY.get(player);
                if (comp.selectedSpell == WizardPlayerComponent.Spell.ARMOR && isArmorUsable(comp)) {
                    ClientPlayNetworking.send(new WizardShieldC2SPacket(targetPlayer.getProfile().getId()));
                }
            }
        }, DEFAULT_NARRATION);
        this.screen = screen;
        this.targetPlayer = targetPlayer;
    }

    private static boolean isArmorUsable(WizardPlayerComponent comp) {
        if (comp.armorUsed) return false;
        NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
        if (comp.mana < config.wizardArmorMinMana) return false;
        return true;
    }

    private static Component getArmorUnavailableReason(WizardPlayerComponent comp) {
        if (comp.armorUsed) {
            return Component.translatable("hud.wizard.armor_used");
        }
        NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
        if (comp.mana < config.wizardArmorMinMana) {
            return Component.translatable("hud.wizard.armor_no_mana");
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.player.getCooldowns().isOnCooldown(ModItems.WIZARD_STAFF)) {
            float pct = client.player.getCooldowns().getCooldownPercent(ModItems.WIZARD_STAFF, 0f);
            int sec = Math.max(1, Math.round(pct * 30));
            return Component.translatable("hud.wizard.armor_staff_cd", sec);
        }
        return null;
    }

    @Override
    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.renderWidget(context, mouseX, mouseY, delta);
        context.blitSprite(ShopEntry.Type.TOOL.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
        PlayerFaceRenderer.draw(context, targetPlayer.getSkin().texture(), this.getX(), this.getY(), 16);

        AbstractClientPlayer player = Minecraft.getInstance().player;
        boolean armorAvailable = false;
        Component reason = null;
        if (player != null) {
            WizardPlayerComponent comp = WizardPlayerComponent.KEY.get(player);
            armorAvailable = isArmorUsable(comp);
            if (!armorAvailable) {
                reason = getArmorUnavailableReason(comp);
            }
        }

        // 盔甲不可用时绘制半透明暗色覆盖
        if (!armorAvailable) {
            context.fillGradient(RenderType.guiOverlay(), this.getX(), this.getY(),
                    this.getX() + 16, this.getY() + 16, 0xAA000000, 0xAA000000, 0);
        }

        if (this.isHovered()) {
            int hoverColor = armorAvailable ? -1862287543 : 0x44FFFFFF;
            context.fillGradient(RenderType.guiOverlay(), this.getX(), this.getY(),
                    this.getX() + 16, this.getY() + 16, hoverColor, hoverColor, 0);
            // hover 时显示名字，不可用时附带原因
            if (!armorAvailable && reason != null) {
                context.renderTooltip(Minecraft.getInstance().font, reason,
                        this.getX() - 4 - Minecraft.getInstance().font.width(reason) / 2,
                        this.getY() - 9);
            } else {
                context.renderTooltip(Minecraft.getInstance().font,
                        Component.nullToEmpty(targetPlayer.getProfile().getName()),
                        this.getX() - 4 - Minecraft.getInstance().font.width(targetPlayer.getProfile().getName()) / 2,
                        this.getY() - 9);
            }
        }
    }

    @Override
    public void renderString(GuiGraphics context, Font textRenderer, int color) {
        // 自定义渲染，无需默认文字
    }
}
