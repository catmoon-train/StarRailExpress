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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/** Normalized read-only map capabilities shared by map voting and the opening HUD. */
public record MapCapabilitySummary(boolean canSwim, boolean canJump, String weather, boolean snow,
        boolean sand, boolean oxygenDrowning, boolean minigameQuest, int roomCount) {

    public static MapCapabilitySummary forMap(String mapId) {
        return fromJson(MapIntroClientCache.get(mapId));
    }

    public static MapCapabilitySummary fromJson(JsonObject root) {
        if (root == null) return new MapCapabilitySummary(false, false, "clear", false, false, false, false, -1);
        JsonObject settings = object(root, "settings");
        JsonObject source = settings == null ? root : settings;
        boolean canJump = bool(source, "canJump", false);
        boolean canSwim;
        if (settings != null) {
            boolean simple = bool(source, "canSimpleSwim", true);
            boolean underwater = bool(source, "canUnderWater", true);
            boolean deepWater = bool(source, "allowInDeepWater", true);
            canSwim = simple && underwater && deepWater && (canJump || bool(source, "canSwim", false));
        } else {
            canSwim = bool(source, "canSwim", false);
        }
        return new MapCapabilitySummary(canSwim, canJump, string(source, "weather", "clear"),
                bool(source, "snowEnabled", false), bool(source, "sandEnabled", false),
                bool(source, "enableOxygenDrowning", false), bool(root, "minigameQuestEnabled", false),
                integer(root, "roomCount", -1));
    }

    public List<Component> ruleLines(int limit) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(canSwim ? "gui.sre.map_briefing.swim" : "gui.sre.map_briefing.no_swim"));
        lines.add(Component.translatable(canJump ? "gui.sre.map_briefing.jump" : "gui.sre.map_briefing.no_jump"));
        if (!weather.isBlank() && !"clear".equalsIgnoreCase(weather)) {
            lines.add(Component.translatable("gui.sre.map_briefing.weather", weather));
        }
        if (snow) lines.add(Component.translatable("gui.sre.map_briefing.snow"));
        if (sand) lines.add(Component.translatable("gui.sre.map_briefing.sand"));
        if (oxygenDrowning) lines.add(Component.translatable("gui.sre.map_briefing.oxygen"));
        if (minigameQuest) lines.add(Component.translatable("gui.sre.map_briefing.minigame"));
        if (lines.size() <= 2) lines.add(Component.translatable("gui.sre.map_briefing.explore"));
        return List.copyOf(lines.subList(0, Math.min(Math.max(0, limit), lines.size())));
    }

    private static JsonObject object(JsonObject root, String key) {
        JsonElement value = root.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static boolean bool(JsonObject root, String key, boolean fallback) {
        try {
            JsonElement value = root.get(key);
            return value == null || value.isJsonNull() ? fallback : value.getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int integer(JsonObject root, String key, int fallback) {
        try {
            JsonElement value = root.get(key);
            return value == null || value.isJsonNull() ? fallback : value.getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String string(JsonObject root, String key, String fallback) {
        try {
            JsonElement value = root.get(key);
            return value == null || value.isJsonNull() ? fallback : value.getAsString();
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
