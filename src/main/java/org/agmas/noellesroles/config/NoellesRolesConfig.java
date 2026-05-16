package org.agmas.noellesroles.config;

import io.wifi.ConfigCompact.ConfigClassHandler;
import io.wifi.ConfigCompact.annotation.ConfigSync;
import io.wifi.starrailexpress.game.GameConstants;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry.Category;

import java.util.ArrayList;
import java.util.List;

@Config(name = "noellesroles")
public class NoellesRolesConfig implements ConfigData {
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

    public ArrayList<String> swastMaps = new ArrayList<>(List.of("areas1", "areas3", "areas4", "areas7", "areas10","areas_qiyucun","areas17","areas_konggang"));

    /**
     * Areas that will spawn underwater roles (Sea King, Diver, Water Ghost)
     */
    public ArrayList<String> underwaterRolesMaps = new ArrayList<>(List.of("areas14"));

    /**
     * Areas that will spawn Konggang roles (Pilot, Shadow Falcon)
     */
    public ArrayList<String> konggangMaps = new ArrayList<>(List.of("areas_konggang"));

    /**
     * Role - The chance of egg roles
     */

    public int chanceOfTouhouRoles = 40;
    public int chanceOfEggRoles = 15;

    // ==================== 角色刷新概率配置 ====================
    // 普通概率配置（0-100，百分比）

    /**
     * 建筑师刷新概率（%）
     */
    public int chanceOfBuilder = 70;

    /**
     * 建筑师刷新最小玩家数
     */
    public int minPlayerForBuilder = 12;

    /**
     * 布谷鸟刷新概率（%）
     */
    public int chanceOfCuckoo = 45;

    /**
     * 苦力怕刷新概率（%）
     */
    public int chanceOfCreeper = 20;

    /**
     * 画家刷新概率（%）
     */
    public int chanceOfPainter = 50;

    /**
     * 雇佣兵刷新概率（%）
     */
    public int chanceOfMercenary = 20;

    /**
     * 雇佣兵刷新最小玩家数
     */
    public int minPlayerForMercenary = 12;

    /**
     * 愚者刷新概率（%）
     */
    public int chanceOfTheFool = 30;

    /**
     * 愚者刷新最小玩家数
     */
    public int minPlayerForTheFool = 12;

    /**
     * 红尘客刷新概率（%）
     */
    public int chanceOfWayfarer = 25;

    /**
     * 毒师刷新概率（%）
     */
    public int chanceOfPoisoner = 55;

    /**
     * 魔术师刷新概率（%）
     */
    public int chanceOfMagician = 25;

    /**
     * 监察员刷新概率（%）
     */
    public int chanceOfMonitor = 75;

    /**
     * 年兽刷新概率（%）
     */
    public int chanceOfNianShou = 20;

    /**
     * 作家刷新概率（%）
     */
    public int chanceOfWriter = 2;

    /**
     * 棒球员刷新概率（%）
     */
    public int chanceOfBaseballPlayer = 2;

    /**
     * 电报员刷新概率（%）
     */
    public int chanceOfTelegrapher = 2;

    /**
     * 猫死灵法师刷新概率（%）
     */
    public int chanceOfCatNecromancer = 10;

    /**
     * 猫死灵法师刷新最小玩家数
     */
    public int minPlayerForCatNecromancer = 12;

    // StupidExpress 角色配置

    /**
     * 纵火犯刷新最小玩家数
     */
    public int minPlayerForArsonist = 12;

    /**
     * 死灵法师刷新最小玩家数
     */
    public int minPlayerForNecromancer = 12;

    /**
     * 死灵法师刷新概率（%）
     */
    public int chanceOfNecromancer = 50;

    /**
     * 贪婪者刷新最小玩家数
     */
    public int minPlayerForAvaricious = 12;

    /**
     * 新手刷新最小玩家数
     */
    public int minPlayerForInitiate = 12;

    /**
     * 失忆者刷新最小玩家数
     */
    public int minPlayerForAmnesiac = 12;

    /**
     * 失忆者刷新概率（%）
     */
    public int chanceOfAmnesiac = 50;

    // 小概率配置（0-10000，基于10000的概率）

    /**
     * 更好的义警刷新概率（0-10000，0.1% = 10）
     */
    public int chanceOfBestVigilante = 10;

    /**
     * 特殊警卫配置
     */
    public int chanceOfPatroller = 80;
    public int chanceOfMartialArtsInstructor = 60;
    public int chanceOfElf = 70;
    public int chanceOfSwast = 70;
    public int chanceOfDoublePatroller = 20;
    public int chanceOfDoubleElf = 10;

    /**
     * Modifier - The chance of Lovers
     */

    public int chanceOfModifierLovers = 10;
    /**
     * Modifier - The chance of Refugee
     */

    public int chanceOfModifierRefugee = 10;

    /**
     * Modifier - The chance of Split Personality
     */

    public int chanceOfModifierSplitPersonality = 0;

    /**
     * 黑白修饰符刷新概率（%）
     */
    public int chanceOfBlackWhite = 10;

    /**
     * 黑白修饰符刷新最小玩家数
     */
    public int minPlayerForBlackWhite = 10;

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
     * Maximum number of Conductors allowed
     */

    public int conductorMax = 1;
    /**
     * Maximum number of Executioners allowed
     */

    public int executionerMax = 1;
    /**
     * Maximum number of Vultures allowed
     */

    public int vultureMax = 1;
    /**
     * Maximum number of Jesters allowed
     */

    public int jesterMax = 1;
    /**
     * Maximum number of Morphlings allowed
     */

    public int morphlingMax = 1;
    /**
     * Maximum number of Bartenders allowed
     */

    public int bartenderMax = 1;
    /**
     * Maximum number of Noisemakers allowed
     */

    public int noisemakerMax = 1;
    /**
     * Maximum number of Phantoms allowed
     */

    public int phantomMax = 1;
    /**
     * Maximum number of Awesome Bingluses allowed
     */

    public int awesomeBinglusMax = 1;
    /**
     * Maximum number of Swappers allowed
     */

    public int swapperMax = 1;
    /**
     * Maximum number of Voodoos allowed
     */

    public int voodooMax = 1;
    /**
     * Maximum number of Coroners allowed
     */

    public int coronerMax = 1;
    /**
     * Maximum number of Recallers allowed
     */

    public int recallerMax = 1;
    /**
     * Maximum number of Broadcasters allowed
     */
    public int broadcasterMax = 1;
    /**
     * Maximum number of Gamblers allowed
     */

    public int gamblerMax = 1;
    /**
     * Maximum number of Glitch Robots allowed
     */

    public int glitchRobotMax = 1;
    /**
     * Maximum number of Ghosts allowed
     */

    public int ghostMax = 1;

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
    public boolean skillEchoRandomBroadcastEnabled = true;

    /**
     * Skill Echo Event - random broadcast interval in seconds
     */
    public int skillEchoRandomIntervalSeconds = 90;

    /**
     * (Client Side) Welcome Voice - Play welcome voice
     */

    @Category("magic")
    public String credit = "";
}
