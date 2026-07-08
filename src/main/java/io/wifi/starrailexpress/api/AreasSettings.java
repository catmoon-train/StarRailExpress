package io.wifi.starrailexpress.api;

import io.wifi.ConfigCompact.annotation.Category;
import io.wifi.ConfigCompact.annotation.ConfigSync;
import io.wifi.starrailexpress.game.data.MapStatusBarType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
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
 * 在field前标记<br/>
 * 关于分类，可以使用
 * 
 * <pre>
 *  &#64;Category(value = "分类id")
 * </pre>
 * 
 * <h3>记得写翻译键</h3>
 * 位置：{@code starrailexpress:lang/zh_cn.json}<br/>
 * 从 {@code "=== 地图设置开始 ===":""}，后面开始写，直到 {@code "== 地图设置末尾 END ==": ""}<br/>
 * field的翻译：{@code "sre.map_helper.settings." + field名称}<br/>
 * 分类的翻译：{@code "sre.map_helper.settings.category." + 分类名称}<br/>
 * 如果有注释（可选）则写：{@code "sre.map_helper.settings." + field名称 + ".tooltip"}<br/>
 * 枚举（Enum）则写：{@code "sre.map_helper.settings." + field名称 + "." + enum.name()}<br/>
 * Enum的注释（可选）则写{@code "sre.map_helper.settings." + field名称 + "." + enum.name() + ".tooltip"}
 * 
 */

public class AreasSettings {
    public static class StoreableAABB {
        public final double minX;
        public final double minY;
        public final double minZ;
        public final double maxX;
        public final double maxY;
        public final double maxZ;

        public StoreableAABB(AABB aabb) {
            this.minX = aabb.minX;
            this.minY = aabb.minY;
            this.minZ = aabb.minZ;
            this.maxX = aabb.maxX;
            this.maxY = aabb.maxY;
            this.maxZ = aabb.maxZ;
        }

        public StoreableAABB(net.minecraft.world.phys.Vec3 pos1, net.minecraft.world.phys.Vec3 pos2) {
            this.minX = Math.min(pos1.x, pos2.x);
            this.minY = Math.min(pos1.y, pos2.y);
            this.minZ = Math.min(pos1.z, pos2.z);
            this.maxX = Math.max(pos1.x, pos2.x);
            this.maxY = Math.max(pos1.y, pos2.y);
            this.maxZ = Math.max(pos1.z, pos2.z);
        }

        public StoreableAABB(double d, double e, double f, double g, double h, double i) {
            this.minX = Math.min(d, g);
            this.minY = Math.min(e, h);
            this.minZ = Math.min(f, i);
            this.maxX = Math.max(d, g);
            this.maxY = Math.max(e, h);
            this.maxZ = Math.max(f, i);
        }

        public StoreableAABB setMinX(double d) {
            return new StoreableAABB(d, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
        }

        public StoreableAABB setMinY(double d) {
            return new StoreableAABB(this.minX, d, this.minZ, this.maxX, this.maxY, this.maxZ);
        }

        public StoreableAABB setMinZ(double d) {
            return new StoreableAABB(this.minX, this.minY, d, this.maxX, this.maxY, this.maxZ);
        }

        public StoreableAABB setMaxX(double d) {
            return new StoreableAABB(this.minX, this.minY, this.minZ, d, this.maxY, this.maxZ);
        }

        public StoreableAABB setMaxY(double d) {
            return new StoreableAABB(this.minX, this.minY, this.minZ, this.maxX, d, this.maxZ);
        }

        public StoreableAABB setMaxZ(double d) {
            return new StoreableAABB(this.minX, this.minY, this.minZ, this.maxX, this.maxY, d);
        }

        public AABB toAABB() {
            return new AABB(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
        }
    }

    public static class PosWithOrientation {
        public final StoreableVec3 pos;
        public final float yaw;
        public final float pitch;

        public PosWithOrientation(StoreableVec3 pos, float yaw, float pitch) {
            this.pos = pos;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public PosWithOrientation(double x, double y, double z, float yaw, float pitch) {
            this(new StoreableVec3(x, y, z), yaw, pitch);
        }

    }

    public static class StoreableBlockPos {
        public int x = 0, y = 0, z = 0;

        public int x() {
            return x;
        }

        public int y() {
            return y;
        }

        public int z() {
            return z;
        }

        public StoreableBlockPos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

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
        public double x = 0, y = 0, z = 0;

        public double x() {
            return x;
        }

        public double y() {
            return y;
        }

        public double z() {
            return z;
        }

        public StoreableVec3(net.minecraft.world.phys.Vec3 vec) {
            this.x = vec.x;
            this.y = vec.y;
            this.z = vec.z;
        }

        public StoreableVec3(double x, double y, double z) {
            this.x = x;
            this.z = z;
            this.y = y;
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

    // 示例：不需要同步，也不需要保存的类
    // @Expose(serialize = false, deserialize = false)
    // @ConfigSync(shouldSync = false)
    // public boolean __isTest__ = true;

    /** 是否可跳跃 */
    @Category("action")
    // 默认为 true，此处只是为了占位
    @ConfigSync(shouldSync = true)
    public boolean canJump = false;
    /** 是否允许触碰岩浆（isInLava) */
    @Category("action")
    public boolean canInLava = true;
    /** 在禁用跳跃时，是否允许在水下时使用空格键 */
    @Category("action")
    public boolean canSwim = false;
    /**
     * 水下检测，设置为false则需要玩家位置：
     * <li>水</li>
     * <li>水（头）</li>
     * <li>水（脚）</li>
     * 才会去世。允许玩家简单地游泳
     */
    @Category("action")
    public boolean canSimpleSwim = true;
    /**
     * 标准的水下检测，设置为false则只要玩家眼睛在水下就去世。
     */
    @Category("action")
    public boolean canUnderWater = true;
    /**
     * 严格的水下检测，设置为false则需要玩家位置：
     * <li>水（脚）</li>
     * <li>水</li>
     * 才会去世。不允许玩家简单地游泳。
     * 禁用此选项也会禁用 canUnderWater
     */
    @Category("action")
    public boolean allowInDeepWater = true;
    /**
     * 若玩家氧气值耗尽后5s，则去世
     */
    @Category("action")
    public boolean enableOxygenDrowning = false;

    // 雪花效果配置（默认关闭）
    @Category("visual")
    public boolean snowEnabled = false;

    // 沙尘暴效果配置（默认关闭）
    @Category("visual")
    public boolean sandEnabled = false;

    // 雾气效果配置（默认启用）
    @Category("visual")
    public boolean fogEnabled = true;

    // 雾气可见范围（fogEnd，默认200），仅在 fogEnabled 启用时生效
    @Category("visual")
    public float fogEnd = 200.0f;

    public static enum FogShape {
        SPHERE, CYLINDER
    }

    // 雾气形状（SPHERE 或 CYLINDER），默认 SPHERE，仅在 fogEnabled 启用时生效
    @Category("visual")
    public FogShape fogShape = FogShape.SPHERE;

    public static enum MinecraftWeather {
        clear, rain, thunder
    }

    // 天气配置（默认晴天）
    @Category("visual")
    public MinecraftWeather weather = MinecraftWeather.clear; // clear, rain, thunder

    // 重力modifier（默认0）
    @Category("action")
    public double gravityModifier = 0;
    // 时间配置（默认午夜 18000）
    @Category("visual")
    public long time = 18000;

    // 昼夜循环配置（默认关闭）
    @Category("visual")
    public boolean daylightCycle = false;

    // 天气循环配置（默认关闭）
    @Category("visual")
    public boolean weatherCycle = false;
    // 死亡高度。0禁用
    @Category("action")
    public int fallToDeathHeight = 0;
    @Category("action")
    public MapStatusBarType mapStatusBar = MapStatusBarType.NONE;

    @Category("sound")
    public boolean haveOutsideSound = false;

    /** 背景音效类型：train/wind/sand_storm/snow_storm/circus。空字符串或未设置时默认 train。 */
    public static enum BackgroundAmbienceSound {
        train, wind, sand_storm, snow_storm, circus
    }

    @Category("sound")
    public BackgroundAmbienceSound sceneOutsideSound = BackgroundAmbienceSound.train;

    // ==================== 紧急会议系统 / Emergency Meeting ====================
    // 见 net.exmo.sre.meeting.MeetingManager；地图配置 GUI 的「会议」标签页可视化编辑。

    /** 是否启用会议系统（右键尸体召开紧急会议）。 */
    @Category("meeting")
    public boolean meetingEnabled = false;
    /** 这个class里有可以存储的Vec3不用的AI是真的逊。 */
    @Category("meeting")
    public StoreableVec3 meetingPosition = new StoreableVec3(0, 0, 0);
    /** 以会议地点为中心自动搜寻椅子（MountableBlock）的半径。 */
    @Category("meeting")
    public StoreableAABB meetingChairScanBox = new StoreableAABB(-12, -3, -12, 12, 3, 12);
    /** 讨论阶段时长（秒）。 */
    @Category("meeting")
    public int meetingDiscussSeconds = 60;
    /** 两次会议之间的冷却（秒）。 */
    @Category("meeting")
    public int meetingCooldownSeconds = 90;

    // ==================== 摇铃会议系统 / Bell Meeting ====================

    /** 是否启用摇铃会议（右键原版钟方块召开紧急会议）。前提是 meetingEnabled 为 true。 */
    @Category("meeting")
    public boolean bellMeetingEnabled = false;
    /** 摇铃开局冷却（秒）。开局后多少秒才能摇铃。 */
    @Category("meeting")
    public int bellMeetingStartCooldown = 120;
    /** 摇铃冷却（秒）。摇铃后间隔多少秒才能再次摇铃。 */
    @Category("meeting")
    public int bellMeetingCooldown = 120;
}
