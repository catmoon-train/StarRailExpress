package org.agmas.noellesroles.game.minigame.roulette;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.block_entity.DevilRouletteTableEntity;
import java.util.UUID;

/** 公共画面、私密提示与一次性选择分离；动画无需逐帧同步。 */
public final class RouletteNetworking {
    public static final int INFO = 0, HELP = 1, SELECT = 2, CLEAR = 3;
    private RouletteNetworking() {}
    public record Public(BlockPos pos, RouletteView view) implements CustomPacketPayload {
        public static final Type<Public> ID = new Type<>(Noellesroles.id("roulette_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Public> CODEC = StreamCodec.ofMember(
                (p, b) -> { b.writeBlockPos(p.pos); b.writeNbt(p.view.toTag()); },
                b -> new Public(b.readBlockPos(), RouletteView.fromTag(tag(b))));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    /** data 的内容仅向指定玩家发送，绝不进入方块实体更新标签。 */
    public record Private(BlockPos pos, UUID session, int kind, CompoundTag data) implements CustomPacketPayload {
        public static final Type<Private> ID = new Type<>(Noellesroles.id("roulette_private"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Private> CODEC = StreamCodec.ofMember(
                (p, b) -> { b.writeBlockPos(p.pos); b.writeUUID(p.session); b.writeVarInt(p.kind); b.writeNbt(p.data); },
                b -> new Private(b.readBlockPos(), b.readUUID(), b.readVarInt(), tag(b)));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    public record Select(BlockPos pos, UUID session, UUID token, int slot) implements CustomPacketPayload {
        public static final Type<Select> ID = new Type<>(Noellesroles.id("roulette_select"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Select> CODEC = StreamCodec.ofMember(
                (p, b) -> { b.writeBlockPos(p.pos); b.writeUUID(p.session); b.writeUUID(p.token); b.writeVarInt(p.slot); },
                b -> new Select(b.readBlockPos(), b.readUUID(), b.readUUID(), b.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    private static CompoundTag tag(RegistryFriendlyByteBuf buf) {
        CompoundTag result = buf.readNbt();
        return result == null ? new CompoundTag() : result;
    }
    public static void register() {
        PayloadTypeRegistry.playS2C().register(Public.ID, Public.CODEC);
        PayloadTypeRegistry.playS2C().register(Private.ID, Private.CODEC);
        PayloadTypeRegistry.playC2S().register(Select.ID, Select.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Select.ID, (p, ctx) -> ctx.server().execute(() -> {
            var player = ctx.player();
            // 验证请求不得主动加载远处区块；位置信息只能在当前维度生效。
            if (player.distanceToSqr(p.pos.getX() + .5, p.pos.getY() + .5, p.pos.getZ() + .5) > 64
                    || !player.serverLevel().hasChunkAt(p.pos)) return;
            if (player.serverLevel().getBlockEntity(p.pos) instanceof DevilRouletteTableEntity table)
                table.handleSelection(player, p);
        }));
    }
}
