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

package net.exmo.sre.repair.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;
import net.exmo.sre.repair.state.RepairSearchState;

public record RepairSearchCancelC2SPacket() implements CustomPacketPayload {
    public static final Type<RepairSearchCancelC2SPacket> ID = new Type<>(Noellesroles.id("repair_search_cancel"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RepairSearchCancelC2SPacket> CODEC = StreamCodec
            .ofMember(RepairSearchCancelC2SPacket::encode, RepairSearchCancelC2SPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
    }

    public static RepairSearchCancelC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new RepairSearchCancelC2SPacket();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(RepairSearchCancelC2SPacket payload, ServerPlayNetworking.Context context) {
        RepairSearchState.cancel(context.player());
    }
}
