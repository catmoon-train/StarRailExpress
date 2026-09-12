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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 地图介绍 / 投票 UI 用的元数据。
 * <p>
 * 完整 {@code train_maps/*.json} 含坐标、场景资产等，整包下发会超过
 * custom_payload 可用长度，客户端解码越界后被踢。这里只带介绍字段，并按块发送。
 */
public record MapIntroSyncPayload(
        int chunkIndex,
        int totalChunks,
        List<MapJson> maps,
        List<VoteMap> voteMaps,
        List<String> bagMaps,
        List<String> policeMaps,
        List<String> underwaterMaps,
        List<String> airMaps,
        List<String> trapMaps,
        List<String> horseMaps,
        List<String> labMaps) implements CustomPacketPayload {
    public static final Type<MapIntroSyncPayload> ID = new Type<>(SRE.id("map_intro_sync"));
    public static final StreamCodec<FriendlyByteBuf, MapIntroSyncPayload> CODEC =
            CustomPacketPayload.codec(MapIntroSyncPayload::write, MapIntroSyncPayload::new);

    /** 单包地图 JSON 字符预算，远低于 Minecraft custom_payload / writeUtf 上限。 */
    public static final int MAX_CHUNK_CHARS = 24_000;
    private static final int MAX_MAP_JSON_CHARS = 50_000;
    private static final int MAX_LIST = 4_096;

    private static final Set<String> INTRO_ROOT_KEYS = Set.of(
            "roomCount",
            "disabledTasks",
            "disabledRoles",
            "enableSceneTask",
            "minigameQuestEnabled",
            "meetingEnabled",
            "meetingVoteEnabled",
            "bellMeetingEnabled",
            "mapStatusBar",
            "canSwim",
            "canJump",
            "enableOxygenDrowning",
            "snowEnabled",
            "sandEnabled",
            "planeCrashEventEnabled",
            "fogEnabled",
            "fogEnd",
            "weather",
            "gravity",
            "effect",
            "initialItems",
            "time",
            "daylightCycle",
            "weatherCycle");

    private static final Set<String> INTRO_SETTINGS_KEYS = Set.of(
            "canJump",
            "canSwim",
            "canSimpleSwim",
            "canUnderWater",
            "allowInDeepWater",
            "enableOxygenDrowning",
            "minigameQuestEnabled",
            "gravityModifier",
            "mapStatusBar",
            "initialItems",
            "mobEffects",
            "snowEnabled",
            "sandEnabled",
            "fogEnabled",
            "fogEnd",
            "weather",
            "time",
            "daylightCycle",
            "weatherCycle",
            "meetingEnabled",
            "meetingVoteEnabled",
            "bellMeetingEnabled",
            "planeCrashEventEnabled");

    public record MapJson(String id, String json) {
        private static MapJson read(FriendlyByteBuf buffer) {
            return new MapJson(buffer.readUtf(256), buffer.readUtf(MAX_MAP_JSON_CHARS));
        }

        private void write(FriendlyByteBuf buffer) {
            buffer.writeUtf(id == null ? "" : id, 256);
            String safeJson = json == null ? "{}" : json;
            if (safeJson.length() > MAX_MAP_JSON_CHARS) {
                safeJson = "{}";
            }
            buffer.writeUtf(safeJson, MAX_MAP_JSON_CHARS);
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

    public MapIntroSyncPayload(
            List<MapJson> maps,
            List<VoteMap> voteMaps,
            List<String> bagMaps,
            List<String> policeMaps,
            List<String> underwaterMaps,
            List<String> airMaps,
            List<String> trapMaps,
            List<String> horseMaps,
            List<String> labMaps) {
        this(0, 1, maps, voteMaps, bagMaps, policeMaps, underwaterMaps, airMaps, trapMaps, horseMaps, labMaps);
    }

    private MapIntroSyncPayload(FriendlyByteBuf buffer) {
        this(decodeOrEmpty(buffer));
    }

    private MapIntroSyncPayload(MapIntroSyncPayload decoded) {
        this(decoded.chunkIndex(), decoded.totalChunks(), decoded.maps(), decoded.voteMaps(), decoded.bagMaps(),
                decoded.policeMaps(), decoded.underwaterMaps(), decoded.airMaps(), decoded.trapMaps(),
                decoded.horseMaps(), decoded.labMaps());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(Math.max(0, chunkIndex));
        buffer.writeVarInt(Math.max(1, totalChunks));
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
        writeStrings(buffer, labMaps);
    }

    /** 去掉出生点、场景资产等游戏数据，只留介绍 UI 用到的字段。 */
    public static String slimMapJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return "{}";
        }
        try {
            JsonElement parsed = JsonParser.parseString(raw);
            if (!parsed.isJsonObject()) {
                return "{}";
            }
            JsonObject root = parsed.getAsJsonObject();
            JsonObject slim = new JsonObject();
            for (String key : INTRO_ROOT_KEYS) {
                if (root.has(key)) {
                    slim.add(key, root.get(key));
                }
            }
            if (root.has("settings") && root.get("settings").isJsonObject()) {
                slim.add("settings", slimSettings(root.getAsJsonObject("settings")));
            }
            String json = slim.toString();
            if (json.length() <= MAX_MAP_JSON_CHARS) {
                return json;
            }
            JsonObject tiny = new JsonObject();
            if (slim.has("roomCount")) {
                tiny.add("roomCount", slim.get("roomCount"));
            }
            if (slim.has("settings")) {
                tiny.add("settings", slim.get("settings"));
            }
            json = tiny.toString();
            return json.length() <= MAX_MAP_JSON_CHARS ? json : "{}";
        } catch (Exception ignored) {
            return "{}";
        }
    }

    public static List<MapIntroSyncPayload> split(
            List<MapJson> maps,
            List<VoteMap> voteMaps,
            List<String> bagMaps,
            List<String> policeMaps,
            List<String> underwaterMaps,
            List<String> airMaps,
            List<String> trapMaps,
            List<String> horseMaps,
            List<String> labMaps) {
        List<MapJson> safeMaps = maps == null ? List.of() : maps;
        List<VoteMap> safeVoteMaps = voteMaps == null ? List.of() : voteMaps;
        List<String> safeBag = bagMaps == null ? List.of() : bagMaps;
        List<String> safePolice = policeMaps == null ? List.of() : policeMaps;
        List<String> safeUnderwater = underwaterMaps == null ? List.of() : underwaterMaps;
        List<String> safeAir = airMaps == null ? List.of() : airMaps;
        List<String> safeTrap = trapMaps == null ? List.of() : trapMaps;
        List<String> safeHorse = horseMaps == null ? List.of() : horseMaps;
        List<String> safeLab = labMaps == null ? List.of() : labMaps;
        List<List<MapJson>> batches = new ArrayList<>();
        List<MapJson> current = new ArrayList<>();
        int currentSize = 0;
        for (MapJson map : safeMaps) {
            int size = estimate(map);
            if (!current.isEmpty() && currentSize + size > MAX_CHUNK_CHARS) {
                batches.add(current);
                current = new ArrayList<>();
                currentSize = 0;
            }
            current.add(map);
            currentSize += size;
        }
        if (!current.isEmpty() || batches.isEmpty()) {
            batches.add(current);
        }
        int total = batches.size();
        List<MapIntroSyncPayload> result = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            boolean first = i == 0;
            result.add(new MapIntroSyncPayload(
                    i,
                    total,
                    batches.get(i),
                    first ? safeVoteMaps : List.of(),
                    first ? safeBag : List.of(),
                    first ? safePolice : List.of(),
                    first ? safeUnderwater : List.of(),
                    first ? safeAir : List.of(),
                    first ? safeTrap : List.of(),
                    first ? safeHorse : List.of(),
                    first ? safeLab : List.of()));
        }
        return result;
    }

    private static JsonObject slimSettings(JsonObject settings) {
        JsonObject slim = new JsonObject();
        for (String key : INTRO_SETTINGS_KEYS) {
            if (settings.has(key)) {
                slim.add(key, settings.get(key));
            }
        }
        return slim;
    }

    private static int estimate(MapJson map) {
        int id = map == null || map.id() == null ? 0 : map.id().length();
        int json = map == null || map.json() == null ? 0 : map.json().length();
        return id + json + 16;
    }

    private static MapIntroSyncPayload decodeOrEmpty(FriendlyByteBuf buffer) {
        try {
            int chunkIndex = Math.max(0, readVarIntOr(buffer, 0));
            int totalChunks = Math.max(1, readVarIntOr(buffer, 1));
            return new MapIntroSyncPayload(
                    chunkIndex,
                    totalChunks,
                    readMaps(buffer),
                    readVoteMaps(buffer),
                    readStrings(buffer),
                    readStrings(buffer),
                    readStrings(buffer),
                    readStrings(buffer),
                    readStrings(buffer),
                    readStrings(buffer),
                    readStrings(buffer));
        } catch (Exception e) {
            SRE.LOGGER.warn("Failed to decode map intro payload; ignoring truncated packet", e);
            return new MapIntroSyncPayload(0, 1, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(), List.of(), List.of());
        }
    }

    private static int readVarIntOr(FriendlyByteBuf buffer, int fallback) {
        if (buffer.readableBytes() <= 0) {
            return fallback;
        }
        return buffer.readVarInt();
    }

    private static List<MapJson> readMaps(FriendlyByteBuf buffer) {
        int size = readVarIntOr(buffer, 0);
        if (size <= 0) {
            return List.of();
        }
        List<MapJson> result = new ArrayList<>(Math.min(size, MAX_LIST));
        for (int i = 0; i < size; i++) {
            if (buffer.readableBytes() <= 0) {
                break;
            }
            try {
                result.add(MapJson.read(buffer));
            } catch (Exception ignored) {
                break;
            }
        }
        return result;
    }

    private static List<VoteMap> readVoteMaps(FriendlyByteBuf buffer) {
        int size = readVarIntOr(buffer, 0);
        if (size <= 0) {
            return List.of();
        }
        List<VoteMap> result = new ArrayList<>(Math.min(size, MAX_LIST));
        for (int i = 0; i < size; i++) {
            if (buffer.readableBytes() <= 0) {
                break;
            }
            try {
                result.add(VoteMap.read(buffer));
            } catch (Exception ignored) {
                break;
            }
        }
        return result;
    }

    private static List<String> readStrings(FriendlyByteBuf buffer) {
        int size = readVarIntOr(buffer, 0);
        if (size <= 0) {
            return List.of();
        }
        List<String> result = new ArrayList<>(Math.min(size, MAX_LIST));
        for (int i = 0; i < size; i++) {
            if (buffer.readableBytes() <= 0) {
                break;
            }
            try {
                result.add(buffer.readUtf(256));
            } catch (Exception ignored) {
                break;
            }
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
