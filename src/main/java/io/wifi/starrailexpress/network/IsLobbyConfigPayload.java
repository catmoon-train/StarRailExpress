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

package io.wifi.starrailexpress.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record IsLobbyConfigPayload(boolean isLobby) implements CustomPacketPayload {
    public static final Type<IsLobbyConfigPayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath("starrailexpress", "islobby_config"));
    public static final StreamCodec<FriendlyByteBuf, IsLobbyConfigPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, IsLobbyConfigPayload::isLobby,
            IsLobbyConfigPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public IsLobbyConfigPayload(boolean isLobby) {
        this.isLobby = isLobby;
    }

}