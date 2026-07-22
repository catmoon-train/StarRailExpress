package io.wifi.starrailexpress;

import java.util.HashMap;
import java.util.LinkedHashMap;

import io.wifi.ConfigCompact.ConfigClassHandler;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.annotation.ConfigEntry.Category;

/**
 * 写翻译键 at config_translations/lang/zh_cn.json
 * key为text.autoconfig.(Config.name).option.项
 * 如text.autoconfig.starrailexpress-client.option.ultraPerfMode
 */
@Config(name = "starrailexpress-client")
public class SREClientConfig implements ConfigData {

    // 存储默认配置值 - 在静态初始化块中设置
    public static ConfigClassHandler<SREClientConfig> HANDLER = new ConfigClassHandler<>(
            SREClientConfig.class);
    // 客户端专用配置 - 仅在客户端环境生效

    @ConfigEntry.Gui.Tooltip
    public boolean ultraPerfMode = false;

    @ConfigEntry.Gui.Excluded
    public HashMap<Integer, Boolean> taskStatus = new LinkedHashMap<>();

    // Skills configuration
    /**
     * Broadcaster - Broadcast message display duration in seconds
     */
    public enum StaminaStyle {
        DEFAULT,
        OLD_STYLE,
        SPLIT_STYLE,
        MINECRAFT_STYLE,
        NONE
    }

    // 样式
    @Category("style")
    public StaminaStyle staminaStyle = StaminaStyle.DEFAULT;
    @Category("style")
    public int moodTopOffset = 0;
    @Category("style")
    public int moodLeftOffset = 0;
    @Category("style")
    public float playerHudScale = 0.6f;
    @Category("style")
    public float bodyHudScale = 0.6f;

    @Category("style")
    @ConfigEntry.Gui.Tooltip
    public boolean showInfoLinesInInventory = false; // 金币下方的信息行改为在物品栏界面中显示，不再显示在HUD上

    @Category("style")
    @ConfigEntry.Gui.Tooltip
    public boolean showInfoLinesInHud = true; // 显示在HUD上

    @Category("style")
    @ConfigEntry.Gui.Tooltip
    public boolean useLegacyMapSelector = false; // 使用旧版地图投票界面（卡片墙）

    @Category("style")
    public boolean showItemCooldownOverlayNum = false; // 物品栏物品上显示冷却数字
    @Category("style")
    public boolean showHotbarCooldown = true; // 快捷栏上方显示冷却时间
    @Category("style")
    public boolean showMainhandCooldown = true; // 主手物品冷却
    // 通用
    public int broadcasterMessageDuration = 10;
    public boolean disableTitleScreenSound = false;
    public boolean disableTitleScreenVideoBackground = false;
    public boolean disableCustomTitleScreen = false;
    public boolean disableCustomLoadingScreen = false;
    public boolean disableScreenShake = false;
    public boolean disableWaypoints = false;
    @ConfigEntry.Gui.Tooltip
    public boolean enableMovingScenes = true;
    // VT主播随机内置皮肤（可资源包自定义，player_skins.json）
    public boolean enableRandomSkinForStreaming = false;

    public boolean disableStaminaBarSmoothing = false;

    public boolean enableSecurityCameraHUD = true; // 启用安全摄像头HUD显示
    public boolean welcome_voice = false;

    public boolean autoSortVotes = false;

    public boolean isUltraPerfMode() {
        return ultraPerfMode;
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

    public static SREClientConfig instance() {
        return HANDLER.instance();
    }
}
