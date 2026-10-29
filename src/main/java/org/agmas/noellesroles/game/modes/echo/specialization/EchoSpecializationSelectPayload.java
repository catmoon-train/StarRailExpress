package org.agmas.noellesroles.game.modes.echo.specialization;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

/** 只提交选项下标与递增序号；服务端按当前阵营候选解释，不接受任意 ID。 */
public record EchoSpecializationSelectPayload(UUID match, long requestId, int option) implements CustomPacketPayload {
    public static final Type<EchoSpecializationSelectPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath("noellesroles", "echo/specialization_select"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EchoSpecializationSelectPayload> CODEC = StreamCodec.of(
            (b, p) -> { b.writeUUID(p.match()); b.writeVarLong(p.requestId()); b.writeVarInt(p.option()); },
            b -> new EchoSpecializationSelectPayload(b.readUUID(), b.readVarLong(), b.readVarInt()));

    public EchoSpecializationSelectPayload {
        Objects.requireNonNull(match);
        if (requestId < 1 || option < 0 || option > 1) throw new IllegalArgumentException("无效专长请求");
    }

    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
