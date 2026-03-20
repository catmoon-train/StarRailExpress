package org.agmas.noellesroles.packet.Loot;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public record LootMultiResultS2CPacket(int poolID, List<int[]> results) implements CustomPacketPayload {
    public static final ResourceLocation LOOT_MULTI_RESULT_PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "loot_multi_result");
    public static final Type<LootMultiResultS2CPacket> ID = new Type<>(LOOT_MULTI_RESULT_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, LootMultiResultS2CPacket> CODEC;
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(poolID);
        buf.writeInt(results.size());
        for (int[] result : results) {
            buf.writeInt(result[0]);
            buf.writeInt(result[1]);
        }
    }

    public static LootMultiResultS2CPacket read(FriendlyByteBuf buf) {
        int poolID = buf.readInt();
        int count = buf.readInt();
        List<int[]> results = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            results.add(new int[]{buf.readInt(), buf.readInt()});
        }
        return new LootMultiResultS2CPacket(poolID, results);
    }
    static {
        CODEC = StreamCodec.ofMember(LootMultiResultS2CPacket::write, LootMultiResultS2CPacket::read);
    }
}
