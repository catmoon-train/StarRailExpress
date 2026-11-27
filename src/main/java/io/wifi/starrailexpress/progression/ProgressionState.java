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

import io.wifi.starrailexpress.api.SRERole;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ProgressionState {
    public int level = 1;
    public int experience;
    public int totalExperience;
    public int claimedCoinRewards;
    public int claimedLootRewards;
    public long lastQuestRefreshTime;
    public long lastWeeklyRefreshTime;
    public Map<FactionCardType, Integer> factionCards = new EnumMap<>(FactionCardType.class);
    public List<PassQuest> activeQuests = new ArrayList<>();
    /** 当前周期内各地图游玩局数（key=地图 ID），随每日任务刷新重置，供网站读取。 */
    public Map<String, Integer> mapPlayCounts = new HashMap<>();
    public long version;

    public static ProgressionState createDefault() {
        ProgressionState state = new ProgressionState();
        for (FactionCardType type : FactionCardType.values()) {
            if (type != FactionCardType.NONE) {
                state.factionCards.put(type, 0);
            }
        }
        return state;
    }

    public ProgressionState normalized() {
        if (level <= 0) {
            level = 1;
        }
        if (factionCards == null) {
            factionCards = new EnumMap<>(FactionCardType.class);
        }
        for (FactionCardType type : FactionCardType.values()) {
            if (type != FactionCardType.NONE) {
                factionCards.putIfAbsent(type, 0);
            }
        }
        if (activeQuests == null) {
            activeQuests = new ArrayList<>();
        }
        if (mapPlayCounts == null) {
            mapPlayCounts = new HashMap<>();
        }
        return this;
    }

    public int getExperienceForNextLevel() {
        return 100 + Math.max(0, level - 1) * 35;
    }

    public List<PassQuest> getActiveDailyQuests() {
        return activeQuests.stream().filter(q -> q.category == QuestCategory.DAILY).toList();
    }

    public List<PassQuest> getActiveWeeklyQuests() {
        return activeQuests.stream().filter(q -> q.category == QuestCategory.WEEKLY).toList();
    }

    public List<PassQuest> getActivePermanentQuests() {
        return activeQuests.stream().filter(q -> q.category == QuestCategory.PERMANENT).toList();
    }

    /**
     * 阵营卡类型。
     *
     * <p>
     * 前四种是「目标固定」的阵营卡：卡本身即目标阵营。{@link #CUSTOM} 是自定义卡，
     * 使用时才由玩家选择目标阵营，故它自身不带目标（{@link #getTypeRoleId()} 为 {@code 0}）。
     */
    public enum FactionCardType {
        NONE("sre.pass.faction.none", "none", 0),
        KILLER("sre.pass.faction.killer", "killer", 1),
        CIVILIAN("sre.pass.faction.civilian", "civilian", 2),
        NEUTRAL("sre.pass.faction.neutral", "neutral", 3),
        NEUTRAL_FOR_KILLER("sre.pass.faction.neutral_for_killer", "neutral_for_killer", 4),
        /** 自定义卡：使用时弹屏自选目标阵营，卡本身不代表任何阵营。 */
        CUSTOM("sre.pass.faction.custom", "custom", 5);

        public final String displayName;
        public final String questKey;
        public final int type;

        FactionCardType(String displayName, String questKey, int type) {
            this.displayName = displayName;
            this.questKey = questKey;
            this.type = type;
        }

        public Integer getTypeId() {
            return type;
        }

        /** 是否能作为「目标阵营」被选中：四个真实阵营，不含 {@link #NONE} 与 {@link #CUSTOM}。 */
        public boolean isSelectableTarget() {
            return this != NONE && this != CUSTOM;
        }

        /**
         * 解析自定义卡的目标阵营。只接受四个真实阵营的键（{@code killer} / {@code civilian} /
         * {@code neutral} / {@code neutral_for_killer}）；其余（含 {@code null}、{@code custom}）
         * 一律返回 {@link #NONE}，由调用方按「没选目标」处理。
         */
        public static FactionCardType targetFromString(String raw) {
            FactionCardType type = raw == null ? NONE : fromString(raw.trim());
            return type.isSelectableTarget() ? type : NONE;
        }

        public static FactionCardType fromRole(SRERole role) {
            if (role == null) {
                return NONE;
            }
            if (role.canUseKiller() && !role.isInnocent()) {
                return KILLER;
            }
            if (role.isNeutrals() || (!role.isInnocent() && !role.canUseKiller())) {
                return NEUTRAL;
            }
            if (role.isInnocent() && !role.isVigilanteTeam()) {
                return CIVILIAN;
            }
            return NONE;
        }

        public static FactionCardType fromString(String raw) {
            for (FactionCardType type : values()) {
                if (type.name().equalsIgnoreCase(raw) || type.questKey.equalsIgnoreCase(raw)) {
                    return type;
                }
            }
            return NONE;
        }

        public static FactionCardType fromRoleType(int typeValue) {
            if (typeValue == 0)
                return FactionCardType.NONE;
            if (typeValue == 1)
                return FactionCardType.CIVILIAN;
            if (typeValue == 2)
                return FactionCardType.NEUTRAL;
            if (typeValue == 3)
                return FactionCardType.NEUTRAL_FOR_KILLER;
            if (typeValue == 4)
                return FactionCardType.KILLER;
            if (typeValue == 5)
                return FactionCardType.CIVILIAN;
            return NONE;
        }

        public static FactionCardType fromInt(int typeValue) {
            for (FactionCardType type : values()) {
                if (type.type == typeValue) {
                    return type;
                }
            }
            return NONE;
        }

        /**
         * 卡片的目标扮演类型（口径同 {@code PlayerRoleWeightManager.getRoleType(SRERole)}）。
         * 自定义卡没有固定目标，返回 {@code 0}。
         */
        public int getTypeRoleId() {
            return switch (this) {
                case KILLER -> 4;
                case CIVILIAN -> 1;
                case NEUTRAL -> 2;
                case NEUTRAL_FOR_KILLER -> 3;
                default -> 0;
            };
        }
    }

    public enum QuestCategory {
        DAILY, WEEKLY, PERMANENT;

        public static QuestCategory fromString(String raw) {
            if (raw == null || raw.isBlank()) {
                return DAILY;
            }
            try {
                return QuestCategory.valueOf(raw.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                return DAILY;
            }
        }
    }

    public static final class PassQuest {
        public String id;
        public String title;
        public String description;
        public String objectiveType = ProgressionObjectives.PLAY_MATCH;
        public String objectiveKey;
        public int progress;
        public int target;
        public int rewardExperience;
        public int rewardCoins;
        public int rewardLoot;
        public FactionCardType rewardCard = FactionCardType.NONE;
        public boolean rewarded;
        public QuestCategory category = QuestCategory.DAILY;
    }
}
