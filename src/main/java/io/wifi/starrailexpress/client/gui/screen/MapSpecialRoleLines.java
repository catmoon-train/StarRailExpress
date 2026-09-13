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

package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.api.AreasSettingUtils.MapSpecialFeatures;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.network.MapDisplayInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 「该地图会出现哪些特殊职业」的展示行。
 *
 * <p>
 * 完全基于服务端解析好的 {@link MapDisplayInfo}：地图特性（自身 {@code customMapFeatures} ∪
 * 被兼容进来的旧 NoellesRolesConfig 列表）与由属性派生的特性（可跳跃 / 会议 / 会议投票 /
 * 小游戏 / 状态栏）在这里取并集，不再读取原始 JSON。
 */
public final class MapSpecialRoleLines {

    private MapSpecialRoleLines() {
    }

    /** 显示顺序：先配置列表类，后地图属性类。 */
    private static final MapSpecialFeatures[] DISPLAY_ORDER = {
            MapSpecialFeatures.QIYUCUN,
            MapSpecialFeatures.UNDERWATER,
            MapSpecialFeatures.BIGMAP,
            MapSpecialFeatures.FLY,
            MapSpecialFeatures.TRAP,
            MapSpecialFeatures.CAN_JUMP,
            MapSpecialFeatures.MEETING,
            MapSpecialFeatures.MEETING_VOTE,
            MapSpecialFeatures.MINIGAME_QUEST,
            MapSpecialFeatures.MAP_STATUS_BAR,
            MapSpecialFeatures.HORSE,
            MapSpecialFeatures.LAB
    };

    /**
     * 生成特殊地图职业条目。
     *
     * @param info 地图展示数据（null 时返回空列表）
     * @return 每行一条 {@link Component}，无匹配时返回空列表
     */
    public static List<Component> build(MapDisplayInfo info) {
        List<Component> lines = new ArrayList<>();
        if (info == null) {
            return lines;
        }
        for (MapSpecialFeatures category : DISPLAY_ORDER) {
            if (!isActive(category, info)) {
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

    /**
     * 特性是否生效：地图声明的特性优先，属性派生特性作为兜底（两者取并集，
     * 与运行时职业刷新条件保持一致）。
     */
    private static boolean isActive(MapSpecialFeatures category, MapDisplayInfo info) {
        if (info.hasFeature(category)) {
            return true;
        }
        return switch (category) {
            case CAN_JUMP -> info.canJump();
            case MEETING -> info.meetingEnabled();
            case MEETING_VOTE -> info.meetingEnabled() && info.meetingVoteEnabled();
            case MINIGAME_QUEST -> info.minigameQuestEnabled();
            case MAP_STATUS_BAR -> info.displayHasStatusBar();
            default -> false;
        };
    }

    /** 收集该类别下所有职业的翻译名，以“/”连接。 */
    private static String gatherRoleNames(MapSpecialFeatures category) {
        List<String> names = new ArrayList<>();
        for (SRERole role : TMMRoles.ROLES.values()) {
            if (role.getSpecialMapRole() == category) {
                names.add(role.getName().getString());
            }
        }
        return String.join("/", names);
    }
}
