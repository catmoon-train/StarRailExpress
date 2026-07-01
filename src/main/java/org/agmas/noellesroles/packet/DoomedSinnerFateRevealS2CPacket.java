package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 -&gt; 客户端：宿命的罪人「命运的启示」结果。
 * 携带目标名字与其最近若干次的杀人方式（死因 ResourceLocation 字符串）。
 */
public record DoomedSinnerFateRevealS2CPacket(
        String targetName,
        List<String> killMethods) implements CustomPacketPayload {

    public static final Type<DoomedSinnerFateRevealS2CPacket> ID =
            new Type<>(Noellesroles.id("doomed_sinner_fate_reveal"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DoomedSinnerFateRevealS2CPacket> CODEC =
            StreamCodec.ofMember(DoomedSinnerFateRevealS2CPacket::encode, DoomedSinnerFateRevealS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(targetName);
        buf.writeVarInt(killMethods.size());
        killMethods.forEach(buf::writeUtf);
    }

    public static DoomedSinnerFateRevealS2CPacket decode(RegistryFriendlyByteBuf buf) {
        String targetName = buf.readUtf();
        int size = buf.readVarInt();
        List<String> killMethods = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            killMethods.add(buf.readUtf());
        }
        return new DoomedSinnerFateRevealS2CPacket(targetName, killMethods);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
