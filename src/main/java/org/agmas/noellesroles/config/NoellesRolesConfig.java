package org.agmas.noellesroles.config;

import io.wifi.ConfigCompact.ConfigClassHandler;
import io.wifi.ConfigCompact.annotation.ConfigSync;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.game.GameConstants;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry.Category;
import net.minecraft.resources.ResourceLocation;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.SREModifier;
import com.google.gson.annotations.JsonAdapter;

@Config(name = "noellesroles")
public class NoellesRolesConfig implements ConfigData {
    public static class SpawnInfo {
        /**
         * 最小启用玩家数。-1禁用
         */
        public int minEnabledPlayer = -1;
        /**
         * 启用概率，1 = 1/10000。-1禁用
         */
        public int enableChance = -1;
        /**
         * 最大启用玩家数。-1禁用
         */
        public int maxEnabledPlayer = -1;
        /**
         * 最大刷新数量
         */
        public int maxSpawn = 1;
        /**
         * 在什么地图刷新。为空全部
         */
        public ArrayList<String> map = new ArrayList<>();

        public SpawnInfo addMaps(String... maps) {
            for (var t : maps) {
                map.add(t);
            }
            return this;
        }

        public SpawnInfo addMaps(ArrayList<String> maps) {
            map.addAll(maps);
            return this;
        }

        public SpawnInfo setMaps(ArrayList<String> maps) {
            map.clear();
            map.addAll(maps);
            return this;
        }

        public SpawnInfo setMaps(String... maps) {
            this.map.clear();
            for (var t : maps) {
                map.add(t);
            }
            return this;
        }

        public SpawnInfo setMaxEnabledPlayer(int num) {
            this.maxEnabledPlayer = num;
            return this;
        }

        public SpawnInfo setMinEnabledPlayer(int num) {
            this.minEnabledPlayer = num;
            return this;
        }

        public SpawnInfo setEnableChance(int num) {
            this.enableChance = num;
            return this;
        }

        public SpawnInfo setMax(int max) {
            this.maxSpawn = max;
            return this;
        }

        public SpawnInfo() {
        }

        public SpawnInfo(int defaultMinPlayer, int defaultMaxPlayer, int defaultEnableChance, int maxSpawn) {
            this.minEnabledPlayer = defaultMinPlayer;
            this.maxEnabledPlayer = defaultMaxPlayer;
            this.enableChance = defaultEnableChance;
            this.maxSpawn = maxSpawn;
        }

        public SpawnInfo(int defaultMinPlayer, int defaultMaxPlayer, int defaultEnableChance, int maxSpawn,
                ArrayList<String> defaultMaps) {
            this.minEnabledPlayer = defaultMinPlayer;
            this.maxEnabledPlayer = defaultMaxPlayer;
            this.maxSpawn = maxSpawn;
            this.enableChance = defaultEnableChance;
            this.map = new ArrayList<>(defaultMaps);
        }
    }

    @JsonAdapter(RoleSpawnInfoEntriesAdapter.class)
    public static class RoleSpawnInfoEntries {
        public HashMap<ResourceLocation, SpawnInfo> maps = new HashMap<>();
        public int type; // 自动根据 T 设置

        // 无参构造（供 Gson 反序列化使用）
        public RoleSpawnInfoEntries() {
            this.type = 0; // 默认未知
        }

        public SpawnInfo getSpawnInfo(SREModifier modifier) {
            return maps.getOrDefault(modifier.identifier(), null);
        }

        public SpawnInfo getSpawnInfo(SRERole role) {
            return maps.getOrDefault(role.identifier(), null);
        }

        // 内部构造，用于工厂方法
        private RoleSpawnInfoEntries(int type) {
            this.type = type;
        }

        // 根据类型获取对应的 type 值
        private static int getTypeForClass(Class<?> clazz) {
            if (SRERole.class.isAssignableFrom(clazz)) {
                return 1;
            } else if (SREModifier.class.isAssignableFrom(clazz)) {
                return 2;
            }
            return 0;
        }

        // 工厂方法：创建角色默认配置
        public static RoleSpawnInfoEntries createDefaultRoleInfo() {
            RoleSpawnInfoEntries obj = new RoleSpawnInfoEntries(getTypeForClass(SRERole.class));
            for (var entry : TMMRoles.ROLES.entrySet()) {
                SRERole role = entry.getValue();
                if (!role.canSetSpawnInfoInConfig())
                    continue;
                obj.maps.put(entry.getKey(), new SpawnInfo(
                        role.defaultEnableNeedPlayerCount,
                        role.defaultEnableMaxPlayerCount,
                        role.defaultEnableChance,
                        role.defaultMaxCount,
                        role.defaultSpawnMaps));
            }
            return obj;
        }

        // 工厂方法：创建修饰符默认配置
        public static RoleSpawnInfoEntries createDefaultModifierInfo() {
            RoleSpawnInfoEntries obj = new RoleSpawnInfoEntries(getTypeForClass(SREModifier.class));
            for (SREModifier entry : HMLModifiers.MODIFIERS) {
                if (!entry.canSetSpawnInfoInConfig())
                    continue;
                obj.maps.put(entry.identifier(), new SpawnInfo(
                        entry.defaultNeedPlayerCount,
                        entry.defaultMaxPlayerCount,
                        entry.defaultEnableChance,
                        entry.defaultMaxCount,
                        entry.defaultSpawnMaps));
            }
            return obj;
        }
    }

    public static ConfigClassHandler<NoellesRolesConfig> HANDLER = new ConfigClassHandler<>(
            NoellesRolesConfig.class);

    /**
     * Whether insane players will randomly see people as morphed
     */

    public boolean insanePlayersSeeMorphs = true;

    /**
     * Areas that will spawn Swast. Use | to split maps
     */

    public ArrayList<String> maChenXuMaps = new ArrayList<>(List.of("areas_qiyucun"));

    /**
     * Areas that will spawn Swast. Use | to split maps
     */

    public ArrayList<String> swastMaps = new ArrayList<>(
            List.of("areas1", "areas3", "areas4", "areas7", "areas10", "areas_qiyucun", "areas17",
                    "areas_konggang"));

    /**
     * Areas that will spawn underwater roles (Sea King, Diver, Water Ghost)
     */
    public ArrayList<String> underwaterRolesMaps = new ArrayList<>(List.of("areas14"));

    /**
     * Areas that will spawn Konggang roles (Pilot, Shadow Falcon)
     */
    public ArrayList<String> airRolesMaps = new ArrayList<>(List.of("areas_konggang"));

    /**
     * Role - The chance of egg roles
     */
    @ConfigEntry.Category(value = "detail")
    public RoleSpawnInfoEntries roleDetails = RoleSpawnInfoEntries.createDefaultRoleInfo();
    @ConfigEntry.Category(value = "detail")
    public RoleSpawnInfoEntries modifierDetails = RoleSpawnInfoEntries.createDefaultModifierInfo();

    @ConfigEntry.Category(value = "detail")
    public int chanceOfTouhouRoles = 40;
    @ConfigEntry.Category(value = "detail")
    public int minPlayerForTouhouRoles = 12;
    @ConfigEntry.Category(value = "detail")
    public int chanceOfEggRoles = 15;

    // ==================== 角色刷新概率配置 ====================
    // 普通概率配置（0-100，百分比）

    /**
     * 特殊警卫配置
     */
    @ConfigEntry.Category(value = "detail")
    public int chanceOfPatroller = 80;
    @ConfigEntry.Category(value = "detail")
    public int chanceOfMartialArtsInstructor = 60;
    @ConfigEntry.Category(value = "detail")
    public int chanceOfElf = 70;
    @ConfigEntry.Category(value = "detail")
    public int chanceOfSwast = 70;
    @ConfigEntry.Category(value = "detail")
    public int chanceOfDoublePatroller = 20;
    @ConfigEntry.Category(value = "detail")
    public int chanceOfDoubleElf = 10;
    @ConfigEntry.Category(value = "detail")
    public int chanceOfBestVigilante = 10;

    /**
     * 特殊警卫刷新最小玩家数
     */
    @ConfigEntry.Category(value = "detail")
    public int minPlayerForSpecialPolice1 = 12;
    @ConfigEntry.Category(value = "detail")
    public int minPlayerForSpecialPolice2 = 18;
    @ConfigEntry.Category(value = "detail")
    public int minPlayerForSpecialPolice3 = 24;
    @ConfigEntry.Category(value = "detail")
    public int minPlayerForSpecialPolice4 = 30;
    @ConfigEntry.Category(value = "detail")
    public int minPlayerForSpecialPolice5 = 36;

    /**
     * Starting cooldown (in ticks)
     */

    public int generalCooldownTicks = GameConstants.getInTicks(0, 30);

    /**
     * Enable client blood render
     */

    public boolean enableClientBlood = true;

    /**
     * Punishment for a civilian's accidental killing of another civilian
     */

    public boolean accidentalKillPunishment = true;

    /**
     * Allow Natural deaths to trigger voodoo (deaths without an assigned killer)
     */

    public boolean voodooNonKillerDeaths = false;

    /**
     * Makes voodoos act like Evil players when shot by a revolver (no backfire, no
     * gun lost)
     */

    public boolean voodooShotLikeEvil = true;

    /**
     * Whether Executioners can manually select their targets. If disabled, targets
     * will be assigned randomly
     */
    @ConfigSync(shouldSync = true)
    public boolean executionerCanSelectTarget = false;

    /**
     * Morphling - Morph duration in seconds
     */

    public int morphlingMorphDuration = 35;
    /**
     * Morphling - Morph cooldown in seconds
     */

    public int morphlingMorphCooldown = 20;

    // // /**
    // *Recaller-
    // Maximum recall
    // distance in blocks*/

    public int recallerMaxDistance = 50;

    /**
     * Recaller - Recall mark cooldown in seconds
     */

    public int recallerMarkCooldown = 10;

    /**
     * Recaller - Teleport cooldown in seconds
     */

    public int recallerTeleportCooldown = 30;

    /**
     * Phantom - Invisibility duration in seconds
     */

    public int phantomInvisibilityDuration = 30;

    /**
     * Phantom - Invisibility cooldown in seconds
     */

    public int phantomInvisibilityCooldown = 90;

    /**
     * Voodoo - Voodoo ritual cooldown in seconds
     */

    public int voodooCooldown = 15;

    /**
     * Vulture - Eat body cooldown in seconds
     */

    public int vultureEatCooldown = 3;

    /**
     * Swapper - Swap cooldown in seconds
     */

    public int swapperSwapCooldown = 60;

    /**
     * Manipulator - Control target cooldown in seconds
     */

    public int manipulatorCooldown = 60;

    /**
     * Skill Echo Event - global switch (default off)
     */
    public boolean skillEchoEventEnabled = false;

    /**
     * Skill Echo Event - random unannounced role broadcast switch
     */
    public boolean skillEchoRandomBroadcastEnabled = false;

    /**
     * Skill Echo Event - random broadcast interval in seconds
     */
    public int skillEchoRandomIntervalSeconds = 90;

    /**
     * Pelican - percentage of starting players needed to swallow for victory
     */
    public double pelicanEatPercentage = 70.0D;

    // ==================== Mafia 配置 ====================
    public int godfatherStartingBullets = 1;
    public int godfatherMaxLoadedBullets = 3;
    public int mafiaRecruitRange = 16;

    /**
     * (Client Side) Welcome Voice - Play welcome voice
     */

    @Category("magic")
    public String credit = "";
    @Category("detail")
    public int chanceOfTaskmaster = 30;
    @Category("detail")
    public int minPlayerForSecretive = 12;
    @Category("detail")
    public int chanceOfSecretive = 20;
    @Category("detail")
    public int minPlayerForLovers = 12;
    @Category("detail")
    public int chanceOfModifierLovers = 10;

    public static NoellesRolesConfig instance() {
        return HANDLER.instance();
    }
}
