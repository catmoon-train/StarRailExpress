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

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public class MorticianToggleModeC2SPacket implements CustomPacketPayload {

    public static final Type<MorticianToggleModeC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "mortician_toggle_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MorticianToggleModeC2SPacket> CODEC = StreamCodec.of(
            (buf, packet) -> {},
            buf -> new MorticianToggleModeC2SPacket()
    );

    public MorticianToggleModeC2SPacket() {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
