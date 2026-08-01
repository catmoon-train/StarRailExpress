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

package io.wifi.starrailexpress.client.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.wifi.starrailexpress.backpack.BackpackState;
import io.wifi.starrailexpress.progression.ProgressionState;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientPlayerDataCache {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<UUID, Map<String, PartRecord>> PARTS = new ConcurrentHashMap<>();

    private ClientPlayerDataCache() {
    }

    public static void update(UUID playerUuid, String part, String json, long updatedAt) {
        if (playerUuid == null || part == null || part.isBlank() || json == null) {
            return;
        }
        PARTS.computeIfAbsent(playerUuid, ignored -> new ConcurrentHashMap<>())
                .compute(part, (ignored, previous) -> {
                    if (previous != null && previous.updatedAt > updatedAt) {
                        return previous;
                    }
                    return new PartRecord(json, updatedAt);
                });
    }

    public static EconomyState economy(UUID playerUuid) {
        return read(playerUuid, "economy", EconomyState.class, new EconomyState());
    }

    public static ProgressionState progression(UUID playerUuid) {
        return read(playerUuid, "progression", ProgressionState.class, ProgressionState.createDefault());
    }

    public static BackpackState backpack(UUID playerUuid) {
        return read(playerUuid, "backpack", BackpackState.class, BackpackState.createDefault());
    }

    public static void clear() {
        PARTS.clear();
    }

    private static <T> T read(UUID playerUuid, String part, Class<T> type, T fallback) {
        if (playerUuid == null) {
            return fallback;
        }
        PartRecord record = PARTS.getOrDefault(playerUuid, Map.of()).get(part);
        if (record == null || record.json == null || record.json.isBlank()) {
            return fallback;
        }
        try {
            T decoded = GSON.fromJson(record.json, type);
            return decoded == null ? fallback : decoded;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private record PartRecord(String json, long updatedAt) {
    }

    public static final class EconomyState {
        public Map<String, String> equipped = new ConcurrentHashMap<>();
        public Map<String, Map<String, Boolean>> unlocked = new ConcurrentHashMap<>();
        public int lootChance;
        public int coinNum;
        public long version;

        public String getEquippedSkin(String itemType) {
            if (itemType == null || itemType.isBlank()) {
                return "default";
            }
            return equipped.getOrDefault(itemType, "default");
        }

        public boolean isSkinUnlocked(String itemType, String skinName) {
            if ("default".equals(skinName)) {
                return true;
            }
            Map<String, Boolean> skins = unlocked.get(itemType);
            return skins != null && Boolean.TRUE.equals(skins.get(skinName));
        }
    }
}
