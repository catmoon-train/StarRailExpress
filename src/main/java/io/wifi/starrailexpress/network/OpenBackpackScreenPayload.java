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

public record OpenBackpackScreenPayload() implements CustomPacketPayload {
    public static final Type<OpenBackpackScreenPayload> ID = new Type<>(SRE.id("open_backpack_screen"));
    public static final StreamCodec<FriendlyByteBuf, OpenBackpackScreenPayload> CODEC =
            CustomPacketPayload.codec(OpenBackpackScreenPayload::encode, OpenBackpackScreenPayload::decode);

    public static final OpenBackpackScreenPayload INSTANCE = new OpenBackpackScreenPayload();

    public static void encode(OpenBackpackScreenPayload payload, FriendlyByteBuf buf) {
    }

    public static OpenBackpackScreenPayload decode(FriendlyByteBuf buf) {
        return INSTANCE;
    }

    @Override
    public Type<OpenBackpackScreenPayload> type() {
        return ID;
    }
}
