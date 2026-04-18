package org.agmas.noellesroles.game.modes.fourthroom.network;

import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.game.modes.fourthroom.game.FourthRoomGameManager;
import org.agmas.noellesroles.game.modes.fourthroom.shop.FourthRoomShopItem;
import org.jetbrains.annotations.NotNull;

public record BuyFourthRoomItemPayload(String itemId) implements CustomPacketPayload {
    public static final Type<BuyFourthRoomItemPayload> ID = new Type<>(SRE.id("fourth_room_buy_item"));
    public static final StreamCodec<FriendlyByteBuf, BuyFourthRoomItemPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            BuyFourthRoomItemPayload::itemId,
            BuyFourthRoomItemPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<BuyFourthRoomItemPayload> {
        @Override
        public void receive(@NotNull BuyFourthRoomItemPayload payload, ServerPlayNetworking.@NotNull Context context) {
            FourthRoomShopItem item = FourthRoomShopItem.byId(payload.itemId());
            if (item != null) {
                FourthRoomGameManager.of(context.player().serverLevel()).buyItem(context.player().getUUID(), item);
            }
        }
    }
}