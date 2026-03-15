package io.wifi.ConfigCompact;

import io.wifi.ConfigCompact.network.SyncConfigPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public class ConfigEvents {
    public static void register() {
        PayloadTypeRegistry.playS2C().register(SyncConfigPayload.ID, SyncConfigPayload.CODEC);
    }
}
