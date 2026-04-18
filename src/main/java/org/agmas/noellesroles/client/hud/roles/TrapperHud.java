package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.killer.trapper.TrapperPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

public class TrapperHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.TRAPPER_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.isPlayerSpectator())
                return;

            // 检查玩家是否存活
            if (!SREClient.isPlayerAliveAndInSurvival()) return;

            // 获取设陷者组件
            TrapperPlayerComponent trapperComponent = TrapperPlayerComponent.KEY.get(client.player);

            // 渲染位置 - 右下角
            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = screenWidth - 150;
            int y = screenHeight - 62;

            Font textRenderer = client.font;

            // 显示当前陷阱类型
            Component trapTypeText = Component.translatable(trapperComponent.getTrapTypeName());
            int trapTypeColor = trapperComponent.getSelectedTrapType() == TrapperPlayerComponent.TRAP_TYPE_CALAMITY
                    ? 0xBB44CC
                    : 0xFF8C00;
            context.drawString(textRenderer, trapTypeText, x, y, trapTypeColor);

            // 显示陷阱储存数量
            int charges = trapperComponent.getTrapCharges();
            int maxCharges = TrapperPlayerComponent.MAX_TRAP_CHARGES;

            int chargeColor;
            if (charges == 0) {
                chargeColor = CommonColors.RED;
            } else if (charges == maxCharges) {
                chargeColor = CommonColors.GREEN;
            } else {
                chargeColor = 0xFFFF00;
            }

            Component chargeText = Component.translatable("hud.noellesroles.trapper.charges", charges, maxCharges);
            context.drawString(textRenderer, chargeText, x, y + 12, chargeColor);

            // 如果陷阱未满，显示恢复时间
            if (charges < maxCharges) {
                float rechargeSeconds = trapperComponent.getRechargeSeconds();
                Component rechargeText = Component.translatable("hud.noellesroles.trapper.recharge",
                        String.format("%.1f", rechargeSeconds));
                context.drawString(textRenderer, rechargeText, x, y + 24, 0xAAAAAA);
            } else {
                Component readyText = Component.translatable("hud.noellesroles.trapper.ready");
                context.drawString(textRenderer, readyText, x, y + 24, CommonColors.GREEN);
            }
        });
    }
}
