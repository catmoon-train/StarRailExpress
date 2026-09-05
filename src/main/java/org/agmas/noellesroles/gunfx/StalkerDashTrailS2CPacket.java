/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.agmas.noellesroles.gunfx;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/** 服务端→客户端：潜行者一个真实冲刺位移段，用于绘制双刀残影。 */
public record StalkerDashTrailS2CPacket(int playerId,
        double fromX, double fromY, double fromZ,
        double toX, double toY, double toZ,
        boolean attackDash) implements CustomPacketPayload {

    public static final Type<StalkerDashTrailS2CPacket> ID =
            new Type<>(Noellesroles.id("stalker_dash_trail"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StalkerDashTrailS2CPacket> CODEC =
            StreamCodec.ofMember(StalkerDashTrailS2CPacket::encode, StalkerDashTrailS2CPacket::decode);

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(playerId);
        buf.writeDouble(fromX);
        buf.writeDouble(fromY);
        buf.writeDouble(fromZ);
        buf.writeDouble(toX);
        buf.writeDouble(toY);
        buf.writeDouble(toZ);
        buf.writeBoolean(attackDash);
    }

    private static StalkerDashTrailS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new StalkerDashTrailS2CPacket(buf.readVarInt(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
