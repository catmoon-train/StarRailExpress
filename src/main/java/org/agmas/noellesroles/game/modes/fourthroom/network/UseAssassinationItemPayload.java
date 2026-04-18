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

import java.util.UUID;

public record UseAssassinationItemPayload(String itemId, String targetId) implements CustomPacketPayload {
    public static final Type<UseAssassinationItemPayload> ID = new Type<>(SRE.id("fourth_room_use_item"));
    public static final StreamCodec<FriendlyByteBuf, UseAssassinationItemPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            UseAssassinationItemPayload::itemId,
            ByteBufCodecs.STRING_UTF8,
            UseAssassinationItemPayload::targetId,
            UseAssassinationItemPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<UseAssassinationItemPayload> {
        @Override
        public void receive(@NotNull UseAssassinationItemPayload payload, ServerPlayNetworking.@NotNull Context context) {
            FourthRoomShopItem item = FourthRoomShopItem.byId(payload.itemId());
            if (item == null || payload.targetId().isBlank()) {
                return;
            }
            FourthRoomGameManager.of(context.player().serverLevel())
                    .useAssassinationItem(context.player().getUUID(), UUID.fromString(payload.targetId()), item);
        }
    }
}