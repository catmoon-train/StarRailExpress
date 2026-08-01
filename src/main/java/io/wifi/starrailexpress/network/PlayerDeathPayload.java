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

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public class PlayerDeathPayload implements CustomPacketPayload {
    public static final Type<PlayerDeathPayload> ID = new Type<>(SRE.id("player_death"));
    public static final StreamCodec<FriendlyByteBuf, PlayerDeathPayload> CODEC = CustomPacketPayload.codec(PlayerDeathPayload::write, PlayerDeathPayload::new);

    public PlayerDeathPayload(FriendlyByteBuf friendlyByteBuf) {

    }
    public PlayerDeathPayload() {

    }


    public void write(FriendlyByteBuf buf) {

    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
