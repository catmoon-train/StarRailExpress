package org.agmas.noellesroles.mixin.client.roles;

import org.agmas.noellesroles.component.BoxerPlayerComponent;
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
 * 拳击手 HUD Mixin
 * 
 * 显示拳击手的技能状态：
 * - 技能冷却时间
 * - 无敌状态激活提示
 * - 技能就绪提示
 */
@Mixin(Gui.class)
public class BoxerHudMixin {
    
    @Inject(method = "render", at = @At("TAIL"))
    private void renderBoxerAbilityStatus(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        if(client.player.isSpectator()) return;
        
        // 检查是否是拳击手
        GameWorldComponent gameWorld = GameWorldComponent.KEY.get(client.level);
        if (!gameWorld.isRole(client.player, ModRoles.BOXER)) return;
        
        // 检查玩家是否存活
        if (!GameFunctions.isPlayerAliveAndSurvival(client.player)) return;
        
        // 获取拳击手组件
        BoxerPlayerComponent boxerComponent = BoxerPlayerComponent.KEY.get(client.player);
        
        // 渲染位置 - 右下角
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int x = screenWidth - 150;  // 距离右边缘
        int y = screenHeight - 30;  // 距离底部
        
        Font textRenderer = client.font;
        
        // 检查无敌状态
        if (boxerComponent.isInvulnerable) {
            // 无敌激活 - 显示黄色闪烁文字
            Component activeText = Component.translatable("hud.noellesroles.boxer.active",
                String.format("%.1f", boxerComponent.getInvulnerabilitySeconds()));
            
            // 使用黄色表示无敌激活
            int color = 0xFFFF00; // 黄色
            context.drawString(textRenderer, activeText, x, y, color);
            
        } else if (boxerComponent.cooldown > 0) {
            // 显示技能冷却
            float cdSeconds = boxerComponent.getCooldownSeconds();
            Component cdText = Component.translatable("hud.noellesroles.boxer.cooldown",
                String.format("%.1f", cdSeconds));
            
            // 红色文字表示冷却中
            context.drawString(textRenderer, cdText, x, y, CommonColors.RED);
            
        } else {
            // 技能就绪 - 显示绿色提示
            Component readyText = Component.translatable("hud.noellesroles.boxer.ready");
            context.drawString(textRenderer, readyText, x, y, CommonColors.GREEN);
        }
    }
}