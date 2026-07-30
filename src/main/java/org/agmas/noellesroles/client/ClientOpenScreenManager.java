package org.agmas.noellesroles.client;

import org.agmas.noellesroles.client.utils.OpenScreenManager;
import org.agmas.noellesroles.packet.OpenScreenPayload;

import io.wifi.rhythm.client.screen.RhythmGameListScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.Context;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

/**
 * ClientOpenScreenManager
 */
public class ClientOpenScreenManager {

    public static void openScreen(OpenScreenPayload payload, Context context) {
        Screen screen = null;
        ResourceLocation id = payload.id();
        if (id.equals(OpenScreenManager.RHYTHM_GAME_SCREEN)) {
            screen = new RhythmGameListScreen(null);
        }
        if (screen != null) {
            final var finalScreen = screen;
            context.client().execute(() -> {
                context.client().setScreen(finalScreen);
            });
        }
    }

}
