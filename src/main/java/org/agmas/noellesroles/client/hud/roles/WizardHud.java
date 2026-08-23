/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREWorldBlackoutComponent;
import io.wifi.starrailexpress.client.SREClient;
import net.exmo.sre.camera.client.AdvancedCameraDirector;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import io.wifi.starrailexpress.api.data.RoleData;
import org.agmas.noellesroles.role_data.killer.WizardRoleData;
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

            WizardRoleData comp = RoleData.getOptional(WizardRoleData.class, client.player).orElse(null);
            if (comp == null) {
                return;
            }
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

            NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
            Component spell = Component.translatable(
                    "hud.noellesroles.wizard.spell." + comp.selectedSpell.name().toLowerCase());

            // 状态提示（技能名上方同行）
            Component statusLine = getSpellStatus(comp, client, config);
            int textRight = screenWidth - 8;
            if (statusLine != null) {
                context.drawString(font, statusLine,
                        textRight - font.width(statusLine), context.guiHeight() - 34, -1);
            }

            // 魔素 + 技能名
            Component manaText = Component.translatable("hud.noellesroles.wizard.mana",
                    Math.round(comp.mana), Math.round(maxMana), spell);
            context.drawString(font, manaText,
                    textRight - font.width(manaText), context.guiHeight() - 24, 0xFFE6D7FF);
        });
    }

    private static Component getSpellStatus(WizardRoleData comp, Minecraft client, NoellesRolesConfig config) {
        int mana = Math.round(comp.mana);
        Component cdText;

        switch (comp.selectedSpell) {
            case ARMOR -> {
                if (comp.armorUsed) {
                    return Component.translatable("hud.wizard.armor_used").withStyle(ChatFormatting.RED);
                }
                if (mana < config.wizardArmorMinMana) {
                    return Component.translatable("hud.wizard.no_mana", config.wizardArmorMinMana)
                            .withStyle(ChatFormatting.RED);
                }
                return Component.translatable("hud.wizard.ready").withStyle(ChatFormatting.GREEN);
            }
            case FROST -> {
                cdText = cooldownText(comp.frostCooldownTicks);
                if (cdText != null) {
                    return Component.translatable("hud.wizard.cooldown", cdText).withStyle(ChatFormatting.RED);
                }
                if (mana < config.wizardFrostMinMana) {
                    return Component.translatable("hud.wizard.no_mana", config.wizardFrostMinMana)
                            .withStyle(ChatFormatting.RED);
                }
                return Component.translatable("hud.wizard.ready").withStyle(ChatFormatting.GREEN);
            }
            case SHADOW -> {
                SREWorldBlackoutComponent blackout = SREWorldBlackoutComponent.KEY.get(client.player.level());
                boolean isBlackout = blackout != null && blackout.isBlackoutActive();
                cdText = cooldownText(comp.shadowCooldownTicks);
                if (cdText != null) {
                    return Component.translatable("hud.wizard.cooldown", cdText).withStyle(ChatFormatting.RED);
                }
                if (!isBlackout && mana < config.wizardShadowCost) {
                    return Component.translatable("hud.wizard.shadow_only_blackout").withStyle(ChatFormatting.RED);
                }
                if (!isBlackout) {
                    return Component.translatable("hud.wizard.shadow_only_blackout").withStyle(ChatFormatting.RED);
                }
                if (mana < config.wizardShadowCost) {
                    return Component.translatable("hud.wizard.no_mana", config.wizardShadowCost)
                            .withStyle(ChatFormatting.RED);
                }
                return Component.translatable("hud.wizard.ready").withStyle(ChatFormatting.GREEN);
            }
            case EXPLOSION -> {
                cdText = cooldownText(comp.explosionCooldownTicks);
                if (cdText != null) {
                    return Component.translatable("hud.wizard.cooldown", cdText).withStyle(ChatFormatting.RED);
                }
                if (mana < config.wizardExplosionMinMana) {
                    return Component.translatable("hud.wizard.no_mana", config.wizardExplosionMinMana)
                            .withStyle(ChatFormatting.RED);
                }
                return Component.translatable("hud.wizard.ready").withStyle(ChatFormatting.GREEN);
            }
            case BLINK -> {
                cdText = cooldownText(comp.blinkCooldownTicks);
                if (cdText != null) {
                    return Component.translatable("hud.wizard.cooldown", cdText).withStyle(ChatFormatting.RED);
                }
                if (mana < config.wizardBlinkCost) {
                    return Component.translatable("hud.wizard.no_mana", config.wizardBlinkCost)
                            .withStyle(ChatFormatting.RED);
                }
                return Component.translatable("hud.wizard.ready").withStyle(ChatFormatting.GREEN);
            }
        }
        return null;
    }

    private static Component cooldownText(int ticks) {
        if (ticks <= 0) return null;
        return Component.literal((ticks + 19) / 20 + "s");
    }
}
