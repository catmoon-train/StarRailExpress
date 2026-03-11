package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 跟踪者突进网络包
 * 客户端 -> 服务端
 * 
 * 用于控制三阶段的蓄力突进
 * - charging=true: 开始蓄力
 * - charging=false: 释放突进
 */
public record StalkerDashC2SPacket(boolean charging) implements CustomPacketPayload {

    public static final Type<StalkerDashC2SPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "stalker_dash"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StalkerDashC2SPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, StalkerDashC2SPacket::charging,
            StalkerDashC2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}