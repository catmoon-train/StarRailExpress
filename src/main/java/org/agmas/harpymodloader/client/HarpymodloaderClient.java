package org.agmas.harpymodloader.client;

import io.wifi.starrailexpress.api.SRERole;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.modifiers.SREModifier;

import java.util.ArrayList;

public class HarpymodloaderClient implements ClientModInitializer {

    public static float rainbowRoleTime = 0;
    public static SRERole hudRole = null;
    public static ArrayList<SREModifier> modifiers = null;

    @Override
    public void onInitializeClient() {
        ClientPlayConnectionEvents.JOIN.register((clientPlayNetworkHandler, packetSender, minecraftClient) -> {
            Harpymodloader.refreshRoles();
        });
        ClientTickEvents.END_CLIENT_TICK.register((t) -> {
            rainbowRoleTime += 1;
        });
    }

}
