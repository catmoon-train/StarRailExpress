package io.wifi.starrailexpress.api;

import com.google.gson.annotations.Expose;

import io.wifi.ConfigCompact.annotation.ConfigSync;
import io.wifi.starrailexpress.game.data.MapStatusBarType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * <b>AreasWorldComponent 其他地图设置</b><br/>
 * 写里面自动保存/读取，也可通过命令直接快捷修改<br/>
 * 本类使用 {@link com.google.gson.Gson} 序列化与反序列化。<br/>
 * 故支持 Gson 的序列化注解。<br/>
 * 仅支持：Collection (List/Set)，其他常见类 (如String, int, boolean 等)<br/>
 * <b> 请不要用原版的类！因为混淆的缘故，他们会显示为乱码！</b><br/>
 * 关于同步，默认会同步全部field。
 * 如果不想同步或者不需要同步的，可以使用
 * 
 * <pre>
 *  &#64;ConfigSync(shouldSync = false)
 * </pre>
 * 
 * 在field前标记
 */

public class AreasSettings {

    public static class StoreableBlockPos {
        int x = 0, y = 0, z = 0;

        public StoreableBlockPos(BlockPos blockPos) {
            this.x = blockPos.getX();
            this.y = blockPos.getY();
            this.z = blockPos.getZ();
        }

        public BlockPos toBlockPos() {
            return new BlockPos(x, y, z);
        }
    }

    public static class StoreableVec3 {
        double x = 0, y = 0, z = 0;

        public StoreableVec3(net.minecraft.world.phys.Vec3 vec) {
            this.x = vec.x;
            this.y = vec.y;
            this.z = vec.z;
        }

        public net.minecraft.world.phys.Vec3 toVec3() {
            return new Vec3(x, y, z);
        }
    }

    public AreasSettings() {
        // 不要在这里初始化，请在各值处直接初始化。Gson反序列化不走此处。
    }

    /*
     * 正文开始
     * 关于同步，默认会同步全部field。
     * 如果不想同步或者不需要同步的，可以使用
     * <pre> @ConfigSync(shouldSync = false) </pre>
     * 在field前标记
     */
    /** 是否允许触碰岩浆（isInLava) */
    public boolean canInLava = true;

    public boolean canJump = false;
    public boolean canSwim = false;
    public boolean enableOxygenDrowning = false;

    // 雪花效果配置（默认关闭）
    public boolean snowEnabled = false;

    // 沙尘暴效果配置（默认关闭）
    public boolean sandEnabled = false;

    // 雾气效果配置（默认启用）
    public boolean fogEnabled = true;

    // 雾气可见范围（fogEnd，默认200），仅在 fogEnabled 启用时生效
    public float fogEnd = 200.0f;

    // 雾气形状（SPHERE 或 CYLINDER），默认 SPHERE，仅在 fogEnabled 启用时生效
    public String fogShape = "SPHERE";

    // 天气配置（默认晴天）
    public String weather = "clear"; // clear, rain, thunder

    // 重力modifier（默认0）
    public double gravityModifier = 0;
    // 时间配置（默认午夜 18000）
    public long time = 18000;

    // 昼夜循环配置（默认关闭）
    public boolean daylightCycle = false;

    // 天气循环配置（默认关闭）
    public boolean weatherCycle = false;
    /**
     * 示例：不需要同步，也不需要保存的类
     */
    @Expose(serialize = false, deserialize = false)
    @ConfigSync(shouldSync = false)
    public boolean isTest = true;
    // 死亡高度。0禁用
    public int fallToDeathHeight = 0;

    public MapStatusBarType mapStatusBar = MapStatusBarType.NONE;
}
