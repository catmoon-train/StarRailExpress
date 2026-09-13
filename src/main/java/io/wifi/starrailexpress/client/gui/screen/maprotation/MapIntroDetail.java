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

package io.wifi.starrailexpress.client.gui.screen.maprotation;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.client.gui.screen.MapSpecialRoleLines;
import io.wifi.starrailexpress.network.MapDisplayInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 把服务端解析好的 {@link MapDisplayInfo} 渲染成可换行的地图详情文本行。
 *
 * <p>
 * 与 {@code MapIntroduceScreen} 共用同一份 DTO 与同一套 {@code map_intro.*} 翻译键，
 * 属性判定全部走 DTO 上的 {@code displayXxx()} 辅助方法（以 {@code AreasSettings} 语义为准），
 * 因此两个界面不会再出现「同一张图显示不同属性」的情况；这里也不再解析任何 JSON。
 */
public final class MapIntroDetail {

    private MapIntroDetail() {
    }

    /**
     * 构建详情行。
     *
     * @param info 地图展示数据（null 时给一行提示）
     */
    public static List<FormattedCharSequence> build(Font font, int wrapW, MapDisplayInfo info) {
        return build(font, wrapW, info, true);
    }

    /**
     * @param includeHeader 是否输出顶部的「名称 + 地图 ID」两行。
     *                      投票界面自己已经画了大标题，传 {@code false} 避免重复、把空间留给属性。
     */
    public static List<FormattedCharSequence> build(Font font, int wrapW, MapDisplayInfo info,
            boolean includeHeader) {
        Sink sink = new Sink(font, Math.max(16, wrapW));
        if (info == null) {
            sink.wrapped(Component.translatable("map_intro.loading").withStyle(ChatFormatting.RED));
            return sink.lines;
        }
        Component displayName = mapDisplayName(info.id(), info);
        if (includeHeader) {
            sink.wrapped(displayName.copy().withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
            sink.wrapped(Component.translatable("map_intro.map.id", info.id()).withStyle(ChatFormatting.GRAY));
            sink.blank();
        }

        // ---- 投票配置 ----
        if (info.hasVoteConfig()) {
            sink.section("map_intro.section.vote_config");
            sink.line("map_intro.vote.display_name", displayName.getString());
            sink.line("map_intro.vote.min_count", Component.translatable(
                    info.minCount() == -1 ? "map_intro.vote.no_min_count" : "map_intro.vote.count_value",
                    info.minCount()));
            sink.line("map_intro.vote.max_count", Component.translatable(
                    info.maxCount() == -1 ? "map_intro.vote.no_max_count" : "map_intro.vote.count_value",
                    info.maxCount()));
            sink.key(info.canSelect() ? "map_intro.vote.can_select.true" : "map_intro.vote.can_select.false");
            sink.line("map_intro.vote.game_modes", gameModesText(info.gameModes()));
            sink.blank();
        }

        // ---- 特殊职业 ----
        sink.section("map_intro.section.special_roles");
        List<Component> specialLines = MapSpecialRoleLines.build(info);
        if (specialLines.isEmpty()) {
            sink.wrapped(Component.translatable("map_intro.special.none").withStyle(ChatFormatting.GRAY));
        } else {
            for (Component specialLine : specialLines) {
                sink.wrapped(specialLine);
            }
        }
        sink.blank();

        // ---- 属性 ----
        sink.section("map_intro.section.properties");
        sink.line("map_intro.property.room_count", info.roomCount());
        addNameSet(sink, info.disabledTasks(), "map_intro.property.disabled_tasks", value -> taskName(value, false));
        addNameSet(sink, info.disabledRoles(), "map_intro.property.disabled_roles", MapIntroDetail::roleName);
        addNameSet(sink, info.enableSceneTask(), "map_intro.property.scene_tasks", value -> taskName(value, true));
        if (info.minigameQuestEnabled()) {
            sink.key("map_intro.property.minigame_quest");
        }
        if (info.meetingEnabled()) {
            sink.key("map_intro.property.meeting_enabled");
        }
        if (info.meetingVoteEnabled()) {
            sink.key("map_intro.property.meeting_vote_enabled");
        }
        if (info.bellMeetingEnabled()) {
            sink.key("map_intro.property.bell_meeting_enabled");
        }
        if (info.displayHasStatusBar()) {
            sink.line("map_intro.property.status_bar", statusName(info.mapStatusBar()));
        }
        sink.key(info.displayCanSwim() ? "map_intro.property.can_swim.true" : "map_intro.property.can_swim.false");
        if (info.enableOxygenDrowning()) {
            sink.key("map_intro.property.oxygen_drowning");
        }
        sink.key(info.canJump() ? "map_intro.property.can_jump.true" : "map_intro.property.can_jump.false");
        if (info.snowEnabled()) {
            sink.key("map_intro.property.snow");
        }
        if (info.sandEnabled()) {
            sink.key("map_intro.property.sand");
        }
        if (info.planeCrashEventEnabled()) {
            sink.key("map_intro.property.plane_crash");
        }
        if (info.displayNoFog()) {
            sink.key("map_intro.property.no_fog");
        }
        sink.line("map_intro.property.fog_end", trimNumber(info.fogEnd()));
        if (info.displayHasWeather()) {
            sink.line("map_intro.property.weather", weatherText(info.weather()));
        }
        if (info.displayGravityChanged()) {
            sink.line("map_intro.property.gravity",
                    Component.translatable(info.displayGravityLow() ? "map_intro.gravity.low" : "map_intro.gravity.high"));
        }
        addEffects(sink, info.mobEffects());
        addInitialItems(sink, info.initialItems());
        if (info.displayCustomTime()) {
            sink.line("map_intro.property.time", Component.translatable(timeName(info.time())));
        }
        if (info.daylightCycle()) {
            sink.key("map_intro.property.daylight_cycle");
        }
        if (info.weatherCycle()) {
            sink.key("map_intro.property.weather_cycle");
        }
        return sink.lines;
    }

    /** 地图显示名：优先投票配置里的 displayName（可能是翻译键），否则退回 {@code map.<id>.name}。 */
    public static Component mapDisplayName(String id, MapDisplayInfo info) {
        if (info != null && info.displayName() != null && !info.displayName().isBlank()) {
            return translateConfiguredText(info.displayName());
        }
        return Component.translatableWithFallback("map." + id + ".name", id);
    }

    /** 配置里的文本既可能是翻译键也可能是字面量，且历史上有 tmm/sre 两套前缀。 */
    public static Component translateConfiguredText(String value) {
        String trimmed = value.trim();
        List<String> candidates = new ArrayList<>();
        candidates.add(trimmed);
        if (trimmed.startsWith("gui.tmm.map_selector.")) {
            candidates.add("gui.sre.map_selector." + trimmed.substring("gui.tmm.map_selector.".length()));
        } else if (trimmed.startsWith("gui.sre.map_selector.")) {
            candidates.add("gui.tmm.map_selector." + trimmed.substring("gui.sre.map_selector.".length()));
        }
        Language language = Language.getInstance();
        for (String key : candidates) {
            String translated = language.getOrDefault(key);
            if (!translated.equals(key)) {
                return Component.literal(translated);
            }
        }
        return Component.literal(trimmed);
    }

    public static Component gameModesText(List<String> values) {
        if (values == null || values.isEmpty()
                || values.stream().allMatch(value -> value == null || value.isBlank())) {
            return Component.translatable("map_intro.vote.all_game_modes");
        }
        List<String> names = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            names.add(gameModeName(value).getString());
        }
        return names.isEmpty()
                ? Component.translatable("map_intro.vote.all_game_modes")
                : Component.literal(String.join(", ", names));
    }

    public static Component gameModeName(String mode) {
        String path = mode.contains(":") ? mode.substring(mode.indexOf(':') + 1) : mode;
        return Component.translatableWithFallback("game_mode.noellesroles." + path,
                Component.translatableWithFallback("game_mode.starrailexpress." + path, mode).getString());
    }

    /** 天气名 → 翻译键（{@code map_intro.weather.<小写枚举名>}，缺失时回退枚举名）。 */
    public static Component weatherText(String weather) {
        if (weather == null || weather.isBlank()) {
            return Component.literal("");
        }
        return Component.translatableWithFallback("map_intro.weather." + weather.toLowerCase(Locale.ROOT), weather);
    }

    // ------------------------------------------------------------------
    // 列表型属性
    // ------------------------------------------------------------------

    private interface NameMapper {
        String apply(String rawId);
    }

    private static void addNameSet(Sink sink, List<String> ids, String labelKey, NameMapper mapper) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<String> names = new ArrayList<>();
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                names.add(mapper.apply(id));
            }
        }
        if (!names.isEmpty()) {
            sink.line(labelKey, String.join(", ", names));
        }
    }

    private static void addInitialItems(Sink sink, List<String> initialItems) {
        if (initialItems == null || initialItems.isEmpty()) {
            return;
        }
        List<String> parts = new ArrayList<>();
        for (String element : initialItems) {
            if (element == null || element.isBlank()) {
                continue;
            }
            String[] split = element.split("[;,]", 2);
            ResourceLocation id = ResourceLocation.tryParse(split[0]);
            if (id == null) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item == Items.AIR) {
                continue;
            }
            int count = split.length > 1 ? parseInt(split[1], 1) : 1;
            String name = item.getDescription().getString();
            parts.add(count > 1 ? Component.translatable("map_intro.item.entry", name, count).getString() : name);
        }
        if (!parts.isEmpty()) {
            sink.line("map_intro.property.initial_items", String.join(", ", parts));
        }
    }

    private static void addEffects(Sink sink, List<String> mobEffects) {
        if (mobEffects == null || mobEffects.isEmpty()) {
            return;
        }
        List<String> parts = new ArrayList<>();
        for (String element : mobEffects) {
            if (element == null || element.isBlank()) {
                continue;
            }
            String[] split = element.split(",", 2);
            int level = split.length > 1 ? parseInt(split[1], 1) : 1;
            String name = split[0];
            ResourceLocation id = ResourceLocation.tryParse(split[0]);
            if (id != null) {
                var effect = BuiltInRegistries.MOB_EFFECT.getHolder(id).orElse(null);
                if (effect != null) {
                    name = Component.translatable(effect.value().getDescriptionId()).getString();
                }
            }
            parts.add(Component.translatable("map_intro.effect.entry", name, level).getString());
        }
        if (!parts.isEmpty()) {
            sink.line("map_intro.property.effects", String.join(", ", parts));
        }
    }

    private static String taskName(String id, boolean scene) {
        String normalized = id.toLowerCase(Locale.ROOT);
        if (scene) {
            return Component.translatableWithFallback("scene_task.noellesroles." + normalized,
                    Component.translatableWithFallback("task." + normalized, id).getString()).getString();
        }
        if ("raed_book".equals(normalized)) {
            normalized = "read_book";
        }
        return Component.translatableWithFallback("task." + normalized, id).getString();
    }

    private static String roleName(String id) {
        SRERole role = null;
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location != null) {
            role = TMMRoles.getRole(location);
        }
        if (role == null) {
            String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
            for (SRERole candidate : TMMRoles.ROLES.values()) {
                if (candidate.identifier().getPath().equals(path)) {
                    role = candidate;
                    break;
                }
            }
        }
        return role == null ? id : role.getName().getString();
    }

    private static String statusName(String value) {
        if (value == null) {
            return "";
        }
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "COLD", "WARM", "WARMTH" -> Component.translatable("map_intro.status.warmth").getString();
            case "THIRST" -> Component.translatable("map_intro.status.thirst").getString();
            case "HUNGER" -> Component.translatable("map_intro.status.hunger").getString();
            case "POLLUTION" -> Component.translatable("map_intro.status.pollution").getString();
            default -> value;
        };
    }

    private static String timeName(long time) {
        long t = Math.floorMod(time, 24000L);
        long[] points = { 6000L, 12000L, 18000L, 23000L };
        String[] keys = { "map_intro.time.noon", "map_intro.time.dusk", "map_intro.time.midnight",
                "map_intro.time.dawn" };
        int best = 0;
        long bestDist = Long.MAX_VALUE;
        for (int i = 0; i < points.length; i++) {
            long dist = Math.min(Math.abs(t - points[i]), 24000L - Math.abs(t - points[i]));
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return keys[best];
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String trimNumber(double value) {
        return Math.abs(value - Math.rint(value)) < 0.0001D
                ? String.valueOf((int) Math.rint(value))
                : String.format(Locale.ROOT, "%.2f", value);
    }

    // ------------------------------------------------------------------

    private static final class Sink {
        private final Font font;
        private final int wrapW;
        private final List<FormattedCharSequence> lines = new ArrayList<>();

        private Sink(Font font, int wrapW) {
            this.font = font;
            this.wrapW = wrapW;
        }

        private void wrapped(Component text) {
            lines.addAll(font.split(text, wrapW));
        }

        private void section(String key) {
            wrapped(Component.translatable(key).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }

        private void key(String key) {
            wrapped(Component.translatable(key));
        }

        private void line(String key, Object value) {
            wrapped(Component.translatable(key, value));
        }

        private void blank() {
            lines.add(FormattedCharSequence.EMPTY);
        }
    }
}
