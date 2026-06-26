package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import net.exmo.sre.camera.client.AdvancedCameraDirector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.game.roles.killer.wizard.WizardPlayerComponent;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;

public class WizardHud {
    private static final ResourceLocation MANA_ICON = Noellesroles.id("stamina/mana_potion_icon");
    private static final int BAR_WIDTH = 120;
    private static final int BAR_HEIGHT = 4;
    private static final int ICON_SIZE = 9;
    private static final int ICON_GAP = 4;

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.WIZARD_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.options.hideGui || SREClient.isPlayerSpectator()
                    || AdvancedCameraDirector.shouldOverride()) {
                return;
            }
            if (!SREClient.shouldUseTrainHud()) {
                return;
            }
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(client.player.level());
            if (gameWorld == null || !gameWorld.isRunning()) {
                return;
            }
            if (!gameWorld.isRole(client.player, ModRoles.WIZARD)) {
                return;
            }

            WizardPlayerComponent comp = WizardPlayerComponent.KEY.get(client.player);
            Font font = client.font;
            int screenWidth = context.guiWidth();
            int barX = screenWidth / 2 - BAR_WIDTH / 2;
            int barY = context.guiHeight() - 47;
            int iconX = barX - ICON_SIZE - ICON_GAP;
            int iconY = barY - ICON_SIZE / 2 + BAR_HEIGHT / 2;

            context.blitSprite(MANA_ICON, iconX, iconY, ICON_SIZE, ICON_SIZE);

            float maxMana = Math.max(1f, comp.maxMana());
            float percent = Mth.clamp(comp.mana / maxMana, 0f, 1f);
            int fillWidth = Math.round(BAR_WIDTH * percent);

            context.fill(barX - 1, barY - 1, barX + BAR_WIDTH + 1, barY + BAR_HEIGHT + 1, 0xAA000000);
            context.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, 0x66000000);
            if (fillWidth > 0) {
                context.fill(barX, barY, barX + fillWidth, barY + BAR_HEIGHT, 0xFF8A4DFF);
            }

            Component spell = Component.translatable(
                    "hud.noellesroles.wizard.spell." + comp.selectedSpell.name().toLowerCase());
            Component cdText = getSpellCooldownText(comp, client);
            Component text;
            if (cdText != null) {
                text = Component.translatable("hud.noellesroles.wizard.mana_cd",
                        Math.round(comp.mana), Math.round(maxMana), spell, cdText);
            } else {
                text = Component.translatable("hud.noellesroles.wizard.mana",
                        Math.round(comp.mana), Math.round(maxMana), spell);
            }
            context.drawString(font, text, screenWidth - font.width(text) - 8, context.guiHeight() - 24, 0xFFE6D7FF);
        });
    }

    private static Component getSpellCooldownText(WizardPlayerComponent comp, Minecraft client) {
        NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
        return switch (comp.selectedSpell) {
            case FROST -> {
                if (comp.frostCooldownTicks > 0) {
                    yield Component.literal((comp.frostCooldownTicks + 19) / 20 + "s");
                }
                yield null;
            }
            case SHADOW -> {
                if (comp.shadowCooldownTicks > 0) {
                    yield Component.literal((comp.shadowCooldownTicks + 19) / 20 + "s");
                }
                yield null;
            }
            case EXPLOSION -> {
                if (comp.explosionCooldownTicks > 0) {
                    yield Component.literal((comp.explosionCooldownTicks + 19) / 20 + "s");
                }
                yield null;
            }
            case ARMOR -> {
                if (comp.armorUsed) {
                    yield Component.translatable("hud.wizard.armor_used");
                }
                if (comp.mana < config.wizardArmorMinMana) {
                    yield Component.translatable("hud.wizard.armor_no_mana");
                }
                if (client.player.getCooldowns().isOnCooldown(ModItems.WIZARD_STAFF)) {
                    float pct = client.player.getCooldowns().getCooldownPercent(ModItems.WIZARD_STAFF, 0f);
                    int sec = Math.max(1, Math.round(pct * 30));
                    yield Component.translatable("hud.wizard.armor_staff_cd", sec);
                }
                yield null;
            }
        };
    }
}
