package org.agmas.noellesroles.client.utils;

import java.util.HashMap;

import org.agmas.noellesroles.packet.OpenScreenPayload;

import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public interface OpenScreenManager {
    HashMap<ResourceLocation, Component> SCREENS = new HashMap<>();

    public static void init() {
    }

    public static ResourceLocation register(ResourceLocation id, Component name) {
        SCREENS.put(id, name);
        return id;
    }

    public static void openScreen(ServerPlayer player, ResourceLocation screenId) {
        if (screenId == null)
            return;
        ServerPlayNetworking.send(player, new OpenScreenPayload(screenId));
    }

    ResourceLocation RHYTHM_GAME_SCREEN = register(SRE.id("rhythm_game"), Component.translatable("gui.rhythm.title"));
}
