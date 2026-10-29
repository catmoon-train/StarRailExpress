package org.agmas.noellesroles.game.modes.echo.anim;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** 服务端批准后的翻滚动作；只向同维度近处玩家发送，不公开本人冷却。 */
public record EchoRollPayload(UUID match, ResourceLocation dimension, UUID actor, long start, boolean active, float x, float z) implements CustomPacketPayload {
    public static final Type<EchoRollPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath("noellesroles", "echo/roll_animation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EchoRollPayload> CODEC = StreamCodec.of((b,p) -> {
        b.writeUUID(p.match()); b.writeResourceLocation(p.dimension()); b.writeUUID(p.actor()); b.writeVarLong(p.start());
        b.writeBoolean(p.active()); b.writeFloat(p.x()); b.writeFloat(p.z());
    }, b -> new EchoRollPayload(b.readUUID(), b.readResourceLocation(), b.readUUID(), b.readVarLong(), b.readBoolean(), b.readFloat(), b.readFloat()));
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
