package io.wifi.starrailexpress;

import io.wifi.ConfigCompact.ConfigClassHandler;
import io.wifi.ConfigCompact.annotation.ConfigSync;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.annotation.ConfigEntry.Gui.Tooltip;

@Config(name = "starrailexpress")
public class SREConfig implements ConfigData {
    // 存储默认配置值 - 在静态初始化块中设置
    public static ConfigClassHandler<SREConfig> HANDLER = new ConfigClassHandler<>(
            SREConfig.class);
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
    public int notePrice = 10;

    // 物品冷却时间配置（秒）- 服务端只读

    @ConfigEntry.Category(value = "cooldowns")
    public int knifeCooldown = 30;
    @ConfigEntry.Category(value = "cooldowns")
    public int revolverCooldown = 10;
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

    // 游戏配置 - 服务端只读

    // Bartender - Glow duration in seconds

    public int safeTimeCooldown = 30;
    public int bartenderGlowDuration = 40;
    public int startingMoney = 100;
    public int passiveMoneyAmount = 5;
    public int passiveMoneyInterval = 10;
    public int moneyPerKill = 100;
    public int psychoModeArmor = 1;
    public int psychoModeDuration = 30;
    public int firecrackerDuration = 15;
    public int blackoutMinDuration = 15;
    public int blackoutMaxDuration = 30;
    public boolean enableAutoTrainReset = false;
    public boolean verboseTrainResetLogs = true;

    // 自动切换预设配置 - 游戏开始前自动应用指定预设，留空则不自动切换
    @Tooltip
    public String autoPresetName = "";

    // 玩家数据设置
    @ConfigEntry.Category(value = "stats")
    public boolean isStatsEnabled = true;
    @ConfigEntry.Category(value = "stats")
    public boolean isStatsSyncEnabled = true;
    @ConfigEntry.Category(value = "stats")
    public boolean isTeammedStatsSyncEnabled = true;
    @ConfigEntry.Category(value = "stats")
    public boolean isDetailedStatsSyncEnabled = false;
    // 皮肤设置
    @ConfigEntry.Category(value = "skin")
    public boolean isItemSkinEnabled = true;
    @ConfigEntry.Category(value = "skin")
    public boolean isItemSkinManagementEnabled = false;
    @ConfigEntry.Category(value = "skin")
    public String itemSkinSyncServerHost = "";
    @ConfigEntry.Category(value = "skin")
    public int itemSkinSyncServerPort = 8080;
    @ConfigEntry.Category(value = "skin")
    public String itemSkinSyncServerKey = "";
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

    public boolean isUltraPerfMode() {
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
}
