package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.neutral.reasoner.ReasonerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

public final class ReasonerHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.REASONER_ID, (context, tickCounter) -> {
            if (SREClient.isPlayerSpectator()) return;
            var player = Minecraft.getInstance().player;
            ReasonerPlayerComponent comp = ModComponents.REASONER.get(player);

            int solved = comp.getSolvedCount();
            int x = context.guiWidth() - 100;
            int y = context.guiHeight() - 23;
            var font = Minecraft.getInstance().font;

            context.drawString(font,
                    Component.translatable("hud.noellesroles.reasoner.progress", solved, 5),
                    x, y, 0xFFD4B25C);
        });
    }
}
