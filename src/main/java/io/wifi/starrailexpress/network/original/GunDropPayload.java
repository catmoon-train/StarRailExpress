package io.wifi.starrailexpress.network.original;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public record GunDropPayload() implements CustomPacketPayload {
    public static final Type<GunDropPayload> ID = new Type<>(SRE.id("gundrop"));
    public static final StreamCodec<FriendlyByteBuf, GunDropPayload> CODEC = StreamCodec.unit(new GunDropPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    @Environment(EnvType.CLIENT)
    public static class Receiver implements ClientPlayNetworking.PlayPayloadHandler<GunDropPayload> {
        @Override
        public void receive(@NotNull GunDropPayload payload, ClientPlayNetworking.@NotNull Context context) {
            var player = context.player();
            if (player.getMainHandItem().is(TMMItemTags.GUNS)) {
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
        }
    }
}