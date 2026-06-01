package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

public record RepairCoinRewardS2CPacket(int amount, String sourceKey) implements CustomPacketPayload {
    public static final Type<RepairCoinRewardS2CPacket> ID = new Type<>(Noellesroles.id("repair_coin_reward"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RepairCoinRewardS2CPacket> CODEC = StreamCodec
            .ofMember(RepairCoinRewardS2CPacket::encode, RepairCoinRewardS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(amount);
        buf.writeUtf(sourceKey);
    }

    public static RepairCoinRewardS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new RepairCoinRewardS2CPacket(buf.readVarInt(), buf.readUtf());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
