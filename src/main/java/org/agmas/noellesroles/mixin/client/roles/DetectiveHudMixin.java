package org.agmas.noellesroles.mixin.client.roles;

import org.agmas.noellesroles.component.DetectivePlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.game.GameFunctions;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 私家侦探 HUD Mixin
 * 
 * 显示私家侦探的技能状态：
 * - 审查技能冷却时间
 * - 技能就绪提示
 */
@Mixin(Gui.class)
public class DetectiveHudMixin {
    
    @Inject(method = "render", at = @At("TAIL"))
    private void renderDetectiveAbilityStatus(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        if(client.player.isSpectator()) return;
        
        // 检查是否是私家侦探
        GameWorldComponent gameWorld = GameWorldComponent.KEY.get(client.level);
        if (!gameWorld.isRole(client.player, ModRoles.DETECTIVE)) return;
        
        // 检查玩家是否存活
        if (!GameFunctions.isPlayerAliveAndSurvival(client.player)) return;
        
        // 获取私家侦探组件
        DetectivePlayerComponent detectiveComponent = DetectivePlayerComponent.KEY.get(client.player);
        
        // 渲染位置 - 右下角
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int x = screenWidth - 120;  // 距离右边缘
        int y = screenHeight - 30;  // 距离底部
        
        Font textRenderer = client.font;
        
        if (detectiveComponent.cooldown > 0) {
            // 显示技能冷却
            float cdSeconds = detectiveComponent.getCooldownSeconds();
            Component cdText = Component.translatable("hud.noellesroles.detective.cooldown",
                String.format("%.1f", cdSeconds));
            
            // 红色文字表示冷却中
            context.drawString(textRenderer, cdText, x, y, CommonColors.RED);
            
        } else {
            // 技能可用 - 显示金币消耗提示
            Component readyText = Component.translatable("hud.noellesroles.detective.ready_cost");
            context.drawString(textRenderer, readyText, x, y, CommonColors.GREEN);
        }
    }
}