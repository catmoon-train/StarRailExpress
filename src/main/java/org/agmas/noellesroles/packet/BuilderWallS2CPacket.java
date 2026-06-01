package org.agmas.noellesroles.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 建筑师墙数据包（服务端 -> 客户端）
 * 通知客户端创建一堵客户端墙
 */
public record BuilderWallS2CPacket(UUID wallId, List<BlockPos> brickPositions, List<BlockPos> cobwebPositions, int durationTicks)
        implements CustomPacketPayload {
    
    public static final ResourceLocation PACKET_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID,
            "builder_wall");
    public static final Type<BuilderWallS2CPacket> ID = new Type<>(PACKET_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, BuilderWallS2CPacket> CODEC;
    
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
    
    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(wallId);
        buf.writeInt(brickPositions.size());
        for (BlockPos pos : brickPositions) {
            buf.writeInt(pos.getX());
            buf.writeInt(pos.getY());
            buf.writeInt(pos.getZ());
        }
        buf.writeInt(cobwebPositions.size());
        for (BlockPos pos : cobwebPositions) {
            buf.writeInt(pos.getX());
            buf.writeInt(pos.getY());
            buf.writeInt(pos.getZ());
        }
        buf.writeInt(durationTicks);
    }
    
    public static BuilderWallS2CPacket read(FriendlyByteBuf buf) {
        UUID wallId = buf.readUUID();
        int brickSize = buf.readInt();
        List<BlockPos> brickPositions = new ArrayList<>();
        for (int i = 0; i < brickSize; i++) {
            brickPositions.add(new BlockPos(buf.readInt(), buf.readInt(), buf.readInt()));
        }
        int cobwebSize = buf.readInt();
        List<BlockPos> cobwebPositions = new ArrayList<>();
        for (int i = 0; i < cobwebSize; i++) {
            cobwebPositions.add(new BlockPos(buf.readInt(), buf.readInt(), buf.readInt()));
        }
        int durationTicks = buf.readInt();
        return new BuilderWallS2CPacket(wallId, brickPositions, cobwebPositions, durationTicks);
    }
    
    static {
        CODEC = StreamCodec.ofMember(BuilderWallS2CPacket::write, BuilderWallS2CPacket::read);
    }
}
