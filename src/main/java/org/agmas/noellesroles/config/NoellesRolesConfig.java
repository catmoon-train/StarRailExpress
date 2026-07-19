package org.agmas.noellesroles.config;

import io.wifi.ConfigCompact.ConfigClassHandler;
import io.wifi.ConfigCompact.annotation.ConfigSync;
import io.wifi.starrailexpress.game.GameConstants;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
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
     * Areas that will spawn Ma Chen Xu and Guest Ghost. Use | to split maps
     */

    public ArrayList<String> maChenXuMaps = new ArrayList<>(List.of("areas_qiyucun"));

    /**
     * Areas that will spawn big-map roles (for example Swast). Use | to split maps
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
     * Areas that will spawn trap-map roles (Adventurer)
     */
    public ArrayList<String> trapRolesMaps = new ArrayList<>(List.of("areas_shamo"));

    /**
     * Areas that will spawn horse-riding roles (Tamer, Hunter)
     */
    public ArrayList<String> horseRolesMaps = new ArrayList<>(List.of("chaoshengzhilu"));

    /**
     * Role - The chance of egg roles
     */

    @ConfigEntry.Category(value = "detail")
    public int chanceOfTouhouRoles = 40;
    @ConfigEntry.Category(value = "detail")
    public int minPlayerForTouhouRoles = 12;
    @ConfigEntry.Category(value = "detail")
    public int chanceOfEggRoles = 15;
    @ConfigEntry.Category(value = "detail")
    public int minPlayerForEggRoles = 12;
    // ==================== 角色刷新概率配置 ====================
    // 普通概率配置（0-100，百分比）

    /**
     * 特殊警卫配置
     */
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
     * Nostalgist (怀旧者) - Interval in seconds between passive coin payouts while in the back world
     */
    public int nostalgistBackWorldIncomeInterval = 60;

    /**
     * Nostalgist (怀旧者) - Coins granted each passive payout while in the back world
     */
    public int nostalgistBackWorldIncomeAmount = 35;

    /**
     * Nostalgist (怀旧者) - Coins granted when leaving the back world (manual or forced collapse)
     */
    public int nostalgistCollapseReward = 100;

    /**
     * Nostalgist (怀旧者) - Wind-up time in ticks before manually leaving the back world (20 ticks = 1s, default 1.5s)
     */
    public int nostalgistCollapseWindupTicks = 30;

    /**
     * Jade General - Flying kick displacement distance in blocks
     */
    public int jadeGeneralDashBlocks = 5;

    /**
     * Jade General - Knockback distance applied to a kicked target in blocks
     */
    public int jadeGeneralKnockbackBlocks = 2;

    /**
     * Jade General - Stun seconds when the knocked-back target hits a wall
     */
    public int jadeGeneralStunCollideSeconds = 4;

    /**
     * Jade General - Stun seconds when the knocked-back target does not hit a wall
     */
    public int jadeGeneralStunSeconds = 2;

    /**
     * Ghost Eye / 杨间 (鬼眼·杨间) - Passive scan interval in seconds
     */
    public int ghostEyeScanInterval = 16;

    /**
     * Ghost Eye - Domain (诡域) skill cooldown in seconds
     */
    public int ghostEyeDomainCooldown = 70;

    /**
     * Ghost Eye - Domain duration in seconds
     */
    public int ghostEyeDomainDuration = 6;

    /**
     * Ghost Eye - Domain radius in blocks
     */
    public int ghostEyeDomainRadius = 12;



    // ==================== Diviner (占卜家) ====================
    /** Diviner - Divination cooldown in seconds */
    public int divinerCooldown = 60;
    /** Diviner - Crystal ball targeting range in blocks */
    public double divinerRange = 4.0;
    /** Diviner - Crystal ball shop price (coins) */
    public int divinerCrystalBallPrice = 300;

    // ==================== Photographer (摄影师) 画框传送 ====================
    /** Photographer - 每局最多购买画框次数 */
    public int photographerFrameMaxBuy = 2;
    /** Photographer - 穿越画框赋予的失明秒数 */
    public int photographerFrameBlindSeconds = 3;
    /** Photographer - 穿越画框后的冷却秒数 */
    public int photographerFrameCooldownSeconds = 3;
    /** Photographer - 触发穿越的画框碰撞箱外扩距离（方块） */
    public double photographerFrameTriggerInflate = 0.25;
    /** Photographer - 单个画框最多可传送玩家的次数（用尽后画框失效） */
    public int photographerFrameMaxTeleports = 8;
    /** Photographer - 画框传送的最大水平距离（方块，<=0 表示不限制） */
    public double photographerFrameMaxDistance = 256.0;
    /** Photographer - 画框传送的最大垂直(Y轴)距离（方块，<=0 表示不限制） */
    public double photographerFrameMaxYDistance = 12.0;

    // ==================== Delayer (滞时鬼) ====================
    /** Delayer - Rewind skill: seconds the time anchor lasts before the automatic rewind */
    public int delayerRewindDelaySeconds = 15;
    /** Delayer - Rewind skill cooldown in seconds */
    public int delayerRewindCooldown = 120;
    /** Delayer - Rewind skill coin cost */
    public int delayerRewindCost = 75;
    /** Delayer - Duration (seconds) of the daze/shader filter applied to everyone else on rewind */
    public int delayerDazeSeconds = 1;
    /** Delayer - Path sampling interval (seconds) while anchored, used to trace the rewind route */
    public int delayerPathSampleSeconds = 2;
    /** Delayer - Rolling path buffer length (seconds); older samples are dropped */
    public int delayerPathRecordSeconds = 30;
    /** Delayer - Ticks spent smoothly traversing each recorded path segment during the rewind */
    public int delayerReturnSegmentTicks = 6;
    /** Delayer - Prone + immobilize duration (seconds) when hit while the anchor is still active */
    public int delayerDownedSeconds = 30;

    // ==================== Dream (梦魇) ====================
    /** Dream - 全员虚拟血量上限（Dream 斧头攻击扣此血量，归零死亡） */
    public int dreamMaxHealth = 20;
    /** Dream - 脱战多少秒后开始缓慢回血 */
    public int dreamHealthRegenDelaySeconds = 30;
    /** Dream - 铁斧平A伤害 */
    public int dreamAxeDamage = 9;
    /** Dream - 铁斧跳劈伤害 */
    public int dreamAxeCritDamage = 12;
    /** Dream - 狂暴时铁斧平A伤害 */
    public int dreamAxeBerserkDamage = 12;
    /** Dream - 狂暴时铁斧跳劈伤害 */
    public int dreamAxeBerserkCritDamage = 20;
    /** Dream - 铁斧命中眩晕秒数（狂暴时翻倍见 dreamBerserkStunSeconds） */
    public double dreamAxeStunSeconds = 1.0;
    /** Dream - 狂暴时铁斧命中眩晕秒数 */
    public double dreamBerserkStunSeconds = 2.0;
    /** Dream - 巨幕面具冷却秒数（狂暴时长跟随疯魔 Psycho 计时器） */
    public int dreamMaskCooldownSeconds = 120;
    /** Dream - 狂暴时"看到 Dream"的最大判定距离（格），受障碍物遮挡影响 */
    public double dreamFearSightDistance = 30.0;
    /** Dream - "看到 Dream"的视线容错半径（格）：Dream 到玩家视线射线的垂距在此范围内即算看到 */
    public double dreamFearSightRadius = 4.0;
    /** Dream - 制酒技能冷却秒数 */
    public int dreamBrewCooldownSeconds = 120;
    /** Dream - 酒的隐身持续秒数（期间无法攻击、无法受伤） */
    public int dreamWineDurationSeconds = 10;
    /** Dream - 船强制乘坐半径（格） */
    public double dreamBoatRadius = 3.0;
    /** Dream - 船存在秒数 */
    public int dreamBoatDurationSeconds = 10;
    /** Dream - 船使用冷却秒数 */
    public int dreamBoatCooldownSeconds = 60;
    /** Dream - 范围关灯半径（格） */
    public int dreamBlackoutRadius = 30;
    /** Dream - 杀死皮革噶的获得的金币（"十万美金"） */
    public int dreamLeatherPigReward = 300;
    /** Dream - 铁斧价格 */
    public int dreamAxePrice = 135;
    /** Dream - 巨幕面具价格 */
    public int dreamMaskPrice = 350;
    /** Dream - 钻石镐价格 */
    public int dreamPickaxePrice = 90;
    /** Dream - 船价格 */
    public int dreamBoatPrice = 100;
    /** Dream - 范围关灯价格 */
    public int dreamBlackoutPrice = 150;

    // ==================== Wizard (巫师) ====================
    /** Wizard - Max mana (魔素) capacity */
    public int wizardMaxMana = 500;
    public int wizardStartingMana = 120;
    /** Wizard - Mana gained per coin of income (all coins convert to mana) */
    public int wizardManaPerCoin = 1;
    /** Wizard - Passive mana regen per second */
    /** Wizard - Staff left-click knockback strength */
    public double wizardStaffKnockback = 1.2;
    /** Wizard - Fire arrow max range in blocks */
    public double wizardFireArrowRange = 30.0;
    /** Wizard - Max players a single fire arrow can pierce */
    public int wizardFireArrowMaxPierce = 2;
    /** Wizard - Fire arrow delayed death seconds after enough hits */
    public int wizardFireArrowDeathDelaySeconds = 3;
    /** Wizard - Fire arrow (staff projectile) cooldown in seconds between shots */
    public double wizardFireArrowCooldownSeconds = 2.0;
    /** Wizard - Armor spell minimum mana to cast */
    public int wizardArmorMinMana = 200;
    /** Wizard - Granted shield (armor) lifetime in seconds before it expires */
    public int wizardShieldDurationSeconds = 120;
    /** Wizard - Frost spell minimum mana to cast */
    public int wizardFrostMinMana = 200;
    /** Wizard - Frost spell freeze duration in seconds */
    public int wizardFrostSeconds = 4;
    /** Wizard - Frost spell cooldown in seconds */
    public int wizardFrostCooldownSeconds = 90;
    /** Wizard - Frost spell effect range in blocks */
    public double wizardFrostRange = 8.0;
    /** Wizard - Shadow spell mana cost */
    public int wizardShadowCost = 150;
    /** Wizard - Shadow spell blindness duration in seconds */
    public int wizardShadowSeconds = 6;
    /** Wizard - Shadow spell cooldown in seconds */
    public int wizardShadowCooldownSeconds = 90;
    /** Wizard - Explosion! spell minimum mana to cast */
    public int wizardExplosionMinMana = 400;
    /** Wizard - Percent of current mana spent by Explosion! */
    public int wizardExplosionManaPercentCost = 85;
    /** Wizard - Explosion! spell cooldown in seconds */
    public int wizardExplosionCooldownSeconds = 180;
    /** Wizard - Nine-ring fireball max travel range in blocks */
    public double wizardFireballRange = 25.0;
    /** Wizard - Nine-ring fireball explosion radius in blocks */
    public double wizardFireballRadius = 3.5;
    /** Wizard - Max players killed by one nine-ring fireball */
    public int wizardFireballMaxKills = 3;
    /** Wizard - Mana gained from drinking a potion */
    public int wizardPotionManaGain = 150;
    /** Wizard - Potion attack-immunity window in seconds */
    public int wizardPotionImmuneSeconds = 60;
    /** Wizard - Blink spell flat mana cost */
    public int wizardBlinkCost = 80;
    /** Wizard - Blink spell cooldown in seconds */
    public int wizardBlinkCooldownSeconds = 15;
    /** Wizard - Blink spell max distance in blocks */
    public double wizardBlinkDistance = 8.0;

    // ==================== Undead Lord (亡灵之主) ====================
    /** Undead Lord - Raise-from-corpse skill cooldown in seconds */
    public int undeadLordReviveCooldownSeconds = 45;
    /** Undead Lord - Max undead raised from corpses that can coexist (skill cap) */
    public int undeadLordMaxActive = 3;
    /** Undead Lord - Absolute hard cap of simultaneous undead (incl. conversions/charm) */
    public int undeadLordHardCap = 8;
    /** Undead Lord - Infection decay per second (percent) */
    public double undeadLordInfectionDecayPerSecond = 2.0;
    /** Undead Lord - Delay (seconds) between reaching 100% infection and death */
    public int undeadLordInfectionDeathDelaySeconds = 3;
    /** Undead Lord - Plague Fog duration in seconds */
    public int undeadLordFogSeconds = 15;
    /** Undead Lord - Plague Fog radius in blocks */
    public double undeadLordFogRadius = 5.0;
    /** Undead Lord - Plague Fog infection added per second to players inside */
    public double undeadLordFogInfectPerSecond = 8.0;
    /** Undead Lord - Soul Summon Charm temporary undead lifetime in seconds */
    public int undeadLordCharmLifetimeSeconds = 45;
    /** Undead Lord - Soul Summon Charm cooldown in seconds */
    public int undeadLordCharmCooldownSeconds = 90;
    /** Undead Lord - Soul Summon Charm shop price */
    public int undeadLordCharmPrice = 200;
    /** Undead Lord - Infection Amplifier duration in seconds (undead infection doubled + undead haste) */
    public int undeadLordAmpSeconds = 60;
    /** Undead Lord - Coins awarded each time infection is successfully injected (bone staff / undead attack); 0 disables */
    public int undeadLordInfectionCoinReward = 25;
    /** Undead Lord - Bone Staff durability (number of hits) */
    public int undeadLordBoneStaffDurability = 5;
    /** Undead Lord - Bone Staff recharge time in seconds after durability is depleted (refills to full, never breaks) */
    public int undeadLordBoneStaffRechargeSeconds = 40;
    /** Undead Lord - Bone Staff infection added per hit (0~100) */
    public double undeadLordBoneStaffInfection = 24.0;
    /** Undead Lord - Bone Staff shop price */
    public int undeadLordBoneStaffPrice = 130;
    /** Undead Lord - Real damage each undead deals to a player per attack (HP, 0=disabled) */
    public double undeadLordUndeadAttackDamage = 1.0;

    /** 格罗赛尔游记 (Groselle Travelog) - 放逐目标坐标 X */
    public int grosellTravelogBanishX = -100;
    /** 格罗赛尔游记 (Groselle Travelog) - 放逐目标坐标 Y */
    public int grosellTravelogBanishY = 50;
    /** 格罗赛尔游记 (Groselle Travelog) - 放逐目标坐标 Z */
    public int grosellTravelogBanishZ = 21000;
    /** 格罗赛尔游记 - 蓄力时间（秒），蓄满后才会放逐/召回目标 */
    public double grosellTravelogChargeSeconds = 1.0;
    /** 格罗赛尔游记 - 自动回归时间（秒），放逐后多久被放逐者自动回归 */
    public int grosellTravelogAutoReturnSeconds = 60;
    /** 格罗赛尔游记 - 冷却（秒），被放逐者释放后进入冷却 */
    public int grosellTravelogCooldownSeconds = 75;
    /** 格罗赛尔游记 - 瞄准放逐目标的最大距离（格） */
    public double grosellTravelogRange = 6;

    /** Leon (里昂) - Combat skill (kick) cooldown in seconds */
    public int leonKickCooldown = 12;
    /** Leon (里昂) - Combat skill (kick) knockback strength (larger = farther) */
    public double leonKickKnockback = 1.5;
    /** Leon (里昂) - Combat skill (kick) slowdown duration in seconds */
    public double leonKickSlowSeconds = 2.5;
    /** Leon (里昂) - Combat skill (kick) reach in blocks */
    public double leonKickRange = 3.5;
    /** Leon (里昂) - Alive player count threshold to grant the blue herb */
    public int leonBlueHerbAtPlayers = 6;
    /** Leon (里昂) - Alive player count threshold to grant the red herb */
    public int leonRedHerbAtPlayers = 3;

    /** 宿命的罪人 - 不同死因数量下限（&lt;=16 人时） */
    public int doomedSinnerMinReasons = 4;
    /** 宿命的罪人 - 不同死因数量上限（&gt;=32 人时） */
    public int doomedSinnerMaxReasons = 6;
    /** 宿命的罪人 - 同一死因死亡多少次后彻底死亡 */
    public int doomedSinnerSamePermanentCount = 3;
    /** 宿命的罪人 - 复活后留下的尸体多少秒后消失 */
    public int doomedSinnerCorpseDespawnSeconds = 15;
    /** 宿命的罪人 - 命运的启示技能的近距离接触范围（格） */
    public double doomedSinnerRevealRange = 4.0;
    /** 宿命的罪人 - 复活 / 重启后的短暂无敌时间（秒） */
    public int doomedSinnerReviveInvincibleSeconds = 3;

    /** Morphling (变形者) - Knife dummy skill cooldown in seconds */
    public int morphlingDummyCooldown = 90;
    /** Morphling (变形者) - Knife dummy max lifetime / forward-rush time in seconds */
    public int morphlingDummyLifetime = 10;

    /**
     * Phantom - Invisibility duration in seconds
     */

    public int phantomInvisibilityDuration = 30;

    /**
     * Phantom - Invisibility cooldown in seconds
     */

    public int phantomInvisibilityCooldown = 90;



    /**
     * Vulture - Eat body cooldown in seconds
     */

    public int vultureEatCooldown = 3;

    /**
     * Swapper - Swap cooldown in seconds
     */

    public int swapperSwapCooldown = 60;

    /** Swapper - G 键瞬移交换：与正前方目标交换位置的冷却（秒） */
    public int swapperFrontSwapCooldown = 120;
    /** Swapper - G 键瞬移交换：可作用的最大距离（格） */
    public double swapperFrontSwapRange = 10.0;

    // ==================== Noisemaker (大嗓门) 冲击波 ====================
    /** 大嗓门 - 冲击波击退前方玩家的作用距离（格） */
    public double noisemakerShockwaveRange = 8.0;
    /** 大嗓门 - 冲击波水平击退强度 */
    public double noisemakerShockwaveKnockback = 1.4;
    /** 大嗓门 - 冲击波眩晕（定身）秒数 */
    public int noisemakerStunSeconds = 2;

    /**
     * Manipulator - Control target cooldown in seconds
     */

    public int manipulatorCooldown = 80;

    /**
     * Manipulator - Max distance (blocks) allowed to start controlling a marked target
     */
    public int manipulatorMaxControlRange = 100;

    /**
     * Manipulator - Total control duration in seconds
     */
    public int manipulatorControlSeconds = 30;

    /**
     * Manipulator - Seconds of uninterrupted staring required to mark a target
     */
    public int manipulatorMarkSeconds = 4;

    /**
     * Manipulator - Max distance (blocks) to stare-mark a target
     */
    public int manipulatorMarkRange = 20;

    /**
     * Manipulator - Nausea duration (seconds) applied to the target on a successful mark
     */
    public int manipulatorMarkNauseaSeconds = 4;

    /**
     * Manipulator - Coins rewarded for a successful mark
     */
    public int manipulatorMarkReward = 15;

    /**
     * Manipulator - Coins rewarded when the controlled target dies during control
     */
    public int manipulatorTargetDeathReward = 75;

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
