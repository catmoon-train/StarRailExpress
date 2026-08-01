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

import java.util.UUID;

public record VultureEatC2SPacket(UUID playerBody) implements CustomPacketPayload {
    public static final ResourceLocation VULTURE_PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "vulture");
    public static final Type<VultureEatC2SPacket> ID = new Type<>(VULTURE_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, VultureEatC2SPacket> CODEC;

    public VultureEatC2SPacket(UUID playerBody) {
        this.playerBody = playerBody;
    }

    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.playerBody);
    }

    public static VultureEatC2SPacket read(FriendlyByteBuf buf) {
        return new VultureEatC2SPacket(buf.readUUID());
    }


    public UUID playerBody() {
        return this.playerBody;
    }


    static {
        CODEC = StreamCodec.ofMember(VultureEatC2SPacket::write, VultureEatC2SPacket::read);
    }
}