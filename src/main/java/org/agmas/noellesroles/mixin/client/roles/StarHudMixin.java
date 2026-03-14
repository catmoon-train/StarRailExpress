package org.agmas.noellesroles.mixin.client.roles;

import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.component.SuperStarPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
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
 * 明星 HUD 显示
 *
 * 显示：
 * - 发光中状态（主动技能触发时）
 * - 主动技能冷却时间或就绪提示
 */
@Mixin(Gui.class)
public abstract class StarHudMixin {
    
    @Shadow 
    public abstract Font getFont();

    @Inject(method = "render", at = @At("TAIL"))
    public void renderStarHud(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        if(client.player.isSpectator()) return;
        // 检查玩家是否是明星
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(client.level);
        if (!gameWorld.isRole(client.player, ModRoles.SUPERSTAR)) return;
        
        // 获取明星组件
        SuperStarPlayerComponent starComp = ModComponents.STAR.get(client.player);
        if (!starComp.isActive) return;
        
        Font textRenderer = this.getFont();
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        
        // 基础Y位置（屏幕中下方）
        int baseX = 10;
        int baseY = screenHeight - 80;
        
        // ==================== 显示角色名称 ====================
        Component titleText = Component.translatable("hud.noellesroles.star.title")
            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        int titleWidth = textRenderer.width(titleText);
        context.drawString(textRenderer, titleText,
            (screenWidth - titleWidth) / 2, baseY - 20, 0xFFD700);
        
        // ==================== 显示发光状态（仅在发光时显示） ====================
        if (starComp.isGlowing) {
            Component glowText = Component.translatable("hud.noellesroles.star.glowing")
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
            int glowWidth = textRenderer.width(glowText);
            context.drawString(textRenderer, glowText,
                (screenWidth - glowWidth) / 2, baseY, 0xFFFF00);
        }
        
        // ==================== 显示技能状态 ====================
        int abilityY = starComp.isGlowing ? baseY + 12 : baseY;
        Component abilityText;
        if (starComp.abilityCooldown > 0) {
            // 冷却中
            abilityText = Component.translatable("hud.noellesroles.star.cooldown",
                String.format("%.0f", starComp.getCooldownSeconds()))
                .withStyle(ChatFormatting.RED);
        } else {
            // 就绪
            abilityText = Component.translatable("hud.noellesroles.star.ready")
                .withStyle(ChatFormatting.GREEN);
        }
        int abilityWidth = textRenderer.width(abilityText);
        context.drawString(textRenderer, abilityText,
            (screenWidth - abilityWidth) / 2, abilityY,
            starComp.abilityCooldown > 0 ? 0xFF5555 : 0x55FF55);
    }
}