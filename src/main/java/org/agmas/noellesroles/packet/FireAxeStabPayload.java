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
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

public record FireAxeStabPayload(int target) implements CustomPacketPayload {
    public static final Type<FireAxeStabPayload> ID = new Type<>(Noellesroles.id("fire_axe_stab"));
    public static final StreamCodec<FriendlyByteBuf, FireAxeStabPayload> CODEC = StreamCodec.composite(ByteBufCodecs.INT,
            FireAxeStabPayload::target, FireAxeStabPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}