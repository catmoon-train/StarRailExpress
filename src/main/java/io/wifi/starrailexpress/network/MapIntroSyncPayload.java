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

import java.util.ArrayList;
import java.util.List;

public record MapIntroSyncPayload(
        List<MapJson> maps,
        List<VoteMap> voteMaps,
        List<String> bagMaps,
        List<String> policeMaps,
        List<String> underwaterMaps,
        List<String> airMaps,
        List<String> trapMaps,
        List<String> horseMaps) implements CustomPacketPayload {
    public static final Type<MapIntroSyncPayload> ID = new Type<>(SRE.id("map_intro_sync"));
    public static final StreamCodec<FriendlyByteBuf, MapIntroSyncPayload> CODEC =
            CustomPacketPayload.codec(MapIntroSyncPayload::write, MapIntroSyncPayload::new);

    public record MapJson(String id, String json) {
        private static MapJson read(FriendlyByteBuf buffer) {
            return new MapJson(buffer.readUtf(256), buffer.readUtf(1_048_576));
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(id, 256);
            buffer.writeUtf(json, 1_048_576);
        }
    }

    public record VoteMap(String id, String displayName, int minCount, int maxCount, boolean canSelect,
            List<String> gameModes) {
        private static VoteMap read(FriendlyByteBuf buffer) {
            return new VoteMap(buffer.readUtf(256), buffer.readUtf(512), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readBoolean(), readStrings(buffer));
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(id == null ? "" : id, 256);
            buffer.writeUtf(displayName == null ? "" : displayName, 512);
            buffer.writeVarInt(minCount);
            buffer.writeVarInt(maxCount);
            buffer.writeBoolean(canSelect);
            writeStrings(buffer, gameModes == null ? List.of() : gameModes);
        }
    }

    private MapIntroSyncPayload(FriendlyByteBuf buffer) {
        this(readMaps(buffer), readVoteMaps(buffer), readStrings(buffer), readStrings(buffer), readStrings(buffer),
                readStrings(buffer), readStrings(buffer), readStrings(buffer));
    }

    private void write(FriendlyByteBuf buffer) {
        List<MapJson> safeMaps = maps == null ? List.of() : maps;
        buffer.writeVarInt(safeMaps.size());
        for (MapJson map : safeMaps) {
            map.write(buffer);
        }
        List<VoteMap> safeVoteMaps = voteMaps == null ? List.of() : voteMaps;
        buffer.writeVarInt(safeVoteMaps.size());
        for (VoteMap map : safeVoteMaps) {
            map.write(buffer);
        }
        writeStrings(buffer, bagMaps);
        writeStrings(buffer, policeMaps);
        writeStrings(buffer, underwaterMaps);
        writeStrings(buffer, airMaps);
        writeStrings(buffer, trapMaps);
        writeStrings(buffer, horseMaps);
    }

    private static List<MapJson> readMaps(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<MapJson> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(MapJson.read(buffer));
        }
        return result;
    }

    private static List<VoteMap> readVoteMaps(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<VoteMap> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(VoteMap.read(buffer));
        }
        return result;
    }

    private static List<String> readStrings(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<String> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(buffer.readUtf(256));
        }
        return result;
    }

    private static void writeStrings(FriendlyByteBuf buffer, List<String> values) {
        List<String> safeValues = values == null ? List.of() : values;
        buffer.writeVarInt(safeValues.size());
        for (String value : safeValues) {
            buffer.writeUtf(value == null ? "" : value, 256);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
