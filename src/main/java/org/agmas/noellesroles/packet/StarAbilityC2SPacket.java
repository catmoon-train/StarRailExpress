package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 明星技能网络包
 * 客户端 -> 服务端
 * 
 * 当玩家按下技能键时发送，请求激活"聚光灯"技能
 * 让10格范围内的玩家视野都看向明星
 */
public record StarAbilityC2SPacket() implements CustomPacketPayload {
    
    public static final Type<StarAbilityC2SPacket> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "star_ability")
    );
    
    public static final StreamCodec<RegistryFriendlyByteBuf, StarAbilityC2SPacket> CODEC = StreamCodec.unit(new StarAbilityC2SPacket());
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}