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

    public enum FactionCardType {
        NONE("sre.pass.faction.none", "none", 0),
        KILLER("sre.pass.faction.killer", "killer", 1),
        CIVILIAN("sre.pass.faction.civilian", "civilian", 2),
        NEUTRAL("sre.pass.faction.neutral", "neutral", 3),
        NEUTRAL_FOR_KILLER("sre.pass.faction.neutral_for_killer", "neutral_for_killer", 4);

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

        public int getTypeRoleId() {
            return switch (this.type) {
                case 1 -> 4;
                case 2 -> 1;
                case 3 -> 2;
                case 4 -> 3;
                default -> 0;
            };
        }
    }

    public enum QuestCategory {
        DAILY, WEEKLY, PERMANENT
    }

    public static final class PassQuest {
        public String id;
        public String title;
        public String description;
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
