package io.wifi.starrailexpress.network;

import java.util.ArrayList;
import java.util.List;

import io.wifi.starrailexpress.api.AreasSettingUtils.MapSpecialFeatures;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * 地图展示信息（DTO）：地图介绍 / 轮抽 / 投票界面需要展示的**具体字段**，
 * 由服务端把地图文件解析成 {@link io.wifi.starrailexpress.api.AreasSettings} 后组装，逐字段下发。
 *
 * <p>
 * 取代了以前「读取原始 JSON → 瘦身成字符串 → 客户端再 Gson 解析」的做法：
 * 包体只有这些字段、没有 JSON 文本，客户端也不再需要解析。
 *
 * <p>
 * 属性语义统一以 {@link io.wifi.starrailexpress.api.AreasSettings} 为准（见下面的 {@code displayXxx()} 辅助方法），
 * 以前「地图介绍界面读原始 JSON、轮抽界面解析 AreasSettings」两套判定不一致的问题在这里被消除。
 */
public record MapDisplayInfo(
        // ==================== 标识与投票配置 ====================
        String id,
        /** 投票配置里的显示名（可能是翻译键）；没有投票配置时为空串 */
        String displayName,
        /** 投票配置里的描述（可能是翻译键）；没有投票配置时为空串 */
        String description,
        /** 投票配置里的颜色 ARGB；没有投票配置时为 0 */
        int color,
        /**
         * 地图特性列表（{@link MapSpecialFeatures} 的枚举名，如 {@code "UNDERWATER"}）。
         * 来源 = 地图自身的 {@code AreasSettings.customMapFeatures} ∪ 旧的 NoellesRolesConfig 地图列表
         * （旧配置在地图侧被兼容成特性，因此不再单独同步 config）。
         * 用枚举名而不是 ordinal，避免枚举增删导致的错位；客户端不认识的名字直接忽略。
         */
        List<String> features,
        /** 是否在 train_vote_maps.json 里登记过 */
        boolean hasVoteConfig,
        int minCount,
        int maxCount,
        boolean canSelect,
        List<String> gameModes,

        // ==================== 根级地图数据 ====================
        int roomCount,
        List<String> disabledTasks,
        List<String> disabledRoles,
        List<String> enableSceneTask,

        // ==================== AreasSettings 属性 ====================
        boolean minigameQuestEnabled,
        boolean meetingEnabled,
        boolean meetingVoteEnabled,
        boolean bellMeetingEnabled,
        /** {@code MapStatusBarType} 的名字 */
        String mapStatusBar,
        boolean canJump,
        boolean canSimpleSwim,
        boolean canUnderWater,
        boolean allowInDeepWater,
        boolean canSwim,
        boolean enableOxygenDrowning,
        boolean snowEnabled,
        boolean sandEnabled,
        boolean planeCrashEventEnabled,
        boolean fogEnabled,
        float fogEnd,
        /** {@code MinecraftWeather} 的名字 */
        String weather,
        double gravityModifier,
        long time,
        boolean daylightCycle,
        boolean weatherCycle,
        List<String> mobEffects,
        List<String> initialItems) {

    public static final StreamCodec<FriendlyByteBuf, MapDisplayInfo> CODEC = StreamCodec.ofMember(
            MapDisplayInfo::write, MapDisplayInfo::read);

    /** 列表/字符串上限：畸形包只会解出「少一点的数据」，不会因为超大长度分配而 OOM。 */
    private static final int MAX_LIST = 4_096;
    private static final int MAX_STRING_CHARS = 1_024;

    // ==================== UI 辅助（两个界面共用，保证判定一致） ====================

    /** 地图是否声明了某个特性（含被兼容进来的旧配置列表） */
    public boolean hasFeature(MapSpecialFeatures feature) {
        return feature != null && features != null && features.contains(feature.name());
    }

    /**
     * 展示用「是否可以游泳」：沿用 AreasSettings 的组合判定
     * （{@code canSimpleSwim && canUnderWater && allowInDeepWater && (canJump || canSwim)}）。
     */
    public boolean displayCanSwim() {
        return canSimpleSwim && canUnderWater && allowInDeepWater && (canJump || canSwim);
    }

    /** 展示用「无雾」：只有在关闭了雾气时才提示 */
    public boolean displayNoFog() {
        return !fogEnabled;
    }

    /** 展示用：重力被改过（gravityModifier 非 0，基准重力是 0.08 + modifier） */
    public boolean displayGravityChanged() {
        return Math.abs(gravityModifier) > 0.0001D;
    }

    /** 展示用：重力偏低 */
    public boolean displayGravityLow() {
        return gravityModifier < 0.0D;
    }

    /** 展示用：状态栏是否非 NONE */
    public boolean displayHasStatusBar() {
        return mapStatusBar != null && !mapStatusBar.isBlank() && !"NONE".equalsIgnoreCase(mapStatusBar);
    }

    /** 展示用：天气是否非晴天 */
    public boolean displayHasWeather() {
        return weather != null && !weather.isBlank() && !"clear".equalsIgnoreCase(weather);
    }

    /** 展示用：时间是否非默认（18000 = 午夜） */
    public boolean displayCustomTime() {
        return time != 18000L;
    }

    // ==================== 编解码 ====================

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(clampUtf(id, MAX_STRING_CHARS), MAX_STRING_CHARS);
        buf.writeUtf(clampUtf(displayName, MAX_STRING_CHARS), MAX_STRING_CHARS);
        buf.writeUtf(clampUtf(description, MAX_STRING_CHARS), MAX_STRING_CHARS);
        buf.writeInt(color);
        writeStrings(buf, features);
        buf.writeBoolean(hasVoteConfig);
        buf.writeInt(minCount);
        buf.writeInt(maxCount);
        buf.writeBoolean(canSelect);
        writeStrings(buf, gameModes);
        buf.writeInt(roomCount);
        writeStrings(buf, disabledTasks);
        writeStrings(buf, disabledRoles);
        writeStrings(buf, enableSceneTask);
        buf.writeBoolean(minigameQuestEnabled);
        buf.writeBoolean(meetingEnabled);
        buf.writeBoolean(meetingVoteEnabled);
        buf.writeBoolean(bellMeetingEnabled);
        buf.writeUtf(clampUtf(orNone(mapStatusBar), MAX_STRING_CHARS), MAX_STRING_CHARS);
        buf.writeBoolean(canJump);
        buf.writeBoolean(canSimpleSwim);
        buf.writeBoolean(canUnderWater);
        buf.writeBoolean(allowInDeepWater);
        buf.writeBoolean(canSwim);
        buf.writeBoolean(enableOxygenDrowning);
        buf.writeBoolean(snowEnabled);
        buf.writeBoolean(sandEnabled);
        buf.writeBoolean(planeCrashEventEnabled);
        buf.writeBoolean(fogEnabled);
        buf.writeFloat(fogEnd);
        buf.writeUtf(clampUtf(orClear(weather), MAX_STRING_CHARS), MAX_STRING_CHARS);
        buf.writeDouble(gravityModifier);
        buf.writeLong(time);
        buf.writeBoolean(daylightCycle);
        buf.writeBoolean(weatherCycle);
        writeStrings(buf, mobEffects);
        writeStrings(buf, initialItems);
    }

    public static MapDisplayInfo read(FriendlyByteBuf buf) {
        return new MapDisplayInfo(
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readInt(),
                readStrings(buf),
                buf.readBoolean(),
                buf.readInt(),
                buf.readInt(),
                buf.readBoolean(),
                readStrings(buf),
                buf.readInt(),
                readStrings(buf),
                readStrings(buf),
                readStrings(buf),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readUtf(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readFloat(),
                buf.readUtf(),
                buf.readDouble(),
                buf.readLong(),
                buf.readBoolean(),
                buf.readBoolean(),
                readStrings(buf),
                readStrings(buf));
    }

    private static void writeStrings(FriendlyByteBuf buf, List<String> list) {
        if (list == null || list.isEmpty()) {
            buf.writeVarInt(0);
            return;
        }
        buf.writeVarInt(list.size());
        for (String s : list) {
            buf.writeUtf(clampUtf(s, MAX_STRING_CHARS), MAX_STRING_CHARS);
        }
    }

    private static List<String> readStrings(FriendlyByteBuf buf) {
        int size = Math.min(MAX_LIST, Math.max(0, buf.readVarInt()));
        if (size == 0) {
            return List.of();
        }
        List<String> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            if (buf.readableBytes() <= 0) {
                break;
            }
            list.add(buf.readUtf(MAX_STRING_CHARS));
        }
        return List.copyOf(list);
    }

    /** 写入前截断：writeUtf 超长会抛异常，服务端不该因为一条超长文案发不出包。 */
    private static String clampUtf(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String orNone(String value) {
        return value == null || value.isBlank() ? "NONE" : value;
    }

    private static String orClear(String value) {
        return value == null || value.isBlank() ? "clear" : value;
    }

    /** 便于 {@code MapIntroData} 更新单个字段（轮抽开关就地更新） */
    public MapDisplayInfo withCanSelect(boolean newCanSelect) {
        return new MapDisplayInfo(id, displayName, description, color, features, hasVoteConfig, minCount,
                maxCount, newCanSelect, gameModes, roomCount, disabledTasks, disabledRoles, enableSceneTask,
                minigameQuestEnabled, meetingEnabled, meetingVoteEnabled, bellMeetingEnabled, mapStatusBar, canJump,
                canSimpleSwim, canUnderWater, allowInDeepWater, canSwim, enableOxygenDrowning, snowEnabled,
                sandEnabled, planeCrashEventEnabled, fogEnabled, fogEnd, weather, gravityModifier, time,
                daylightCycle, weatherCycle, mobEffects, initialItems);
    }
}
