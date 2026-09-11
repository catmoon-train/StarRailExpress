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
import java.util.HashMap;
import java.util.HashSet;
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
        MAPS.clear();
        VOTE_MAPS.clear();
        BAG_MAPS.clear();
        POLICE_MAPS.clear();
        UNDERWATER_MAPS.clear();
        AIR_MAPS.clear();
        TRAP_MAPS.clear();
        HORSE_MAPS.clear();
        LAB_MAPS.clear();
        for (MapIntroSyncPayload.MapJson entry : payload.maps()) {
            try {
                MAPS.put(entry.id(), JsonParser.parseString(entry.json()).getAsJsonObject());
            } catch (Exception ignored) {
                // A malformed optional map description should not prevent the vote UI from opening.
            }
        }
        for (MapIntroSyncPayload.VoteMap entry : payload.voteMaps()) {
            VOTE_MAPS.put(entry.id(), entry);
        }
        BAG_MAPS.addAll(payload.bagMaps());
        POLICE_MAPS.addAll(payload.policeMaps());
        UNDERWATER_MAPS.addAll(payload.underwaterMaps());
        AIR_MAPS.addAll(payload.airMaps());
        TRAP_MAPS.addAll(payload.trapMaps());
        HORSE_MAPS.addAll(payload.horseMaps());
        LAB_MAPS.addAll(payload.labMaps());
        refreshRequestedAt = 0L;
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
