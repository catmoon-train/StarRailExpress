package org.agmas.noellesroles.mixin.client.roles;

import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.component.PsychologistPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 心理学家 HUD Mixin
 * 
 * 显示心理学家的状态：
 * - 技能冷却
 * - 自己的san值状态
 * - 正在治疗时的进度
 */
@Mixin(Gui.class)
public class PsychologistHudMixin {
    
    @Inject(method = "render", at = @At("TAIL"))
    private void renderPsychologistHud(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        if(client.player.isSpectator()) return;
        
        // 检查是否是心理学家
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(client.level);
        if (!gameWorld.isRole(client.player, ModRoles.PSYCHOLOGIST)) return;
        
        // 检查玩家是否存活
        if (!GameUtils.isPlayerAliveAndSurvival(client.player)) return;
        
        // 获取心理学家组件
        PsychologistPlayerComponent psychComp = ModComponents.PSYCHOLOGIST.get(client.player);
        
        // 渲染位置 - 左下角
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int x = 10;
        int y = screenHeight - 80;
        
        Font textRenderer = client.font;
        
        // 标题
        Component titleText = Component.translatable("announcement.star.role.psychologist").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
        context.drawString(textRenderer, titleText, x, y, 0xFFFFFF);
        y += 12;
        
        // 检查自己的san值（游戏中san值范围是0.0-1.0，需要转换为百分比显示）
        SREPlayerMoodComponent selfMood = SREPlayerMoodComponent.KEY.get(client.player);
        float sanity = selfMood.getMood();  // 0.0 到 1.0
        int sanityPercent = (int)(sanity * 100);  // 转换为百分比
        ChatFormatting sanColor = sanity >= 0.99f ? ChatFormatting.GREEN :
                             sanity >= 0.5f ? ChatFormatting.YELLOW : ChatFormatting.RED;
        Component sanText = Component.literal("San: " + sanityPercent + "/100").withStyle(sanColor);
        context.drawString(textRenderer, sanText, x, y, 0xFFFFFF);
        y += 12;
        
        // 正在治疗中
        if (psychComp.isHealing) {
            int healedSeconds = (int) psychComp.getHealingSeconds();
            int totalSeconds = PsychologistPlayerComponent.HEALING_DURATION / 20;
            Component healingText = Component.translatable("hud.noellesroles.psychologist.healing",
                psychComp.healingTargetName, healedSeconds, totalSeconds)
                .withStyle(ChatFormatting.GREEN);
            context.drawString(textRenderer, healingText, x, y, 0xFFFFFF);
            y += 12;
            
            // 进度条
            int barWidth = 100;
            int barHeight = 5;
            float progress = (float) psychComp.healingTicks / PsychologistPlayerComponent.HEALING_DURATION;
            int filledWidth = (int) (barWidth * progress);
            
            // 背景
            context.fill(x, y, x + barWidth, y + barHeight, 0x80000000);
            // 进度
            context.fill(x, y, x + filledWidth, y + barHeight, 0xFF00FF00);
            y += barHeight + 5;
        }
        // 冷却中
        else if (psychComp.cooldown > 0) {
            Component cooldownText = Component.translatable("hud.noellesroles.psychologist.cooldown",
                psychComp.getCooldownSeconds()).withStyle(ChatFormatting.GRAY);
            context.drawString(textRenderer, cooldownText, x, y, 0xFFFFFF);
            y += 12;
        }
        // 技能就绪（san值需要 >= 0.99 才算满）
        else if (sanity >= 0.99f) {
            Component readyText = Component.translatable("hud.noellesroles.psychologist.ready")
                .withStyle(ChatFormatting.GREEN);
            context.drawString(textRenderer, readyText, x, y, 0xFFFFFF);
            y += 12;
        }
        // san值不足
        else {
            Component notReadyText = Component.translatable("hud.noellesroles.psychologist.not_ready")
                .withStyle(ChatFormatting.YELLOW);
            context.drawString(textRenderer, notReadyText, x, y, 0xFFFFFF);
        }
    }
}