package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 歌手技能网络包
 * 客户端 -> 服务端
 * 
 * 当玩家按下技能键时发送，请求播放随机音乐
 */
public record SingerAbilityC2SPacket() implements CustomPacketPayload {
    
    public static final Type<SingerAbilityC2SPacket> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "singer_ability")
    );
    
    public static final StreamCodec<RegistryFriendlyByteBuf, SingerAbilityC2SPacket> CODEC = StreamCodec.unit(new SingerAbilityC2SPacket());
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}