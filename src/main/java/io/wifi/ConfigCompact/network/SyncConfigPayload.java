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

package io.wifi.ConfigCompact.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncConfigPayload(String configId, String content) implements CustomPacketPayload {
    public static final Type<SyncConfigPayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, "sync_config"));
    public static final StreamCodec<FriendlyByteBuf, SyncConfigPayload> CODEC = StreamCodec
            .ofMember(SyncConfigPayload::encode, SyncConfigPayload::decode);

    public static SyncConfigPayload decode(FriendlyByteBuf buf) {
        return new SyncConfigPayload(buf.readUtf(), buf.readUtf());
    }

    public static void encode(SyncConfigPayload payload, FriendlyByteBuf buf) {
        buf.writeUtf(payload.configId);
        buf.writeUtf(payload.content);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}