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

package org.agmas.noellesroles.role;

import io.wifi.starrailexpress.api.InstinctType;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import net.minecraft.resources.ResourceLocation;

import java.awt.*;

/**
 * 仅在地图会议系统中刷新的职业（命名空间: meetingrole）。
 * - 加拿大鹅：meetingEnabled
 * - 呆呆鸟/政客/传教士：meetingEnabled && meetingVoteEnabled
 */
public class ModMeetingRoles {

    private static final String MOD_ID = "meetingrole";

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static final String CANADA_GOOSE_ID = "canada_goose";
    public static final String DUMMY_BIRD_ID = "dummy_bird";
    public static final String POLITICIAN_ID = "politician";
    public static final String MISSIONARY_ID = "missionary";

    /** 加拿大鹅 — 平民：被杀/被鹈鹕吞时自动发起会议 */
    public static SRERole CANADA_GOOSE = TMMRoles.registerRole(
            new NormalRole(id(CANADA_GOOSE_ID), new Color(200, 200, 200).getRGB(),
                    true, false, SRERole.MoodType.REAL,
                    TMMRoles.CIVILIAN.getMaxSprintTime(), false)
    ).setSpecialMapRole(SRERole.SpecialMapRoleMap.MEETING).setCanSeeCoin(true).setCanBeRandomedByOtherRoles(false).setDefaultMax(1);

    /**
     * 呆呆鸟 — 独立胜利中立：有杀手透视但看不到人，被投票出局即胜。
     * - 不会被杀手透视看到（与小透明 ghost 相同，见 setBeSeenInstinctType）。
     * - 初始物品：假刀 + 假左轮（见 RoleInitialItems）。
     * - 会议投票中投给呆呆鸟的每一票实际按 1.5 票计算（显示仍为 1 票，见 MeetingManager）。
     */
    public static SRERole DUMMY_BIRD = TMMRoles.registerRole(
            new NormalRole(id(DUMMY_BIRD_ID), new Color(255, 215, 0).getRGB(),
                    false, false, SRERole.MoodType.FAKE,
                    Integer.MAX_VALUE, true)
    ).setSpecialMapRole(SRERole.SpecialMapRoleMap.MEETING_VOTE)
            .setCanUseInstinctAndNightVision(true)
            .setBeSeenInstinctType(InstinctType.DEFAULT, InstinctType.NONE)
            .setNeutrals(true).setCanBeRandomedByOtherRoles(false).setDefaultMax(1);

    /** 政客 — 平民：不会因投票出局，投票权重2(>24人时3)，获得一定票数后得左轮 */
    public static SRERole POLITICIAN = TMMRoles.registerRole(
            new NormalRole(id(POLITICIAN_ID), new Color(0, 128, 0).getRGB(),
                    true, false, SRERole.MoodType.REAL,
                    TMMRoles.CIVILIAN.getMaxSprintTime(), false)
    ).setSpecialMapRole(SRERole.SpecialMapRoleMap.MEETING_VOTE).setCanSeeCoin(true).setCanBeRandomedByOtherRoles(false).setDefaultMax(1);

    /** 传教士 — 杀手：背包点击玩家头像增加其投票权重 */
    public static SRERole MISSIONARY = TMMRoles.registerRole(
            new NormalRole(id(MISSIONARY_ID), new Color(138, 43, 226).getRGB(),
                    false, true, SRERole.MoodType.FAKE,
                    Integer.MAX_VALUE, true)
    ).setSpecialMapRole(SRERole.SpecialMapRoleMap.MEETING_VOTE)
            .setCanBeRandomedByOtherRoles(false).setDefaultMax(1);

    public static void init() {
        // 静态初始化即触发注册
    }
}
