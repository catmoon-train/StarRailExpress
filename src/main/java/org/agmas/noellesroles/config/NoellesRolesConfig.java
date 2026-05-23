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
     * Areas that will spawn Cuckoo. If empty, Cuckoo spawns on all maps.
     * Adding maps here restricts Cuckoo to only spawn on those maps.
     */
    public ArrayList<String> cuckooMaps = new ArrayList<>();

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
     * 东方角色刷新最小玩家数
     */
    public int minPlayerForTouhouRoles = 12;

    /**
     * 红尘客刷新最小玩家数
     */
    public int minPlayerForWayfarer = 10;

    /**
     * 布谷鸟刷新概率（%）
     */
    public int chanceOfCuckoo = 45;

    /**
     * 苦力怕刷新概率（%）
     */
    public int chanceOfCreeper = 25;

    /**
     * 画家刷新概率（%）
     */
    public int chanceOfPainter = 50;

    /**
     * 雇佣兵刷新概率（%）
     */
    public int chanceOfMercenary = 25;

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
     * 毒师刷新最小玩家数
     */
    public int minPlayerForPoisoner = 12;

    /**
     * 疫使刷新概率（%）
     */
    public int chanceOfInfected = 50;

    /**
     * 疫使刷新最小玩家数
     */
    public int minPlayerForInfected = 12;

    /**
     * 疫使感染致死时间（秒）
     * 玩家被感染后多久会死亡
     */
    public int infectedKillTime = 180;

    /**
     * 疫使病毒传播间隔（秒）
     * 每隔多久被感染者会传播病毒给附近玩家
     */
    public int infectedSpreadInterval = 50;

    /**
     * 疫使技能冷却时间（秒）
     */
    public int infectedCooldown = 80;

    /**
     * 彩蛋角色刷新最小玩家数
     */
    public int minPlayerForEggRoles = 12;

    /**
     * 魔术师刷新概率（%）
     */
    public int chanceOfMagician = 25;

    /**
     * 魔术师刷新最小玩家数
     */
    public int minPlayerForMagician = 16;

    /**
     * 迷失杀手刷新概率（%）
     */
    public int chanceOfLostKiller = 20;

    /**
     * 监察员刷新概率（%）
     */
    public int chanceOfMonitor = 75;

    /**
     * 年兽刷新概率（%）
     */
    public int chanceOfNianShou = 20;

    /**
     * 记录员刷新最小玩家数
     */
    public int minPlayerForRecorder = 10;

    /**
     * 秃鹫刷新最小玩家数
     */
    public int minPlayerForVulture = 8;

    /**
     * 秉烛人刷新最小玩家数
     */
    public int minPlayerForCandleBearer = 12;

    /**
     * 钟表匠刷新最小玩家数
     */
    public int minPlayerForClockmaker = 12;

    /**
     * 仇杀客刷新最小玩家数
     */
    public int minPlayerForBloodFeudist = 12;

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
     * 特殊警卫刷新最小玩家数
     */
    public int minPlayerForSpecialPolice1 = 12;
    public int minPlayerForSpecialPolice2 = 18;
    public int minPlayerForSpecialPolice3 = 24;
    public int minPlayerForSpecialPolice4 = 30;
    public int minPlayerForSpecialPolice5 = 36;

    /**
     * REFUGEE修饰符刷新最小玩家数
     */
    public int minPlayerForRefugee = 12;

    /**
     * CURSED修饰符刷新最小玩家数
     */
    public int minPlayerForCursed = 12;

    /**
     * SECRETIVE修饰符刷新最小玩家数
     */
    public int minPlayerForSecretive = 12;

    /**
     * KNIGHT修饰符刷新最小玩家数
     */
    public int minPlayerForKnight = 12;

    /**
     * SPLIT_PERSONALITY修饰符刷新最小玩家数
     */
    public int minPlayerForSplitPersonality = 12;

    // ==================== 修饰符概率配置 ====================

    /**
     * MAGNATE修饰符刷新概率（%）
     */
    public int chanceOfMagnate = 50;

    /**
     * TASKMASTER修饰符刷新概率（%）
     */
    public int chanceOfTaskmaster = 30;

    /**
     * ALLERGIST修饰符刷新概率（%）
     */
    public int chanceOfAllergist = 20;

    /**
     * CURSED修饰符刷新概率（%）
     */
    public int chanceOfCursed = 30;

    /**
     * SECRETIVE修饰符刷新概率（%）
     */
    public int chanceOfSecretive = 20;

    /**
     * KNIGHT修饰符刷新概率（%）
     */
    public int chanceOfKnight = 10;

    /**
     * Jeb_修饰符刷新概率（%）
     */
    public int chanceOfJeb = 30;

    /**
     * VIGOROUS修饰符刷新概率（%）
     */
    public int chanceOfVigorous = 80;

    /**
     * UNYIELDING修饰符刷新概率（%）
     */
    public int chanceOfUnyielding = 80;

    /**
     * PARANOID修饰符刷新概率（%）
     */
    public int chanceOfParanoid = 10;

    /**
     * EXPEDITION修饰符刷新概率（%）
     */
    public int chanceOfExpedition = 50;

    /**
     * TAXED修饰符刷新概率（%）
     */
    public int chanceOfTaxed = 20;

    /**
     * INTROVERTED修饰符刷新概率（%）
     */
    public int chanceOfIntroverted = 50;

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

    // ==================== 新修饰符概率配置（叛徒扩展包）====================

    /**
     * 鬼祟修饰符刷新概率（%）
     * 当距离8格内有玩家时，杀手无法透视看到你
     */
    public int chanceOfSneaky = 10;

    /**
     * 黄油手修饰符刷新概率（%）
     * 手枪冷却随机变化
     */
    public int chanceOfButterFingers = 15;

    /**
     * 强壮修饰符刷新概率（%）
     * 35%击退抗性
     */
    public int chanceOfStrong = 80;

    /**
     * 夜猫子修饰符刷新概率（%）
     * 免疫黑暗效果
     */
    public int chanceOfNightOwl = 15;

    /**
     * 慷慨修饰符刷新概率（%）
     * 每1.5分钟给予最近玩家25金币
     */
    public int chanceOfGenerous = 10;

    /**
     * 勇敢修饰符刷新概率（%）
     * 关灯时恢复50%理智
     */
    public int chanceOfBrave = 15;

    /**
     * 工作狂修饰符刷新概率（%）
     * 任务刷新快50%
     */
    public int chanceOfWorkaholic = 20;

    /**
     * 大胃王修饰符刷新概率（%）
     * 每1.5分钟获得苹果，吃饭任务恢复75% san和25金币
     */
    public int chanceOfBigEater = 10;

    /**
     * 狂躁症修饰符刷新概率（%）
     * 任务乱码，无法完成任务，附近完成任务恢复san和金币
     */
    public int chanceOfManic = 5;

    /**
     * 回光返照修饰符刷新概率（%）
     * 被击杀时获得3秒特殊效果后死亡
     */
    public int chanceOfLastGasp = 5;

    /**
     * 起义军修饰符刷新概率（%）
     * 被同阵营误杀时变为叛徒
     */
    public int chanceOfRebel = 25;

    /**
     * 晕血症修饰符刷新概率（%）
     * 看到死亡获得缓慢和反胃
     */
    public int chanceOfHemophobia = 5;

    /**
     * 敛财修饰符刷新概率（%）
     * 死后扣除击杀者40%金币
     */
    public int chanceOfMoneyGrubber = 5;

    /**
     * 素食主义者修饰符刷新概率（%）
     * 肉类获得负面效果，其他食物获得正面效果
     */
    public int chanceOfVegetarian = 20;

    /**
     * 侏儒修饰符刷新概率（%）
     * 尺寸缩小50%
     */
    public int chanceOfDwarf = 3;

    /**
     * 绝境信徒修饰符刷新概率（%）
     * 唯一杀手时获得金币和药水效果，刀冷却减半
     */
    public int chanceOfDesperateFaith = 5;

    /**
     * 吝啬修饰符刷新概率（%）
     * 商店购买返还20%金币
     */
    public int chanceOfStingy = 10;

    /**
     * 腐化修饰符刷新概率（%）
     * 死亡后尸体直接变骷髅
     */
    public int chanceOfCorrupted = 10;

    /**
     * 柔韧修饰符刷新概率（%）
     * 潜行速度提升40%
     */
    public int chanceOfFlexible = 40;

    /**
     * 反牛顿修饰符刷新概率（%）
     * 重力减少20%
     */
    public int chanceOfAntiNewton = 10;

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
     * 静语者最大数量
     */
    public int silencerMax = 1;

    /**
     * 静语者生成概率（%）
     */
    public int chanceOfSilencer = 60;
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
