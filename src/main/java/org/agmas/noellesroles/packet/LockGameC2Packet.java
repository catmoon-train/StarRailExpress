package org.agmas.noellesroles.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 撬锁游戏网络包
 * 从客户端发回服务端：
 * - 包含锁实体的位置
 * - 撬锁结果
 */
public record LockGameC2Packet(BlockPos pos, int entityId, boolean result) implements CustomPacketPayload{
    public static final ResourceLocation LOCK_PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "lock_game");
    public static final Type<LockGameC2Packet> ID = new Type<>(LOCK_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, LockGameC2Packet> CODEC;
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeInt(entityId);
        buf.writeBoolean(result);
    }

    public static LockGameC2Packet read(FriendlyByteBuf buf) {
        return new LockGameC2Packet(buf.readBlockPos(), buf.readInt(), buf.readBoolean());
    }
    static {
        CODEC = StreamCodec.ofMember(LockGameC2Packet::write, LockGameC2Packet::read);
    }
}
