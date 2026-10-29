package org.agmas.noellesroles.packet;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.agmas.noellesroles.Noellesroles;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 只向高维中的 404 本人同步战术终端数据，不依赖客户端已加载的实体。 */
public record Error404StateS2CPacket(boolean high, boolean open, List<Contact> players) implements CustomPacketPayload {
    public static final Type<Error404StateS2CPacket> ID = new Type<>(Noellesroles.id("error404_state"));
    public static final int MAX_CONTACTS = 256;
    /** state：0 普通玩家，1 自己的傀儡，2 正在同化；order：0 停驻，1 移动，2 追击。 */
    public record Contact(UUID id, String name, double x, double y, double z, int state, int seconds, int order) { }

    public static final StreamCodec<RegistryFriendlyByteBuf, Error404StateS2CPacket> CODEC = StreamCodec.ofMember(
            (packet, buf) -> {
                buf.writeBoolean(packet.high());
                buf.writeBoolean(packet.open());
                int size = Math.min(MAX_CONTACTS, packet.players().size());
                buf.writeVarInt(size);
                for (Contact contact : packet.players().subList(0, size)) {
                    buf.writeUUID(contact.id());
                    buf.writeUtf(contact.name(), 64);
                    buf.writeDouble(contact.x());
                    buf.writeDouble(contact.y());
                    buf.writeDouble(contact.z());
                    buf.writeVarInt(contact.state());
                    buf.writeVarInt(contact.seconds());
                    buf.writeVarInt(contact.order());
                }
            }, buf -> {
                boolean high = buf.readBoolean();
                boolean open = buf.readBoolean();
                int size = buf.readVarInt();
                if (size < 0 || size > MAX_CONTACTS) throw new IllegalArgumentException("Invalid 404 contact count");
                List<Contact> players = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    players.add(new Contact(buf.readUUID(), buf.readUtf(64), buf.readDouble(), buf.readDouble(),
                            buf.readDouble(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
                }
                return new Error404StateS2CPacket(high, open, List.copyOf(players));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
