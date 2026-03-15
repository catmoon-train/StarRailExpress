package io.wifi.ConfigCompact;

import io.wifi.ConfigCompact.network.SyncConfigPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

@Environment(EnvType.CLIENT)
public class ClientConfigEvents {
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SyncConfigPayload.ID, (payload, context) -> {
            ConfigClassHandler.recieveConfigPackFromServer(payload.configId(),payload.content());
        });
    }
}
