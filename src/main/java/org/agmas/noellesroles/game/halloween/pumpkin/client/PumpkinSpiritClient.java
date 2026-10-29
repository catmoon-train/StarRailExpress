package org.agmas.noellesroles.game.halloween.pumpkin.client;

import io.wifi.starrailexpress.api.data.RoleData;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.halloween.HalloweenRuntime;
import org.agmas.noellesroles.game.halloween.pumpkin.PumpkinSpiritRole;
import org.agmas.noellesroles.game.halloween.pumpkin.PumpkinSpiritRoleData;

/** 客户端入口只注册 HUD；技能冷却继续使用统一 RoleSkill HUD。 */
public final class PumpkinSpiritClient {
    private static boolean registered;
    private PumpkinSpiritClient() {}
    public static void register() {
        if (registered) return;
        registered = true;
        RoleHudRenderCallback.EVENT.register(PumpkinSpiritRole.ID, (graphics, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || !HalloweenRuntime.isEnabled(client.player.level())) return;
            var data = RoleData.getNullable(PumpkinSpiritRoleData.class, client.player);
            if (data == null) return;
            int x = graphics.guiWidth() - 195;
            int y = graphics.guiHeight() - 75;
            graphics.drawString(client.font, Component.translatable("hud.noellesroles.pumpkin_spirit.progress",
                    data.collected, data.target), x, y, 0xFFAA33);
            if (data.suppressed) graphics.drawString(client.font,
                    Component.translatable("hud.noellesroles.pumpkin_spirit.suppressed"), x, y + 12, 0xFF5555);
        });
    }
}
