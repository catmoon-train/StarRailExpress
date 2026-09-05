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

/** 服务端→客户端：刺客形态命中后的红色穿透斩击。 */
public record StalkerPierceFxS2CPacket(double x, double y, double z,
        double dirX, double dirY, double dirZ) implements CustomPacketPayload {

    public static final Type<StalkerPierceFxS2CPacket> ID =
            new Type<>(Noellesroles.id("stalker_pierce_fx"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StalkerPierceFxS2CPacket> CODEC =
            StreamCodec.ofMember(StalkerPierceFxS2CPacket::encode, StalkerPierceFxS2CPacket::decode);

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeDouble(dirX);
        buf.writeDouble(dirY);
        buf.writeDouble(dirZ);
    }

    private static StalkerPierceFxS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new StalkerPierceFxS2CPacket(
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
