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

package io.wifi.starrailexpress.customrole;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.AreasSettingUtils.MapSpecialFeatures;
import io.wifi.starrailexpress.api.AreasSettings;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.util.TrueFalseResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * 自定义职业配置里的「地图生成限制」文本解析，补齐工具原本没接的两个 API：
 * {@link SRERole#setSpecialMapRolesCondition} 与 {@link SRERole#setCanSpawnInMap}。
 *
 * <p>
 * 提供两件事：
 * <ul>
 * <li>{@link #parseFeatures(List)}：把 {@code specialMapRoles} 里的特性名解析成
 * {@link MapSpecialFeatures} 集合，交给 {@code setSpecialMapRolesCondition} 做「全部 / 任一」判定；</li>
 * <li>{@link #parse(List, boolean)}：把 {@code canSpawnInMapConditions} 里的
 * {@code 字段 运算符 值} 文本编译成 {@code setCanSpawnInMap} 需要的谓词。</li>
 * </ul>
 *
 * <p>
 * 条件支持的字段（大小写不敏感，运算符与值之间用空格分隔）：
 * <ul>
 * <li>{@code mapId}：当前地图 id，支持 {@code =} {@code !=} {@code contains} {@code !contains}</li>
 * <li>{@code statusBar}：地图状态条枚举名（{@code NONE} / {@code WARMTH} / {@code THIRST} /
 * {@code HUNGER} / {@code POLLUTION}），支持 {@code =} {@code !=}</li>
 * <li>布尔项：{@code canJump} / {@code meeting} / {@code meetingVote} / {@code minigameQuest} /
 * {@code snow} / {@code noReset} / {@code mustCopy} / {@code bellMeeting}，
 * 值为 {@code true}/{@code false}（也接受 {@code yes}/{@code no}、{@code 1}/{@code 0}）</li>
 * <li>数值项：{@code fallToDeathHeight} / {@code gravity}，支持 {@code =} {@code !=} {@code >}
 * {@code >=} {@code <} {@code <=}</li>
 * <li>{@code features}：地图特性集合（即 {@code SRERole.getMapFeatures} 的结果），
 * 只能用 {@code contains} / {@code !contains}，值为特性枚举名</li>
 * </ul>
 *
 * <p>
 * 解析失败的条目会记录警告并<b>忽略</b>（与单个 {@code specialMapRole} 填错时的行为一致），
 * 所有条目都无效时视为「不限制」。
 */
public final class CustomRoleSpawnCondition {

    /** 文本字段可用的运算符。 */
    private static final String[] TEXT_OPERATORS = { "!contains", "contains", "!=", "=" };
    /** 布尔字段可用的运算符。 */
    private static final String[] FLAG_OPERATORS = { "!=", "=" };
    /** 数值字段可用的运算符。 */
    private static final String[] NUMBER_OPERATORS = { "!=", ">=", "<=", "=", ">", "<" };

    private CustomRoleSpawnCondition() {
    }

    // ==================== 特性名列表 ====================

    /**
     * 解析 {@code MapSpecialFeatures} 名称列表。
     *
     * @param names 配置里的特性名（如 {@code UNDERWATER}、{@code lab}）
     * @return 解析成功的特性集合；非法名会被忽略并记录警告
     */
    public static Set<MapSpecialFeatures> parseFeatures(List<String> names) {
        Set<MapSpecialFeatures> result = new HashSet<>();
        if (names == null) {
            return result;
        }
        for (String raw : names) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            MapSpecialFeatures feature = parseFeature(raw);
            if (feature == null) {
                SRE.LOGGER.warn("[CustomRole] 未知的地图特性：'{}'（已忽略）", raw);
                continue;
            }
            result.add(feature);
        }
        return result;
    }

    // ==================== 条件列表 ====================

    /**
     * 把条件文本编译成谓词。
     *
     * @param conditions 条件文本，每条形如 {@code 字段 运算符 值}
     * @param matchAll   {@code true} = 全部满足（AND），{@code false} = 任一满足（OR）
     * @return 编译后的谓词；没有任何有效条件时返回 {@code null}（表示不加限制）
     */
    public static BiPredicate<String, AreasSettings> parse(List<String> conditions, boolean matchAll) {
        List<BiPredicate<String, AreasSettings>> compiled = new ArrayList<>();
        if (conditions != null) {
            for (String raw : conditions) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                BiPredicate<String, AreasSettings> condition = compile(raw.trim());
                if (condition == null) {
                    SRE.LOGGER.warn("[CustomRole] 无法解析的生成条件：'{}'（已忽略）", raw);
                    continue;
                }
                compiled.add(condition);
            }
        }
        if (compiled.isEmpty()) {
            return null;
        }
        if (compiled.size() == 1) {
            return compiled.getFirst();
        }
        return matchAll
                ? (mapId, settings) -> compiled.stream().allMatch(condition -> condition.test(mapId, settings))
                : (mapId, settings) -> compiled.stream().anyMatch(condition -> condition.test(mapId, settings));
    }

    /** 编译单条条件；格式非法、字段未知或运算符不匹配时返回 {@code null}。 */
    private static BiPredicate<String, AreasSettings> compile(String raw) {
        String[] tokens = raw.split("\\s+");
        if (tokens.length < 3) {
            return null;
        }
        String field = tokens[0].toLowerCase(Locale.ROOT);
        String operator = tokens[1].toLowerCase(Locale.ROOT);
        // 值里允许带空格（地图 id 有可能带空格），后面的 token 全部拼回去
        String value = String.join(" ", Arrays.copyOfRange(tokens, 2, tokens.length)).trim();
        if (value.isEmpty()) {
            return null;
        }
        switch (field) {
            case "mapid":
            case "map":
            case "mapname":
                return isAny(operator, TEXT_OPERATORS)
                        ? (mapId, settings) -> compareText(mapId, value, operator)
                        : null;
            case "statusbar":
                return isAny(operator, TEXT_OPERATORS)
                        ? (mapId, settings) -> compareText(
                                settings == null || settings.mapStatusBar == null ? "NONE"
                                        : settings.mapStatusBar.name(),
                                value, operator)
                        : null;
            case "canjump":
                return flag(operator, value, settings -> settings != null && settings.canJump);
            case "meeting":
                return flag(operator, value, settings -> settings != null && settings.meetingEnabled);
            case "meetingvote":
                return flag(operator, value, settings -> settings != null && (settings.meetingVoteEnabled
                        || settings.emergencyMeetingVoteEnabled == TrueFalseResult.TRUE));
            case "minigamequest":
                return flag(operator, value, settings -> settings != null && settings.minigameQuestEnabled);
            case "snow":
                return flag(operator, value, settings -> settings != null && settings.snowEnabled);
            case "noreset":
                return flag(operator, value, settings -> settings != null && settings.noReset);
            case "mustcopy":
                return flag(operator, value, settings -> settings != null && settings.mustCopy);
            case "bellmeeting":
                return flag(operator, value, settings -> settings != null && settings.bellMeetingEnabled);
            case "falltodeathheight":
                return isAny(operator, NUMBER_OPERATORS)
                        ? (mapId, settings) -> compareNumber(settings == null ? 0 : settings.fallToDeathHeight,
                                value, operator)
                        : null;
            case "gravity":
                return isAny(operator, NUMBER_OPERATORS)
                        ? (mapId, settings) -> compareNumber(settings == null ? 0 : settings.gravityModifier,
                                value, operator)
                        : null;
            case "features":
            case "feature":
                MapSpecialFeatures feature = parseFeature(value);
                if (feature == null || !isAny(operator, new String[] { "contains", "!contains" })) {
                    return null;
                }
                return (mapId, settings) -> {
                    boolean contains = settings != null
                            && SRERole.getMapFeatures(mapId, settings).contains(feature);
                    return "contains".equals(operator) ? contains : !contains;
                };
            default:
                return null;
        }
    }

    // ==================== 工具 ====================

    /** 布尔字段：把取值器包装成谓词，运算符只接受 {@code =} / {@code !=}。 */
    private static BiPredicate<String, AreasSettings> flag(String operator, String value,
            java.util.function.Predicate<AreasSettings> getter) {
        if (!isAny(operator, FLAG_OPERATORS)) {
            return null;
        }
        Boolean wanted = parseBoolean(value);
        if (wanted == null) {
            return null;
        }
        return (mapId, settings) -> "!=".equals(operator) ? getter.test(settings) != wanted
                : getter.test(settings) == wanted;
    }

    private static boolean isAny(String operator, String[] allowed) {
        for (String candidate : allowed) {
            if (candidate.equals(operator)) {
                return true;
            }
        }
        return false;
    }

    private static MapSpecialFeatures parseFeature(String name) {
        try {
            return MapSpecialFeatures.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 文本比较：{@code =} / {@code !=} 忽略大小写，{@code contains} / {@code !contains} 为子串匹配。 */
    private static boolean compareText(String actual, String expected, String operator) {
        String left = actual == null ? "" : actual.toLowerCase(Locale.ROOT);
        String right = expected.toLowerCase(Locale.ROOT);
        return switch (operator) {
            case "=" -> left.equals(right);
            case "!=" -> !left.equals(right);
            case "contains" -> left.contains(right);
            case "!contains" -> !left.contains(right);
            default -> false;
        };
    }

    private static Boolean parseBoolean(String text) {
        return switch (text.trim().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "1", "on" -> Boolean.TRUE;
            case "false", "no", "0", "off" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static boolean compareNumber(double actual, String expected, String operator) {
        double wanted;
        try {
            wanted = Double.parseDouble(expected.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        return switch (operator) {
            case "=" -> actual == wanted;
            case "!=" -> actual != wanted;
            case ">" -> actual > wanted;
            case ">=" -> actual >= wanted;
            case "<" -> actual < wanted;
            case "<=" -> actual <= wanted;
            default -> false;
        };
    }
}
