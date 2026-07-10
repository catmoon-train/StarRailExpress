package io.wifi.starrailexpress.client.gui.screen;

import com.google.gson.JsonObject;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 根据地图配置与各职业的 {@code setSpecialMapRole} 标记，动态生成“特定地图刷新的职业”条目。
 *
 * <p>每种匹配到的类别单独成行，行首统一为 {@code map_intro.special.prefix}（如“地图会刷新”），
 * 后面跟上该类别下所有职业的翻译名，以“/”分隔。地图类别是否激活的判断与
 * {@code InitModRolesMax.isSpecialMapRoleEnabled} 保持一致：
 * <ul>
 *     <li>配置列表类（QIYUCUN/BIGMAP/UNDERWATER/FLY/TRAP）：由服务端下发的地图集合决定；</li>
 *     <li>地图属性类（CAN_JUMP/MEETING/MEETING_VOTE/MINIGAME_QUEST/MAP_STATUS_BAR）：由地图自身的配置决定。</li>
 * </ul>
 */
public final class MapSpecialRoleLines {

    private MapSpecialRoleLines() {
    }

    /** 显示顺序：先配置列表类，后地图属性类。 */
    private static final SRERole.SpecialMapRoleMap[] DISPLAY_ORDER = {
            SRERole.SpecialMapRoleMap.QIYUCUN,
            SRERole.SpecialMapRoleMap.UNDERWATER,
            SRERole.SpecialMapRoleMap.BIGMAP,
            SRERole.SpecialMapRoleMap.FLY,
            SRERole.SpecialMapRoleMap.TRAP,
            SRERole.SpecialMapRoleMap.CAN_JUMP,
            SRERole.SpecialMapRoleMap.MEETING,
            SRERole.SpecialMapRoleMap.MEETING_VOTE,
            SRERole.SpecialMapRoleMap.MINIGAME_QUEST,
            SRERole.SpecialMapRoleMap.MAP_STATUS_BAR
    };

    /**
     * 生成特殊地图职业条目。
     *
     * @param mapId          当前地图 ID
     * @param bagMaps        布袋鬼（奇遇村）地图集合
     * @param policeMaps     大图（特警）地图集合
     * @param underwaterMaps 水下地图集合
     * @param airMaps        天空地图集合
     * @param trapMaps       机关地图集合
     * @param mapJson        当前地图的属性 JSON（用于判断地图属性类）
     * @return 每行一条 {@link Component}，无匹配时返回空列表
     */
    public static List<Component> build(String mapId,
            Set<String> bagMaps, Set<String> policeMaps, Set<String> underwaterMaps,
            Set<String> airMaps, Set<String> trapMaps, JsonObject mapJson) {
        List<Component> lines = new ArrayList<>();
        for (SRERole.SpecialMapRoleMap category : DISPLAY_ORDER) {
            if (!isActive(category, mapId, bagMaps, policeMaps, underwaterMaps, airMaps, trapMaps, mapJson)) {
                continue;
            }
            String names = gatherRoleNames(category);
            if (names.isEmpty()) {
                continue;
            }
            lines.add(Component.translatable("map_intro.special.prefix").append(Component.literal(names)));
        }
        return lines;
    }

    private static boolean isActive(SRERole.SpecialMapRoleMap category, String mapId,
            Set<String> bagMaps, Set<String> policeMaps, Set<String> underwaterMaps,
            Set<String> airMaps, Set<String> trapMaps, JsonObject json) {
        return switch (category) {
            case QIYUCUN -> contains(bagMaps, mapId);
            case UNDERWATER -> contains(underwaterMaps, mapId);
            case BIGMAP -> contains(policeMaps, mapId);
            case FLY -> contains(airMaps, mapId);
            case TRAP -> contains(trapMaps, mapId);
            case CAN_JUMP -> boolValue(json, "canJump", false);
            case MEETING -> meetingEnabled(json);
            case MEETING_VOTE -> meetingEnabled(json) && meetingVoteEnabled(json);
            case MINIGAME_QUEST -> boolValue(json, "minigameQuestEnabled", false);
            case MAP_STATUS_BAR -> {
                String status = stringValue(json, "mapStatusBar", "NONE");
                yield !status.equalsIgnoreCase("NONE") && !status.isBlank();
            }
            default -> false;
        };
    }

    private static boolean contains(Set<String> set, String mapId) {
        return set != null && mapId != null && set.contains(mapId);
    }

    /** 收集该类别下所有职业的翻译名，以“/”连接。 */
    private static String gatherRoleNames(SRERole.SpecialMapRoleMap category) {
        List<String> names = new ArrayList<>();
        for (SRERole role : TMMRoles.ROLES.values()) {
            if (role.getSpecialMapRole() == category) {
                names.add(role.getName().getString());
            }
        }
        return String.join("/", names);
    }

    // ---- 类型安全的 JSON 取值（字段类型不符时退回默认值） ----

    private static boolean meetingEnabled(JsonObject json) {
        return meetingBoolValue(json, "meetingEnabled", false);
    }

    private static boolean meetingVoteEnabled(JsonObject json) {
        return meetingBoolValue(json, "meetingVoteEnabled", false);
    }

    private static boolean boolValue(JsonObject json, String key, boolean fallback) {
        if (json == null || !json.has(key)) {
            return fallback;
        }
        var el = json.get(key);
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isBoolean()) {
            return fallback;
        }
        return el.getAsBoolean();
    }

    private static String stringValue(JsonObject json, String key, String fallback) {
        if (json == null || !json.has(key)) {
            return fallback;
        }
        var el = json.get(key);
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
            return fallback;
        }
        return el.getAsString();
    }

    /** 会议相关字段可能被嵌在 settings 子对象里。 */
    private static boolean meetingBoolValue(JsonObject json, String key, boolean fallback) {
        if (boolValue(json, key, fallback)) {
            return true;
        }
        if (json != null && json.has("settings") && json.get("settings").isJsonObject()) {
            return boolValue(json.getAsJsonObject("settings"), key, fallback);
        }
        return fallback;
    }
}
