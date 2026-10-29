package org.agmas.noellesroles.game.modes.echo.action;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** 翻滚意图只包含会话和水平朝向，权限、距离、速度与冷却全部由服务端决定。 */
public record EchoRollRequest(UUID match, float x, float z) implements CustomPacketPayload {
    public static final Type<EchoRollRequest> ID = new Type<>(ResourceLocation.fromNamespaceAndPath("noellesroles", "echo/roll_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EchoRollRequest> CODEC = StreamCodec.of(
            (b,p) -> { b.writeUUID(p.match()); b.writeFloat(p.x()); b.writeFloat(p.z()); },
            b -> new EchoRollRequest(b.readUUID(), b.readFloat(), b.readFloat()));
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
