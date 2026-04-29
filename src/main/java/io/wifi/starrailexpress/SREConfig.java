package io.wifi.starrailexpress;

import io.wifi.ConfigCompact.ConfigClassHandler;
import io.wifi.ConfigCompact.annotation.ConfigSync;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.annotation.ConfigEntry.Gui.Tooltip;

import java.util.ArrayList;

@Config(name = "starrailexpress")
public class SREConfig implements ConfigData {
    // 存储默认配置值 - 在静态初始化块中设置
    public static ConfigClassHandler<SREConfig> HANDLER = new ConfigClassHandler<>(
            SREConfig.class);


    // 游戏模式设置
    @ConfigEntry.Category(value = "gamemodes")
    public boolean enableNoLimitLoversInLoverMode = false;
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
    // 随机地图设置
    @ConfigEntry.Category(value = "map")
    @Tooltip
    public int mapRandomCount = -1;

    @ConfigEntry.Category(value = "map")
    @Tooltip(count = 3)
    public boolean isLobby = false;

    @ConfigEntry.Category(value = "shop")
    @ConfigSync(shouldSync = true)
    public int knifePrice = 130;
    @ConfigSync(shouldSync = true)
    @ConfigEntry.Category(value = "shop")
    public int revolverPrice = 285;
    @ConfigSync(shouldSync = true)
    @ConfigEntry.Category(value = "shop")
    public int grenadePrice = 330;
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
    public int blackoutPrice = 100;
    @ConfigSync(shouldSync = true)
    @ConfigEntry.Category(value = "shop")
    public int monitorBrokenPrice = 60;
    @ConfigSync(shouldSync = true)
    @ConfigEntry.Category(value = "shop")
    public int shortShotgunPrice = 300;
    @ConfigSync(shouldSync = true)
    @ConfigEntry.Category(value = "shop")
    public int notePrice = 10;

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
    // 游戏配置 - 服务端只读

    // Bartender - Glow duration in seconds

    @ConfigSync(shouldSync = true)
    public int bartenderGlowDuration = 40;

    @ConfigSync(shouldSync = true)
    public int furandoruSafeTime = 6 * 60;// 6分钟外安全

    public int safeTimeCooldown = 30;
    public int startingMoney = 100;
    public int passiveMoneyAmount = 5;
    public int passiveMoneyInterval = 10;
    public int moneyPerKill = 100;
    public int psychoModeArmor = 1;
    public int psychoModeDuration = 30;
    public int firecrackerDuration = 15;
    public int blackoutMaxDuration = 25;
    public int monitorBrokenDuration = 30;
    public float blackoutRandomRangePercent = 0.32f;
    public boolean enableAutoTrainReset = false;
    public boolean verboseTrainResetLogs = true;
    public boolean logGameEvent = false;

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
    public boolean enableRoundBasedAutoPreset = true;

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


    @ConfigEntry.Category(value = "clue")
    public int clueBookMaxSelections = 4;
    @ConfigEntry.Category(value = "clue")
    public int clueBookshelfX = 0;
    @ConfigEntry.Category(value = "clue")
    public int clueBookshelfY = 64;
    @ConfigEntry.Category(value = "clue")
    public int clueBookshelfZ = 0;
    @ConfigEntry.Category(value = "clue")
    public String clueBookshelfDimension = "minecraft:overworld";
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

    @ConfigEntry.Category(value = "afk") // 3秒到20分钟
    public int afkThresholdSeconds = (int) (4.5 * 60); // 5分钟
    @ConfigEntry.Category(value = "afk") // 3秒到10分钟
    public int afkDeathSeconds = (int) (5 * 60); // 5分钟
    @ConfigEntry.Category(value = "afk") // 1.5秒到120秒
    public int afkWarningSeconds = 4 * 60; // 4分钟时开始警告
    @ConfigEntry.Category(value = "afk") // 1秒到30秒
    public int afkSleepySeconds = 3 * 60; // 3分钟时开始困倦效果

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
