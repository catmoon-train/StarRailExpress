/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/** Short-lived vanilla-input lease for the client that owns a possessed body. */
public record FakeSteveControlS2CPacket(long sequence, int durationTicks,
        float forward, float strafe, boolean jump, boolean sprint,
        boolean crouch, float targetYaw, float targetPitch,
        boolean active) implements CustomPacketPayload {
    public static final Type<FakeSteveControlS2CPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "fake_steve_control"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FakeSteveControlS2CPacket> CODEC =
            StreamCodec.ofMember(FakeSteveControlS2CPacket::write, FakeSteveControlS2CPacket::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarLong(sequence);
        buf.writeVarInt(durationTicks);
        buf.writeFloat(forward);
        buf.writeFloat(strafe);
        buf.writeBoolean(jump);
        buf.writeBoolean(sprint);
        buf.writeBoolean(crouch);
        buf.writeFloat(targetYaw);
        buf.writeFloat(targetPitch);
        buf.writeBoolean(active);
    }

    private static FakeSteveControlS2CPacket read(FriendlyByteBuf buf) {
        return new FakeSteveControlS2CPacket(buf.readVarLong(), buf.readVarInt(),
                buf.readFloat(), buf.readFloat(), buf.readBoolean(),
                buf.readBoolean(), buf.readBoolean(), buf.readFloat(),
                buf.readFloat(), buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
