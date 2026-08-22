/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/** Starts (positive ticks) or stops (zero ticks) the local rewind effect. */
public record TimeRewindVisualS2CPacket(int durationTicks) implements CustomPacketPayload {
    public static final Type<TimeRewindVisualS2CPacket> ID =
            new Type<>(Noellesroles.id("time_rewind_visual"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TimeRewindVisualS2CPacket> CODEC =
            StreamCodec.ofMember(TimeRewindVisualS2CPacket::write, TimeRewindVisualS2CPacket::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(durationTicks);
    }

    private static TimeRewindVisualS2CPacket read(RegistryFriendlyByteBuf buf) {
        return new TimeRewindVisualS2CPacket(buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
