package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.client.SREClient;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.ModRoles;

public class WardenHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.WARDEN_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || SREClient.isPlayerSpectator())
                return;

            org.agmas.noellesroles.game.roles.neutral.warden.WardenPlayerComponent comp =
                    org.agmas.noellesroles.game.roles.neutral.warden.WardenPlayerComponent.KEY.maybeGet(client.player).orElse(null);
            if (comp == null) return;

            int guiWidth = context.guiWidth();
            int guiHeight = context.guiHeight();
            int currentY = guiHeight - 30;

            // 审判阶段信息（击杀数/上限）
            if (comp.isInJudgment()) {
                Component judgmentLine = Component.translatable("hud.noellesroles.warden.judgment",
                        comp.getJudgmentKills(), comp.getMaxJudgmentKills());
                int judgmentX = guiWidth - client.font.width(judgmentLine) - 10;
                context.drawString(client.font, judgmentLine, judgmentX, currentY, 0xFF4444, true);
                currentY -= client.font.lineHeight + 2;
            }

            // 目标名字（目标死亡时覆盖为目标已死亡）
            if (comp.getTargetUuid() != null && !comp.getTargetName().isEmpty()) {
                if (comp.isTargetDead()) {
                    // 目标已死亡 - 覆盖目标显示
                    Component targetDeadLine = Component.translatable("hud.noellesroles.warden.target_dead")
                            .withStyle(ChatFormatting.RED);
                    int targetDeadX = guiWidth - client.font.width(targetDeadLine) - 10;
                    context.drawString(client.font, targetDeadLine, targetDeadX, currentY, 0xFF4444, true);
                } else {
                    Component targetLine = Component.translatable("hud.noellesroles.warden.target",
                            comp.getTargetName());
                    int targetX = guiWidth - client.font.width(targetLine) - 10;
                    context.drawString(client.font, targetLine, targetX, currentY, ModRoles.WARDEN.color(), true);
                }
                currentY -= client.font.lineHeight + 2;
            }

            // 技能冷却倒计时（审判阶段和安全时间内不显示）
            boolean inSafeTime = client.player.hasEffect(ModEffects.SAFE_TIME);
            if (!comp.isInJudgment() && !inSafeTime && client.level != null) {
                // 检查是否有假左轮
                boolean hasFakeRevolver = client.player.getInventory().items.stream()
                        .anyMatch(stack -> stack.is(ModItems.FAKE_REVOLVER));
                if (!hasFakeRevolver) {
                    // 没有假左轮时显示提示
                    Component noGunLine = Component.translatable("hud.noellesroles.warden.no_gun")
                            .withStyle(ChatFormatting.GOLD);
                    int noGunX = guiWidth - client.font.width(noGunLine) - 10;
                    context.drawString(client.font, noGunLine, noGunX, currentY, 0xFFAA00, true);
                    currentY -= client.font.lineHeight + 2;
                } else {
                    long cooldownTicks = comp.getSkillCooldownTicks();
                    if (cooldownTicks > 0) {
                        long remainingSeconds = cooldownTicks / 20;
                        Component cooldownLine = Component.translatable("tip.noellesroles.cooldown",
                                remainingSeconds);
                        int cooldownX = guiWidth - client.font.width(cooldownLine) - 10;
                        context.drawString(client.font, cooldownLine, cooldownX, currentY, 0xFF5555, true);
                        currentY -= client.font.lineHeight + 2;
                    } else {
                        // 技能就绪
                        Component readyLine = Component.translatable("hud.noellesroles.warden.ready")
                                .withStyle(ChatFormatting.GREEN);
                        int readyX = guiWidth - client.font.width(readyLine) - 10;
                        context.drawString(client.font, readyLine, readyX, currentY, 0x44FF44, true);
                        currentY -= client.font.lineHeight + 2;
                    }
                }
            }

            // 护盾数量（技能冷却下方）
            var armorPlayerComponent = SREArmorPlayerComponent.KEY.get(client.player);
            if (armorPlayerComponent != null && armorPlayerComponent.armor > 0) {
                Component armorLine = Component.translatable("hud.bartender.has_armor", armorPlayerComponent.armor)
                        .withStyle(ChatFormatting.GOLD);
                int armorX = guiWidth - client.font.width(armorLine) - 10;
                context.drawString(client.font, armorLine, armorX, currentY, 0xFFAA00, true);
                currentY -= client.font.lineHeight + 2;
            }
        });
    }
}
