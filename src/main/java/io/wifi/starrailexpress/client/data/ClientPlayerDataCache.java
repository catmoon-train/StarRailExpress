package io.wifi.starrailexpress.client.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
