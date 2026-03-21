package io.wifi.starrailexpress;

import io.wifi.ConfigCompact.ConfigClassHandler;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "starrailexpress-client")
public class SREClientConfig implements ConfigData {
    // 存储默认配置值 - 在静态初始化块中设置
    public static ConfigClassHandler<SREClientConfig> HANDLER = new ConfigClassHandler<>(
            SREClientConfig.class);
    // 客户端专用配置 - 仅在客户端环境生效

    @ConfigEntry.Gui.Tooltip
    public boolean ultraPerfMode = false;
    // Skills configuration
    /**
     * Broadcaster - Broadcast message display duration in seconds
     */
    public int broadcasterMessageDuration = 10;
    public boolean disableTitleScreenSound = false;
    public boolean disableTitleScreenVideoBackground = false;

    public boolean disableScreenShake = false;

    public boolean disableStaminaBarSmoothing = false;

    public boolean enableSecurityCameraHUD = true; // 启用安全摄像头HUD显示
    public boolean welcome_voice = false;

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
