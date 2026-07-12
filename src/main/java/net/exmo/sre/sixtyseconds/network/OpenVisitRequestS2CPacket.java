package net.exmo.sre.sixtyseconds.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/** 服务端→客户端：打开拜访请求界面，携带可拜访的别队列表。 */
public record OpenVisitRequestS2CPacket(int[] teamIds, String[] labels) implements CustomPacketPayload {
    public static final Type<OpenVisitRequestS2CPacket> ID = new Type<>(Noellesroles.id("open_visit_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenVisitRequestS2CPacket> CODEC =
            StreamCodec.ofMember(OpenVisitRequestS2CPacket::encode, OpenVisitRequestS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(teamIds.length);
        for (int i = 0; i < teamIds.length; i++) {
            buf.writeVarInt(teamIds[i]);
            buf.writeUtf(labels[i]);
        }
    }

    public static OpenVisitRequestS2CPacket decode(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        int[] ids = new int[n];
        String[] labels = new String[n];
        for (int i = 0; i < n; i++) {
            ids[i] = buf.readVarInt();
            labels[i] = buf.readUtf();
        }
        return new OpenVisitRequestS2CPacket(ids, labels);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
