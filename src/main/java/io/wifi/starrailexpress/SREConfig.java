package io.wifi.starrailexpress;

import io.wifi.ConfigCompact.ConfigClassHandler;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "starrailexpress")
public class SREConfig implements ConfigData {
    // 存储默认配置值 - 在静态初始化块中设置
    public static ConfigClassHandler<SREConfig> HANDLER = new ConfigClassHandler<>(
            SREConfig.class);
    // 客户端专用配置 - 仅在客户端环境生效

    @ConfigEntry.Category(value = "client")
    @ConfigEntry.Gui.Tooltip
    public static boolean ultraPerfMode = false;

    @ConfigEntry.Category(value = "client")
    public static boolean disableScreenShake = true;

    @ConfigEntry.Category(value = "client")
    public static boolean disableStaminaBarSmoothing = false;

    @ConfigEntry.Category(value = "client")
    public static boolean enableSecurityCameraHUD = true; // 启用安全摄像头HUD显示

    // 随机地图设置
    @ConfigEntry.Category(value = "map")
    public static int mapRandomCount = -1;

    @ConfigEntry.Category(value = "map")
    public static boolean isLobby = false;

    @ConfigEntry.Category(value = "shop")
    public static int knifePrice = 130;
    @ConfigEntry.Category(value = "shop")
    public static int revolverPrice = 285;
    @ConfigEntry.Category(value = "shop")
    public static int grenadePrice = 330;
    @ConfigEntry.Category(value = "shop")
    public static int psychoModePrice = 400;
    @ConfigEntry.Category(value = "shop")
    public static int poisonVialPrice = 80;
    @ConfigEntry.Category(value = "shop")
    public static int scorpionPrice = 40;
    @ConfigEntry.Category(value = "shop")
    public static int firecrackerPrice = 10;
    @ConfigEntry.Category(value = "shop")
    public static int lockpickPrice = 80;
    @ConfigEntry.Category(value = "shop")
    public static int crowbarPrice = 35;
    @ConfigEntry.Category(value = "shop")
    public static int bodyBagPrice = 100;
    @ConfigEntry.Category(value = "shop")
    public static int blackoutPrice = 100;
    @ConfigEntry.Category(value = "shop")
    public static int notePrice = 10;

    // 物品冷却时间配置（秒）- 服务端只读

    @ConfigEntry.Category(value = "cooldowns")
    public static int knifeCooldown = 30;
    @ConfigEntry.Category(value = "cooldowns")
    public static int revolverCooldown = 10;
    @ConfigEntry.Category(value = "cooldowns")
    public static int derringerCooldown = 1;
    @ConfigEntry.Category(value = "cooldowns")
    public static int grenadeCooldown = 300;
    @ConfigEntry.Category(value = "cooldowns")
    public static int lockpickCooldown = 180;
    @ConfigEntry.Category(value = "cooldowns")
    public static int crowbarCooldown = 45;
    @ConfigEntry.Category(value = "cooldowns")
    public static int bodyBagCooldown = 300;
    @ConfigEntry.Category(value = "cooldowns")
    public static int psychoModeCooldown = 275;
    @ConfigEntry.Category(value = "cooldowns")
    public static int blackoutCooldown = 180;

    // 游戏配置 - 服务端只读

    // Bartender - Glow duration in seconds
    @ConfigEntry.Category(value = "game")
    public static int bartenderGlowDuration = 40;
    @ConfigEntry.Category(value = "game")
    public static int startingMoney = 100;
    @ConfigEntry.Category(value = "game")
    public static int passiveMoneyAmount = 5;
    @ConfigEntry.Category(value = "game")
    public static int passiveMoneyInterval = 10;
    @ConfigEntry.Category(value = "game")
    public static int moneyPerKill = 100;
    @ConfigEntry.Category(value = "game")
    public static int psychoModeArmor = 1;
    @ConfigEntry.Category(value = "game")
    public static int psychoModeDuration = 30;
    @ConfigEntry.Category(value = "game")
    public static int firecrackerDuration = 15;
    @ConfigEntry.Category(value = "game")
    public static int blackoutMinDuration = 15;
    @ConfigEntry.Category(value = "game")
    public static int blackoutMaxDuration = 30;
    @ConfigEntry.Category(value = "game")
    public static boolean enableAutoTrainReset = false;
    @ConfigEntry.Category(value = "game")
    public static boolean verboseTrainResetLogs = true;

    // AFK设置

    @ConfigEntry.Category(value = "skin")
    public static String itemSkinSyncServerHost = "";
    @ConfigEntry.Category(value = "skin")
    public static int itemSkinSyncServerPort = 8080;
    @ConfigEntry.Category(value = "skin")
    public static String itemSkinSyncServerKey = "";
    @ConfigEntry.Category(value = "skin")
    public static boolean itemSkinSyncServerEnabled = false;

    @ConfigEntry.Category(value = "afk") // 3秒到20分钟
    public static int afkThresholdSeconds = (int) (4.5 * 60); // 5分钟
    @ConfigEntry.Category(value = "afk") // 3秒到10分钟
    public static int afkDeathSeconds = (int) (5 * 60); // 5分钟
    @ConfigEntry.Category(value = "afk") // 1.5秒到120秒
    public static int afkWarningSeconds = 4 * 60; // 4分钟时开始警告
    @ConfigEntry.Category(value = "afk") // 1秒到30秒
    public static int afkSleepySeconds = 3 * 60; // 3分钟时开始困倦效果

    public static boolean isUltraPerfMode() {
        return ultraPerfMode;
    }

    /**
     * 重新加载配置文件
     * 服务端：只从文件读取，不修改
     * 客户端：可以通过UI修改
     */
    public static void reload() {
        HANDLER.load();
    }

    /**
     * 重置配置为默认值
     * 通过精确修改配置文件内容来实现，不删除文件
     */
    public static void reset() {
        HANDLER.reset();
    }

    /**
     * 接口不需要了
     */
    public static void init() {
    }
}
