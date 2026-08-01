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

package io.wifi.starrailexpress.network.packet;

import com.google.common.reflect.TypeToken;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.game.data.MapConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record SyncRoomToPlayerPayload(Map<UUID, Integer> data) implements CustomPacketPayload {
    public static final Type<SyncRoomToPlayerPayload> ID = new Type<>(
            ResourceLocation.tryBuild(SRE.MOD_ID, "sync_roomtoplayer"));
    public static final StreamCodec<FriendlyByteBuf, SyncRoomToPlayerPayload> CODEC = StreamCodec.ofMember(
            (packet, buf) -> {
                buf.writeUtf(MapConfig.gson.toJson(packet.data()));
            },
            buf -> {
                String dat = buf.readUtf();
                java.lang.reflect.Type type = new TypeToken<Map<UUID, Integer>>() {
                }.getType();

                var data1 = new HashMap<UUID, Integer>();
                try {
                    Map<UUID, Integer> data2 = MapConfig.gson.fromJson(dat, type);
                    data1.putAll(data2);
                } catch (Exception e) {
                    data1.clear();
                }
                return new SyncRoomToPlayerPayload(data1);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}