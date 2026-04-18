package org.agmas.noellesroles.game.roles.Innocent.fool;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

/**
 * 塔罗会投票 C2S 包
 *
 * 玩家在投票GUI中选择投票目标后发送到服务端
 */
public record FoolTarotVoteC2SPacket(UUID votedFor) implements CustomPacketPayload {
    public static final ResourceLocation PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(
            Noellesroles.MOD_ID, "fool_tarot_vote");
    public static final Type<FoolTarotVoteC2SPacket> ID = new Type<>(PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, FoolTarotVoteC2SPacket> CODEC;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(votedFor);
    }

    public static FoolTarotVoteC2SPacket read(FriendlyByteBuf buf) {
        return new FoolTarotVoteC2SPacket(buf.readUUID());
    }

    static {
        CODEC = StreamCodec.ofMember(FoolTarotVoteC2SPacket::write, FoolTarotVoteC2SPacket::read);
    }
}
