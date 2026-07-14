package net.exmo.sre.sixtyseconds.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/** 服务端→客户端：打开撬棍/开锁器的闯入目标选择界面，携带可闯入的别队列表。 */
public record OpenBreakInSelectS2CPacket(int[] teamIds, String[] labels, boolean alarms)
        implements CustomPacketPayload {
    public static final Type<OpenBreakInSelectS2CPacket> ID = new Type<>(Noellesroles.id("open_break_in_select"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenBreakInSelectS2CPacket> CODEC =
            StreamCodec.ofMember(OpenBreakInSelectS2CPacket::encode, OpenBreakInSelectS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(teamIds.length);
        for (int i = 0; i < teamIds.length; i++) {
            buf.writeVarInt(teamIds[i]);
            buf.writeUtf(labels[i]);
        }
        buf.writeBoolean(alarms);
    }

    public static OpenBreakInSelectS2CPacket decode(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        int[] ids = new int[n];
        String[] labels = new String[n];
        for (int i = 0; i < n; i++) {
            ids[i] = buf.readVarInt();
            labels[i] = buf.readUtf();
        }
        return new OpenBreakInSelectS2CPacket(ids, labels, buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
