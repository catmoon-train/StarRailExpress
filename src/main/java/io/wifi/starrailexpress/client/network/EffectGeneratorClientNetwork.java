package io.wifi.starrailexpress.client.network;

import io.wifi.starrailexpress.client.gui.screen.EffectGeneratorConfigScreen;
import io.wifi.starrailexpress.network.EffectGeneratorPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class EffectGeneratorClientNetwork {
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(EffectGeneratorPayload.OpenConfig.TYPE, (payload, context) -> {
            context.client().execute(() -> context.client()
                    .setScreen(new EffectGeneratorConfigScreen(payload.pos(), payload.data())));
        });
    }
}
