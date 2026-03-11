package org.agmas.noellesroles.packet;

import org.agmas.noellesroles.Noellesroles;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 心理学家治疗网络包
 * 客户端 -> 服务端
 * 
 * 当玩家按下技能键并瞄准玩家时发送，请求开始心理治疗
 * - 包含目标玩家UUID
 */
public record PsychologistC2SPacket(UUID targetUuid) implements CustomPacketPayload {
    
    public static final ResourceLocation PSYCHOLOGIST_PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "psychologist_heal");
    public static final Type<PsychologistC2SPacket> ID = new Type<>(PSYCHOLOGIST_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, PsychologistC2SPacket> CODEC;
    
    public PsychologistC2SPacket(UUID targetUuid) {
        this.targetUuid = targetUuid;
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
    
    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.targetUuid);
    }
    
    public static PsychologistC2SPacket read(FriendlyByteBuf buf) {
        return new PsychologistC2SPacket(buf.readUUID());
    }
    
    public UUID targetUuid() {
        return this.targetUuid;
    }
    
    static {
        CODEC = StreamCodec.ofMember(PsychologistC2SPacket::write, PsychologistC2SPacket::read);
    }
}