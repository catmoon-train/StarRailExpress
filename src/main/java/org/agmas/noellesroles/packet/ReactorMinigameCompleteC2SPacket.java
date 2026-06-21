package org.agmas.noellesroles.packet;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.block_entity.scene.ReactorBlockEntity;
import org.jetbrains.annotations.NotNull;

public record ReactorMinigameCompleteC2SPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<ReactorMinigameCompleteC2SPacket> TYPE = new Type<>(Noellesroles.id("reactor_minigame_complete"));
    public static final StreamCodec<FriendlyByteBuf, ReactorMinigameCompleteC2SPacket> STREAM_CODEC = StreamCodec.ofMember(
            ReactorMinigameCompleteC2SPacket::write, ReactorMinigameCompleteC2SPacket::new);

    private ReactorMinigameCompleteC2SPacket(FriendlyByteBuf buf) {
        this(buf.readBlockPos());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ReactorMinigameCompleteC2SPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        BlockEntity be = serverLevel.getBlockEntity(payload.pos());
        if (be instanceof ReactorBlockEntity reactor) {
            reactor.close();
            // 基于配对关系检查是否所有反应堆都已关闭（直接从 Chunk 读取块状态）
            reactor.onSelfClosed();
        }
    }
}
