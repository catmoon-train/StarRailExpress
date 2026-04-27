package org.agmas.noellesroles.packet;


import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 飞行员脱下喷气背包网络包
 * 客户端 -> 服务端
 * 
 * 当玩家按下技能键时发送，请求脱下喷气背包
 */
public record PilotRemoveJetpackC2SPacket() implements CustomPacketPayload {
    
    public static final Type<PilotRemoveJetpackC2SPacket> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "pilot_remove_jetpack")
    );
    
    public static final StreamCodec<RegistryFriendlyByteBuf, PilotRemoveJetpackC2SPacket> CODEC = StreamCodec.unit(new PilotRemoveJetpackC2SPacket());
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
