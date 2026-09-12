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

package io.wifi.starrailexpress.client.gui.screen.mapui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.wifi.starrailexpress.network.MapIntroSyncPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/** Shared, client-only copy of the map metadata supplied for the map vote. */
public final class MapIntroClientCache {
    private static final Map<String, JsonObject> MAPS = new HashMap<>();
    private static final Map<String, MapIntroSyncPayload.VoteMap> VOTE_MAPS = new HashMap<>();
    private static final Set<String> BAG_MAPS = new HashSet<>();
    private static final Set<String> POLICE_MAPS = new HashSet<>();
    private static final Set<String> UNDERWATER_MAPS = new HashSet<>();
    private static final Set<String> AIR_MAPS = new HashSet<>();
    private static final Set<String> TRAP_MAPS = new HashSet<>();
    private static final Set<String> HORSE_MAPS = new HashSet<>();
    private static final Set<String> LAB_MAPS = new HashSet<>();
    private static long refreshRequestedAt;

    private MapIntroClientCache() {}

    public static void update(MapIntroSyncPayload payload) {
        accept(payload);
    }

    /**
     * 合并分块：{@code chunkIndex == 0} 时重置，后续块只追加地图 JSON。
     * 返回当前已累积的完整视图，供界面一次性刷新。
     */
    public static MapIntroSyncPayload accept(MapIntroSyncPayload payload) {
        if (payload == null) {
            return snapshot();
        }
        if (payload.chunkIndex() <= 0) {
            MAPS.clear();
            VOTE_MAPS.clear();
            BAG_MAPS.clear();
            POLICE_MAPS.clear();
            UNDERWATER_MAPS.clear();
            AIR_MAPS.clear();
            TRAP_MAPS.clear();
            HORSE_MAPS.clear();
            LAB_MAPS.clear();
        }
        applyMeta(payload);
        applyMaps(payload);
        refreshRequestedAt = 0L;
        return snapshot();
    }

    private static void applyMeta(MapIntroSyncPayload payload) {
        if (payload.voteMaps() != null) {
            for (MapIntroSyncPayload.VoteMap entry : payload.voteMaps()) {
                if (entry != null && entry.id() != null) {
                    VOTE_MAPS.put(entry.id(), entry);
                }
            }
        }
        addAll(BAG_MAPS, payload.bagMaps());
        addAll(POLICE_MAPS, payload.policeMaps());
        addAll(UNDERWATER_MAPS, payload.underwaterMaps());
        addAll(AIR_MAPS, payload.airMaps());
        addAll(TRAP_MAPS, payload.trapMaps());
        addAll(HORSE_MAPS, payload.horseMaps());
        addAll(LAB_MAPS, payload.labMaps());
    }

    private static void addAll(Set<String> target, List<String> values) {
        if (values != null) {
            target.addAll(values);
        }
    }

    private static void applyMaps(MapIntroSyncPayload payload) {
        if (payload.maps() == null) {
            return;
        }
        for (MapIntroSyncPayload.MapJson entry : payload.maps()) {
            try {
                MAPS.put(entry.id(), JsonParser.parseString(entry.json()).getAsJsonObject());
            } catch (Exception ignored) {
                // A malformed optional map description should not prevent the vote UI from opening.
            }
        }
    }

    public static MapIntroSyncPayload snapshot() {
        List<MapIntroSyncPayload.MapJson> maps = new ArrayList<>();
        for (Map.Entry<String, JsonObject> entry : MAPS.entrySet()) {
            maps.add(new MapIntroSyncPayload.MapJson(entry.getKey(), entry.getValue().toString()));
        }
        return new MapIntroSyncPayload(
                maps,
                List.copyOf(VOTE_MAPS.values()),
                List.copyOf(BAG_MAPS),
                List.copyOf(POLICE_MAPS),
                List.copyOf(UNDERWATER_MAPS),
                List.copyOf(AIR_MAPS),
                List.copyOf(TRAP_MAPS),
                List.copyOf(HORSE_MAPS),
                List.copyOf(LAB_MAPS));
    }

    public static void beginRefresh() {
        refreshRequestedAt = System.currentTimeMillis();
    }

    /** Wait briefly for authoritative metadata, but never strand the opening if an optional packet is lost. */
    public static boolean isRefreshPending() {
        return refreshRequestedAt > 0L && System.currentTimeMillis() - refreshRequestedAt < 2_500L;
    }

    @Nullable
    public static JsonObject get(String id) {
        return MAPS.get(id);
    }

    @Nullable
    public static MapIntroSyncPayload.VoteMap getVoteMap(String id) {
        return VOTE_MAPS.get(id);
    }

    public static Set<String> specialTags(String id) {
        Set<String> tags = new HashSet<>();
        if (BAG_MAPS.contains(id)) tags.add("bag");
        if (POLICE_MAPS.contains(id)) tags.add("police");
        if (UNDERWATER_MAPS.contains(id)) tags.add("underwater");
        if (AIR_MAPS.contains(id)) tags.add("air");
        if (TRAP_MAPS.contains(id)) tags.add("trap");
        if (HORSE_MAPS.contains(id)) tags.add("horse");
        if (LAB_MAPS.contains(id)) tags.add("lab");
        return Set.copyOf(tags);
    }
}
