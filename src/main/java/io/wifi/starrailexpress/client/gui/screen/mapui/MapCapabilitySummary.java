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

import io.wifi.starrailexpress.network.MapDisplayInfo;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/** Normalized read-only map capabilities shared by map voting and the opening HUD. */
public record MapCapabilitySummary(boolean canSwim, boolean canJump, String weather, boolean snow,
        boolean sand, boolean oxygenDrowning, boolean minigameQuest, boolean planeCrash, int roomCount) {

    public static MapCapabilitySummary forMap(String mapId) {
        return fromDisplayInfo(MapIntroClientCache.get(mapId));
    }

    /** 直接从服务端解析好的展示数据取，不再解析 JSON。 */
    public static MapCapabilitySummary fromDisplayInfo(MapDisplayInfo info) {
        if (info == null) {
            return new MapCapabilitySummary(false, false, "clear", false, false, false, false, false, -1);
        }
        return new MapCapabilitySummary(info.displayCanSwim(), info.canJump(), info.weather(), info.snowEnabled(),
                info.sandEnabled(), info.enableOxygenDrowning(), info.minigameQuestEnabled(),
                info.planeCrashEventEnabled(), info.roomCount());
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
        if (planeCrash) lines.add(Component.translatable("gui.sre.map_briefing.plane_crash"));
        if (lines.size() <= 2) lines.add(Component.translatable("gui.sre.map_briefing.explore"));
        return List.copyOf(lines.subList(0, Math.min(Math.max(0, limit), lines.size())));
    }
}
