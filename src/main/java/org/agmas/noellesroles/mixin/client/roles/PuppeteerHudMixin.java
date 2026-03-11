package org.agmas.noellesroles.mixin.client.roles;

import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.component.PuppeteerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import io.wifi.starrailexpress.cca.GameWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 傀儡师 HUD 显示
 * 
 * 显示：
 * - 当前阶段（收集者/傀儡大师）
 * - 收集的尸体数量和阈值
 * - 收集冷却/技能冷却
 * - 假人操控剩余时间
 */
@Mixin(Gui.class)
public abstract class PuppeteerHudMixin {

    @Shadow
    public abstract Font getFont();

    @Inject(method = "render", at = @At("TAIL"))
    public void renderPuppeteerHud(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        if(client.player.isSpectator()) return;

        // 获取傀儡师组件
        PuppeteerPlayerComponent puppeteerComp = ModComponents.PUPPETEER.get(client.player);

        // 检查玩家是否是傀儡师（包括操控假人时角色临时变更的情况）
        // 操控假人时角色会变成其他杀手，但 isActivePuppeteer() 仍然返回 true
        GameWorldComponent gameWorld = GameWorldComponent.KEY.get(client.level);
        final var role = gameWorld.getRole(client.player);
        if (role==null)return;
        if (!role.getIdentifier().equals(ModRoles.PUPPETEER_ID) && !puppeteerComp.isActivePuppeteer()) return;

        // 确保傀儡师已激活
        if (!puppeteerComp.isActivePuppeteer()) return;

        Font textRenderer = this.getFont();
        int screenHeight = client.getWindow().getGuiScaledHeight();

        // 左下角位置
        int baseX = 10;
        int baseY = screenHeight - 80;

        // ==================== 显示角色名称 ====================
        Component titleText = Component.translatable("hud.noellesroles.puppeteer.title")
            .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD);
        context.drawString(textRenderer, titleText, baseX, baseY - 25, 0x9400D3);

        // ==================== 显示阶段 ====================
        Component phaseText;
        if (puppeteerComp.phase == 1) {
            phaseText = Component.translatable("hud.noellesroles.puppeteer.phase1")
                .withStyle(ChatFormatting.LIGHT_PURPLE);
        } else {
            phaseText = Component.translatable("hud.noellesroles.puppeteer.phase2")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD);
        }
        context.drawString(textRenderer, phaseText, baseX, baseY - 12, 0xDA70D6);

        // ==================== 阶段一：收集者模式 ====================
        if (puppeteerComp.phase == 1) {
            // 计算阈值（总人数/6）
            int totalPlayers = client.getConnection().getListedOnlinePlayers().size();
            int threshold = Math.max(1, totalPlayers / 6);

            // 显示收集进度
            Component bodiesText = Component.translatable("hud.noellesroles.puppeteer.bodies_collected",
                puppeteerComp.collectedBodies, threshold)
                .withStyle(puppeteerComp.collectedBodies >= threshold ? ChatFormatting.GREEN : ChatFormatting.GRAY);
            context.drawString(textRenderer, bodiesText, baseX, baseY,
                puppeteerComp.collectedBodies >= threshold ? 0x55FF55 : 0xAAAAAA);

            // 显示收集冷却
            Component collectText;
            if (puppeteerComp.collectCooldown > 0) {
                collectText = Component.translatable("hud.noellesroles.puppeteer.collect_cooldown",
                    String.format("%.1f", puppeteerComp.getCollectCooldownSeconds()))
                    .withStyle(ChatFormatting.RED);
            } else {
                collectText = Component.translatable("hud.noellesroles.puppeteer.collect_ready")
                    .withStyle(ChatFormatting.GREEN);
            }
            context.drawString(textRenderer, collectText, baseX, baseY + 12,
                puppeteerComp.collectCooldown > 0 ? 0xFF5555 : 0x55FF55);

        // ==================== 阶段二：傀儡大师模式 ====================
        } else if (puppeteerComp.phase == 2) {
            // 如果正在操控假人
            if (puppeteerComp.isControllingPuppet) {
                // 显示操控剩余时间
                Component controlText = Component.translatable("hud.noellesroles.puppeteer.controlling",
                    String.format("%.0f", puppeteerComp.getPuppetControlSeconds()))
                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
                context.drawString(textRenderer, controlText, baseX, baseY, 0xFFFF00);

                // 显示返回本体提示
                Component returnHint = Component.translatable("message.noellesroles.puppeteer.returned_to_body_tip")
                    .withStyle(ChatFormatting.GOLD);
                context.drawString(textRenderer, returnHint, baseX, baseY + 12, 0xFFA500);
            } else {
                // 显示本体模式标识
                Component bodyModeText = Component.translatable("hud.noellesroles.puppeteer.body_mode")
                    .withStyle(ChatFormatting.GRAY);
                context.drawString(textRenderer, bodyModeText, baseX, baseY, 0x888888);

                // 显示技能冷却或就绪状态
                Component abilityText;
                int remainingPuppets = puppeteerComp.getRemainingPuppetUses();

                if (puppeteerComp.abilityCooldown > 0) {
                    abilityText = Component.translatable("hud.noellesroles.puppeteer.puppet_cooldown",
                        String.format("%.0f", puppeteerComp.getAbilityCooldownSeconds()))
                        .withStyle(ChatFormatting.RED);
                } else if (remainingPuppets > 0) {
                    abilityText = Component.translatable("hud.noellesroles.puppeteer.puppet_ready", remainingPuppets)
                        .withStyle(ChatFormatting.GREEN);
                } else {
                    abilityText = Component.translatable("hud.noellesroles.puppeteer.no_puppets")
                        .withStyle(ChatFormatting.DARK_GRAY);
                }
                context.drawString(textRenderer, abilityText, baseX, baseY + 12,
                    puppeteerComp.abilityCooldown > 0 ? 0xFF5555 : (remainingPuppets > 0 ? 0x55FF55 : 0x555555));
            }
        }
    }
}