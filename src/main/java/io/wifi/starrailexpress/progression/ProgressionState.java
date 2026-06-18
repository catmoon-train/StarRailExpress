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
        NEUTRAL_FOR_KILLER("sre.pass.faction.neutral_for_killer", "neutral_for_killer", 3);

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

        public static FactionCardType fromInt(int typeValue) {
            for (FactionCardType type : values()) {
                if (type.type == typeValue) {
                    return type;
                }
            }
            return NONE;
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
