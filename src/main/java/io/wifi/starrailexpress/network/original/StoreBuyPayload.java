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

package io.wifi.starrailexpress.network.original;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

public record StoreBuyPayload(int index) implements CustomPacketPayload {
    public static final Type<StoreBuyPayload> ID = new Type<>(SRE.id("storebuy"));
    public static final StreamCodec<FriendlyByteBuf, StoreBuyPayload> CODEC = StreamCodec.composite(ByteBufCodecs.INT, StoreBuyPayload::index, StoreBuyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<StoreBuyPayload> {
        @Override
        public void receive(@NotNull StoreBuyPayload payload, ServerPlayNetworking.@NotNull Context context) {
            SREPlayerShopComponent.KEY.get(context.player()).tryBuy(payload.index());
        }
    }
}