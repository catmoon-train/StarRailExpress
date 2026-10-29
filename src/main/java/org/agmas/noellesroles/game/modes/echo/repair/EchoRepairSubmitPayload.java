package org.agmas.noellesroles.game.modes.echo.repair;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * 玩家按下按钮时上报：这一轮判定编号 + 客户端看到的指针位置。
 *
 * <p>服务端不会直接采信 {@code position}，而是按自己的 tick 重算；两者偏差超过
 * {@link EchoRepairCheck#TOLERANCE} 时判 {@code DESYNC}（不扣资源、不给奖励）。
 */
public record EchoRepairSubmitPayload(UUID matchId, UUID checkId, int position) implements CustomPacketPayload {
    public static final Type<EchoRepairSubmitPayload> ID =
            new Type<>(ResourceLocation.fromNamespaceAndPath("noellesroles", "echo/repair_submit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EchoRepairSubmitPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeUUID(value.matchId());
                buf.writeUUID(value.checkId());
                buf.writeVarInt(value.position());
            },
            buf -> new EchoRepairSubmitPayload(buf.readUUID(), buf.readUUID(), buf.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
