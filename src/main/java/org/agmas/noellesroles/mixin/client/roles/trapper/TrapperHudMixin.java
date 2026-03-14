package org.agmas.noellesroles.mixin.client.roles.trapper;

import org.agmas.noellesroles.component.TrapperPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
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
 * 设陷者 HUD Mixin
 * 
 * 显示设陷者的陷阱储存状态：
 * - 当前陷阱数量 / 最大数量
 * - 恢复进度（如果未满）
 */
@Mixin(Gui.class)
public class TrapperHudMixin {
    
    @Inject(method = "render", at = @At("TAIL"))
    private void renderTrapperChargesStatus(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        if (client.player.isSpectator())
            return;
        // 检查是否是设陷者
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(client.level);
        if (!gameWorld.isRole(client.player, ModRoles.TRAPPER)) return;
        
        // 检查玩家是否存活
        if (!GameUtils.isPlayerAliveAndSurvival(client.player)) return;
        
        // 获取设陷者组件
        TrapperPlayerComponent trapperComponent = TrapperPlayerComponent.KEY.get(client.player);
        
        // 渲染位置 - 右下角
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int x = screenWidth - 150;  // 距离右边缘
        int y = screenHeight - 50;  // 距离底部
        
        Font textRenderer = client.font;
        
        // 显示陷阱储存数量
        int charges = trapperComponent.getTrapCharges();
        int maxCharges = TrapperPlayerComponent.MAX_TRAP_CHARGES;
        
        // 根据陷阱数量选择颜色
        int chargeColor;
        if (charges == 0) {
            chargeColor = CommonColors.RED;
        } else if (charges == maxCharges) {
            chargeColor = CommonColors.GREEN;
        } else {
            chargeColor = 0xFFFF00; // 黄色
        }
        
        // 显示陷阱数量文本
        Component chargeText = Component.translatable("hud.noellesroles.trapper.charges", charges, maxCharges);
        context.drawString(textRenderer, chargeText, x, y, chargeColor);
        
        // 如果陷阱未满，显示恢复时间
        if (charges < maxCharges) {
            float rechargeSeconds = trapperComponent.getRechargeSeconds();
            Component rechargeText = Component.translatable("hud.noellesroles.trapper.recharge",
                String.format("%.1f", rechargeSeconds));
            context.drawString(textRenderer, rechargeText, x, y + 12, 0xAAAAAA);
        } else {
            // 满了就显示就绪
            Component readyText = Component.translatable("hud.noellesroles.trapper.ready");
            context.drawString(textRenderer, readyText, x, y + 12, CommonColors.GREEN);
        }
    }
}