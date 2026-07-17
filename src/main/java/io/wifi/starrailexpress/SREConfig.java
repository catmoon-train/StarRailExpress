package io.wifi.starrailexpress;

import io.wifi.ConfigCompact.ConfigClassHandler;
import io.wifi.ConfigCompact.annotation.ConfigSync;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.annotation.ConfigEntry.Gui.Tooltip;

import java.util.ArrayList;

/**
 * 写翻译键 at config_translations/lang/zh_cn.json
 * key为text.autoconfig.(Config.name).option.项
 * 如text.autoconfig.starrailexpress.option.enableNoLimitLoversInLoverMode
 */
@Config(name = "starrailexpress")
public class SREConfig implements ConfigData {
    // 存储默认配置值 - 在静态初始化块中设置
    public static ConfigClassHandler<SREConfig> HANDLER = new ConfigClassHandler<>(
            SREConfig.class);

    // 游戏模式设置
    @ConfigEntry.Category(value = "gamemodes")
    public int defaultStartTimeForMurder = 10;

    @ConfigEntry.Category(value = "gamemodes")
    public boolean enableRepairMode = false;
    @ConfigEntry.Category(value = "gamemodes")
    public boolean enableNoLimitLoversInLoverMode = false;
    @ConfigEntry.Category(value = "gamemodes")
    public int volunteerModeVolunteerCount = 3;
    @ConfigEntry.Category(value = "gamemodes")
    public int volunteerModeSelectionTime = 20;
    @ConfigEntry.Category(value = "gamemodes")
    public float loverModeLoversPercent = 1f;
    @ConfigEntry.Category(value = "gamemodes")
    public float refugeeModeRefugeePercent = 0.5f;
    @ConfigEntry.Category(value = "gamemodes")
    public float gamblerModeGamblerPercent = 0.9f;
    @ConfigEntry.Category(value = "gamemodes")
    public int gamblerModeGamblerKillTime = 90;
    @ConfigEntry.Category(value = "gamemodes")
    public int customRoleModeForceSelectTime = 20;

    @ConfigEntry.Category(value = "gamemodes")
    public double antWarPlayerScale = -0.5;
    @ConfigEntry.Category(value = "gamemodes")
    public int antWarPlayerSpeedLvl = 1;
    @ConfigEntry.Category(value = "gamemodes")
    public int antWarClockStopTick = 40;
    @ConfigEntry.Category(value = "gamemodes")
    public int antWarClockCooldownTick = 140;
    @ConfigEntry.Category(value = "gamemodes")
    public int evilWarKillGroupNumber = 7;
    @ConfigEntry.Category(value = "gamemodes")
    public int hideAndSeekRewardKillRemoveTime = 30;
    @ConfigEntry.Category(value = "gamemodes")
    public int hideAndSeekRewardKillAddTime = 10;
    @ConfigEntry.Category(value = "gamemodes")
    public int hideAndSeekBaseTime = 30;
    @ConfigEntry.Category(value = "gamemodes")
    public int hideAndSeekTimePerPlayer = 15;
    @ConfigEntry.Category(value = "gamemodes")
    public double hideAndSeekHiderScale = -0.25;
    @ConfigEntry.Category(value = "gamemodes")
    @Tooltip
    public boolean hideRandomRoleInRoleRotation = true;
    
    @ConfigEntry.Category(value = "gamemodes")
    public boolean roleRotationForceRoleSettings = false;
    // 随机地图设置

    @ConfigEntry.Category(value = "commands")
    public int customRolePermission = 3;
    @ConfigEntry.Category(value = "commands")
    public int startGameRequiredPermission = 2;
    @ConfigEntry.Category(value = "commands")
    public int stopGameRequiredPermission = 3;
    @ConfigEntry.Category(value = "commands")
    public int switchMapRequiredPermission = 2;
    @ConfigEntry.Category(value = "commands")
    public int modifyEnableStatusRequiredPermission = 2;
    @ConfigEntry.Category(value = "commands")
    public int changeRoleRequiredPermission = 3;
    @ConfigEntry.Category(value = "commands")
    public int changeModifierRequiredPermission = 3;
    @ConfigEntry.Category(value = "commands")
    public int forceRoleRequiredPermission = 3;
    @ConfigEntry.Category(value = "commands")
    public int forceModifierRequiredPermission = 3;
    @ConfigEntry.Category(value = "commands")
    public int forceTeamRequiredPermission = 3;
    @ConfigEntry.Category(value = "commands")
    public int spawnInfoConfigRequiredPermission = 3;
    @ConfigEntry.Category(value = "commands")
    public int killRequiredPermission = 3;
    @ConfigEntry.Category(value = "commands")
    public int gameKillRequiredPermission = 3;
    @ConfigEntry.Category(value = "commands")
    public int gameKickRequiredPermission = 2;
    @ConfigEntry.Category(value = "commands")
    public int gameUtilsRequiredPermission = 2;
    @ConfigEntry.Category(value = "commands")
    public int forceResetRequiredPermission = 3;
    @ConfigEntry.Category(value = "commands")
    public int cooldownCommandsRequiredPermission = 2;

    @ConfigEntry.Category(value = "map")
    @Tooltip
    public int mapRandomCount = -1;

    @ConfigEntry.Category(value = "map")
    @Tooltip(count = 3)
    public boolean isLobby = false;

    @ConfigEntry.Category(value = "map")
    @Tooltip(count = 2)
    @ConfigSync(shouldSync = true)
    public boolean shouldOpenDoorHaveCollision = true;

    @ConfigEntry.Category(value = "shop")
    @ConfigSync(shouldSync = true)
    public int knifePrice = 130;
    // 杀手刀「有限耐久」模式：开启后 murder（及继承 murder）模式下，商店购买的刀仅 3 点耐久，
    // 耗尽后不消失但无法使用，需重新购买替换。关闭则恢复为无耐久的普通刀。
    @ConfigEntry.Category(value = "shop")
    @ConfigSync(shouldSync = true)
    @Tooltip(count = 2)
    public boolean knifeDurabilityMode = true;
    @ConfigEntry.Category(value = "shop")
    public boolean knifeBrokenWhenNotAvailable = true;
    @ConfigSync(shouldSync = true)
    @ConfigEntry.Category(value = "shop")
    public int revolverPrice = 285;
    @ConfigSync(shouldSync = true)
    @ConfigEntry.Category(value = "shop")
    public int grenadePrice = 330;
    @ConfigSync(shouldSync = true)
    @ConfigEntry.Category(value = "shop")
    public int stickyGrenadePrice = 300;
    @ConfigSync(shouldSync = true)
    @ConfigEntry.Category(value = "shop")
    public int timedGrenadePrice = 350;
    @ConfigSync(shouldSync = true)
    @ConfigEntry.Category(value = "shop")
    public int psychoModePrice = 400;
    @ConfigSync(shouldSync = true)
    @ConfigEntry.Category(value = "shop")
    public int poisonVialPrice = 80;
    @ConfigSync(shouldSync = true)
    @ConfigEntry.Category(value = "shop")
    public int scorpionPrice = 40;
    @ConfigSync(shouldSync = true)
    @ConfigEntry.Category(value = "shop")
    public int firecrackerPrice = 10;
    @ConfigSync(shouldSync = true)
    @ConfigEntry.Category(value = "shop")
    public int lockpickPrice = 80;
    @ConfigSync(shouldSync = true)
    @ConfigEntry.Category(value = "shop")
    public int crowbarPrice = 35;
    @ConfigSync(shouldSync = true)
    @ConfigEntry.Category(value = "shop")
    public int bodyBagPrice = 100;
    @ConfigSync(shouldSync = true)
    @ConfigEntry.Category(value = "shop")
    public int blackoutPrice = 120;
    @ConfigSync(shouldSync = true)
    @ConfigEntry.Category(value = "shop")
    public int monitorBrokenPrice = 60;
    @ConfigSync(shouldSync = true)
    @ConfigEntry.Category(value = "shop")
    public int shortShotgunPrice = 300;
    @ConfigSync(shouldSync = true)
    @ConfigEntry.Category(value = "shop")
    public int notePrice = 10;
    @ConfigSync(shouldSync = true)
    @ConfigEntry.Category(value = "shop")
    public int sheriffBulletPrice = 55;
    @ConfigSync(shouldSync = true)
    @ConfigEntry.Category(value = "shop")
    public int sheriffBulletMaxCarry = 4;

    // 物品冷却时间配置（秒）- 服务端只读

    @ConfigEntry.Category(value = "cooldowns")
    public int knifeCooldown = 30;
    @ConfigEntry.Category(value = "cooldowns")
    public int revolverCooldown = 15;
    @ConfigEntry.Category(value = "cooldowns")
    public int derringerCooldown = 1;
    @ConfigEntry.Category(value = "cooldowns")
    public int grenadeCooldown = 300;
    @ConfigEntry.Category(value = "cooldowns")
    public int grenadePurchaseCooldown = 30;
    @ConfigEntry.Category(value = "cooldowns")
    public int lockpickCooldown = 180;
    @ConfigEntry.Category(value = "cooldowns")
    public int crowbarCooldown = 45;
    @ConfigEntry.Category(value = "cooldowns")
    public int bodyBagCooldown = 300;
    @ConfigEntry.Category(value = "cooldowns")
    public int psychoModeCooldown = 275;
    @ConfigEntry.Category(value = "cooldowns")
    public int blackoutCooldown = 180;
    @ConfigEntry.Category(value = "cooldowns")
    public int blackoutCooldownGlobal = 40;
    @ConfigEntry.Category(value = "cooldowns")
    public int monitorBrokenCooldown = 180;
    @ConfigEntry.Category(value = "cooldowns")
    public int monitorBrokenCooldownGlobal = 40;
    @ConfigEntry.Category(value = "cooldowns")
    public int psychoGlobalCooldown = 20;
    @ConfigEntry.Category(value = "cooldowns")
    public int sheriffRevolverReloadCooldown = 12;
    // 游戏配置 - 服务端只读

    // 双重人格配置
    @ConfigEntry.Category(value = "modifiers")
    @Tooltip(count = 2)
    public int splitPersonalityMax = 0;

    // Bartender - Glow duration in seconds

    @ConfigSync(shouldSync = true)
    public int bartenderGlowDuration = 40;

    @ConfigSync(shouldSync = true)
    public int furandoruSafeTime = 6 * 60;// 6分钟外安全

    public int safeTimeCooldown = 30;
    public int startingMoney = 100;
    // 杀手保底被动（降到 3/15秒，主要收入改由任务驱动，见 killerTaskIncome）
    public int passiveMoneyAmount = 5;
    public int passiveMoneyInterval = 15;
    // 任意平民/中立完成一个任务时，每个杀手获得的金币（任务驱动收入）
    public int killerTaskIncome = 5;
    // 平民/中立完成一个任务本人获得的金币（不含连击奖励）
    public int civilianTaskReward = 50;
    // 同一小游戏任务点被该玩家使用后的复用冷却（秒），各玩家独立；冷却期间不可再次使用且不显示透视
    public int minigameBlockCooldownSeconds = 200;
    // 小游戏任务独立刷新间隔（秒），与 Mood 任务解耦
    public int minigameTaskIntervalSeconds = 75;
    // 小游戏任务轮换模式：开启后小游戏任务并入 Mood 任务轮换（刷 2~3 个普通任务后替换刷新一个
    // 小游戏任务，不重复刷新），小游戏任务完成额外获得 minigameRotationCoinBonus 金币，
    // 全局任务刷新速率减缓 15%；关闭后恢复独立计时刷新逻辑
    @Tooltip
    public boolean minigameTaskRotationMode = true;
    // 轮换模式下小游戏任务完成时的额外金币奖励
    public int minigameRotationCoinBonus = 15;
    public int moneyPerKill = 100;
    public int grenadeMoneyPerKill = 80;
    public int grenadeMaxMoneyReward = 375;
    public int grenadeMaxHurtPlayers = 8;
    public int psychoModeArmor = 1;
    public int psychoModeDuration = 30;
    public int firecrackerDuration = 15;
    public int blackoutMaxDuration = 25;
    public int monitorBrokenDuration = 30;
    public float blackoutRandomRangePercent = 0.32f;
    public boolean enableAutoTrainReset = false;
    public boolean verboseTrainResetLogs = true;
    public boolean logGameEvent = true;
    public boolean savePlayerBodyItems = true;

    @ConfigEntry.Category(value = "items")
    public boolean enableBrokenGunDropWhenKillerKillsGunHolder = false;
    @ConfigEntry.Category(value = "items")
    public int brokenGunDropChanceWhenKillerKillsGunHolder = 60;
    @ConfigEntry.Category(value = "items")
    public boolean enableBrokenGunDropWhenKillerShootsPlayer = false;
    @ConfigEntry.Category(value = "items")
    public int brokenGunDropChanceWhenKillerShootsPlayer = 60;

    @ConfigSync(shouldSync = true)
    public boolean disablePlayerMountain = true;

    // ===== 通用物证系统（尸检线索下放给所有人）=====
    @ConfigEntry.Category(value = "forensic")
    @ConfigSync(shouldSync = true)
    public boolean enableForensicEvidence = true;
    @ConfigEntry.Category(value = "forensic")
    @ConfigSync(shouldSync = true)
    public boolean forensicShowWeaponCategory = false;
    @ConfigEntry.Category(value = "forensic")
    @ConfigSync(shouldSync = true)
    public boolean forensicShowCorpseFacing = true;
    @ConfigEntry.Category(value = "forensic")
    public boolean forensicBloodTrail = true;
    @ConfigEntry.Category(value = "forensic")
    public int bloodTrackWetDistance = 8;
    @ConfigEntry.Category(value = "forensic")
    public int forensicBloodTrailSeconds = 5;
    @ConfigEntry.Category(value = "forensic")
    @ConfigSync(shouldSync = true)
    public boolean forensicShowDeathOrder = false;
    @ConfigEntry.Category(value = "forensic")
    @ConfigSync(shouldSync = true)
    public boolean forensicShowDragMark = true;
    @ConfigEntry.Category(value = "forensic")
    public boolean forensicDoorMark = false;

    // // 自动切换预设配置 - 游戏开始前自动应用指定预设，留空则不自动切换
    // @Tooltip(count = 3)
    // public String autoPresetName = "";

    public static class AutoPresetInfo {
        public int advanceCount = 0;
        public String presetName = "";

        public AutoPresetInfo() {
        }

        public AutoPresetInfo(String present, int advanceCount) {
            this.advanceCount = advanceCount;
            this.presetName = present;
        }
    }

    @ConfigEntry.Category(value = "presents")
    @Tooltip(count = 2)
    public boolean enableRoundBasedAutoPreset = false;

    // 按游戏轮数自动切换预设配置
    @ConfigEntry.Category(value = "presents")
    @Tooltip
    public ArrayList<AutoPresetInfo> roundBasedPreset = getDefaultAutoPresetInfos();
    @ConfigEntry.Category(value = "presents")
    @Tooltip(count = 3)
    public String roundBasedPresetAllRoles = "";
    // 当前已进行的游戏轮数（自动维护，勿手动修改）
    @ConfigEntry.Category(value = "presents")
    public int roundBasedCurrentRound = 0;
    // 当前正在使用的预设名称（自动维护，反映当前阶段）
    @ConfigEntry.Category(value = "presents")
    public String roundBasedCurrentPreset = "";

    // 玩家数据设置
    @ConfigEntry.Category(value = "stats")
    public boolean isStatsEnabled = true;
    @ConfigEntry.Category(value = "stats")
    public boolean isStatsSyncEnabled = true;
    @ConfigEntry.Category(value = "stats")
    public boolean isTeammedStatsSyncEnabled = true;
    @ConfigEntry.Category(value = "stats")
    public boolean isDetailedStatsSyncEnabled = false;
    @ConfigEntry.Category(value = "sync")
    public boolean mysqlPlayerSyncEnabled = false;
    @ConfigEntry.Category(value = "sync")
    public String mysqlSyncHost = "127.0.0.1";
    @ConfigEntry.Category(value = "sync")
    public int mysqlSyncPort = 3306;
    @ConfigEntry.Category(value = "sync")
    public String mysqlSyncDatabase = "starrailexpress";
    @ConfigEntry.Category(value = "sync")
    public String mysqlSyncUsername = "root";
    @ConfigEntry.Category(value = "sync")
    public String mysqlSyncPassword = "";
    @ConfigEntry.Category(value = "sync")
    public String mysqlSyncTablePrefix = "sre_";
    @ConfigEntry.Category(value = "sync")
    public boolean mysqlSyncUseSsl = false;
    @ConfigEntry.Category(value = "sync")
    public int mysqlSyncPoolSize = 4;
    @ConfigEntry.Category(value = "sync")
    public int mysqlSyncConnectTimeoutMs = 5000;

    @ConfigEntry.Category(value = "sponsor")
    @Tooltip(count = 2)
    public String sponsorListUrl = "";

    @ConfigEntry.Category(value = "progression")
    public boolean enableProgressionSystem = false;
    @ConfigEntry.Category(value = "progression")
    public boolean progressionSyncServerEnabled = false;
    @ConfigEntry.Category(value = "progression")
    public boolean enableWeeklyTasks = true;
    @ConfigEntry.Category(value = "progression")
    public int dailyTaskCount = 6;
    @ConfigEntry.Category(value = "progression")
    public int weeklyTaskCount = 6;
    // 皮肤设置
    @ConfigEntry.Category(value = "skin")
    public boolean isItemSkinEnabled = true;
    @ConfigEntry.Category(value = "skin")
    public boolean isItemSkinManagementEnabled = false;

    @ConfigEntry.Category(value = "skin")
    public boolean itemSkinSyncServerEnabled = false;
    // AFK设置

    @ConfigEntry.Category(value = "afk")
    public boolean afkKickEnabled = true; // 是否启用挂机踢出功能
    @ConfigEntry.Category(value = "afk")
    public boolean afkDeathEnabled = true; // 是否启用挂机死亡功能
    @ConfigEntry.Category(value = "afk") // 3秒到20分钟
    public int afkThresholdSeconds = (int) (4.5 * 60); // 5分钟
    @ConfigEntry.Category(value = "afk") // 3秒到10分钟
    public int afkDeathSeconds = (int) (5 * 60); // 5分钟
    @ConfigEntry.Category(value = "afk") // 1.5秒到120秒
    public int afkWarningSeconds = 4 * 60; // 4分钟时开始警告
    @ConfigEntry.Category(value = "afk") // 1秒到30秒
    public int afkSleepySeconds = 3 * 60; // 3分钟时开始困倦效果

    // 队友击杀违规检测配置
    @ConfigEntry.Category(value = "friendly_teammate_kill")
    public boolean teamKillViolationEnabled = false;
    @ConfigEntry.Category(value = "friendly_teammate_kill")
    public int teamKillViolationThreshold = 2; // 窗口内队友击杀次数阈值
    @ConfigEntry.Category(value = "friendly_teammate_kill")
    public int teamKillViolationWindowSeconds = 60; // 检测时间窗口（秒）
    @ConfigEntry.Category(value = "friendly_teammate_kill")
    public String teamKillViolationMcFunction = "starrailexpress:teamkill_violation"; // 触发后执行的 mcfunction

    public boolean enableRoster = true;

    public static boolean isUltraPerfMode() {
        return SREClientConfig.instance().ultraPerfMode;
    }

    /**
     * 重新加载配置文件
     * 服务端：只从文件读取，不修改
     * 客户端：可以通过UI修改
     */
    public void reload() {
        HANDLER.load();
    }

    /**
     * 重置配置为默认值
     * 通过精确修改配置文件内容来实现，不删除文件
     */
    public void reset() {
        HANDLER.reset();
    }

    /**
     * 接口不需要了
     */
    public void init() {
    }

    public static SREConfig instance() {
        return HANDLER.instance();
    }

    public ArrayList<AutoPresetInfo> getDefaultAutoPresetInfos() {
        ArrayList<AutoPresetInfo> arr = new ArrayList<>();
        arr.add(new AutoPresetInfo("low_level", 3));
        arr.add(new AutoPresetInfo("medium_level", 5));
        arr.add(new AutoPresetInfo("high_level", 5));
        return arr;
    }
}
