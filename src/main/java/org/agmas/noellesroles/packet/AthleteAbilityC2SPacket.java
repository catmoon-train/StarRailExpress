package org.agmas.noellesroles.packet;


import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 运动员技能网络包
 * 客户端 -> 服务端
 * 
 * 当玩家按下技能键时发送，请求激活疾跑技能
 */
public record AthleteAbilityC2SPacket() implements CustomPacketPayload {
    
    public static final Type<AthleteAbilityC2SPacket> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "athlete_ability")
    );
    
    public static final StreamCodec<RegistryFriendlyByteBuf, AthleteAbilityC2SPacket> CODEC = StreamCodec.unit(new AthleteAbilityC2SPacket());
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}