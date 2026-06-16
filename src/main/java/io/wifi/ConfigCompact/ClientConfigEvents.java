package io.wifi.ConfigCompact;

import io.wifi.ConfigCompact.network.RoleEnableInfoPacket;
import io.wifi.ConfigCompact.network.SyncConfigPayload;
import io.wifi.ConfigCompact.ui.RoleManageConfigUI;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

@Environment(EnvType.CLIENT)
public class ClientConfigEvents {
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SyncConfigPayload.ID, (payload, context) -> {
            ConfigClassHandler.recieveConfigPackFromServer(payload.configId(), payload.content());
        });

        ClientPlayNetworking.registerGlobalReceiver(RoleEnableInfoPacket.ID, (payload, context) -> {
            var packet = payload.packetInfo();
            boolean openUI = payload.openUI();
            RoleManageConfigUI.setRoleInfo(packet.roleInfo);
            RoleManageConfigUI.setModifierInfo(packet.modifierInfo);
            if (openUI) {
                context.client().execute(() -> {
                    context.client().setScreen(RoleManageConfigUI.getScreen(context.client().screen));
                });
            }
            SRE.LOGGER.info("Recieved role and modifer disabled infomation. [Size: {}, openUI: {}]",
                    packet.roleInfo.size() + packet.modifierInfo.size(), (openUI ? "Yes" : "No"));
        });
    }
}
