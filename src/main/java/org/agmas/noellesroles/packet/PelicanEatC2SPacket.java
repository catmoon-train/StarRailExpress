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

public record PelicanEatC2SPacket() implements CustomPacketPayload {
    public static final ResourceLocation PELICAN_EAT_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "pelican_eat");
    public static final Type<PelicanEatC2SPacket> ID = new Type<>(PELICAN_EAT_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, PelicanEatC2SPacket> CODEC;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {}
    public static PelicanEatC2SPacket read(FriendlyByteBuf buf) {
        return new PelicanEatC2SPacket();
    }

    static {
        CODEC = StreamCodec.ofMember(PelicanEatC2SPacket::write, PelicanEatC2SPacket::read);
    }
}
