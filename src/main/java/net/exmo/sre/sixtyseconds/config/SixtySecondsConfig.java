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

    /**
     * 以下出生点写<b>模板内的绝对坐标</b>——建图时自动换算成相对模板 min 的偏移量套到每队克隆区
     * （见 {@code SixtySecondsArena.spawnFor}）；不在模板盒内的值按“相对模板 min 的偏移”兼容（旧写法）。
     */
    @SerializedName("residentialSpawn")
    public Vec residentialSpawn;

    @SerializedName("shelterSpawn")
    public Vec shelterSpawn;

    /**
     * 探索区出口门绑定列表（用绑定工具 {@code sixty_seconds_area_wand} 生成）。
     * <p>
     * 「探索区」已不再是一块要传送进去、并用空气墙圈起来的独立区域——出门探索现在直接落在<b>门外那格</b>，
     * 之后整片世界自由活动。本列表只剩两个用途：① 每条绑定的 {@code box} 作为该片区域的<b>危险等级区</b>
     * （{@code level}，见 {@link SixtySecondsAreaLevels}）；② 建在避难所外的出口门按队分配为各队的
     * 回家门（{@code returnDoorPos}）与夜袭锚点。绑定盒不再限制玩家活动。
     */
    @SerializedName("searchDoorBindings")
    public java.util.List<DoorBinding> searchDoorBindings = new java.util.ArrayList<>();

    /**
     * 避难所模板内的<b>锚点门</b>（模板绝对坐标）。{@link #shelterAtSearchDoorEnabled} 开启时，这扇门会与本队在
     * 探索区里的<b>出口门</b>（{@link #searchDoorBindings} 中落在模板盒<b>外</b>的那类，按队序号分配）对齐——
     * 整座避难所按 {@code 出口门 - 锚点门} 的差值平移克隆过去，玩家推门即是探索区，不再跨空间传送。
     * 未登记时该开关失效、回退网格克隆（{@code SixtySecondsArena.build} 会告警）。
     * 用 {@code /sre:60s_area anchor <x y z>} 登记（写模板内那扇门的绝对坐标）。
     */
    @SerializedName("shelterAnchorDoor")
    public Vec shelterAnchorDoor;

    /**
     * 避难所是否直接生成在探索区登记的门位置（默认<b>开</b>）：以「避难所锚点门 ↔ 探索区出口门」为锚点平移克隆，
     * 「外出探索」即字面意义的出门（门是实心方块，仍走门菜单传送到门外落点）。
     * 关闭时按 {@link #teamBase} 网格克隆（旧行为）。需 {@link #shelterAnchorDoor} 与探索区出口门绑定齐备，缺一回退网格。
     * 与 {@link #seaChartTeleportEnabled} 互不影响。{@code /sre:60s shelter_at_door on|off} 切换（按图持久化）。
     */
    @SerializedName("shelterAtSearchDoorEnabled")
    public boolean shelterAtSearchDoorEnabled = true;

    /**
     * 海图是否允许<b>扬帆传送</b>与<b>返回住所</b>（默认<b>关</b>）：关闭时海图退化为纯导航图——岛屿轮廓、解锁迷雾、
     * 庇护所与队友点位<b>照常显示</b>，但点岛不再传送、「返回住所」按钮置灰，玩家须自己乘船去岛、走门回家。
     * 创造模式不受限。与 {@link #shelterAtSearchDoorEnabled} 互不影响。
     * {@code /sre:60s sea_teleport on|off} 切换（按图持久化）。
     */
    @SerializedName("seaChartTeleportEnabled")
    public boolean seaChartTeleportEnabled = false;

    /**
     * 生成海岛时是否在<b>一级岛</b>上自动放置一扇避难所门并登记为门绑定/锚点（默认<b>开</b>）：
     * 开启后 {@code /sre:60s island start} 会给每座 1 级岛在地表合适位置建一扇 {@code ShelterDoorBlock}，
     * 并向 {@link #searchDoorBindings} 追加一条 {@code auto=true} 的绑定（门=该门、box=门周围危险区、
     * 等级=岛屿等级）——这样开局建图时各队避难所可锚定到岛门上。{@code island stop/delete} 会自动移除这些
     * 绑定；门方块随地形回滚一并清除。{@code /sre:60s_area clearbindings} 可手动清掉全部门绑定。
     */
    @SerializedName("islandShelterDoorEnabled")
    public boolean islandShelterDoorEnabled = true;

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
     * 海洋生物自然刷新开关（默认<b>开</b>）：鲨鱼/海怪在海上自然刷新。
     * 与 PVE 开关 {@link #pveEnabled} 相互独立。
     * {@code /sre:ocean toggle on|off} 切换（按图持久化）。
     */
    @SerializedName("oceanCreaturesEnabled")
    public boolean oceanCreaturesEnabled = true;

    /**
     * 中途自动入队开关（默认<b>开</b>）：游戏进行中新加入服务器（且无重连备份）的玩家，
     * 自动补入一支<b>在线不满 {@link net.exmo.sre.sixtyseconds.logic.SixtySecondsTeamAllocator#TEAM_SIZE 四人}</b>
     * 的队伍（选在线人数最少的未满队），传送到该队住宅并发身份。所有队伍都满则留观战。
     * {@code /sre:60s autojoin on|off} 切换（按图持久化）。见 {@code SixtySecondsAutoJoin}。
     */
    @SerializedName("autoJoinEnabled")
    public boolean autoJoinEnabled = true;

    /**
     * 自动复活开关（默认<b>开</b>）：玩家死亡后经 {@link #autoReviveIntervalSeconds} 自动在<b>本队避难所</b>复活，
     * 死亡处的尸体会被标注到区域地图上（复活后自动清除），HUD 显示复活倒计时。
     * <p>
     * 开启时「无存活幸存者 → 提前败」<b>不会</b>因为一波团灭就触发——等待复活的玩家算「未阵亡」
     * （见 {@code SixtySecondsWinConditions}）；胜负仍由「撑到最后一天 / 救援信标 / 幸存者阵营」决定。
     * {@code /sre:60s autorevive on|off} 切换（按图持久化）。见 {@code SixtySecondsAutoRevive}。
     */
    @SerializedName("autoReviveEnabled")
    public boolean autoReviveEnabled = true;

    /**
     * 自动复活间隔（秒，默认 240=4 分钟）。{@code /sre:60s autorevive interval <秒>} 设置（按图持久化）。
     * 局中改只影响<b>此后</b>的死亡——已在倒计时中的玩家按死亡当时的间隔走完，免得改一下把在等的人瞬间拉活或永久卡住。
     */
    @SerializedName("autoReviveIntervalSeconds")
    public int autoReviveIntervalSeconds = 240;

    /**
     * 本局总游戏日数（默认 {@value net.exmo.sre.sixtyseconds.logic.SixtySecondsManager#DEFAULT_TOTAL_DAYS}）：
     * 撑过最后一天即幸存者胜利。终极 Boss「终焉之王」固定在<b>最后一天</b>降临（随本值浮动）。
     * {@code /sre:60s days <1..30>} 设置（按图持久化）。见 {@code SixtySecondsManager.totalDays}。
     */
    @SerializedName("totalDays")
    public int totalDays = 7;

    /**
     * 全局危险等级基线 1..5（{@code SixtySecondsAreaLevels}）：不在任何门绑定危险区、也不在岛屿上的坐标
     * 一律取此值。等级越高，物资箱稀有物越常见、掷出件数越多，但游荡怪更多更强。
     * {@code /sre:60s_area level <1..5>} 设置。
     */
    @SerializedName("searchZoneLevel")
    public int searchZoneLevel = 1;

    /**
     * 手动放置的 NPC 生成点（用 NPC 放置器 {@code sixty_seconds_npc_placer} 登记，模板绝对坐标）。
     * 建图时（{@code SixtySecondsNpcSpawner.spawnConfigured}）：点落在住宅/避难所模板盒内 → <b>每队各克隆一份</b>
     * （叠加队伍网格偏移）；落在搜索区/野外 → <b>只生成一份</b>（全队共用，不克隆）。
     * <p>Gson 默认值保证旧存档读进来是空表。
     */
    @SerializedName("npcSpawns")
    public java.util.List<NpcSpawn> npcSpawns = new java.util.ArrayList<>();

    /** 第 index（从 0 起）支队伍的网格偏移。 */
    public BlockPos teamOffset(int index) {
        return new BlockPos(teamBase.x + index * teamGridSpacing, teamBase.y, teamBase.z);
    }

    public boolean isComplete() {
        return residentialTemplate != null && shelterTemplate != null
                && residentialSpawn != null && shelterSpawn != null;
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

    /** 一个手动登记的 NPC 生成点（坐标为模板绝对坐标）。 */
    public static class NpcSpawn {
        /** 变体 id，对齐 {@code SixtySecondsNpcEntity.Variant.id}（0=商人 1=军人 2=强盗 3=旅者 4=海盗）。 */
        @SerializedName("variant")
        public int variant;
        @SerializedName("pos")
        public Vec pos;
        @SerializedName("yaw")
        public float yaw;
        /** 商人的货架档案名（对应 {@code sixty_seconds_npc_shop.json} 的键）；非商人忽略。 */
        @SerializedName("profile")
        public String profile = "default";
        /** 驻守半径（军人巡逻 / 商人摊位活动范围）。 */
        @SerializedName("garrisonRadius")
        public int garrisonRadius = 8;

        public NpcSpawn() {
        }

        public NpcSpawn(int variant, Vec pos, float yaw) {
            this.variant = variant;
            this.pos = pos;
            this.yaw = yaw;
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
        /**
         * 是否由系统自动生成（海岛一级岛自动放门时置 true，见 {@link SixtySecondsConfig#islandShelterDoorEnabled}）。
         * {@code island stop/delete} 只移除 {@code auto=true} 的绑定，不碰管理员手动登记的绑定。
         */
        @SerializedName("auto")
        public boolean auto = false;

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
