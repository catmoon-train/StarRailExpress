package org.agmas.harpymodloader.modded_murder;

import java.util.HashMap;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;
import io.wifi.starrailexpress.api.Role;

public class PlayerRoleWeightManager {
    public static HashMap<UUID, WeightInfo> playerWeights = new HashMap<>();

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
        return 1. - (double) typeWeight / (double) total;
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
        if (weightManager.getWeight(type) >= 50) {
            // 重置
            weightManager = new PlayerRoleWeightManager.WeightInfo();
            PlayerRoleWeightManager.playerWeights.put(player, weightManager);
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
    public static int getRoleType(Role role) {
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

    public static int getRoleType_OnlyDistinctKiller(Role r) {
        int rt = getRoleType(r);
        if (rt == -1)
            return -1;
        if (rt <= 3 || rt == 5) {
            return 1;
        }
        return rt;
    }

    public static int getRoleType_IgnoreNeutralType(Role r) {
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

        public WeightInfo() {
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
    }

}
