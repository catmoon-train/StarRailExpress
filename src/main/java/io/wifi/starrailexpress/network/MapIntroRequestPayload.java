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

public record MapIntroRequestPayload(boolean includeAll) implements CustomPacketPayload {
    public static final Type<MapIntroRequestPayload> ID = new Type<>(SRE.id("map_intro_request"));
    public static final StreamCodec<FriendlyByteBuf, MapIntroRequestPayload> CODEC =
            CustomPacketPayload.codec(MapIntroRequestPayload::write, MapIntroRequestPayload::new);

    public MapIntroRequestPayload() {
        this(false);
    }

    private MapIntroRequestPayload(FriendlyByteBuf buffer) {
        // 旧客户端的这个包是空的：没有字节就当作 includeAll=false，别抛异常
        this(buffer.readableBytes() >= 1 && buffer.readBoolean());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeBoolean(includeAll);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
