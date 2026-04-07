package io.wifi.starrailexpress.client.fourthroom;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

public record FourthRoomClientSnapshot(
        boolean active,
        String phase,
        String phaseDisplayName,
        long serverTick,
        int rotationCount,
        long nextRotationTick,
        long rotationIntervalTicks,
        boolean hasActiveTask,
        String activeTaskId,
        String activeTaskDescription,
        long taskDeadlineTick,
        long taskDurationTicks,
        String winner,
        String winnerDisplayName,
        Viewer viewer,
        List<RoomPlayer> roomPlayers,
        List<ActionView> roomActions,
        int roomTurnNumber,
        String activePlayerId,
        String activePlayerName) {
    private static final FourthRoomClientSnapshot EMPTY = new FourthRoomClientSnapshot(
            false,
            "INACTIVE",
            "未开始",
            0L,
            0,
            0L,
            0L,
            false,
            "",
            "",
            0L,
            0L,
            "",
            "",
            Viewer.EMPTY,
            List.of(),
            List.of(),
            1,
            "",
            "");

    public static FourthRoomClientSnapshot empty() {
        return EMPTY;
    }

    public static FourthRoomClientSnapshot parse(String json) {
        if (json == null || json.isBlank()) {
            return EMPTY;
        }
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            return new FourthRoomClientSnapshot(
                    getBoolean(root, "active", false),
                    getString(root, "phase"),
                    getString(root, "phaseDisplayName", getString(root, "phase")),
                    getLong(root, "serverTick", 0L),
                    getInt(root, "rotationCount", 0),
                    getLong(root, "nextRotationTick", 0L),
                    getLong(root, "rotationIntervalTicks", 0L),
                    getBoolean(root, "hasActiveTask", false),
                    getString(root, "activeTaskId"),
                    getString(root, "activeTaskDescription"),
                    getLong(root, "taskDeadlineTick", 0L),
                    getLong(root, "taskDurationTicks", 0L),
                    getString(root, "winner"),
                    getString(root, "winnerDisplayName"),
                    parseViewer(root.getAsJsonObject("viewer")),
                    parseRoomPlayers(root.getAsJsonArray("roomPlayers")),
                    parseRoomActions(root.getAsJsonArray("roomActions")),
                    getInt(root, "roomTurnNumber", 1),
                    getString(root, "activePlayerId"),
                    getString(root, "activePlayerName"));
        } catch (Exception ignored) {
            return EMPTY;
        }
    }

    public int secondsUntil(long targetTick) {
        if (targetTick <= 0L || targetTick <= serverTick) {
            return 0;
        }
        return (int) Math.max(0L, (targetTick - serverTick + 19L) / 20L);
    }

    public boolean inCardBattle() {
        return active && "CARD_BATTLE".equals(phase);
    }

    public int latestActionSequence() {
        return roomActions.stream().mapToInt(ActionView::sequence).max().orElse(0);
    }

    public ActionView latestAction() {
        return roomActions.stream().max(java.util.Comparator.comparingInt(ActionView::sequence)).orElse(null);
    }

    private static Viewer parseViewer(JsonObject object) {
        if (object == null) {
            return Viewer.EMPTY;
        }
        return new Viewer(
                getString(object, "uuid"),
                getString(object, "name"),
                getBoolean(object, "alive", false),
                getString(object, "team"),
                getString(object, "teamDisplayName"),
                getInt(object, "roomId", -1),
                getInt(object, "coins", 0),
                getInt(object, "hiddenIdentityCount", 0),
                getBoolean(object, "taskCompleted", false),
                getBoolean(object, "yourTurn", false),
                getInt(object, "lifeShield", 0),
                getInt(object, "skipTurns", 0),
                getInt(object, "extraTurns", 0),
                getInt(object, "markedForKill", 0),
                getBoolean(object, "canReveal", false),
                getBoolean(object, "canEndTurn", false),
                getInt(object, "recentDrawSequence", 0),
                getString(object, "recentDrawSummary"),
                getInt(object, "drawPileSize", 0),
                parseIdentities(object.getAsJsonArray("identities")),
                parseCards(object.getAsJsonArray("hand")),
                parsePeekCards(object.getAsJsonArray("peekCache")),
                parseShopItems(object.getAsJsonArray("shopItems")));
    }

    private static List<Identity> parseIdentities(JsonArray array) {
        List<Identity> result = new ArrayList<>();
        if (array == null) {
            return result;
        }
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            result.add(new Identity(getString(object, "blockId"), getBoolean(object, "revealed", false)));
        }
        return List.copyOf(result);
    }

    private static List<CardView> parseCards(JsonArray array) {
        List<CardView> result = new ArrayList<>();
        if (array == null) {
            return result;
        }
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            result.add(new CardView(
                    getString(object, "id"),
                    getString(object, "displayName", getString(object, "id")),
                    getString(object, "description"),
                    getBoolean(object, "gold", false),
                    getBoolean(object, "requiresTarget", false),
                    getBoolean(object, "skill", false)));
        }
        return List.copyOf(result);
    }

    private static List<PeekCard> parsePeekCards(JsonArray array) {
        List<PeekCard> result = new ArrayList<>();
        if (array == null) {
            return result;
        }
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            result.add(new PeekCard(
                    getString(object, "id"),
                    getString(object, "displayName", getString(object, "id")),
                    getString(object, "description"),
                    getBoolean(object, "skill", false)));
        }
        return List.copyOf(result);
    }

    private static List<ShopItemView> parseShopItems(JsonArray array) {
        List<ShopItemView> result = new ArrayList<>();
        if (array == null) {
            return result;
        }
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            result.add(new ShopItemView(
                    getString(object, "id"),
                    getString(object, "displayName", getString(object, "id")),
                    getString(object, "description"),
                    getInt(object, "price", 0),
                    getInt(object, "ownedCount", 0),
                    getBoolean(object, "canUse", false),
                    getBoolean(object, "requiresTarget", false)));
        }
        return List.copyOf(result);
    }

    private static List<RoomPlayer> parseRoomPlayers(JsonArray array) {
        List<RoomPlayer> result = new ArrayList<>();
        if (array == null) {
            return result;
        }
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            result.add(new RoomPlayer(
                    getString(object, "uuid"),
                    getString(object, "name", getString(object, "uuid")),
                    getBoolean(object, "alive", false),
                    getBoolean(object, "self", false),
                    getBoolean(object, "currentTurn", false),
                    getInt(object, "hiddenIdentityCount", 0)));
        }
        return List.copyOf(result);
    }

    private static List<ActionView> parseRoomActions(JsonArray array) {
        List<ActionView> result = new ArrayList<>();
        if (array == null) {
            return result;
        }
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            result.add(new ActionView(
                    getInt(object, "sequence", 0),
                    getLong(object, "tick", 0L),
                    getString(object, "category", "system"),
                    getString(object, "actorName"),
                    getString(object, "verb"),
                    getString(object, "subject"),
                    getString(object, "targetName"),
                    getString(object, "detail")));
        }
        return List.copyOf(result);
    }

    private static String getString(JsonObject object, String key) {
        return getString(object, key, "");
    }

    private static String getString(JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        return object.get(key).getAsString();
    }

    private static boolean getBoolean(JsonObject object, String key, boolean fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        return object.get(key).getAsBoolean();
    }

    private static int getInt(JsonObject object, String key, int fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        return object.get(key).getAsInt();
    }

    private static long getLong(JsonObject object, String key, long fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        return object.get(key).getAsLong();
    }

    public record Viewer(
            String uuid,
            String name,
            boolean alive,
            String team,
            String teamDisplayName,
            int roomId,
            int coins,
            int hiddenIdentityCount,
            boolean taskCompleted,
            boolean yourTurn,
            int lifeShield,
            int skipTurns,
            int extraTurns,
            int markedForKill,
            boolean canReveal,
            boolean canEndTurn,
            int recentDrawSequence,
            String recentDrawSummary,
            int drawPileSize,
            List<Identity> identities,
            List<CardView> hand,
            List<PeekCard> peekCards,
            List<ShopItemView> shopItems) {
        private static final Viewer EMPTY = new Viewer(
                "",
                "",
                false,
                "",
                "",
                -1,
                0,
                0,
                false,
                false,
                0,
                0,
                0,
                0,
                false,
                false,
                0,
                "",
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    public record Identity(String blockId, boolean revealed) {
    }

    public record CardView(
            String id,
            String displayName,
            String description,
            boolean gold,
            boolean requiresTarget,
            boolean skill) {
    }

    public record PeekCard(
            String id,
            String displayName,
            String description,
            boolean skill) {
    }

    public record ShopItemView(
            String id,
            String displayName,
            String description,
            int price,
            int ownedCount,
            boolean canUse,
            boolean requiresTarget) {
    }

    public record RoomPlayer(
            String uuid,
            String name,
            boolean alive,
            boolean self,
            boolean currentTurn,
            int hiddenIdentityCount) {
    }

    public record ActionView(
            int sequence,
            long tick,
            String category,
            String actorName,
            String verb,
            String subject,
            String targetName,
            String detail) {
        public String summary() {
            StringBuilder builder = new StringBuilder();
            if (!actorName.isBlank()) {
                builder.append(actorName).append(' ');
            }
            if (!verb.isBlank()) {
                builder.append(verb).append(' ');
            }
            if (!subject.isBlank()) {
                builder.append(subject).append(' ');
            }
            if (!targetName.isBlank()) {
                builder.append("对 ").append(targetName).append(' ');
            }
            if (!detail.isBlank()) {
                builder.append(detail);
            }
            return builder.toString().trim();
        }
    }
}