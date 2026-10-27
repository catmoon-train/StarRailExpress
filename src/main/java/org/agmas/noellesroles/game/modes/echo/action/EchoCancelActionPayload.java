package org.agmas.noellesroles.game.modes.echo.action;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** 只申请取消本人当前动作；旧actionId不能取消后来的引导。 */
public record EchoCancelActionPayload(UUID matchId,UUID actionId) implements CustomPacketPayload {
    public static final Type<EchoCancelActionPayload> ID=new Type<>(ResourceLocation.fromNamespaceAndPath("noellesroles","echo/cancel_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf,EchoCancelActionPayload> CODEC=StreamCodec.of(
            (buf,value) -> { buf.writeUUID(value.matchId()); buf.writeUUID(value.actionId()); },
            buf -> new EchoCancelActionPayload(buf.readUUID(),buf.readUUID()));
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
