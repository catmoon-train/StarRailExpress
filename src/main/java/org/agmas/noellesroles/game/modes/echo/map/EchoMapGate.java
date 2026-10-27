package org.agmas.noellesroles.game.modes.echo.map;

import io.wifi.starrailexpress.cca.AreasWorldComponent;

import java.util.ArrayList;
import java.util.Locale;

/**
 * 回响模式的地图闸门：复用 {@link AreasWorldComponent#gameModes} 白名单，不新增模式字段。
 *
 * <p>语义与其它模式一致（空列表 = 支持所有模式），但回响**必须显式声明**：
 * {@code gameModes} 里没有 {@code echo_freight} 的地图会被开局预检直接拒绝，
 * 这样「只能在指定地图启用」由 Areas 的地图配置决定，而不是散落在代码里的地图名判断。
 */
public final class EchoMapGate {
    /** 写进 {@code AreasWorldComponent.gameModes} 的模式 id（等于模式 ResourceLocation 的 path）。 */
    public static final String MODE_ID = "echo_freight";

    private EchoMapGate() { }

    /** 当前地图是否显式声明支持回响。 */
    public static boolean supported(AreasWorldComponent areas) {
        return areas != null && areas.gameModes != null && areas.gameModes.contains(MODE_ID);
    }

    /**
     * 声明 / 取消声明本模式。
     *
     * @return 列表是否真的发生变化；调用方负责 {@code sync()} 与（需要时）保存地图文件
     */
    public static boolean setSupported(AreasWorldComponent areas, boolean supported) {
        if (areas == null) return false;
        if (areas.gameModes == null) areas.gameModes = new ArrayList<>();
        boolean declared = areas.gameModes.contains(MODE_ID);
        if (supported == declared) return false;
        if (supported) areas.gameModes.add(MODE_ID);
        else areas.gameModes.removeIf(MODE_ID::equals);
        return true;
    }

    /** 给命令与地图编辑工具显示的一句摘要。 */
    public static String describe(AreasWorldComponent areas) {
        if (areas == null) return MODE_ID + " unknown";
        return String.format(Locale.ROOT, "%s mode=%s %s", areas.mapName == null || areas.mapName.isBlank() ? "?" : areas.mapName,
                MODE_ID, supported(areas) ? "declared" : "missing");
    }
}
