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
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

public record MediumAnswerC2SPacket(int answerId) implements CustomPacketPayload {

    public static final Type<MediumAnswerC2SPacket> ID = new Type<>(Noellesroles.id("medium_answer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MediumAnswerC2SPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MediumAnswerC2SPacket::answerId,
            MediumAnswerC2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
