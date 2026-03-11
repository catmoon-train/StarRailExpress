package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 拳击手技能网络包
 * 客户端 -> 服务端
 * 
 * 当玩家按下技能键时发送，请求激活钢筋铁骨技能
 */
public record BoxerAbilityC2SPacket() implements CustomPacketPayload {
    
    public static final Type<BoxerAbilityC2SPacket> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "boxer_ability")
    );
    
    public static final StreamCodec<RegistryFriendlyByteBuf, BoxerAbilityC2SPacket> CODEC = StreamCodec.unit(new BoxerAbilityC2SPacket());
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}