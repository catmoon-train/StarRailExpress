package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 神父咏诵界面提交的一句台词。
 */
public record PriestChantC2SPacket(String text) implements CustomPacketPayload {
    public static final ResourceLocation PAYLOAD_ID = Noellesroles.id("priest_chant");
    public static final Type<PriestChantC2SPacket> ID = new Type<>(PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, PriestChantC2SPacket> CODEC = StreamCodec.ofMember(
            (packet, buf) -> buf.writeUtf(packet.text()),
            buf -> new PriestChantC2SPacket(buf.readUtf()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
