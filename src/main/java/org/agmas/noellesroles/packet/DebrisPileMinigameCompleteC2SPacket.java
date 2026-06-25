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
import org.agmas.noellesroles.content.block_entity.scene.DebrisPileBlockEntity;
import org.jetbrains.annotations.NotNull;

public record DebrisPileMinigameCompleteC2SPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<DebrisPileMinigameCompleteC2SPacket> TYPE = new Type<>(Noellesroles.id("debris_pile_minigame_complete"));
    public static final StreamCodec<FriendlyByteBuf, DebrisPileMinigameCompleteC2SPacket> STREAM_CODEC = StreamCodec.ofMember(
            DebrisPileMinigameCompleteC2SPacket::write, DebrisPileMinigameCompleteC2SPacket::new);

    private DebrisPileMinigameCompleteC2SPacket(FriendlyByteBuf buf) {
        this(buf.readBlockPos());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(DebrisPileMinigameCompleteC2SPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        BlockEntity be = serverLevel.getBlockEntity(payload.pos());
        if (be instanceof DebrisPileBlockEntity pile) {
            pile.extinguish();
            pile.onSelfExtinguished();
        }
    }
}
