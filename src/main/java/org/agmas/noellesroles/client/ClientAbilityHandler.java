package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.gamemode.CustomRoleGameModeTeamsPlayerComponent;
import io.wifi.starrailexpress.client.gui.screen.gamemode.custom_role.CustomRoleSelectScreen;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.agmas.noellesroles.packet.AbilityC2SPacket;

public class ClientAbilityHandler {

    public static void handler(Minecraft client) {

        SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
                .get(client.player.level());
        // 游戏模式：自选职业
        if (gameWorldComponent.isRunning() && gameWorldComponent.getGameMode().equals(SREGameModes.CUSTOM_SELECTED_MODE)
                && gameWorldComponent.isRole(client.player, SpecialGameModeRoles.CUSTOM_PENDING)) {
            if (!CustomRoleGameModeTeamsPlayerComponent.KEY.get(client.player).selected()) {
                client.execute(() -> {
                    client.setScreen(new CustomRoleSelectScreen(client.player));
                });
            }
            return;
        }
        // 慕恋者持续按键检测（窥视）

        RicesRoleRhapsodyClient.handleAdmirerContinuousInput(client);
        if (client.player == null)
            return;

        if (GKeyRoleSkill.trigger(client, gameWorldComponent, true)) {
            return;
        }
        if (RicesRoleRhapsodyClient.onAbilityKeyPressed(client)) {
            return;
        }
        if (GKeyRoleSkill.trigger(client, gameWorldComponent, false)) {
            return;
        }
        ClientPlayNetworking.send(new AbilityC2SPacket());
    }

}
