package org.agmas.noellesroles.packet;

import org.agmas.noellesroles.Noellesroles;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 私家侦探审查玩家网络包
 * 
 * 从客户端发送到服务端，包含：
 * - 要审查的目标玩家UUID
 */
public record DetectiveC2SPacket(UUID targetUuid) implements CustomPacketPayload {
    
    public static final ResourceLocation DETECTIVE_PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "detective_inspect");
    public static final Type<DetectiveC2SPacket> ID = new Type<>(DETECTIVE_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, DetectiveC2SPacket> CODEC;
    
    public DetectiveC2SPacket(UUID targetUuid) {
        this.targetUuid = targetUuid;
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
    
    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.targetUuid);
    }
    
    public static DetectiveC2SPacket read(FriendlyByteBuf buf) {
        return new DetectiveC2SPacket(buf.readUUID());
    }
    
    public UUID targetUuid() {
        return this.targetUuid;
    }
    
    static {
        CODEC = StreamCodec.ofMember(DetectiveC2SPacket::write, DetectiveC2SPacket::read);
    }
}