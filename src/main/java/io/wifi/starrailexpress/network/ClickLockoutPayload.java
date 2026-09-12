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
import net.minecraft.resources.ResourceLocation;

public record ClickLockoutPayload(int remainingMillis) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, "click_lockout");
    public static final Type<ClickLockoutPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, ClickLockoutPayload> CODEC =
            StreamCodec.ofMember(ClickLockoutPayload::write, ClickLockoutPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(remainingMillis);
    }

    public static ClickLockoutPayload read(FriendlyByteBuf buf) {
        return new ClickLockoutPayload(buf.readVarInt());
    }
}
