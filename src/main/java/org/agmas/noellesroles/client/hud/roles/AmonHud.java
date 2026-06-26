package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.neutral.amon.AmonPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

public final class AmonHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.AMON_ID, (context, tickCounter) -> {
            if (SREClient.isPlayerSpectator()) return;
            var player = Minecraft.getInstance().player;
            AmonPlayerComponent amon = ModComponents.AMON.get(player);
            int x = context.guiWidth() - 190;
            int y = context.guiHeight() - 46;

            // 终幕阶段：显示倒计时与备用能力，戏耍众人。
            if (amon.finalePhase) {
                int seconds = (amon.finaleTicks + 19) / 20;
                context.drawString(Minecraft.getInstance().font,
                        Component.translatable("hud.noellesroles.amon.finale_time", seconds),
                        x, y, 0xFFD700);
                context.drawString(Minecraft.getInstance().font,
                        Component.translatable("hud.noellesroles.amon.reserve", amon.reserveLives),
                        x, y + 11, 0xC79BE6);
                return;
            }

            // 潜伏中的时之虫数 / 上限
            context.drawString(Minecraft.getInstance().font,
                    Component.translatable("hud.noellesroles.amon.seeds", amon.clientSeeds, amon.seedCap),
                    x, y, 0x8A6FB0);

            // 已成熟（可夺舍）宿主数
            context.drawString(Minecraft.getInstance().font,
                    Component.translatable("hud.noellesroles.amon.matured", amon.clientMatured),
                    x, y + 11, 0xC79BE6);

            // 夺舍就绪提示
            if (amon.clientMatured > 0) {
                context.drawString(Minecraft.getInstance().font,
                        Component.translatable("hud.noellesroles.amon.usurp_ready"),
                        x, y + 22, 0x55FF55);
            }

            // 已夺舍次数
            context.drawString(Minecraft.getInstance().font,
                    Component.translatable("hud.noellesroles.amon.usurped", amon.usurpCount),
                    x, y + 33, amon.hasUsurped ? 0xFFD700 : 0xAAAAAA);
        });
    }
}
