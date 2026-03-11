package org.agmas.noellesroles.packet;

import org.agmas.noellesroles.Noellesroles;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 阴谋家猜测网络包
 * 
 * 从客户端发送到服务端，包含：
 * - 目标玩家 UUID
 * - 猜测的角色 ID
 */
public record ConspiratorC2SPacket(UUID targetPlayer, String roleId) implements CustomPacketPayload {
    
    public static final ResourceLocation CONSPIRATOR_PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "conspirator_guess");
    public static final Type<ConspiratorC2SPacket> ID = new Type<>(CONSPIRATOR_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ConspiratorC2SPacket> CODEC;
    
    public ConspiratorC2SPacket(UUID targetPlayer, String roleId) {
        this.targetPlayer = targetPlayer;
        this.roleId = roleId;
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
    
    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.targetPlayer);
        buf.writeUtf(this.roleId);
    }
    
    public static ConspiratorC2SPacket read(FriendlyByteBuf buf) {
        return new ConspiratorC2SPacket(buf.readUUID(), buf.readUtf());
    }
    
    public UUID targetPlayer() {
        return this.targetPlayer;
    }
    
    public String roleId() {
        return this.roleId;
    }
    
    static {
        CODEC = StreamCodec.ofMember(ConspiratorC2SPacket::write, ConspiratorC2SPacket::read);
    }
}