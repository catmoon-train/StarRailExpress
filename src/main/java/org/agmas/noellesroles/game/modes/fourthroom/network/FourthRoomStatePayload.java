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
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.game.modes.fourthroom.game.FourthRoomGameManager;

public record FourthRoomStatePayload(String json) implements CustomPacketPayload {
    public static final Type<FourthRoomStatePayload> ID = new Type<>(SRE.id("fourth_room_state"));
    public static final StreamCodec<FriendlyByteBuf, FourthRoomStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            FourthRoomStatePayload::json,
            FourthRoomStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void send(ServerPlayer player) {
        FourthRoomGameManager manager = FourthRoomGameManager.of(player.serverLevel());
        send(player, manager.buildSnapshot(player).toString());
    }

    public static void send(ServerPlayer player, String json) {
        ServerPlayNetworking.send(player, new FourthRoomStatePayload(json));
    }

    @Environment(EnvType.CLIENT)
    public static void registerReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(ID, (payload, context) ->
                context.client().execute(() -> io.wifi.starrailexpress.client.fourthroom.FourthRoomClientState.updateSnapshot(payload.json())));
    }
}