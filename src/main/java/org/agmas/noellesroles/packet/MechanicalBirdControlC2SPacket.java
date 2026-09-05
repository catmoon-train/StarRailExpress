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

/** 机械小鸟控制输入：同步视野方向与 WASD/跳跃/潜行。 */
public record MechanicalBirdControlC2SPacket(int entityId, float yaw, float pitch, int movementBits)
        implements CustomPacketPayload {
    public static final int BIT_FORWARD = 1;
    public static final int BIT_BACK = 2;
    public static final int BIT_LEFT = 4;
    public static final int BIT_RIGHT = 8;
    public static final int BIT_JUMP = 16;
    public static final int BIT_SNEAK = 32;

    public static final ResourceLocation PACKET_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "mechanical_bird_control");
    public static final Type<MechanicalBirdControlC2SPacket> ID = new Type<>(PACKET_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, MechanicalBirdControlC2SPacket> CODEC =
            StreamCodec.ofMember(MechanicalBirdControlC2SPacket::write, MechanicalBirdControlC2SPacket::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
        buf.writeVarInt(movementBits);
    }

    public static MechanicalBirdControlC2SPacket read(FriendlyByteBuf buf) {
        return new MechanicalBirdControlC2SPacket(buf.readVarInt(), buf.readFloat(), buf.readFloat(),
                buf.readVarInt());
    }
}
