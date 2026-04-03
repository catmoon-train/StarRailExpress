package org.agmas.harpymodloader.modded_murder;

import io.wifi.starrailexpress.api.SRERole;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.UUID;

public class PlayerRoleWeightManager {
    public static HashMap<UUID, Integer> ForcePlayerTeam = new HashMap<>();
    public static HashMap<UUID, WeightInfo> playerWeights = new HashMap<>();

    /**
     * 将角色类型归并为阵营组：
     * - 无辜阵营 (0/1) → 1
     * - 中立阵营 (2) → 2
     * - 亲杀中立 (3) → 3
     * - 杀手阵营 (4) → 4
     * - 警卫阵营 (5) → 5
     */
    private static int getFactionGroup(int type) {
        if (type <= 1)
            return 1;
        if (type == 5)
            return 1;
        if (type == 3)
            return 2;
        return type;
    }

    private static boolean isKillerSideType(int type) {
        return type == 3 || type == 4;
    }

    public static double getRoleWeightPercent(UUID player, int type) {
        var weightManager = PlayerRoleWeightManager.playerWeights.get(player);
        if (weightManager == null) {
            weightManager = new PlayerRoleWeightManager.WeightInfo();
            PlayerRoleWeightManager.playerWeights.putIfAbsent(player, weightManager);
        }
        int typeWeight = weightManager.getWeight(type);
        int total = weightManager.getWeightSum();
        if (total <= 0)
            total = 1;
        double basePercent = 1.0 - (double) typeWeight / (double) total;

        // 连续相同阵营惩罚：streak>=3时每多一局概率减半，防止一直游玩同一阵营
        int streak = weightManager.getStreakCount();
        if (streak >= 3) {
            if (getFactionGroup(type) == weightManager.getLastAssignedFactionGroup()) {
                double streakPenalty = Math.pow(0.5, streak - 1);
                basePercent *= streakPenalty;
            } else {
                double streakPenalty = Math.pow(1.5, streak - 1);
                basePercent *= streakPenalty;
            }
        }

        return Math.max(0.0, basePercent);
    }

    public static int getHighestScoredType(UUID player) {
        int bestType = 1;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int type = 1; type <= 5; type++) {
            double score = getRoleWeightPercent(player, type);
            if (score > bestScore) {
                bestScore = score;
                bestType = type;
                continue;
            }
            if (Double.compare(score, bestScore) == 0 && isKillerSideType(type) && !isKillerSideType(bestType)) {
                bestType = type;
            }
        }
        return bestType;
    }

    public static double getRoleWeightPercent(Player playerEntity, int roleType) {
        return getRoleWeightPercent(playerEntity.getUUID(), roleType);
    }

    public static int getWeight(Player player, int type) {
        return getWeight(player.getUUID(), type);
    }

    public static void resetWeight(Player player) {
        resetWeight(player.getUUID());
    }

    public static void clearWeight(UUID player) {
        if (playerWeights.containsKey(player))
            playerWeights.remove(player);
    }

    public static void resetWeight(UUID player) {
        var weightManager = new PlayerRoleWeightManager.WeightInfo();
        PlayerRoleWeightManager.playerWeights.put(player, weightManager);
    }

    public static int getWeight(UUID player, int type) {
        var weightManager = PlayerRoleWeightManager.playerWeights.get(player);
        if (weightManager == null) {
            weightManager = new PlayerRoleWeightManager.WeightInfo();
            PlayerRoleWeightManager.playerWeights.putIfAbsent(player, weightManager);
        }
        return weightManager.getWeight(type);
    }

    public static void addWeight(Player player, int type, int weightPlus) {
        addWeight(player.getUUID(), type, weightPlus);
    }

    public static void addWeight(UUID player, int type, int weightPlus) {
        var weightManager = PlayerRoleWeightManager.playerWeights.get(player);
        if (weightManager == null) {
            weightManager = new PlayerRoleWeightManager.WeightInfo();
            PlayerRoleWeightManager.playerWeights.putIfAbsent(player, weightManager);
        }
        // 记录本局阵营，更新连续计数
        weightManager.updateStreak(type);
        // 比例缩放：当总权重过大时等比缩小，保留历史比例且避免极端权重堆积
        if (weightManager.getWeightSum() >= 25) {
            weightManager.scaleDown();
        }
        weightManager.addWeight(type, weightPlus);
    }

    /**
     * Get Role type(int) for a role
     * 
     * @param role
     * @return - 0: Innocent and Cannot Use Killer
     *         - 1: Innocent but can Use Killer
     *         - 2: Neturals but not for killer
     *         - 3: Neturals for killer
     *         - 4: Killer
     *         - 5: Vigilante
     */
    public static int getRoleType(SRERole role) {
        if (role == null)
            return -1;

        if (role.isVigilanteTeam()) {
            return 5;
        }
        if (role.isInnocent()) {
            return 1;
        }

        if (role.isNeutrals() && !role.isNeutralForKiller()) {
            return 2;
        }
        if (role.isNeutrals() && role.isNeutralForKiller()) {
            return 3;
        }
        if (!role.isInnocent() && !role.canUseKiller() && !role.isNeutralForKiller()) {
            return 2;
        }
        if (!role.isInnocent() && !role.canUseKiller() && role.isNeutralForKiller()) {
            return 3;
        }
        if (role.canUseKiller()) {
            return 4;
        }

        return -1; // Unknown
    }

    public static int getRoleType_OnlyDistinctKiller(SRERole r) {
        int rt = getRoleType(r);
        if (rt == -1)
            return -1;
        if (rt <= 3 || rt == 5) {
            return 1;
        }
        return rt;
    }

    public static int getRoleType_IgnoreNeutralType(SRERole r) {
        int rt = getRoleType(r);
        if (rt == -1)
            return -1;
        if (rt <= 1) {
            return 1;
        } else if (rt <= 3) {
            return 2;
        }
        return rt;
    }

    public static class WeightInfo {
        public int innocentWeight = 1;
        public int killerWeight = 1;
        public int neutralsWeight = 1;
        public int neutralsForKillerWeight = 1;
        public int vigilanteWeight = 1;
        private int killerSideFailureBoost = 0;

        // 连续阵营追踪：记录上一局所属阵营组及连续次数
        private int lastAssignedFactionGroup = -1;
        private int streakCount = 0;

        public WeightInfo() {
        }

        /**
         * 在记录本局权重前调用，更新连续阵营计数。
         * 阵营组与 getFactionGroup 保持一致：无辜=1, 中立=2, 亲杀中立=3, 杀手=4, 警卫=5。
         */
        public void updateStreak(int type) {
            int fg = getFactionGroup(type);
            if (fg == lastAssignedFactionGroup) {
                streakCount++;
            } else {
                streakCount = 1;
                lastAssignedFactionGroup = fg;
            }
            if (isKillerSideType(type)) {
                killerSideFailureBoost = 0;
            }
        }

        public int getStreakCount() {
            return streakCount;
        }

        public int getLastAssignedFactionGroup() {
            return lastAssignedFactionGroup;
        }

        public int getKillerSideFailureBoost() {
            return killerSideFailureBoost;
        }

        public void incrementKillerSideFailureBoost() {
            killerSideFailureBoost++;
        }

        /**
         * 等比缩小所有权重（各取最大值与1的较大值），保持相对比例、防止权重无限堆积。
         */
        public void scaleDown() {
            innocentWeight = Math.max(1, innocentWeight / 2);
            killerWeight = Math.max(1, killerWeight / 2);
            neutralsWeight = Math.max(1, neutralsWeight / 2);
            neutralsForKillerWeight = Math.max(1, neutralsForKillerWeight / 2);
            vigilanteWeight = Math.max(1, vigilanteWeight / 2);
        }

        public int getWeightSum() {
            return this.innocentWeight + this.killerWeight + this.neutralsForKillerWeight + this.neutralsWeight
                    + this.vigilanteWeight;
        }

        public void putInnocentWeight(int weight) {
            innocentWeight = weight;
        }

        public void putVigilanteWeight(int weight) {
            vigilanteWeight = weight;
        }

        public void putKillerWeight(int weight) {
            killerWeight = weight;
        }

        public void putNeutralsWeight(int weight) {
            neutralsWeight = weight;
        }

        public void putNeutralsForKillerWeight(int weight) {
            neutralsForKillerWeight = weight;
        }

        /**
         * 
         * @param type
         *               - 1: Innocent but can Use Killer
         *               - 2: Neturals but not for killer
         *               - 3: Neturals for killer
         *               - 4: Killer
         * @param weight
         */
        public void addWeight(int type, int weight) {
            if (type <= 1) {
                this.innocentWeight += weight;
                return;
            }
            if (type == 2) {
                this.neutralsWeight += (weight);
                return;
            }
            if (type == 3) {
                this.neutralsForKillerWeight += (weight);
                return;
            }
            if (type == 4) {
                this.killerWeight += (weight);
                return;
            }
            if (type == 5) {
                this.vigilanteWeight += (weight);
                return;
            }
        }

        /**
         * 
         * @param type
         *               - 1: Innocent but can Use Killer
         *               - 2: Neturals but not for killer
         *               - 3: Neturals for killer
         *               - 4: Killer
         * @param weight
         */
        public void putWeight(int type, int weight) {
            if (type <= 1) {
                putInnocentWeight(weight);
                return;
            }
            if (type == 2) {
                putNeutralsWeight(weight);
                return;
            }
            if (type == 3) {
                putNeutralsForKillerWeight(weight);
                return;
            }
            if (type == 4) {
                putKillerWeight(weight);
                return;
            }

            if (type == 5) {
                putVigilanteWeight(weight);
                return;
            }
        }

        /**
         * 
         * @param type
         *             - 1: Innocent but can Use Killer
         *             - 2: Neturals but not for killer
         *             - 3: Neturals for killer
         *             - 4: Killer
         */
        public int getWeight(int type) {
            if (type <= 1)
                return innocentWeight;
            if (type == 2) {
                return this.neutralsWeight;
            }
            if (type == 3) {
                return this.neutralsForKillerWeight;
            }
            if (type == 4) {
                return this.killerWeight;
            }
            if (type == 5) {
                return this.vigilanteWeight;
            }
            return -1;
        }

        public int getHighestWeightType() {
            int minType = 1;
            double minCount = 1f;
            for (int i = 1; i <= 5; i++) {
                int tmp = this.getWeight(i);
                if (tmp <= minCount) {
                    minCount = tmp;
                    minType = i;
                }
            }
            return minType;
        }
    }

    public static void forceTeam(UUID player, int roleType) {
        if (roleType == -1) {
            ForcePlayerTeam.remove(player);
        } else {
            ForcePlayerTeam.put(player, roleType);
        }
    }

    public static void boostKillerSideAfterForceFailure(UUID player) {
        var weightManager = PlayerRoleWeightManager.playerWeights.get(player);
        if (weightManager == null) {
            weightManager = new PlayerRoleWeightManager.WeightInfo();
            PlayerRoleWeightManager.playerWeights.put(player, weightManager);
        }
        weightManager.incrementKillerSideFailureBoost();
    }

}
