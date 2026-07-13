package io.wifi.starrailexpress.client.gui.screen.maprotation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.client.gui.screen.MapSpecialRoleLines;
import io.wifi.starrailexpress.network.MapIntroSyncPayload;
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
import java.util.Set;

/**
 * 把一张地图的属性 JSON + 投票配置渲染成可换行的详情文本行。
 *
 * <p>
 * 内容与 {@code MapIntroduceScreen.buildMapDetail} 对齐，复用同一套 {@code map_intro.*} 翻译键。
 * 之所以独立成类而不是直接调用那个屏幕：它的构建方法与静态工具全部是 {@code private}，
 * 且绑定在实例状态（{@code detailLines}、{@code font}）上，按 {@code ai_doc.md}
 * 不应改动既有代码。这里的 JSON 取值做了类型校验，字段类型不符时退回默认值而不是抛异常。
 */
public final class MapIntroDetail {

    private MapIntroDetail() {
    }

    /** 各类特殊职业可用的地图集合。 */
    public record SpecialSets(Set<String> bag, Set<String> police, Set<String> underwater,
            Set<String> air, Set<String> trap, Set<String> horse) {
    }

    public static List<FormattedCharSequence> build(Font font, int wrapW, String id, Component displayName,
            JsonObject json, MapIntroSyncPayload.VoteMap voteMap, SpecialSets special) {
        Sink sink = new Sink(font, Math.max(16, wrapW));

        sink.wrapped(displayName.copy().withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        sink.wrapped(Component.translatable("map_intro.map.id", id).withStyle(ChatFormatting.GRAY));
        sink.blank();

        if (voteMap != null) {
            sink.section("map_intro.section.vote_config");
            sink.line("map_intro.vote.display_name", displayName.getString());
            sink.line("map_intro.vote.min_count", Component.translatable(
                    voteMap.minCount() == -1 ? "map_intro.vote.no_min_count" : "map_intro.vote.count_value",
                    voteMap.minCount()));
            sink.line("map_intro.vote.max_count", Component.translatable(
                    voteMap.maxCount() == -1 ? "map_intro.vote.no_max_count" : "map_intro.vote.count_value",
                    voteMap.maxCount()));
            sink.key(voteMap.canSelect() ? "map_intro.vote.can_select.true" : "map_intro.vote.can_select.false");
            sink.line("map_intro.vote.game_modes", gameModesText(voteMap.gameModes()));
            sink.blank();
        }

        if (special != null) {
            sink.section("map_intro.section.special_roles");
            List<Component> specialLines = MapSpecialRoleLines.build(id, special.bag(), special.police(),
                    special.underwater(), special.air(), special.trap(), special.horse(), json);
            if (specialLines.isEmpty()) {
                sink.wrapped(Component.translatable("map_intro.special.none").withStyle(ChatFormatting.GRAY));
            } else {
                for (Component specialLine : specialLines) {
                    sink.wrapped(specialLine);
                }
            }
            sink.blank();
        }

        if (json == null) {
            return sink.lines;
        }

        sink.section("map_intro.section.properties");
        sink.line("map_intro.property.room_count", intValue(json, "roomCount", 1));
        addNameSet(sink, json, "disabledTasks", "map_intro.property.disabled_tasks",
                value -> taskName(value, false));
        addNameSet(sink, json, "disabledRoles", "map_intro.property.disabled_roles", MapIntroDetail::roleName);
        addNameSet(sink, json, "enableSceneTask", "map_intro.property.scene_tasks", value -> taskName(value, true));
        if (boolValue(json, "minigameQuestEnabled", false)) {
            sink.key("map_intro.property.minigame_quest");
        }
        if (meetingBoolValue(json, "meetingEnabled", false)) {
            sink.key("map_intro.property.meeting_enabled");
        }
        if (meetingBoolValue(json, "meetingVoteEnabled", false)) {
            sink.key("map_intro.property.meeting_vote_enabled");
        }
        if (meetingBoolValue(json, "bellMeetingEnabled", false)) {
            sink.key("map_intro.property.bell_meeting_enabled");
        }
        String status = stringValue(json, "mapStatusBar", "NONE");
        if (!status.equalsIgnoreCase("NONE") && !status.isBlank()) {
            sink.line("map_intro.property.status_bar", statusName(status));
        }
        sink.key(boolValue(json, "canSwim", false)
                ? "map_intro.property.can_swim.true"
                : "map_intro.property.can_swim.false");
        if (boolValue(json, "enableOxygenDrowning", false)) {
            sink.key("map_intro.property.oxygen_drowning");
        }
        sink.key(boolValue(json, "canJump", false)
                ? "map_intro.property.can_jump.true"
                : "map_intro.property.can_jump.false");
        if (boolValue(json, "snowEnabled", false)) {
            sink.key("map_intro.property.snow");
        }
        if (boolValue(json, "sandEnabled", false)) {
            sink.key("map_intro.property.sand");
        }
        if (!boolValue(json, "fogEnabled", true)) {
            sink.key("map_intro.property.no_fog");
        }
        sink.line("map_intro.property.fog_end", trimNumber(doubleValue(json, "fogEnd", 200.0D)));
        String weather = stringValue(json, "weather", "clear");
        if (!weather.equalsIgnoreCase("clear")) {
            sink.line("map_intro.property.weather",
                    Component.translatableWithFallback("map_intro.weather." + weather.toLowerCase(Locale.ROOT),
                            weather));
        }
        double gravity = doubleValue(json, "gravity", 0.08D);
        if (Math.abs(gravity - 0.08D) > 0.0001D) {
            sink.line("map_intro.property.gravity",
                    Component.translatable(gravity < 0.08D ? "map_intro.gravity.low" : "map_intro.gravity.high"));
        }
        addEffects(sink, json);
        addInitialItems(sink, json);
        long time = longValue(json, "time", 18000L);
        if (time != 18000L) {
            sink.line("map_intro.property.time", Component.translatable(timeName(time)));
        }
        if (boolValue(json, "daylightCycle", false)) {
            sink.key("map_intro.property.daylight_cycle");
        }
        if (boolValue(json, "weatherCycle", false)) {
            sink.key("map_intro.property.weather_cycle");
        }
        return sink.lines;
    }

    /** 地图显示名：优先投票配置里的 displayName（可能是翻译键），否则退回 {@code map.<id>.name}。 */
    public static Component mapDisplayName(String id, MapIntroSyncPayload.VoteMap voteMap) {
        if (voteMap != null && voteMap.displayName() != null && !voteMap.displayName().isBlank()) {
            return translateConfiguredText(voteMap.displayName());
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

    // ------------------------------------------------------------------
    // 列表型属性
    // ------------------------------------------------------------------

    private interface NameMapper {
        String apply(String rawId);
    }

    private static void addNameSet(Sink sink, JsonObject json, String key, String labelKey, NameMapper mapper) {
        List<String> names = new ArrayList<>();
        for (JsonElement element : arrayOf(json, key)) {
            if (element.isJsonPrimitive()) {
                names.add(mapper.apply(element.getAsString()));
            }
        }
        if (!names.isEmpty()) {
            sink.line(labelKey, String.join(", ", names));
        }
    }

    private static void addEffects(Sink sink, JsonObject json) {
        List<String> parts = new ArrayList<>();
        for (JsonElement element : arrayOf(json, "effect")) {
            if (!element.isJsonPrimitive()) {
                continue;
            }
            String[] split = element.getAsString().split(",", 2);
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

    private static void addInitialItems(Sink sink, JsonObject json) {
        List<String> parts = new ArrayList<>();
        for (JsonElement element : arrayOf(json, "initialItems")) {
            if (!element.isJsonPrimitive()) {
                continue;
            }
            String[] split = element.getAsString().split("[;,]", 2);
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
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "COLD", "WARM", "WARMTH" -> Component.translatable("map_intro.status.warmth").getString();
            case "THIRST" -> Component.translatable("map_intro.status.thirst").getString();
            case "HUNGER" -> Component.translatable("map_intro.status.hunger").getString();
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

    // ------------------------------------------------------------------
    // 类型安全的 JSON 取值：字段类型不符时退回默认值
    // ------------------------------------------------------------------

    private static List<JsonElement> arrayOf(JsonObject json, String key) {
        if (json == null || !json.has(key) || !json.get(key).isJsonArray()) {
            return List.of();
        }
        List<JsonElement> result = new ArrayList<>();
        json.getAsJsonArray(key).forEach(result::add);
        return result;
    }

    private static boolean isNumber(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonPrimitive() && json.getAsJsonPrimitive(key).isNumber();
    }

    private static int intValue(JsonObject json, String key, int fallback) {
        return isNumber(json, key) ? json.get(key).getAsInt() : fallback;
    }

    private static long longValue(JsonObject json, String key, long fallback) {
        return isNumber(json, key) ? json.get(key).getAsLong() : fallback;
    }

    private static double doubleValue(JsonObject json, String key, double fallback) {
        return isNumber(json, key) ? json.get(key).getAsDouble() : fallback;
    }

    private static boolean boolValue(JsonObject json, String key, boolean fallback) {
        if (json.has(key) && json.get(key).isJsonPrimitive() && json.getAsJsonPrimitive(key).isBoolean()) {
            return json.get(key).getAsBoolean();
        }
        return fallback;
    }

    private static String stringValue(JsonObject json, String key, String fallback) {
        if (json.has(key) && json.get(key).isJsonPrimitive() && json.getAsJsonPrimitive(key).isString()) {
            return json.get(key).getAsString();
        }
        return fallback;
    }

    /** 会议相关字段可能被嵌在 settings 子对象里。 */
    private static boolean meetingBoolValue(JsonObject json, String key, boolean fallback) {
        if (json.has(key) && json.get(key).isJsonPrimitive() && json.getAsJsonPrimitive(key).isBoolean()) {
            return json.get(key).getAsBoolean();
        }
        if (json.has("settings") && json.get("settings").isJsonObject()) {
            return boolValue(json.getAsJsonObject("settings"), key, fallback);
        }
        return fallback;
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

        private boolean ifContains(Set<String> set, String mapId, String key) {
            if (set == null || !set.contains(mapId)) {
                return false;
            }
            wrapped(Component.translatable(key));
            return true;
        }
    }
}
