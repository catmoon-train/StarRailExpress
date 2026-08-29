/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/** 巡飞弹控制输入：同步玩家视野方向，并保留 A/D 的小幅辅助转向。 */
public record NiaoshoushouMissileControlC2SPacket(int entityId, float yaw, float pitch, int steering)
        implements CustomPacketPayload {
    public static final ResourceLocation PACKET_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "niaoshoushou_missile_control");
    public static final Type<NiaoshoushouMissileControlC2SPacket> ID = new Type<>(PACKET_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, NiaoshoushouMissileControlC2SPacket> CODEC =
            StreamCodec.ofMember(NiaoshoushouMissileControlC2SPacket::write,
                    NiaoshoushouMissileControlC2SPacket::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
        buf.writeByte(steering);
    }

    public static NiaoshoushouMissileControlC2SPacket read(FriendlyByteBuf buf) {
        return new NiaoshoushouMissileControlC2SPacket(buf.readVarInt(), buf.readFloat(), buf.readFloat(),
                buf.readByte());
    }
}
