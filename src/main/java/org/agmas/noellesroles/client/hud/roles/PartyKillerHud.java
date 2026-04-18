package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.killer.party.PartyPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

public class PartyKillerHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.PARTY_KILLER_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.isPlayerSpectator())
                return;

            // 检查玩家是否存活
            if (!SREClient.isPlayerAliveAndInSurvival()) return;

            // 获取派对狂组件
            PartyPlayerComponent partyComponent = PartyPlayerComponent.KEY.get(client.player);

            // 渲染位置 - 右下角
            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int x = screenWidth - 150;
            int y = screenHeight - 50;

            // 使用组件同步的阈值（服务端计算并同步）
            int threshold = partyComponent.getThreshold();
            int currentCount = partyComponent.getCount();

            // 显示计数
            Component countText = Component.translatable("hud.noellesroles.party_killer.count", currentCount, threshold);
            int countColor = currentCount >= threshold ? CommonColors.GREEN : CommonColors.YELLOW;
            context.drawString(client.font, countText, x, y, countColor);
        });
    }
}
