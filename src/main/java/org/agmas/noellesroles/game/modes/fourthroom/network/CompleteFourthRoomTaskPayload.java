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

package org.agmas.noellesroles.game.modes.fourthroom.network;

import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.game.modes.fourthroom.game.FourthRoomGameManager;
import org.jetbrains.annotations.NotNull;

public record CompleteFourthRoomTaskPayload() implements CustomPacketPayload {
    public static final Type<CompleteFourthRoomTaskPayload> ID = new Type<>(SRE.id("fourth_room_complete_task"));
    public static final StreamCodec<FriendlyByteBuf, CompleteFourthRoomTaskPayload> CODEC = StreamCodec.unit(new CompleteFourthRoomTaskPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<CompleteFourthRoomTaskPayload> {
        @Override
        public void receive(@NotNull CompleteFourthRoomTaskPayload payload, ServerPlayNetworking.@NotNull Context context) {
            FourthRoomGameManager.of(context.player().serverLevel()).taskScheduler().completeTask(context.player().getUUID());
        }
    }
}