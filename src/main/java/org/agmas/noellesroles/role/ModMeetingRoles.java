package org.agmas.noellesroles.role;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import org.agmas.noellesroles.Noellesroles;

import java.awt.*;

/**
 * 仅在地图会议系统中刷新的职业。
 * - 加拿大鹅：meetingEnabled
 * - 呆呆鸟/政客/传教士：meetingEnabled && meetingVoteEnabled
 */
public class ModMeetingRoles {

    public static final String CANADA_GOOSE_ID = "canada_goose";
    public static final String DUMMY_BIRD_ID = "dummy_bird";
    public static final String POLITICIAN_ID = "politician";
    public static final String MISSIONARY_ID = "missionary";

    /** 加拿大鹅 — 平民：被杀/被鹈鹕吞时自动发起会议 */
    public static SRERole CANADA_GOOSE = TMMRoles.registerRole(
            new SRERole(Noellesroles.id(CANADA_GOOSE_ID), new Color(200, 200, 200).getRGB(),
                    true, false, SRERole.MoodType.REAL,
                    TMMRoles.CIVILIAN.getMaxSprintTime(), false)
    ).setSpecialMapRole(SRERole.SpecialMapRoleMap.MEETING).setCanSeeCoin(true);

    /** 呆呆鸟 — 独立胜利中立：有杀手透视但看不到人，被投票出局即胜 */
    public static SRERole DUMMY_BIRD = TMMRoles.registerRole(
            new SRERole(Noellesroles.id(DUMMY_BIRD_ID), new Color(255, 215, 0).getRGB(),
                    false, false, SRERole.MoodType.REAL,
                    TMMRoles.CIVILIAN.getMaxSprintTime(), false)
    ).setSpecialMapRole(SRERole.SpecialMapRoleMap.MEETING_VOTE)
            .setCanUseInstinctAndNightVision(true)
            .setNeutrals(true).setNeutralForKiller(true);

    /** 政客 — 平民：不会因投票出局，投票权重2(>24人时3)，获得一定票数后得左轮 */
    public static SRERole POLITICIAN = TMMRoles.registerRole(
            new SRERole(Noellesroles.id(POLITICIAN_ID), new Color(0, 128, 0).getRGB(),
                    true, false, SRERole.MoodType.REAL,
                    TMMRoles.CIVILIAN.getMaxSprintTime(), false)
    ).setSpecialMapRole(SRERole.SpecialMapRoleMap.MEETING_VOTE).setCanSeeCoin(true);

    /** 传教士 — 杀手：背包点击玩家头像增加其投票权重 */
    public static SRERole MISSIONARY = TMMRoles.registerRole(
            new SRERole(Noellesroles.id(MISSIONARY_ID), new Color(138, 43, 226).getRGB(),
                    false, true, SRERole.MoodType.FAKE,
                    TMMRoles.CIVILIAN.getMaxSprintTime(), false)
    ).setSpecialMapRole(SRERole.SpecialMapRoleMap.MEETING_VOTE);

    public static void init() {
        // 静态初始化即触发注册
    }
}
