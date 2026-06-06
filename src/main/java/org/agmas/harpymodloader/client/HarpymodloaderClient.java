package org.agmas.harpymodloader.client;

import io.wifi.starrailexpress.api.SRERole;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.modifiers.SREModifier;

import java.util.HashSet;

public class HarpymodloaderClient implements ClientModInitializer {
    public static SRERole hudRole = null;
    public static HashSet<SREModifier> modifiers = null;

    @Override
    public void onInitializeClient() {
        ClientPlayConnectionEvents.JOIN.register((clientPlayNetworkHandler, packetSender, minecraftClient) -> {
            Harpymodloader.refreshRoles();
        });
    }

}
