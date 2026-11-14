package org.agmas.noellesroles.packet;

import java.util.UUID;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.command_block.StringerRole;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 只提交两名玩家的身份，冷却与牵线距离均不接受客户端输入。 */
public record StringerLinkC2SPayload(UUID first, UUID second) implements CustomPacketPayload {
    public static final Type<StringerLinkC2SPayload> ID = new Type<>(Noellesroles.id("stringer_link"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StringerLinkC2SPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> { buf.writeUUID(payload.first()); buf.writeUUID(payload.second()); },
            buf -> new StringerLinkC2SPayload(buf.readUUID(), buf.readUUID()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void receive(StringerLinkC2SPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> StringerRole.selectLink(context.player(), payload.first(), payload.second()));
    }
}
