package io.wifi.starrailexpress;

import io.wifi.starrailexpress.game.GameConstants;
import eu.midnightdust.lib.config.MidnightConfig;
import net.fabricmc.api.EnvType;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class SREConfig extends MidnightConfig {
    // 存储默认配置值 - 在静态初始化块中设置
    private static final Map<String, Object> DEFAULT_VALUES = new HashMap<>();

    static {
        DEFAULT_VALUES.put("ultraPerfMode", false);
        DEFAULT_VALUES.put("disableScreenShake", false);
        DEFAULT_VALUES.put("disableStaminaBarSmoothing", false);
        DEFAULT_VALUES.put("bartenderGlowDuration", 40);
        DEFAULT_VALUES.put("itemSkinSyncServerEnabled", false);
        DEFAULT_VALUES.put("itemSkinSyncServerHost", "");
        DEFAULT_VALUES.put("itemSkinSyncServerPort", 8080);
        DEFAULT_VALUES.put("itemSkinSyncServerKey", "");

        // 商店物品价格默认值
        DEFAULT_VALUES.put("mapRandomCount", -1);
        DEFAULT_VALUES.put("isLobby", false);
        DEFAULT_VALUES.put("knifePrice", 100);
        DEFAULT_VALUES.put("revolverPrice", 300);
        DEFAULT_VALUES.put("grenadePrice", 350);
        DEFAULT_VALUES.put("psychoModePrice", 300);
        DEFAULT_VALUES.put("poisonVialPrice", 100);
        DEFAULT_VALUES.put("scorpionPrice", 50);
        DEFAULT_VALUES.put("firecrackerPrice", 10);
        DEFAULT_VALUES.put("lockpickPrice", 50);
        DEFAULT_VALUES.put("crowbarPrice", 25);
        DEFAULT_VALUES.put("bodyBagPrice", 200);
        DEFAULT_VALUES.put("blackoutPrice", 200);
        DEFAULT_VALUES.put("notePrice", 10);

        // 物品冷却时间默认值（秒）
        DEFAULT_VALUES.put("knifeCooldown", 60);
        DEFAULT_VALUES.put("revolverCooldown", 10);
        DEFAULT_VALUES.put("derringerCooldown", 1);
        DEFAULT_VALUES.put("grenadeCooldown", 300);
        DEFAULT_VALUES.put("lockpickCooldown", 180);
        DEFAULT_VALUES.put("crowbarCooldown", 10);
        DEFAULT_VALUES.put("bodyBagCooldown", 300);
        DEFAULT_VALUES.put("psychoModeCooldown", 300);
        DEFAULT_VALUES.put("blackoutCooldown", 180);

        // 游戏配置默认值
        DEFAULT_VALUES.put("startingMoney", 100);
        DEFAULT_VALUES.put("passiveMoneyAmount", 5);
        DEFAULT_VALUES.put("passiveMoneyInterval", 10);
        DEFAULT_VALUES.put("moneyPerKill", 100);
        DEFAULT_VALUES.put("psychoModeArmor", 1);
        DEFAULT_VALUES.put("psychoModeDuration", 30);
        DEFAULT_VALUES.put("firecrackerDuration", 15);
        DEFAULT_VALUES.put("blackoutMinDuration", 15);
        DEFAULT_VALUES.put("blackoutMaxDuration", 20);
        DEFAULT_VALUES.put("enableAutoTrainReset", false);
        DEFAULT_VALUES.put("verboseTrainResetLogs", false);
    }

    // 客户端专用配置 - 仅在客户端环境生效
    @Comment(category = "client", centered = true)
    public static Comment clientConfigComment;

    @Entry(category = "client")
    public static boolean ultraPerfMode = false;

    @Entry(category = "client")
    public static boolean disableScreenShake = true;

    @Entry(category = "client")
    public static boolean disableStaminaBarSmoothing = false;

    @Entry(category = "client")
    public static boolean enableSecurityCameraHUD = true; // 启用安全摄像头HUD显示

    // 随机地图设置
    @Entry(category = "map")
    public static int mapRandomCount = -1;

    @Entry(category = "map")
    public static boolean isLobby = false;

    // 商店物品价格配置 - 服务端只读
    @Comment(category = "shop", centered = true)
    public static Comment shopPricesComment;

    @Entry(category = "shop", min = 0, max = 1000, isSlider = true)
    public static int knifePrice = 130;
    @Entry(category = "shop", min = 0, max = 1000, isSlider = true)
    public static int revolverPrice = 285;
    @Entry(category = "shop", min = 0, max = 1000, isSlider = true)
    public static int grenadePrice = 330;
    @Entry(category = "shop", min = 0, max = 1000, isSlider = true)
    public static int psychoModePrice = 400;
    @Entry(category = "shop", min = 0, max = 1000, isSlider = true)
    public static int poisonVialPrice = 80;
    @Entry(category = "shop", min = 0, max = 1000, isSlider = true)
    public static int scorpionPrice = 40;
    @Entry(category = "shop", min = 0, max = 1000, isSlider = true)
    public static int firecrackerPrice = 10;
    @Entry(category = "shop", min = 0, max = 1000, isSlider = true)
    public static int lockpickPrice = 80;
    @Entry(category = "shop", min = 0, max = 1000, isSlider = true)
    public static int crowbarPrice = 35;
    @Entry(category = "shop", min = 0, max = 1000, isSlider = true)
    public static int bodyBagPrice = 100;
    @Entry(category = "shop", min = 0, max = 1000, isSlider = true)
    public static int blackoutPrice = 100;
    @Entry(category = "shop", min = 0, max = 1000, isSlider = true)
    public static int notePrice = 10;

    // 物品冷却时间配置（秒）- 服务端只读
    @Comment(category = "cooldowns", centered = true)
    public static Comment cooldownsComment;

    @Entry(category = "cooldowns", min = 0, max = 300, isSlider = true)
    public static int knifeCooldown = 30;
    @Entry(category = "cooldowns", min = 0, max = 300, isSlider = true)
    public static int revolverCooldown = 10;
    @Entry(category = "cooldowns", min = 0, max = 300, isSlider = true)
    public static int derringerCooldown = 1;
    @Entry(category = "cooldowns", min = 0, max = 300, isSlider = true)
    public static int grenadeCooldown = 300;
    @Entry(category = "cooldowns", min = 0, max = 300, isSlider = true)
    public static int lockpickCooldown = 180;
    @Entry(category = "cooldowns", min = 0, max = 300, isSlider = true)
    public static int crowbarCooldown = 45;
    @Entry(category = "cooldowns", min = 0, max = 300, isSlider = true)
    public static int bodyBagCooldown = 300;
    @Entry(category = "cooldowns", min = 0, max = 300, isSlider = true)
    public static int psychoModeCooldown = 275;
    @Entry(category = "cooldowns", min = 0, max = 300, isSlider = true)
    public static int blackoutCooldown = 180;

    // 游戏配置 - 服务端只读
    @Comment(category = "game", centered = true)
    public static Comment gameConfigComment;

    // Bartender - Glow duration in seconds
    @Entry(category = "game", min = 0, max = 120, isSlider = true)
    public static int bartenderGlowDuration = 40;
    @Entry(category = "game", min = 0, max = 1000, isSlider = true)
    public static int startingMoney = 100;
    @Entry(category = "game", min = 0, max = 100, isSlider = true)
    public static int passiveMoneyAmount = 5;
    @Entry(category = "game", min = 1, max = 1200, isSlider = true)
    public static int passiveMoneyInterval = 10;
    @Entry(category = "game", min = 0, max = 1000, isSlider = true)
    public static int moneyPerKill = 100;
    @Entry(category = "game", min = 0, max = 30, isSlider = true)
    public static int psychoModeArmor = 1;
    @Entry(category = "game", min = 0, max = 300, isSlider = true)
    public static int psychoModeDuration = 30;
    @Entry(category = "game", min = 0, max = 300, isSlider = true)
    public static int firecrackerDuration = 15;
    @Entry(category = "game", min = 0, max = 300, isSlider = true)
    public static int blackoutMinDuration = 15;
    @Entry(category = "game", min = 0, max = 300, isSlider = true)
    public static int blackoutMaxDuration = 30;
    @Entry(category = "game")
    public static boolean enableAutoTrainReset = false;
    @Entry(category = "game")
    public static boolean verboseTrainResetLogs = true;

    // AFK设置

    @Entry(category = "skin")
    public static String itemSkinSyncServerHost = "";
    @Entry(category = "skin", min = 1, max = 65536)
    public static int itemSkinSyncServerPort = 8080;
    @Entry(category = "skin")
    public static String itemSkinSyncServerKey = "";
    @Entry(category = "skin")
    public static boolean itemSkinSyncServerEnabled = false;

    @Comment(category = "afk", centered = true)
    public static Comment afkConfigComment;
    @Entry(category = "afk", min = 60, max = 12000, isSlider = true) // 3秒到20分钟
    public static int afkThresholdSeconds = (int) (4.5 * 60); // 5分钟
    @Entry(category = "afk", min = 60, max = 24000, isSlider = true) // 3秒到10分钟
    public static int afkDeathSeconds = (int) (5 * 60); // 5分钟
    @Entry(category = "afk", min = 30, max = 2400, isSlider = true) // 1.5秒到120秒
    public static int afkWarningSeconds = 4 * 60; // 4分钟时开始警告
    @Entry(category = "afk", min = 20, max = 1200, isSlider = true) // 1秒到30秒
    public static int afkSleepySeconds = 3 * 60; // 3分钟时开始困倦效果

    public static boolean isUltraPerfMode() {
        return ultraPerfMode;
    }

    /**
     * 初始化配置系统
     * 必须在mod初始化时调用以生成配置文件
     */
    public static void init() {
        // 统一使用MidnightConfig初始化，它会处理客户端和服务端的差异
        MidnightConfig.init(SRE.MOD_ID, SREConfig.class);

        // 服务端需要额外加载配置到内存
        if (net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
            try {
                java.nio.file.Path configPath = net.fabricmc.loader.api.FabricLoader.getInstance()
                        .getConfigDir()
                        .resolve(SRE.MOD_ID + "_server.json");

                if (java.nio.file.Files.exists(configPath)) {
                    // 读取配置文件
                    String content = java.nio.file.Files.readString(configPath);
                    // 解析JSON并设置字段值
                    parseConfig(content);
                    SRE.LOGGER.info("服务端配置已从文件加载");
                }
            } catch (Exception e) {
                SRE.LOGGER.error("服务端加载配置失败", e);
            }
        }

    }

    /**
     * 重新加载配置文件
     * 服务端：只从文件读取，不修改
     * 客户端：可以通过UI修改
     */
    public static void reload() {
        try {
            java.nio.file.Path configPath = net.fabricmc.loader.api.FabricLoader.getInstance()
                    .getConfigDir()
                    .resolve(SRE.MOD_ID + ".json");

            if (java.nio.file.Files.exists(configPath)) {
                String content = java.nio.file.Files.readString(configPath);
                parseConfig(content);

                // 重载冷却时间到GameConstants
                GameConstants.reloadItemCooldowns();

                SRE.LOGGER.info("配置已从文件重新加载");
            } else {
                SRE.LOGGER.warn("配置文件不存在，无法重新加载");
            }
        } catch (Exception e) {
            SRE.LOGGER.error("重新加载配置失败", e);
        }
    }

    /**
     * 解析配置JSON字符串并设置字段值
     * 仅处理服务端相关的配置项，客户端配置由MidnightConfig库自动处理
     */
    private static void parseConfig(String jsonContent) {
        try {
            // 使用Gson解析JSON
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(jsonContent).getAsJsonObject();

            // 读取皮肤同步配置
            if (json.has("itemSkinSyncServerPort") && json.get("itemSkinSyncServerPort").isJsonPrimitive())
                itemSkinSyncServerPort = json.get("itemSkinSyncServerPort").getAsInt();
            if (json.has("itemSkinSyncServerHost") && json.get("itemSkinSyncServerHost").isJsonPrimitive())
                itemSkinSyncServerHost = json.get("itemSkinSyncServerHost").getAsString();
            if (json.has("itemSkinSyncServerKey") && json.get("itemSkinSyncServerKey").isJsonPrimitive())
                itemSkinSyncServerKey = json.get("itemSkinSyncServerKey").getAsString();
            if (json.has("itemSkinSyncServerEnabled") && json.get("itemSkinSyncServerEnabled").isJsonPrimitive())
                itemSkinSyncServerEnabled = json.get("itemSkinSyncServerEnabled").getAsBoolean();
            // 仅读取服务端相关配置
            // 读取shop配置
            if (json.has("knifePrice") && json.get("knifePrice").isJsonPrimitive())
                knifePrice = json.get("knifePrice").getAsInt();
            if (json.has("revolverPrice") && json.get("revolverPrice").isJsonPrimitive())
                revolverPrice = json.get("revolverPrice").getAsInt();
            if (json.has("grenadePrice") && json.get("grenadePrice").isJsonPrimitive())
                grenadePrice = json.get("grenadePrice").getAsInt();
            if (json.has("psychoModePrice") && json.get("psychoModePrice").isJsonPrimitive())
                psychoModePrice = json.get("psychoModePrice").getAsInt();
            if (json.has("poisonVialPrice") && json.get("poisonVialPrice").isJsonPrimitive())
                poisonVialPrice = json.get("poisonVialPrice").getAsInt();
            if (json.has("scorpionPrice") && json.get("scorpionPrice").isJsonPrimitive())
                scorpionPrice = json.get("scorpionPrice").getAsInt();
            if (json.has("firecrackerPrice") && json.get("firecrackerPrice").isJsonPrimitive())
                firecrackerPrice = json.get("firecrackerPrice").getAsInt();
            if (json.has("lockpickPrice") && json.get("lockpickPrice").isJsonPrimitive())
                lockpickPrice = json.get("lockpickPrice").getAsInt();
            if (json.has("crowbarPrice") && json.get("crowbarPrice").isJsonPrimitive())
                crowbarPrice = json.get("crowbarPrice").getAsInt();
            if (json.has("bodyBagPrice") && json.get("bodyBagPrice").isJsonPrimitive())
                bodyBagPrice = json.get("bodyBagPrice").getAsInt();
            if (json.has("blackoutPrice") && json.get("blackoutPrice").isJsonPrimitive())
                blackoutPrice = json.get("blackoutPrice").getAsInt();
            if (json.has("notePrice") && json.get("notePrice").isJsonPrimitive())
                notePrice = json.get("notePrice").getAsInt();

            // 读取cooldowns配置
            if (json.has("knifeCooldown") && json.get("knifeCooldown").isJsonPrimitive())
                knifeCooldown = json.get("knifeCooldown").getAsInt();
            if (json.has("revolverCooldown") && json.get("revolverCooldown").isJsonPrimitive())
                revolverCooldown = json.get("revolverCooldown").getAsInt();
            if (json.has("derringerCooldown") && json.get("derringerCooldown").isJsonPrimitive())
                derringerCooldown = json.get("derringerCooldown").getAsInt();
            if (json.has("grenadeCooldown") && json.get("grenadeCooldown").isJsonPrimitive())
                grenadeCooldown = json.get("grenadeCooldown").getAsInt();
            if (json.has("lockpickCooldown") && json.get("lockpickCooldown").isJsonPrimitive())
                lockpickCooldown = json.get("lockpickCooldown").getAsInt();
            if (json.has("crowbarCooldown") && json.get("crowbarCooldown").isJsonPrimitive())
                crowbarCooldown = json.get("crowbarCooldown").getAsInt();
            if (json.has("bodyBagCooldown") && json.get("bodyBagCooldown").isJsonPrimitive())
                bodyBagCooldown = json.get("bodyBagCooldown").getAsInt();
            if (json.has("psychoModeCooldown") && json.get("psychoModeCooldown").isJsonPrimitive())
                psychoModeCooldown = json.get("psychoModeCooldown").getAsInt();
            if (json.has("blackoutCooldown") && json.get("blackoutCooldown").isJsonPrimitive())
                blackoutCooldown = json.get("blackoutCooldown").getAsInt();

            // 读取game配置
            if (json.has("isLobby") && json.get("isLobby").isJsonPrimitive())
                isLobby = json.get("isLobby").getAsBoolean();
            if (json.has("mapRandomCount") && json.get("mapRandomCount").isJsonPrimitive())
                mapRandomCount = json.get("mapRandomCount").getAsInt();
            if (json.has("bartenderGlowDuration") && json.get("bartenderGlowDuration").isJsonPrimitive())
                bartenderGlowDuration = json.get("bartenderGlowDuration").getAsInt();
            if (json.has("startingMoney") && json.get("startingMoney").isJsonPrimitive())
                startingMoney = json.get("startingMoney").getAsInt();
            if (json.has("passiveMoneyAmount") && json.get("passiveMoneyAmount").isJsonPrimitive())
                passiveMoneyAmount = json.get("passiveMoneyAmount").getAsInt();
            if (json.has("passiveMoneyInterval") && json.get("passiveMoneyInterval").isJsonPrimitive())
                passiveMoneyInterval = json.get("passiveMoneyInterval").getAsInt();
            if (json.has("moneyPerKill") && json.get("moneyPerKill").isJsonPrimitive())
                moneyPerKill = json.get("moneyPerKill").getAsInt();
            if (json.has("psychoModeArmor") && json.get("psychoModeArmor").isJsonPrimitive())
                psychoModeArmor = json.get("psychoModeArmor").getAsInt();
            if (json.has("psychoModeDuration") && json.get("psychoModeDuration").isJsonPrimitive())
                psychoModeDuration = json.get("psychoModeDuration").getAsInt();
            if (json.has("firecrackerDuration") && json.get("firecrackerDuration").isJsonPrimitive())
                firecrackerDuration = json.get("firecrackerDuration").getAsInt();
            if (json.has("blackoutMinDuration") && json.get("blackoutMinDuration").isJsonPrimitive())
                blackoutMinDuration = json.get("blackoutMinDuration").getAsInt();
            if (json.has("blackoutMaxDuration") && json.get("blackoutMaxDuration").isJsonPrimitive())
                blackoutMaxDuration = json.get("blackoutMaxDuration").getAsInt();
            if (json.has("enableAutoTrainReset") && json.get("enableAutoTrainReset").isJsonPrimitive())
                enableAutoTrainReset = json.get("enableAutoTrainReset").getAsBoolean();
            if (json.has("verboseTrainResetLogs") && json.get("verboseTrainResetLogs").isJsonPrimitive())
                verboseTrainResetLogs = json.get("verboseTrainResetLogs").getAsBoolean();

            // 读取AFK设置
            if (json.has("afkThresholdSeconds") && json.get("afkThresholdSeconds").isJsonPrimitive())
                afkThresholdSeconds = json.get("afkThresholdSeconds").getAsInt();
            if (json.has("afkDeathSeconds") && json.get("afkDeathSeconds").isJsonPrimitive())
                afkDeathSeconds = json.get("afkDeathSeconds").getAsInt();
            if (json.has("afkWarningSeconds") && json.get("afkWarningSeconds").isJsonPrimitive())
                afkWarningSeconds = json.get("afkWarningSeconds").getAsInt();
            if (json.has("afkSleepySeconds") && json.get("afkSleepySeconds").isJsonPrimitive())
                afkSleepySeconds = json.get("afkSleepySeconds").getAsInt();

            SRE.LOGGER.debug("配置解析成功");
        } catch (Exception e) {
            SRE.LOGGER.error("配置解析失败: {}", jsonContent, e);
            throw new RuntimeException("配置解析失败", e);
        }
    }

    /**
     * 重置配置为默认值
     * 通过精确修改配置文件内容来实现，不删除文件
     */
    public static void reset() {
        try {
            // 将所有字段重置为默认值
            for (Field field : SREConfig.class.getDeclaredFields()) {
                if (field.isAnnotationPresent(Entry.class) &&
                        java.lang.reflect.Modifier.isStatic(field.getModifiers()) &&
                        !field.getType().equals(Comment.class)) {

                    String fieldName = field.getName();
                    if (DEFAULT_VALUES.containsKey(fieldName)) {
                        field.setAccessible(true);
                        Object defaultValue = DEFAULT_VALUES.get(fieldName);

                        // 安全地设置字段值，处理类型转换
                        if (field.getType() == boolean.class || field.getType() == Boolean.class) {
                            field.setBoolean(null, (Boolean) defaultValue);
                        } else if (field.getType() == int.class || field.getType() == Integer.class) {
                            field.setInt(null, (Integer) defaultValue);
                        } else {
                            field.set(null, defaultValue);
                        }

                        SRE.LOGGER.debug("重置字段 {} 为默认值: {}", fieldName, defaultValue);
                    }
                }
            }

            // 强制写入配置文件
            MidnightConfig.write(SRE.MOD_ID);

            // 重新加载配置到内存和游戏常量
            reload();

            SRE.LOGGER.info("配置已重置为默认值");
        } catch (Exception e) {
            SRE.LOGGER.error("重置配置失败", e);
            throw new RuntimeException("重置配置失败", e);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void writeChanges(String modid) {
        // 调用父类方法来保存配置
        super.writeChanges(modid);

        // 仅在客户端环境应用客户端专用配置
        if (net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            applyClientConfig();
        }

        SRE.LOGGER.debug("配置已保存");
    }

    /**
     * 应用客户端专用配置
     * 仅在客户端环境调用
     */

    private static void applyClientConfig() {
        // 注释掉的代码保留供将来使用
        // int lockedRenderDistance = TMMClient.getLockedRenderDistance(ultraPerfMode);
        // OptionLocker.overrideOption("renderDistance", lockedRenderDistance);
        // MinecraftClient.getInstance().options.viewDistance.setValue(lockedRenderDistance);
    }
}
