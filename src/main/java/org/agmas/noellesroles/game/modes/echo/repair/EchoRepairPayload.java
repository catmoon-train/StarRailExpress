package org.agmas.noellesroles.game.modes.echo.repair;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * 下发一轮修机判定。
 *
 * <p>只携带渲染所需的最小信息：指针速度、判定区起点与宽度、是否失控、开始 tick。
 * 不含目标坐标、其它玩家或隐藏状态，因此旁观者拿到也无法推断地图。
 */
public record EchoRepairPayload(UUID matchId, UUID checkId, String nodeId,
                                int speed, int zoneStart, int zoneWidth, boolean accident,
                                long startedAt, int timeoutTicks) implements CustomPacketPayload {
    public static final Type<EchoRepairPayload> ID =
            new Type<>(ResourceLocation.fromNamespaceAndPath("noellesroles", "echo/repair_check"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EchoRepairPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeUUID(value.matchId());
                buf.writeUUID(value.checkId());
                buf.writeUtf(value.nodeId(), 64);
                buf.writeVarInt(value.speed());
                buf.writeVarInt(value.zoneStart());
                buf.writeVarInt(value.zoneWidth());
                buf.writeBoolean(value.accident());
                buf.writeVarLong(value.startedAt());
                buf.writeVarInt(value.timeoutTicks());
            },
            buf -> new EchoRepairPayload(buf.readUUID(), buf.readUUID(), buf.readUtf(64),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                    buf.readVarLong(), buf.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
