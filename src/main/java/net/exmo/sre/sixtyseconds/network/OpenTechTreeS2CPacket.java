package net.exmo.sre.sixtyseconds.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/** 服务端→客户端：打开科技树界面（科技定义两端共享，只发本队已解锁 id）。 */
public record OpenTechTreeS2CPacket(String[] unlockedIds) implements CustomPacketPayload {
    public static final Type<OpenTechTreeS2CPacket> ID = new Type<>(Noellesroles.id("open_tech_tree"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenTechTreeS2CPacket> CODEC =
            StreamCodec.ofMember(OpenTechTreeS2CPacket::encode, OpenTechTreeS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(unlockedIds.length);
        for (String id : unlockedIds) {
            buf.writeUtf(id);
        }
    }

    public static OpenTechTreeS2CPacket decode(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        String[] ids = new String[n];
        for (int i = 0; i < n; i++) {
            ids[i] = buf.readUtf();
        }
        return new OpenTechTreeS2CPacket(ids);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
