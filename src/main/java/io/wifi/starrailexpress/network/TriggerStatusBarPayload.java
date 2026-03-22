package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.StatusBarHUD;
import io.wifi.starrailexpress.client.StatusInit;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TriggerStatusBarPayload(String id) implements CustomPacketPayload {
    public static final Type<TriggerStatusBarPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, "trigger_status_bar"));
    public static final StreamCodec<FriendlyByteBuf, TriggerStatusBarPayload> CODEC = StreamCodec.ofMember(TriggerStatusBarPayload::encode, TriggerStatusBarPayload::decode);


    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(id);

    }

    public static TriggerStatusBarPayload decode(FriendlyByteBuf buf) {
        String id = buf.readUtf();

        return new TriggerStatusBarPayload(id);
    }
    @Environment(EnvType.CLIENT)
    public static void registerReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> {
            context.client().execute(() -> {
                StatusBarHUD.getInstance().addStatusBar(StatusInit.getStatusBar(payload.id));

            });
        });
    }
}
