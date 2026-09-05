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
        return Set.copyOf(tags);
    }
}
