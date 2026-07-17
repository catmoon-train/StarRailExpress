package net.exmo.sre.sixtyseconds.island;

import com.google.gson.annotations.SerializedName;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;

/**
 * 一座海岛的元数据（Gson 序列化，随 {@link SixtySecondsIslands} 落盘）。
 * <p>
 * 地形本体由 {@link SixtySecondsIslandGenerator} 按 {@link #seed} 程序化生成——
 * 客户端海图（SeaChartScreen）用<b>同一个</b> seed 与形状函数重采样，即可画出与实际地形
 * 一致的岛屿轮廓，无需同步任何方块数据。
 */
public class SixtySecondsIsland {
    /** 语言键前缀；岛名 = name_prefix.N + name_suffix.M 两段翻译拼接。 */
    public static final String LANG = "message.noellesroles.sixty_seconds.island.";
    public static final int NAME_PREFIX_COUNT = 16;
    public static final int NAME_SUFFIX_COUNT = 4;

    /**
     * 岛屿生态类型：决定色板、植被、树木品种、地形特征。
     * 与等级（level）正交——同等级不同类型的岛外观完全不同。
     * 旧存档缺省=null，Generator 回退到纯 level 色板。
     */
    public enum Type {
        TROPICAL,   // 热带：沙+草+丛林树，高植被
        MARSH,      // 沼泽：泥+灰化土+沼泽橡树，低平，水涝
        VOLCANIC,   // 火山：黑石+玄武岩+岩浆，锥形山，枯树
        CORAL,      // 珊瑚环礁：沙+海晶石，环形礁盘+中央潟湖，无树
        FROST,      // 冰霜：雪+冰+云杉，积雪覆盖
        PLATEAU,    // 高原：平顶+陡崖，陶瓦+石头
        JUNGLE,     // 密林：高密度丛林树+藤蔓+可可
        BARREN      // 荒芜：砂土+砾石+枯灌木，极稀疏
    }

    /** 岛屿规模：小型/中型/大型，决定半径、装饰密度、物资数量。 */
    public enum Size {
        SMALL(0.12F, 2, 4, 0.45F, 0.5F),
        MEDIUM(0.35F, 4, 7, 0.75F, 0.8F),
        LARGE(1.0F, 9, 11, 1.0F, 1.0F);

        public final float radiusMult;
        public final int levelRadiusBonus;
        public final int radiusVariance;
        public final float decoMult;
        public final float supplyMult;

        Size(float radiusMult, int levelRadiusBonus, int radiusVariance, float decoMult, float supplyMult) {
            this.radiusMult = radiusMult;
            this.levelRadiusBonus = levelRadiusBonus;
            this.radiusVariance = radiusVariance;
            this.decoMult = decoMult;
            this.supplyMult = supplyMult;
        }
    }

    @SerializedName("id")
    public int id;
    /** 危险等级 1..5：决定地貌色板、废墟/物资箱/怪物的数量与质量。 */
    @SerializedName("level")
    public int level = 1;
    /** 岛屿生态类型（旧存档缺省=null → Generator 回退纯 level 色板）。 */
    @SerializedName("type")
    public Type type = null;
    /** 岛屿规模（Gson 序列化兼容旧存档：缺省→MEDIUM）。 */
    @SerializedName("size")
    public Size size = Size.MEDIUM;
    @SerializedName("namePrefix")
    public int namePrefix;
    @SerializedName("nameSuffix")
    public int nameSuffix;
    /** 地形噪声种子（服务端生成与客户端海图共用）。 */
    @SerializedName("seed")
    public long seed;
    @SerializedName("centerX")
    public int centerX;
    @SerializedName("centerZ")
    public int centerZ;
    /** 海平面 Y（同一群岛统一）。 */
    @SerializedName("seaY")
    public int seaY;
    /** 陆地基准半径（实际岸线随噪声起伏）。 */
    @SerializedName("radius")
    public int radius;
    /** 登岛落点（扬帆传送目标；建岛时在向心一侧的滩头上求得）。 */
    @SerializedName("dockX")
    public int dockX;
    @SerializedName("dockY")
    public int dockY;
    @SerializedName("dockZ")
    public int dockZ;
    /**
     * 自动放置的避难所门坐标（{@link net.exmo.sre.sixtyseconds.config.SixtySecondsConfig#islandShelterDoorEnabled}
     * 开启时给一级岛在建造阶段求得并建门）。{@code shelterDoorY < 0} 表示本岛没有自动门。
     */
    @SerializedName("shelterDoorX")
    public int shelterDoorX;
    @SerializedName("shelterDoorY")
    public int shelterDoorY = Integer.MIN_VALUE;
    @SerializedName("shelterDoorZ")
    public int shelterDoorZ;

    /** 本岛是否有自动放置的避难所门。 */
    public boolean hasShelterDoor() {
        return shelterDoorY != Integer.MIN_VALUE;
    }

    /** 自动放置的避难所门坐标（{@link #hasShelterDoor()} 为真时有效）。 */
    public BlockPos shelterDoorPos() {
        return new BlockPos(shelterDoorX, shelterDoorY, shelterDoorZ);
    }

    /** 岛名（两段翻译键拼接，客户端/服务端同构）。有类型时追加类型标签。 */
    public Component name() {
        Component base = Component.translatable(LANG + "name_prefix." + namePrefix)
                .append(Component.translatable(LANG + "name_suffix." + nameSuffix));
        if (type != null) {
            return base.copy().append(Component.literal(" "))
                    .append(Component.translatable(LANG + "type." + type.name().toLowerCase()));
        }
        return base;
    }

    public BlockPos dockPos() {
        return new BlockPos(dockX, dockY, dockZ);
    }

    /** 本岛「单元格」盒：陆地 + 环岛水裙边 + 纵向生成范围（建造/还原/区域地图共用）。 */
    public AABB cellBox() {
        int r = radius + SixtySecondsIslandGenerator.WATER_SKIRT;
        return new AABB(centerX - r, seaY - SixtySecondsIslandGenerator.DEPTH_BELOW_SEA,
                centerZ - r, centerX + r + 1,
                seaY + SixtySecondsIslandGenerator.HEIGHT_ABOVE_SEA, centerZ + r + 1);
    }

    /** 到岛心的水平距离平方。 */
    public double distSqr(double x, double z) {
        double dx = x - (centerX + 0.5);
        double dz = z - (centerZ + 0.5);
        return dx * dx + dz * dz;
    }

    /** 该坐标是否算「登上了本岛」（水平进入陆地半径内且不深潜在海底）。 */
    public boolean isOnIsland(BlockPos pos) {
        return pos.getY() >= seaY - 2 && distSqr(pos.getX() + 0.5, pos.getZ() + 0.5)
                <= (double) (radius + 2) * (radius + 2);
    }

    /** 该坐标是否在本岛单元格（含水裙边）内——危险等级反查用。 */
    public boolean inCell(BlockPos pos) {
        int r = radius + SixtySecondsIslandGenerator.WATER_SKIRT;
        return pos.getY() >= seaY - SixtySecondsIslandGenerator.DEPTH_BELOW_SEA
                && pos.getY() <= seaY + SixtySecondsIslandGenerator.HEIGHT_ABOVE_SEA
                && distSqr(pos.getX() + 0.5, pos.getZ() + 0.5) <= (double) r * r;
    }
}
