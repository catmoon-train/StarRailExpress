package org.agmas.noellesroles.packet;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/** 头像只提交目标，所有资格与会话互斥检查都在服务端进行。 */
public record RouletteDevilC2SPacket(int action, UUID target) implements CustomPacketPayload {
    public static final Type<RouletteDevilC2SPacket> ID = new Type<>(Noellesroles.id("roulette_devil_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RouletteDevilC2SPacket> CODEC = StreamCodec.ofMember(
            (p, b) -> { b.writeVarInt(p.action); b.writeUUID(p.target); }, b -> new RouletteDevilC2SPacket(b.readVarInt(), b.readUUID()));
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
