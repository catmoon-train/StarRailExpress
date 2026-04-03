package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.CommonColors;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.roles.imitator.ImitatorPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

public class ImitatorHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.IMITATOR_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.isPlayerSpectator()) return;
            if (!SREClient.isPlayerAliveAndInSurvival()) return;

            ImitatorPlayerComponent comp = ImitatorPlayerComponent.KEY.get(client.player);

            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = screenWidth - 160;
            int y = screenHeight - 110;
            Font font = client.font;

            // ==================== Title ====================
            Component title = Component.translatable("role.noellesroles.imitator")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
            context.drawString(font, title, x, y, 0xAA0000);

            // ==================== Cooldown ====================
            y += 14;
            if (comp.cooldown > 0) {
                Component cdText = Component.translatable("hud.noellesroles.imitator.cooldown",
                        String.format("%.0f", comp.cooldown / 20.0f));
                context.drawString(font, cdText, x, y, CommonColors.RED);
            } else {
                Component readyText = Component.translatable("hud.noellesroles.imitator.ready",
                        NoellesrolesClient.abilityBind.getTranslatedKeyMessage());
                context.drawString(font, readyText, x, y, CommonColors.GREEN);
            }

            // ==================== Charging progress ====================
            if (comp.isCharging) {
                y += 14;
                int pct = (int) ((float) comp.chargeTicks / ImitatorPlayerComponent.MAX_CHARGE_TICKS * 100);
                Component chargeText = Component.translatable("hud.noellesroles.imitator.charging", pct);
                context.drawString(font, chargeText, x, y, 0xFFAA00);
            }

            // ==================== Temp copied ability ====================
            if (comp.tempCopiedRoleId != null) {
                y += 14;
                String roleName = comp.tempCopiedRoleId.getPath();
                Component tempText = Component.translatable("hud.noellesroles.imitator.temp_ability",
                        roleName, comp.tempCopiedUsesRemaining);
                context.drawString(font, tempText, x, y, 0x55FF55);
            }

            // ==================== Slot display ====================
            y += 14;
            Component slotTitle = Component.translatable("hud.noellesroles.imitator.slots")
                    .withStyle(ChatFormatting.GRAY);
            context.drawString(font, slotTitle, x, y, 0xAAAAAA);

            for (int i = 0; i < ImitatorPlayerComponent.MAX_SLOTS; i++) {
                y += 12;
                ResourceLocation slotRole = comp.getSlotRoleId(i);
                boolean isActive = (i == comp.activeSlotIndex);
                String prefix = isActive ? "> " : "  ";
                int color;
                Component slotText;

                if (slotRole != null) {
                    String name = slotRole.getPath();
                    boolean unlimited = comp.isSlotUnlimited(i);
                    if (unlimited) {
                        slotText = Component.literal(prefix + (i + 1) + ": " + name + " [∞]");
                    } else {
                        int uses = comp.getSlotUsesRemaining(i);
                        slotText = Component.literal(prefix + (i + 1) + ": " + name + " [" + uses + "]");
                    }
                    color = isActive ? 0x55FF55 : 0xCCCCCC;
                } else {
                    slotText = Component.translatable("hud.noellesroles.imitator.slot_empty_short",
                            prefix + (i + 1));
                    color = isActive ? 0x888888 : 0x555555;
                }

                context.drawString(font, slotText, x, y, color);
            }
        });
    }
}
