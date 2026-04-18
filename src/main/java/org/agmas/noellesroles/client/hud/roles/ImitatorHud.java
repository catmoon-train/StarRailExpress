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
import org.agmas.noellesroles.game.roles.killer.imitator.ImitatorPlayerComponent;
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
            int x = screenWidth - 170;
            int y = screenHeight - 120;
            Font font = client.font;

            // ==================== Title ====================
            Component title = Component.translatable("role.noellesroles.imitator")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
            context.drawString(font, title, x, y, 0xAA0000);

            // ==================== 复制冷却 / 复制模式 ====================
            y += 14;
            if (comp.isCopyMode) {
                if (comp.copyActionCooldown > 0) {
                    Component cdText = Component.translatable("hud.noellesroles.imitator.copy_mode_cooldown",
                            String.format("%.0f", comp.copyActionCooldown / 20.0f));
                    context.drawString(font, cdText, x, y, CommonColors.RED);
                } else {
                    Component modeText = Component.translatable("hud.noellesroles.imitator.copy_mode_ready");
                    context.drawString(font, modeText, x, y, 0xFFFF55);
                }
            } else {
                Component readyText = Component.translatable("hud.noellesroles.imitator.ready",
                        NoellesrolesClient.abilityBind.getTranslatedKeyMessage());
                context.drawString(font, readyText, x, y, CommonColors.GREEN);
            }

            // ==================== 充能进度 ====================
            if (comp.isCharging) {
                y += 14;
                int pct = (int) ((float) comp.chargeTicks / ImitatorPlayerComponent.MAX_CHARGE_TICKS * 100);
                Component chargeText = Component.translatable("hud.noellesroles.imitator.charging", pct);
                context.drawString(font, chargeText, x, y, 0xFFAA00);
            }

            // ==================== 拳击手无敌 ====================
            if (comp.imitBoxerInvulnTicks > 0) {
                y += 14;
                Component boxerText = Component.translatable("hud.noellesroles.imitator.boxer_shield",
                        String.format("%.1f", comp.imitBoxerInvulnTicks / 20.0f));
                context.drawString(font, boxerText, x, y, 0xFFD700);
            }

            // ==================== 临时能力 ====================
            if (comp.tempCopiedRoleId != null) {
                y += 14;
                String roleName = comp.tempCopiedRoleId.getPath();
                int tempCd = comp.tempSkillCooldown;
                String cdStr = tempCd > 0 ? " [" + ((tempCd + 19) / 20) + "s]" : "";
                boolean isTempActive = comp.useTemp;
                String prefix = isTempActive ? "▶ " : "  ";
                Component tempText = Component.translatable("hud.noellesroles.imitator.temp_ability",
                        prefix + roleName, comp.tempCopiedUsesRemaining + cdStr);
                context.drawString(font, tempText, x, y, isTempActive ? 0x55FF55 : 0xAAFFAA);
            }

            // ==================== 召回者标记 ====================
            if (comp.imitRecallerPlaced) {
                y += 14;
                Component recText = Component.translatable("hud.noellesroles.imitator.recaller_marked");
                context.drawString(font, recText, x, y, 0x87CEEB);
            }

            // ==================== 槽位显示 ====================
            y += 14;
            Component slotTitle = Component.translatable("hud.noellesroles.imitator.slots")
                    .withStyle(ChatFormatting.GRAY);
            context.drawString(font, slotTitle, x, y, 0xAAAAAA);

            // 复制模式行
            y += 12;
            if (comp.isCopyMode) {
                context.drawString(font, Component.translatable("hud.noellesroles.imitator.copy_mode_slot"), x, y, 0xFFFF55);
            } else {
                context.drawString(font, Component.translatable("hud.noellesroles.imitator.copy_mode_slot_inactive"), x, y, 0x777755);
            }

            for (int i = 0; i < ImitatorPlayerComponent.MAX_SLOTS; i++) {
                y += 12;
                ResourceLocation slotRole = comp.getSlotRoleId(i);
                boolean isActiveSlot = !comp.useTemp && (i == comp.activeSlotIndex);
                int color;
                Component slotText;

                if (slotRole != null) {
                    String name = slotRole.getPath();
                    int cd = comp.getSlotCooldown(i);
                    String cdStr = cd > 0 ? " [" + ((cd + 19) / 20) + "s]" : "";
                    String slotPrefix = isActiveSlot ? "> " : "  ";
                    slotText = Component.literal(slotPrefix + (i + 1) + ": " + name + " [∞]" + cdStr);
                    color = isActiveSlot ? (cd > 0 ? 0xFFAA00 : 0x55FF55) : 0xCCCCCC;
                } else {
                    String slotPrefix = isActiveSlot ? "> " : "  ";
                    slotText = Component.translatable("hud.noellesroles.imitator.slot_empty_short",
                            slotPrefix + (i + 1));
                    color = isActiveSlot ? 0x888888 : 0x555555;
                }

                context.drawString(font, slotText, x, y, color);
            }
        });
    }
}
