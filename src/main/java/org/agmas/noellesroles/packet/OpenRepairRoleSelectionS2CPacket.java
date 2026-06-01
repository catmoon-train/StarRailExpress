package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

import java.util.ArrayList;
import java.util.List;

public record OpenRepairRoleSelectionS2CPacket(String faction, long endTick, List<String> playerNames)
        implements CustomPacketPayload {
    public static final Type<OpenRepairRoleSelectionS2CPacket> ID = new Type<>(Noellesroles.id("open_repair_role_selection"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenRepairRoleSelectionS2CPacket> CODEC = StreamCodec
            .ofMember(OpenRepairRoleSelectionS2CPacket::encode, OpenRepairRoleSelectionS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(faction);
        buf.writeLong(endTick);
        buf.writeVarInt(playerNames.size());
        playerNames.forEach(buf::writeUtf);
    }

    public static OpenRepairRoleSelectionS2CPacket decode(RegistryFriendlyByteBuf buf) {
        String faction = buf.readUtf();
        long endTick = buf.readLong();
        int size = buf.readVarInt();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            names.add(buf.readUtf());
        }
        return new OpenRepairRoleSelectionS2CPacket(faction, endTick, names);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
