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

package io.wifi.starrailexpress.progression;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 通行证可用操作（任务目标类型）。网站管理员只能从这份列表里编写任务。
 */
public final class ProgressionObjectives {
    private ProgressionObjectives() {
    }

    public static final String PLAY_MATCH = "PLAY_MATCH";
    public static final String WIN_MATCH = "WIN_MATCH";
    public static final String KILL_PLAYER = "KILL_PLAYER";
    public static final String KILL_PLAYER_DIFFERENT_TEAM = "KILL_PLAYER_DIFFERENT_TEAM";
    public static final String COMPLETE_ROUND_QUEST = "COMPLETE_ROUND_QUEST";
    public static final String COMPLETE_SPECIFIC_QUEST = "COMPLETE_SPECIFIC_QUEST";
    public static final String PLAY_AS_FACTION = "PLAY_AS_FACTION";
    public static final String BECOME_FACTION = "BECOME_FACTION";
    public static final String USE_ITEM = "USE_ITEM";
    public static final String WIN_AS_FACTION = "WIN_AS_FACTION";
    public static final String SURVIVE_MATCH = "SURVIVE_MATCH";
    public static final String PICKUP_ITEM = "PICKUP_ITEM";
    public static final String PLAY_AS_ROLE = "PLAY_AS_ROLE";
    public static final String WIN_AS_ROLE = "WIN_AS_ROLE";
    public static final String PLAY_GAME_MODE = "PLAY_GAME_MODE";
    public static final String WIN_GAME_MODE = "WIN_GAME_MODE";
    public static final String SURVIVE_AS_FACTION = "SURVIVE_AS_FACTION";
    public static final String KILL_AS_FACTION = "KILL_AS_FACTION";
    public static final String REPORT_BODY = "REPORT_BODY";
    public static final String CALL_MEETING = "CALL_MEETING";
    public static final String CAST_VOTE = "CAST_VOTE";
    public static final String BUY_SHOP_ITEM = "BUY_SHOP_ITEM";
    public static final String USE_SKILL = "USE_SKILL";

    public record Definition(
            String id,
            String label,
            String description,
            boolean usesKey,
            String keyHint) {
    }

    public static final List<Definition> ALL = List.of(
            def(PLAY_MATCH, "完成对局", "任意模式完成一局", false, ""),
            def(WIN_MATCH, "赢得对局", "任意模式获胜一局", false, ""),
            def(KILL_PLAYER, "击杀玩家", "击杀任意玩家", false, ""),
            def(KILL_PLAYER_DIFFERENT_TEAM, "击杀敌对阵营", "击杀不同阵营的玩家", false, ""),
            def(COMPLETE_ROUND_QUEST, "完成局内任务", "完成列车小游戏/校准等局内任务", false, ""),
            def(COMPLETE_SPECIFIC_QUEST, "完成指定局内任务", "objectiveKey 为局内任务名", true, "signal_calibration"),
            def(PLAY_AS_FACTION, "以指定阵营对局", "objectiveKey=killer/civilian/neutral", true, "killer"),
            def(BECOME_FACTION, "成为指定阵营", "开局被分配到该阵营", true, "civilian,neutral"),
            def(USE_ITEM, "使用物品", "objectiveKey 为物品 ID，逗号分隔；空=任意物品", true, "minecraft:golden_apple"),
            def(WIN_AS_FACTION, "以指定阵营获胜", "objectiveKey=killer/civilian/neutral", true, "civilian"),
            def(SURVIVE_MATCH, "存活至结束", "对局结束时仍存活", false, ""),
            def(PICKUP_ITEM, "拾取物品", "objectiveKey 为物品 ID；空=任意物品", true, "starrailexpress:gun"),
            def(PLAY_AS_ROLE, "以指定职业对局", "objectiveKey 为职业 ID", true, "starrailexpress:civilian"),
            def(WIN_AS_ROLE, "以指定职业获胜", "objectiveKey 为职业 ID", true, "noellesroles:sheriff"),
            def(PLAY_GAME_MODE, "游玩指定模式", "objectiveKey 为模式 ID", true, "starrailexpress:murder"),
            def(WIN_GAME_MODE, "赢得指定模式", "objectiveKey 为模式 ID", true, "starrailexpress:murder"),
            def(SURVIVE_AS_FACTION, "以指定阵营存活", "objectiveKey=killer/civilian/neutral", true, "civilian"),
            def(KILL_AS_FACTION, "以指定阵营击杀", "objectiveKey=killer/civilian/neutral", true, "killer"),
            def(REPORT_BODY, "报告尸体", "召开尸体会议", false, ""),
            def(CALL_MEETING, "召开紧急会议", "摇铃/紧急按钮开会", false, ""),
            def(CAST_VOTE, "参与投票", "地图/模式等投票", false, ""),
            def(BUY_SHOP_ITEM, "购买商店物品", "objectiveKey 为物品 ID；空=任意购买", true, "starrailexpress:gun"),
            def(USE_SKILL, "使用技能", "objectiveKey 为技能 ID；空=任意技能", true, ""));

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return PLAY_MATCH;
        }
        String id = raw.trim().toUpperCase(Locale.ROOT);
        for (Definition definition : ALL) {
            if (definition.id.equals(id)) {
                return definition.id;
            }
        }
        return PLAY_MATCH;
    }

    public static boolean isKnown(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String id = raw.trim().toUpperCase(Locale.ROOT);
        return ALL.stream().anyMatch(definition -> definition.id.equals(id));
    }

    public static List<String> ids() {
        return ALL.stream().map(Definition::id).toList();
    }

    public static boolean keyMatches(String questKey, String triggerKey) {
        if (questKey == null || questKey.isBlank()) {
            return true;
        }
        if (triggerKey == null || triggerKey.isBlank()) {
            return false;
        }
        if (questKey.equalsIgnoreCase(triggerKey)) {
            return true;
        }
        return Arrays.stream(questKey.split("[,|;]"))
                .map(String::trim)
                .anyMatch(candidate -> triggerKey.equalsIgnoreCase(candidate));
    }

    private static Definition def(String id, String label, String description, boolean usesKey, String keyHint) {
        return new Definition(id, label, description, usesKey, keyHint);
    }
}
