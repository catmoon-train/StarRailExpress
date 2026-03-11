package org.agmas.harpymodloader.client;

import io.wifi.starrailexpress.api.Role;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import java.util.ArrayList;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.modifiers.Modifier;

public class HarpymodloaderClient implements ClientModInitializer {

    public static float rainbowRoleTime = 0;
    public static Role hudRole = null;
    public static ArrayList<Modifier> modifiers = null;

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
