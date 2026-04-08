package io.wifi.starrailexpress.fourthroom.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.server.level.ServerPlayer;

public class OpenFourthRoomPeekDeckPayload {

    public static final Type<FourthRoomTableEffectsPayload> ID = new Type<>(SRE.id("fkai"));
    public static final StreamCodec<FriendlyByteBuf, FourthRoomTableEffectsPayload> CODEC = StreamCodec.ofMember(
            FourthRoomTableEffectsPayload::encode,
            FourthRoomTableEffectsPayload::decode);

    public static void send(ServerPlayer player) {
    }

    public static void registerReceiver() {
    }
    
    
}
