package net.exmo.sre.sixtyseconds.config;

import com.google.gson.annotations.SerializedName;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * 末日60秒模式的地图配置（Gson 序列化，存世界存档目录 JSON，见 {@link SixtySecondsConfigStore}）。
 * <p>
 * 授权模型：管理员手搭<b>一份</b>住宅 / 避难所 / 搜索区模板并登记其 AABB 与出生点；开局
 * {@code SixtySecondsArena} 对每队按网格偏移 {@code teamBase + idx*teamGridSpacing} 用
 * {@code BlockCopyUtils.copyLayer} 克隆出一份。门 / 物资箱等方块直接建在模板里，随克隆自动复制，无需在此登记。
 */
public class SixtySecondsConfig {

    /** 第一支队伍相对模板的克隆偏移。 */
    @SerializedName("teamBase")
    public Vec teamBase = new Vec(2048, 0, 0);

    /** 每支队伍在 X 轴上的额外偏移间距（须大于模板尺寸，避免重叠）。 */
    @SerializedName("teamGridSpacing")
    public int teamGridSpacing = 512;

    @SerializedName("residentialTemplate")
    public Region residentialTemplate;

    @SerializedName("shelterTemplate")
    public Region shelterTemplate;

    @SerializedName("searchZoneTemplate")
    public Region searchZoneTemplate;

    /**
     * 以下出生点写<b>模板内的绝对坐标</b>——建图时自动换算成相对模板 min 的偏移量套到每队克隆区
     * （见 {@code SixtySecondsArena.spawnFor}）；不在模板盒内的值按“相对模板 min 的偏移”兼容（旧写法）。
     */
    @SerializedName("residentialSpawn")
    public Vec residentialSpawn;

    @SerializedName("shelterSpawn")
    public Vec shelterSpawn;

    @SerializedName("searchZoneSpawn")
    public Vec searchZoneSpawn;

    /**
     * 共用探索区内的<b>每队出口点</b>列表（模板绝对坐标，不随队偏移）：第 index 支队伍出门落在
     * {@code searchExitPoints[index % size]}——每个避难所对应探索区的不同位置。为空则全部用 {@link #searchZoneSpawn}。
     * 用 {@code /sre:60s_area exit add <x y z>} 登记。
     */
    @SerializedName("searchExitPoints")
    public java.util.List<Vec> searchExitPoints = new java.util.ArrayList<>();

    /**
     * 避难所门 → 专属探索区 的绑定列表（用绑定工具 {@code sixty_seconds_area_wand} 生成）。
     * 每条把一扇 SEARCH 门（模板绝对坐标）绑到一个独立探索区（盒 + 出生点，均模板绝对坐标）；
     * 开局按队叠加网格偏移克隆。为空时该门回退到全局 {@link #searchZoneSpawn}/{@code searchZoneTemplate}。
     */
    @SerializedName("searchDoorBindings")
    public java.util.List<DoorBinding> searchDoorBindings = new java.util.ArrayList<>();

    /**
     * 晚上是否自动刷新夜袭者冲门（默认<b>关</b>）。关闭时仍可用「夜袭者召唤哨」
     * （{@code sixty_seconds_assault_spawner_*}）手动放怪。{@code /sre:60s assault on|off} 切换（按图持久化）。
     */
    @SerializedName("nightAssaultEnabled")
    public boolean nightAssaultEnabled = false;

    /**
     * 是否发放开局保底物资（人均水/罐头/绷带 + 每队废料/破布/火把/污染水，随搜刮所得装进
     * 避难所补给箱；见 {@code SixtySecondsManager.starterSupplies}）。默认<b>关</b>——
     * 全靠准备阶段搜刮。{@code /sre:60s starter on|off} 切换（按图持久化）。
     */
    @SerializedName("starterSuppliesEnabled")
    public boolean starterSuppliesEnabled = false;

    /**
     * PVE 开关（默认<b>开</b>）：探索区游荡怪 + 夜晚 Boss 尸潮领主（{@code SixtySecondsPveSystem}）。
     * 与夜袭开关 {@link #nightAssaultEnabled} 相互独立。{@code /sre:60s pve on|off} 切换（按图持久化）。
     */
    @SerializedName("pveEnabled")
    public boolean pveEnabled = true;

    /**
     * 中途自动入队开关（默认<b>开</b>）：游戏进行中新加入服务器（且无重连备份）的玩家，
     * 自动补入一支<b>在线不满 {@link net.exmo.sre.sixtyseconds.logic.SixtySecondsTeamAllocator#TEAM_SIZE 四人}</b>
     * 的队伍（选在线人数最少的未满队），传送到该队住宅并发身份。所有队伍都满则留观战。
     * {@code /sre:60s autojoin on|off} 切换（按图持久化）。见 {@code SixtySecondsAutoJoin}。
     */
    @SerializedName("autoJoinEnabled")
    public boolean autoJoinEnabled = true;

    /**
     * 全局探索区危险等级 1..5（{@code SixtySecondsAreaLevels}）：等级越高，物资箱稀有物越常见、
     * 掷出件数越多，但游荡怪更多更强。{@code /sre:60s_area level <1..5>} 设置。
     */
    @SerializedName("searchZoneLevel")
    public int searchZoneLevel = 1;

    /** 第 index（从 0 起）支队伍的网格偏移。 */
    public BlockPos teamOffset(int index) {
        return new BlockPos(teamBase.x + index * teamGridSpacing, teamBase.y, teamBase.z);
    }

    public boolean isComplete() {
        return residentialTemplate != null && shelterTemplate != null && searchZoneTemplate != null
                && residentialSpawn != null && shelterSpawn != null && searchZoneSpawn != null;
    }

    public static class Vec {
        @SerializedName("x")
        public int x;
        @SerializedName("y")
        public int y;
        @SerializedName("z")
        public int z;

        public Vec() {
        }

        public Vec(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public BlockPos toBlockPos() {
            return new BlockPos(x, y, z);
        }
    }

    /** 一扇避难所门与其专属探索区的绑定（坐标均为模板绝对坐标，克隆时叠加网格偏移）。 */
    public static class DoorBinding {
        @SerializedName("door")
        public Vec door;
        @SerializedName("boxMin")
        public Vec boxMin;
        @SerializedName("boxMax")
        public Vec boxMax;
        @SerializedName("spawn")
        public Vec spawn;
        /** 该绑定探索区的危险等级 1..5；0=继承全局 {@code searchZoneLevel}。{@code /sre:60s_area level <n> <x y z>} 设置。 */
        @SerializedName("level")
        public int level = 0;

        public DoorBinding() {
        }

        public DoorBinding(Vec door, Vec boxMin, Vec boxMax, Vec spawn) {
            this.door = door;
            this.boxMin = boxMin;
            this.boxMax = boxMax;
            this.spawn = spawn;
        }
    }

    public static class Region {
        @SerializedName("min")
        public Vec min = new Vec();
        /**
         * 第二个对角（<b>绝对坐标</b>，含端点；与 min 自动取正序，两角顺序随意）。
         * 旧存档字段名 {@code size} 兼容读取——旧“各轴方块数”语义已废弃，若加载出的区域异常请用命令重新登记。
         */
        @SerializedName(value = "max", alternate = {"size"})
        public Vec max = new Vec();

        public Region() {
        }

        public Region(Vec min, Vec max) {
            this.min = min;
            this.max = max;
        }

        public BoundingBox toBox() {
            return BoundingBox.fromCorners(min.toBlockPos(), max.toBlockPos());
        }
    }
}
