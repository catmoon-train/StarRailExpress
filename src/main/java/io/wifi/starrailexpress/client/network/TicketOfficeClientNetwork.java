package io.wifi.starrailexpress.client.network;

import io.wifi.starrailexpress.client.gui.screen.TicketOfficeConfigScreen;
import io.wifi.starrailexpress.client.gui.screen.TicketOfficeShopScreen;
import io.wifi.starrailexpress.network.TicketPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public class TicketOfficeClientNetwork {
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(TicketPayload.OpenOfficeConfig.TYPE, (payload, context) -> {
            Minecraft client = context.client();
            client.execute(() -> client.setScreen(new TicketOfficeConfigScreen(payload.pos(), payload.data())));
        });
        ClientPlayNetworking.registerGlobalReceiver(TicketPayload.OpenOfficeShop.TYPE, (payload, context) -> {
            Minecraft client = context.client();
            client.execute(() -> client.setScreen(new TicketOfficeShopScreen(payload.pos(), payload.data())));
        });
    }
}
