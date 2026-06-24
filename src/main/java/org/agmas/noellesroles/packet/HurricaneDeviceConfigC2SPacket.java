package org.agmas.noellesroles.packet;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.block_entity.scene.HurricaneDeviceBlockEntity;
import org.jetbrains.annotations.NotNull;

public record HurricaneDeviceConfigC2SPacket(BlockPos pos, int radius, boolean persistent, int intervalSeconds,
                                             int durationSeconds) implements CustomPacketPayload {
    public static final Type<HurricaneDeviceConfigC2SPacket> TYPE = new Type<>(Noellesroles.id("hurricane_device_config"));
    public static final StreamCodec<FriendlyByteBuf, HurricaneDeviceConfigC2SPacket> STREAM_CODEC = StreamCodec.ofMember(
            HurricaneDeviceConfigC2SPacket::write, HurricaneDeviceConfigC2SPacket::new);

    private HurricaneDeviceConfigC2SPacket(FriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readInt(), buf.readBoolean(), buf.readInt(), buf.readInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeInt(radius);
        buf.writeBoolean(persistent);
        buf.writeInt(intervalSeconds);
        buf.writeInt(durationSeconds);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HurricaneDeviceConfigC2SPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        if (!player.isCreative()) return;
        BlockEntity be = player.serverLevel().getBlockEntity(payload.pos());
        if (be instanceof HurricaneDeviceBlockEntity hbe) {
            hbe.setConfig(payload.radius(), payload.persistent(), payload.intervalSeconds(), payload.durationSeconds());
            BlockPos pos = payload.pos();
            var state = player.serverLevel().getBlockState(pos);
            player.serverLevel().sendBlockUpdated(pos, state, state, net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }
}
