package org.agmas.noellesroles.client.hud.roles;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.game.roles.killer.nostalgist.NostalgistPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

public class NostalgistHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(ModRoles.NOSTALGIST_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (SREClient.isPlayerSpectator())
                return;
            if (client.player == null)
                return;

            final var comp = NostalgistPlayerComponent.KEY.get(client.player);
            if (comp == null)
                return;

            MutableComponent content;
            if (comp.inBackWorld && !comp.converted) {
                content = Component.translatable("hud.noellesroles.nostalgist.back_world",
                        comp.aliveKillerCount);
            } else {
                content = Component.translatable("hud.noellesroles.nostalgist.manifest");
            }
            context.drawString(client.font, content,
                    context.guiWidth() - client.font.width(content) - 12,
                    context.guiHeight() - 20, ModRoles.NOSTALGIST.color());
        });
    }
}
