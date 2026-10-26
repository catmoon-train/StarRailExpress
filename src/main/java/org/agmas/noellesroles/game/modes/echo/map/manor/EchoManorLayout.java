package org.agmas.noellesroles.game.modes.echo.map.manor;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorMachines.Cell;
import org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorMachines.Machine;
import org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorMachines.Model;
import org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorMachines.Facing;

/**
 * 回声货运测试地图「回声枢纽」的纯几何布局：只描述方块种类与模式点位，不引用任何 Minecraft 类。
 *
 * <p>坐标系：局部坐标 x 0..199（西→东）、z 0..199（北→南）、y 0..21（下→上）。
 * 地面基准方块在 {@link #GROUND_Y}，玩家脚踩 {@link #FEET_Y}。
 *
 * <p>绘制顺序固定为
 * {@code 地基 → 外围假山 → 分区地面 → 台地 → 假山 → 道路 → 建筑 → 树木 → 点位}，
 * 后画的图层直接盖掉先画的图层，所以道路能切穿台地、建筑能压平地面，不会出现半格错位。
 *
 * <p>结构不变量（改动坐标时必须同时满足，{@code EchoManorLayoutTest} 会逐条验证）：
 * <ul>
 * <li><b>通路平面唯一</b>：所有点位、出生点、门体、撤离区都落在 {@link #FEET_Y} 基准平面。
 * 台地、假山只做视觉起伏，不承担连通性，因此 {@code canJump=false} 时整张图依然走得通。</li>
 * <li>台地一律 1–2 格高差、整格方块；不放台阶、半砖、栅栏、梯子，避免路线准入退化成人工复核。</li>
 * <li>金库（核心区）只有东西两个门洞，门体关闭时内部完全封闭；门体是整格铁块。</li>
 * <li>所有点位方块互不重叠，且都不落在核心区、门体、撤离区里（黑市箱与内部出口除外）。</li>
 * <li>外围环形假山把整张图围死：玩家走不到模板区域外的虚空。</li>
 * <li>全部方块都是整格方块，没有水、岩浆、沙、砂砾、火、红石或方块实体，
 * 生成时不需要邻居更新，也不会出现流体/重力级联。</li>
 * <li>道路用整数坐标折线 + 圆形印章铺设，转角处自然重叠，不会留下 1 格缝隙。</li>
 * </ul>
 */
public final class EchoManorLayout {
    public static final int WIDTH = 200;
    public static final int DEPTH = 200;
    public static final int HEIGHT = 22;
    public static final int GROUND_Y = 3;
    /** 脚层：所有点位标记方块所在的 y。 */
    public static final int FEET_Y = 4;

    private EchoManorLayout() { }

    /** 方块种类；{@link #AIR} 之外全部是整格方块。 */
    public enum Kind {
        AIR, STONE, DIRT, GRASS, PATH, FLOOR, WALL, GATE, PILLAR, GLASS, ROOF, ROOF_DARK, LAMP,
        LOG, LEAVES, CONTAINER, TABLE, COUNTER, BOOKSHELF, CRATE, DARKSTONE,
        /** 假山岩体 / 苔藓面 / 工业硬地 / 金属平台 / 台地顶面 / 台地挡墙。 */
        ROCK, MOSS, CONCRETE, PLATE, TERRACE, TERRACE_WALL,
        /** 草丛：无碰撞、可穿行，站进去触发隐身。 */
        BUSH,
        /** 矮墙：1 格高，挡视线但不挡走位。 */
        LOW_WALL,
        MARKER_CRYSTAL, MARKER_BEACON, MARKER_ALTAR, MARKER_CRATE, MARKER_BLACK_CRATE,
        MARKER_BLACK_NODE, MARKER_BLACK_DROPOFF, MARKER_BROKER, MARKER_FREE_EXIT, PANEL, MACHINE_PART;

        private static final Kind[] VALUES = values();

        public static Kind of(int ordinal) { return VALUES[ordinal]; }

        public boolean solid() { return this != AIR; }

        public boolean marker() { return name().startsWith("MARKER_") || this == PANEL; }
    }

    /** 脚层点位（id + 局部方块坐标）。 */
    public record Point(String id, int x, int z) { }
    /** 出生点（局部方块坐标 + 朝向）。 */
    public record SpawnPoint(int x, int z, float yaw) { }
    public record Beacon(String id, int x, int z, String region, int minPlayers) { }
    public record Node(String id, int x, int z, int minPlayers) { }
    public record Crate(String id, int x, int z, String quality, boolean repeatable) { }
    /** 核心入口：门外平台上的控制台根坐标 + 门体半开区间。 */
    public record Door(String id, int panelX, int panelZ, Box volume) { }
    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public int volume() { return (maxX - minX) * (maxY - minY) * (maxZ - minZ); }
        public boolean contains(double x, double y, double z) {
            return x >= minX && x < maxX && y >= minY && y < maxY && z >= minZ && z < maxZ;
        }
    }
    /** 摆放在脚层的标记方块。 */
    public record Marker(Kind kind, String id, int x, int y, int z) { }

    /**
     * 完整布局。{@code cells} 按 x→y→z 排列（{@link #index}），空气也占位，
     * 这样生成器可以整块覆盖、不会残留旧地形。
     */
    public record Layout(byte[] cells, List<SpawnPoint> carriers, List<SpawnPoint> interceptors,
                         List<SpawnPoint> brokers, List<Beacon> beacons, List<Node> crystals,
                         List<Point> altars, List<Crate> crates, List<Point> blackNodes,
                         List<Point> blackDropoffs, Point brokerExit, Point freeControl, SpawnPoint freeSafe,
                         List<Door> doors, Box coreArea, Point evacControl, Box evacDoor, Box evacZone,
                         SpawnPoint evacSafe, Point dropAnchor, SpawnPoint spectator, List<Marker> markers,
                         Map<Integer, Cell> machineCells) {

        public static int index(int x, int y, int z) { return (y * WIDTH + x) * DEPTH + z; }

        public Kind at(int x, int y, int z) {
            if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT || z < 0 || z >= DEPTH) return Kind.AIR;
            return Kind.of(cells[index(x, y, z)] & 0xFF);
        }

        public boolean solid(int x, int y, int z) { return at(x, y, z).solid(); }

        public Cell machineAt(int x, int y, int z) {
            if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT || z < 0 || z >= DEPTH) return null;
            return machineCells.get(index(x, y, z));
        }
    }

    // ==================== 构建 ====================

    public static Layout build() {
        Builder b = new Builder();
        b.baseTerrain();
        b.boundaryRidge();
        b.districtFloors();
        b.terraces();
        b.rockeries();
        b.roads();
        b.coreFortress();
        b.evacuationGatehouse();
        b.northYard();
        b.westWorks();
        b.eastMarket();
        b.southCamp();
        b.gardens();
        b.points();
        return b.finish();
    }

    private static final class Builder {
        private final EchoManorCanvas canvas = new EchoManorCanvas();
        private final List<SpawnPoint> carriers = new ArrayList<>();
        private final List<SpawnPoint> interceptors = new ArrayList<>();
        private final List<SpawnPoint> brokers = new ArrayList<>();
        private final List<Beacon> beacons = new ArrayList<>();
        private final List<Node> crystals = new ArrayList<>();
        private final List<Point> altars = new ArrayList<>();
        private final List<Crate> crates = new ArrayList<>();
        private final List<Point> blackNodes = new ArrayList<>();
        private final List<Point> blackDropoffs = new ArrayList<>();
        private final List<Door> doors = new ArrayList<>();
        private final List<Marker> markers = new ArrayList<>();
        private final Map<Integer, Cell> machineCells = new HashMap<>();
        private final Set<Integer> machineSpace = new HashSet<>();
        private Point brokerExit;
        private Point freeControl;

        private Kind at(int x, int y, int z) { return canvas.at(x, y, z); }

        private void put(int x, int y, int z, Kind kind) { canvas.put(x, y, z, kind); }

        private void fill(int x0, int x1, int y0, int y1, int z0, int z1, Kind kind) {
            canvas.fill(x0, x1, y0, y1, z0, z1, kind);
        }

        private void clear(int x0, int x1, int y0, int y1, int z0, int z1) {
            canvas.clear(x0, x1, y0, y1, z0, z1);
        }

        // ---------- 地形 ----------

        /** 三层地基 + 草地表面：整张图的地面基准面，之后所有地形都在它上面做文章。 */
        private void baseTerrain() {
            fill(0, WIDTH - 1, 0, GROUND_Y - 1, 0, DEPTH - 1, Kind.STONE);
            fill(0, WIDTH - 1, GROUND_Y, GROUND_Y, 0, DEPTH - 1, Kind.GRASS);
        }

        /**
         * 外围环形假山：把整张图围死，走不到模板外的虚空，同时给地图一圈起伏的天际线。
         * 最外圈始终至少 6 格高，任何跳跃设置都翻不出去。
         */
        private void boundaryRidge() {
            for (int x = 0; x < WIDTH; x++) {
                for (int z = 0; z < DEPTH; z++) {
                    int edge = Math.min(Math.min(x, z), Math.min(WIDTH - 1 - x, DEPTH - 1 - z));
                    if (edge >= 5) continue;
                    int peak = 6 + Math.floorMod(x * 31 + z * 57, 5) + (edge < 2 ? 3 : 0);
                    for (int y = GROUND_Y + 1; y <= GROUND_Y + peak; y++) {
                        Kind body = Math.floorMod(x * 17 + y * 7 + z * 23, 9) < 3 ? Kind.MOSS : Kind.ROCK;
                        put(x, y, z, body);
                    }
                    put(x, GROUND_Y, z, Kind.ROCK);
                }
            }
        }

        /** 每个分区换一种地面材质，让 200×200 一眼能分出几个区。 */
        private void districtFloors() {
            canvas.flatten(30, 170, 8, 54, Kind.CONCRETE);     // 北院货场
            canvas.flatten(14, 58, 64, 148, Kind.PLATE);       // 西侧工业区
            canvas.flatten(142, 186, 64, 148, Kind.FLOOR);     // 东侧仓储市场
            canvas.flatten(40, 146, 150, 192, Kind.GRASS);     // 南侧托运者营地
            canvas.flatten(84, 116, 58, 86, Kind.FLOOR);       // 核心枢纽
        }

        /**
         * 台地：四个角各抬 1 格，作为货场月台与观景台。
         * 台地不承担连通性——所有点位都在基准平面，跳不上去也不影响开局路线。
         */
        private void terraces() {
            terrace(24, 38, 26, 40);
            terrace(162, 176, 26, 40);
            terrace(22, 30, 182, 192);
            terrace(168, 178, 182, 192);
        }

        private void terrace(int x0, int x1, int z0, int z1) {
            for (int x = x0; x <= x1; x++)
                for (int z = z0; z <= z1; z++) canvas.raise(x, z, 1, Kind.TERRACE, Kind.TERRACE_WALL);
        }

        /**
         * 假山群：只放在分区之间的空白带上，用来填补空旷、同时把几个区隔开。
         * 会挡住人的石堆都由后面画的道路切出通道，所以不会把地图割裂。
         */
        private void rockeries() {
            canvas.rockery(100, 44, 8, 9, 1);     // 核心北侧，主道从西南绕过去
            canvas.rockery(24, 56, 6, 6, 2);      // 西北角
            canvas.rockery(178, 54, 6, 6, 3);     // 东北角
            canvas.rockery(60, 120, 9, 8, 4);     // 西区中部石林
            canvas.rockery(134, 118, 8, 8, 5);    // 东区中部石林
            canvas.rockery(116, 148, 6, 6, 6);    // 南营东北
            canvas.rockery(24, 168, 6, 6, 7);     // 西南角
            canvas.rockery(180, 168, 6, 6, 8);    // 东南角
            canvas.rockery(70, 62, 5, 5, 9);      // 核心西北
            canvas.rockery(132, 62, 5, 5, 10);    // 核心东北
            canvas.rockery(100, 12, 7, 5, 11);    // 北院正中
            canvas.rockery(16, 20, 5, 5, 12);     // 北院西侧空地
            canvas.rockery(184, 20, 5, 5, 13);    // 北院东侧空地
            canvas.rockery(14, 100, 4, 4, 14);    // 西侧窄带
            canvas.rockery(188, 104, 4, 4, 15);   // 东侧窄带
        }

        /** 道路：折线 + 圆形印章，没有一条是笔直的长线。 */
        private void roads() {
            canvas.disc(100, 100, 20, Kind.PATH);          // 中央枢纽广场
            // 主脊：南营 → 广场 → 核心南门
            canvas.road(new int[][]{{100, 172}, {96, 150}, {102, 132}, {98, 116}, {100, 104}}, 4, Kind.PATH);
            canvas.road(new int[][]{{100, 104}, {97, 96}, {100, 88}}, 4, Kind.PATH);
            // 核心北门 → 北院货场（从假山西侧绕过去）
            canvas.road(new int[][]{{100, 56}, {94, 50}, {84, 44}, {74, 38}, {64, 32}, {56, 26}}, 3, Kind.PATH);
            // 西侧支线：广场 → 工业区 → 西南
            canvas.road(new int[][]{{100, 98}, {86, 100}, {72, 104}, {58, 108}, {46, 114}, {36, 122}, {30, 134}, {28, 146}},
                    3, Kind.PATH);
            // 东侧支线：广场 → 市场 → 东南
            canvas.road(new int[][]{{100, 98}, {114, 100}, {128, 104}, {142, 106}, {154, 110}, {166, 118}, {172, 130}},
                    3, Kind.PATH);
            // 北院环线
            canvas.road(new int[][]{{56, 26}, {74, 20}, {94, 18}, {114, 20}, {132, 26}, {146, 34}}, 3, Kind.PATH);
            // 南侧环线：营地 → 撤离门楼西侧
            canvas.road(new int[][]{{100, 172}, {116, 178}, {132, 182}, {144, 176}, {148, 168}, {146, 164}},
                    3, Kind.PATH);
            // 西侧南北联络道（不是直线）
            canvas.road(new int[][]{{28, 146}, {26, 160}, {34, 172}, {48, 178}}, 3, Kind.PATH);
            // 东侧南北联络道
            canvas.road(new int[][]{{172, 130}, {178, 142}, {176, 152}, {168, 158}}, 3, Kind.PATH);
        }

        // ---------- 建筑 ----------

        /**
         * 中心庄园：**两层**大宅，是整张图的视觉与玩法重心。
         *
         * 一层：外墙 6 高、四向门洞（南北宽门 + 东西侧门）、正中金库（东西两扇铁门）。
         * 二层：y=11 楼板（中间留中庭天井）、y=12..15 环廊、y=17..19 两级屋顶。
         * 楼梯：东西各一段，逐级抬升 1 格，canJump=false 时也能走上二层。
         *
         * 关键约束：一层与二层**只靠楼梯连通**；金库顶部在 y=10 封死，
         * 因此不能从二层天井跳进金库，核心区依然只能开门进入。
         */
        private void coreFortress() {
            // 地基与一层地面
            fill(80, 120, GROUND_Y, GROUND_Y, 54, 90, Kind.FLOOR);
            clear(81, 119, FEET_Y, 20, 55, 89);          // 先掏空，防止假山残留

            // 一层外墙（6 高）
            fill(80, 80, FEET_Y, 9, 54, 90, Kind.WALL);
            fill(120, 120, FEET_Y, 9, 54, 90, Kind.WALL);
            fill(80, 120, FEET_Y, 9, 54, 54, Kind.WALL);
            fill(80, 120, FEET_Y, 9, 90, 90, Kind.WALL);
            clear(97, 103, FEET_Y, 6, 54, 54);            // 北门
            clear(97, 103, FEET_Y, 6, 90, 90);            // 南门
            clear(80, 80, FEET_Y, 6, 66, 70);             // 西门
            clear(120, 120, FEET_Y, 6, 66, 70);           // 东门

            // 二层楼板：中间留中庭天井，让一层大厅有挑高
            fill(80, 120, 11, 11, 54, 90, Kind.ROOF);
            clear(92, 108, 11, 11, 66, 78);               // 中庭天井
            // 二层外墙
            fill(80, 80, 12, 16, 54, 90, Kind.WALL);
            fill(120, 120, 12, 16, 54, 90, Kind.WALL);
            fill(80, 120, 12, 16, 54, 54, Kind.WALL);
            fill(80, 120, 12, 16, 90, 90, Kind.WALL);
            // 两级屋顶
            fill(80, 120, 17, 17, 54, 90, Kind.ROOF);
            fill(82, 118, 18, 18, 56, 88, Kind.ROOF_DARK);
            fill(86, 114, 19, 19, 60, 84, Kind.ROOF_DARK);

            // 二层环廊：天井四周各留走道
            clear(81, 119, 12, 15, 55, 65);               // 北侧走廊
            clear(81, 119, 12, 15, 79, 89);               // 南侧走廊
            clear(81, 91, 12, 15, 66, 78);                // 西侧走廊
            clear(109, 119, 12, 15, 66, 78);              // 东侧走廊
            // 天井栏杆：1 格高，不挡视线但防止直接掉下去
            fill(92, 108, 12, 12, 66, 66, Kind.PILLAR);
            fill(92, 108, 12, 12, 78, 78, Kind.PILLAR);
            fill(92, 92, 12, 12, 66, 78, Kind.PILLAR);
            fill(108, 108, 12, 12, 66, 78, Kind.PILLAR);
            // 二层窗
            for (int z : new int[]{58, 62, 82, 86}) {
                put(80, 14, z, Kind.GLASS);
                put(120, 14, z, Kind.GLASS);
            }
            for (int x : new int[]{86, 94, 106, 114}) {
                put(x, 14, 54, Kind.GLASS);
                put(x, 14, 90, Kind.GLASS);
            }
            // 一层窗
            for (int z : new int[]{58, 62, 82, 86}) {
                put(80, 6, z, Kind.GLASS);
                put(120, 6, z, Kind.GLASS);
            }
            for (int x : new int[]{86, 94, 106, 114}) {
                put(x, 6, 54, Kind.GLASS);
                put(x, 6, 90, Kind.GLASS);
            }

            // 西侧楼梯：逐级抬升 1 格，禁用跳跃也能走上二层
            for (int step = 0; step < 8; step++) {
                int y = FEET_Y + step;
                int x0 = 82 + step;
                fill(x0, 87, y, y, 70, 74, Kind.PLATE);
                clear(x0, 87, y + 1, y + 2, 70, 74);      // 楼梯上方净空
            }
            // 东侧楼梯（镜像）
            for (int step = 0; step < 8; step++) {
                int y = FEET_Y + step;
                fill(113 - step, 118, y, y, 70, 74, Kind.PLATE);
                clear(113 - step, 118, y + 1, y + 2, 70, 74);
            }

            // 金库：一层正中的封闭小室，只有东西两扇铁门
            fill(92, 92, FEET_Y, 9, 66, 78, Kind.WALL);
            fill(108, 108, FEET_Y, 9, 66, 78, Kind.WALL);
            fill(92, 108, FEET_Y, 9, 66, 66, Kind.WALL);
            fill(92, 108, FEET_Y, 9, 78, 78, Kind.WALL);
            clear(93, 107, FEET_Y, 9, 67, 77);            // 金库内部
            fill(92, 92, FEET_Y, 6, 71, 73, Kind.GATE);
            fill(108, 108, FEET_Y, 6, 71, 73, Kind.GATE);
            // 门控面板由 marker(Kind.PANEL, ...) 统一写成 2 格高机器（根 y=4 + 上半 y=5），
            // 这里不再单独 put，避免只写了上半格导致机器表缺根。
            // 金库顶部封到二层楼板，避免从二层天井跳进核心区
            fill(92, 108, 10, 10, 66, 78, Kind.ROOF_DARK);

            // 一层大厅灯柱
            for (int[] lamp : new int[][]{{86, 60}, {114, 60}, {86, 84}, {114, 84}}) {
                fill(lamp[0], lamp[0], FEET_Y, 9, lamp[1], lamp[1], Kind.PILLAR);
                put(lamp[0], 10, lamp[1], Kind.LAMP);
            }
            // 二层环廊灯
            for (int[] lamp : new int[][]{{84, 58}, {116, 58}, {84, 86}, {116, 86}}) {
                fill(lamp[0], lamp[0], 12, 15, lamp[1], lamp[1], Kind.PILLAR);
                put(lamp[0], 16, lamp[1], Kind.LAMP);
            }
        }

        /** 撤离门楼：门朝西，门后是封闭检测区；控制台独立放在门外平台。 */
        private void evacuationGatehouse() {
            fill(150, 182, GROUND_Y, GROUND_Y, 152, 178, Kind.FLOOR);
            clear(151, 181, FEET_Y, 8, 153, 177);   // 先掏空门楼，防止假山残留
            fill(150, 150, FEET_Y, 8, 152, 178, Kind.WALL);
            fill(182, 182, FEET_Y, 8, 152, 178, Kind.WALL);
            fill(150, 182, FEET_Y, 8, 152, 152, Kind.WALL);
            fill(150, 182, FEET_Y, 8, 178, 178, Kind.WALL);
            fill(150, 150, FEET_Y, 6, 163, 165, Kind.GATE);
            fill(150, 182, 9, 9, 152, 178, Kind.ROOF);
            fill(152, 180, 10, 10, 154, 176, Kind.ROOF_DARK);
            for (int z : new int[]{156, 160, 168, 172, 176}) put(182, 6, z, Kind.GLASS);
            for (int[] lamp : new int[][]{{156, 156}, {176, 156}, {156, 174}, {176, 174}}) {
                fill(lamp[0], lamp[0], FEET_Y, 7, lamp[1], lamp[1], Kind.PILLAR);
                put(lamp[0], 8, lamp[1], Kind.LAMP);
            }
        }

        /** 北院货场：集装箱堆、龙门吊、货运棚。 */
        private void northYard() {
            for (int[] box : new int[][]{{44, 16}, {60, 16}, {76, 16}, {44, 30}, {136, 16}, {152, 16}, {136, 30},
                    {152, 30}, {50, 42}, {146, 42}}) {
                canvas.container(box[0], box[1], 6, 3, 2);
            }
            canvas.container(88, 12, 8, 3, 3);
            canvas.container(104, 12, 8, 3, 3);
            // 龙门吊：四根立柱 + 顶梁，横跨货场东侧
            for (int[] post : new int[][]{{118, 34}, {142, 34}, {118, 46}, {142, 46}}) {
                fill(post[0], post[0], FEET_Y, 9, post[1], post[1], Kind.PILLAR);
            }
            fill(118, 142, 10, 10, 34, 34, Kind.PLATE);
            fill(118, 142, 10, 10, 46, 46, Kind.PLATE);
            fill(118, 118, 10, 10, 34, 46, Kind.PLATE);
            fill(142, 142, 10, 10, 34, 46, Kind.PLATE);
            put(130, 10, 40, Kind.LAMP);
            // 货运棚
            fill(58, 82, GROUND_Y, GROUND_Y, 40, 52, Kind.FLOOR);
            fill(58, 58, FEET_Y, 8, 40, 52, Kind.WALL);
            fill(82, 82, FEET_Y, 8, 40, 52, Kind.WALL);
            fill(58, 82, FEET_Y, 8, 40, 40, Kind.WALL);
            fill(58, 82, FEET_Y, 8, 52, 52, Kind.WALL);
            clear(64, 69, FEET_Y, 6, 52, 52);
            fill(58, 82, 9, 9, 40, 52, Kind.ROOF);
            fill(60, 80, 10, 10, 42, 50, Kind.ROOF_DARK);
        }

        /** 西侧工业区：厂房、料仓、烟囱、管线。 */
        private void westWorks() {
            canvas.house(20, 40, 66, 82, 8, Kind.PLATE);
            canvas.house(20, 38, 88, 104, 8, Kind.PLATE);
            canvas.house(22, 44, 108, 122, 8, Kind.PLATE);
            canvas.house(18, 34, 132, 148, 7, Kind.PLATE);
            // 烟囱与料仓（整格实心，不用流体）
            fill(48, 48, FEET_Y, 13, 70, 70, Kind.PILLAR);
            fill(52, 52, FEET_Y, 11, 74, 74, Kind.PILLAR);
            fill(48, 48, 14, 14, 70, 70, Kind.LAMP);
            fill(50, 54, GROUND_Y, GROUND_Y, 90, 96, Kind.PLATE);
            fill(50, 54, FEET_Y, FEET_Y + 2, 90, 96, Kind.DARKSTONE);
            for (int x = 20; x <= 44; x += 8) fill(x, x, FEET_Y, FEET_Y, 106, 108, Kind.PLATE);
            for (int[] lamp : new int[][]{{14, 96}, {46, 90}, {14, 136}, {40, 140}}) {
                canvas.lamp(lamp[0], lamp[1], 4);
            }
        }

        /** 东侧仓储市场：仓库、摊位、货堆。 */
        private void eastMarket() {
            canvas.house(156, 178, 66, 84, 8, Kind.FLOOR);
            canvas.house(158, 176, 90, 106, 8, Kind.FLOOR);
            canvas.house(154, 174, 112, 126, 8, Kind.FLOOR);
            canvas.house(158, 176, 134, 148, 7, Kind.FLOOR);
            // 摊位：两排台面 + 顶棚
            for (int z : new int[]{96, 126}) {
                for (int x = 146; x <= 152; x += 3) {
                    fill(x, x + 1, FEET_Y, FEET_Y, z, z + 3, Kind.TABLE);
                    fill(x, x + 1, FEET_Y + 2, FEET_Y + 2, z, z + 3, Kind.ROOF);
                    put(x, FEET_Y + 1, z, Kind.LOG);
                    put(x + 1, FEET_Y + 1, z + 3, Kind.LOG);
                }
            }
            for (int[] lamp : new int[][]{{144, 90}, {144, 132}, {186, 96}, {186, 120}}) {
                canvas.lamp(lamp[0], lamp[1], 4);
            }
        }

        /** 南侧托运者营地：帐篷、营地灯。 */
        private void southCamp() {
            for (int[] tent : new int[][]{{46, 154}, {78, 154}, {114, 154}, {136, 156}, {56, 178}, {100, 182},
                    {130, 182}}) {
                fill(tent[0], tent[0] + 3, FEET_Y, FEET_Y + 2, tent[1], tent[1] + 3, Kind.WALL);
                fill(tent[0] - 1, tent[0] + 4, FEET_Y + 3, FEET_Y + 3, tent[1] - 1, tent[1] + 4, Kind.ROOF);
            }
            for (int x = 44; x <= 144; x += 12) canvas.lamp(x, 148, 4);
        }

        /**
         * 灌木带、矮墙与行道树：专门用来打散 200×200 的空旷感。
         *
         * <p>三条规则：
         * <ul>
         * <li>草丛（{@link Kind#BUSH}）**无碰撞**，只挡视线、提供隐身点，绝不切断通路；</li>
         * <li>矮墙（{@link Kind#LOW_WALL}）只有 1 格高，挡视线不挡走位；</li>
         * <li>两者都只沿道路两侧与分区边界铺，不落在任何点位格上（点位在 {@code points()} 里最后校验）。</li>
         * </ul>
         */
        private void gardens() {
            // 行道树：沿主要道路外侧
            for (int[] tree : new int[][]{{12, 72}, {12, 100}, {12, 128}, {188, 72}, {188, 100}, {188, 128},
                    {44, 90}, {156, 90}, {60, 148}, {140, 148}, {70, 158}, {130, 158},
                    {70, 88}, {130, 88}, {50, 62}, {150, 62}}) {
                canvas.tree(tree[0], tree[1], 4);
            }

            // 草丛：成片铺在道路之间的空地上，制造大量可藏身的位置
            bushField(30, 58, 62, 70);      // 西侧空地
            bushField(138, 58, 170, 70);    // 东侧空地
            bushField(30, 132, 62, 146);    // 西南
            bushField(138, 132, 170, 146);  // 东南
            bushField(72, 96, 128, 108);    // 广场西侧
            bushField(66, 150, 134, 160);   // 南营北缘
            bushField(40, 76, 50, 60);      // 北院西
            bushField(148, 76, 176, 90);    // 市场南
            bushField(18, 96, 28, 120);     // 西侧窄带
            bushField(172, 96, 184, 120);   // 东侧窄带

            // 矮墙：把大块空地切分成有遮挡的小院，同时不挡走位
            lowWallRow(34, 66, 58, 66);     // 西区横向隔断
            lowWallRow(142, 66, 166, 66);   // 东区横向隔断
            lowWallRow(34, 138, 58, 138);   // 西南横向隔断
            lowWallRow(142, 138, 166, 138); // 东南横向隔断
            lowWallCol(66, 76, 66, 94);     // 广场西北竖向隔断
            lowWallCol(134, 76, 134, 94);   // 广场东北竖向隔断
            lowWallCol(66, 112, 66, 130);   // 广场西南
            lowWallCol(134, 112, 134, 130); // 广场东南
        }

        /** 矩形草丛带：逐格铺，跳过已有实心方块与道路，保证不会盖住铺装。 */
        private void bushField(int x0, int z0, int x1, int z1) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    // 只在基准平面的空地上铺草丛，避免盖住道路/建筑/台地
                    if (!at(x, GROUND_Y, z).solid()) continue;
                    if (at(x, FEET_Y, z) != Kind.AIR) continue;
                    Kind ground = at(x, GROUND_Y, z);
                    if (ground == Kind.PATH || ground == Kind.FLOOR || ground == Kind.CONCRETE
                            || ground == Kind.PLATE || ground == Kind.TERRACE) continue;
                    // 用坐标哈希留出空隙，避免变成一整块绿色方块
                    if (Math.floorMod(x * 17 + z * 31, 10) < 3) continue;
                    put(x, FEET_Y, z, Kind.BUSH);
                }
            }
        }

        /** 横向矮墙：只写 1 格高，遇到非空地自动跳过。 */
        private void lowWallRow(int x0, int z, int x1, int ignored) {
            for (int x = x0; x <= x1; x++) {
                if (!at(x, GROUND_Y, z).solid() || at(x, FEET_Y, z) != Kind.AIR) continue;
                put(x, FEET_Y, z, Kind.LOW_WALL);
            }
        }

        /** 纵向矮墙。 */
        private void lowWallCol(int x, int z0, int ignored, int z1) {
            for (int z = z0; z <= z1; z++) {
                if (!at(x, GROUND_Y, z).solid() || at(x, FEET_Y, z) != Kind.AIR) continue;
                put(x, FEET_Y, z, Kind.LOW_WALL);
            }
        }

        // ---------- 点位 ----------

        /** 为完整机器与一格宽的整幅正面通道留位；只清理局部装饰，不削穿结构墙。 */
        private void marker(Kind kind, String id, int x, int z) {
            Model model = switch (kind) {
                case MARKER_BEACON -> id.equals("beacon/core") ? Model.CORE_BEACON : Model.BEACON;
                case MARKER_CRYSTAL -> Model.CRYSTAL;
                case MARKER_ALTAR -> Model.ALTAR;
                case MARKER_CRATE -> Model.SUPPLY;
                case MARKER_BLACK_CRATE -> Model.BLACK_MARKET;
                case MARKER_BLACK_NODE -> Model.BLACK_NODE;
                case MARKER_BLACK_DROPOFF -> Model.BLACK_DROPOFF;
                case MARKER_BROKER -> Model.BROKER_EXIT;
                case MARKER_FREE_EXIT -> Model.FREE_EXIT;
                case PANEL -> id.equals("evac/control") ? Model.EVACUATION_CONSOLE : Model.CORE_CONSOLE;
                default -> throw new IllegalArgumentException("不是机器: " + kind);
            };
            Facing facing = id.equals("core/west") || id.equals("evac/control") ? Facing.WEST
                    : id.equals("core/east") ? Facing.EAST
                    : id.equals("beacon/outer_b") ? Facing.SOUTH : Facing.NORTH;
            Marker root = new Marker(kind, id, x, FEET_Y, z);
            Machine machine = new Machine(root, model, facing);
            boolean core = model == Model.CORE_BEACON || model == Model.BLACK_MARKET || model == Model.FREE_EXIT;
            for (int dx = 0; dx < model.width(); dx++) for (int dz = -1; dz < model.depth(); dz++) {
                int cx = machine.x(dx, dz), cz = machine.z(dx, dz);
                // 通道也必须与机器位于同一区域，不能借清空占地打穿金库或撤离围墙。
                if ((cx >= 93 && cx < 108 && cz >= 66 && cz < 79) != core
                        || (cx >= 150 && cx <= 182 && cz >= 152 && cz <= 178)
                        || reservedSpawn(cx, cz))
                    throw new IllegalArgumentException("机器侵入保护区: " + id + " @" + cx + "," + cz);
                for (int dy = 0; dy < model.height(); dy++) {
                    int cy = FEET_Y + dy, key = Layout.index(cx, cy, cz);
                    Kind present = at(cx, cy, cz);
                    if (present == Kind.WALL || present == Kind.GATE || present.marker()
                            || present == Kind.MACHINE_PART || (dz >= 0 && machineSpace.contains(key)))
                        throw new IllegalArgumentException("机器空间冲突: " + id + " @" + cx + "," + cy + "," + cz + " " + present);
                    machineSpace.add(key);
                    put(cx, cy, cz, Kind.AIR);
                }
                // 机器高 2 格（y=4..5），但步行图要求脚、头、头上再一格都为空；
                // 机器高 2 格（y=4..5），但步行图与通道检查都要求脚、头、头上再一格为空；
                // 这里对「机器格 + 正面通道格」统一清掉 y=6，否则会被判成没有净空。
                int clearY = FEET_Y + model.height();
                if (at(cx, clearY, cz) != Kind.AIR && !machineSpace.contains(Layout.index(cx, clearY, cz)))
                    put(cx, clearY, cz, Kind.AIR);
                if (!at(cx, GROUND_Y, cz).solid()) put(cx, GROUND_Y, cz, Kind.PLATE);
            }
            for (int dy = 0; dy < model.height(); dy++) for (int dz = 0; dz < model.depth(); dz++)
                for (int dx = 0; dx < model.width(); dx++) {
                    int part = dx + 3 * dz + 9 * dy;
                    Cell cell = new Cell(machine, part);
                    put(cell.x(), cell.y(), cell.z(), part == 0 ? kind : Kind.MACHINE_PART);
                    machineCells.put(Layout.index(cell.x(), cell.y(), cell.z()), cell);
                }
            markers.add(root);
        }
        private boolean reservedSpawn(int x, int z) {
            if ((x == 100 && z == 92) || (x == 146 && (z == 163 || z == 170))
                    || (x == 100 && z == 100)) return true;
            return java.util.stream.Stream.of(carriers, interceptors, brokers).flatMap(List::stream)
                    .anyMatch(p -> p.x() == x && p.z() == z);
        }

        private void spawnCheck(String what, int x, int z) {
            if (!at(x, GROUND_Y, z).solid() || at(x, FEET_Y, z) != Kind.AIR || at(x, FEET_Y + 1, z) != Kind.AIR)
                throw new IllegalArgumentException(what + "出生点不合法: " + x + "," + z);
        }

        private void carrier(int x, int z) {
            spawnCheck("托运者", x, z);
            carriers.add(new SpawnPoint(x, z, 180.0F));
        }

        private void interceptor(int x, int z) {
            spawnCheck("截流者", x, z);
            interceptors.add(new SpawnPoint(x, z, 0.0F));
        }

        private void broker(int x, int z) {
            spawnCheck("投机客", x, z);
            brokers.add(new SpawnPoint(x, z, 90.0F));
        }

        private void points() {
            // 出生点：南营托运者、北院截流者、东西两侧与南侧投机客
            int[][] carrierSpots = {{52, 156}, {64, 160}, {76, 164}, {88, 168}, {100, 172}, {112, 168},
                    {124, 164}, {136, 160}, {148, 156}, {52, 172}, {64, 176}, {76, 180}, {88, 184},
                    {100, 188}, {112, 184}, {124, 180}, {136, 176}, {148, 172}, {70, 168}, {90, 176},
                    {120, 176}};
            for (int[] p : carrierSpots) carrier(p[0], p[1]);
            int[][] interceptorSpots = {{56, 28}, {72, 22}, {88, 18}, {112, 18}, {128, 22}, {144, 28}};
            for (int[] p : interceptorSpots) interceptor(p[0], p[1]);
            int[][] brokerSpots = {{20, 128}, {44, 136}, {180, 128}, {180, 140}, {100, 144}};
            for (int[] p : brokerSpots) broker(p[0], p[1]);

            // 六芒星阵：圆心 = 中央广场 (100,100)，六个顶点按 60° 均匀分布。
            // 半径 56：足以避开中心庄园（x80..120、z54..90）与撤离门楼，又留在 200×200 图内。
            // 顶点 = 圆心 + R × (sin θ, -cos θ)，θ = 0/60/120/180/240/300。
            int[][] hexagram = {
                    {40, 20},     // 正上 θ=0   晶体（六芒星北尖角，货场西北开阔地）
                    {148, 72},    // 右上 θ=60  信标
                    {148, 128},   // 右下 θ=120 晶体
                    {100, 156},   // 正下 θ=180 信标
                    {52, 128},    // 左下 θ=240 晶体
                    {52, 72},     // 左上 θ=300 信标
            };
            // 六芒星六顶点：3 晶体 + 2 开放信标 + 1 祭坛。
            // 信标总数受人数档硬约束（N≤7 为 2 座、N≥8 为 3 座，含 1 座核心），
            // 所以第 6 个顶点用祭坛占位，六芒星轮廓完整且不破坏校验。
            crystals.add(new Node("c1", hexagram[0][0], hexagram[0][1], 6));                 // 正上 晶体
            beacons.add(new Beacon("outer_a", hexagram[1][0], hexagram[1][1], "open", 6));   // 右上 信标
            crystals.add(new Node("c2", hexagram[2][0], hexagram[2][1], 6));                 // 右下 晶体
            beacons.add(new Beacon("outer_b", hexagram[3][0], hexagram[3][1], "open", 8));   // 正下 信标
            crystals.add(new Node("c3", hexagram[4][0], hexagram[4][1], 6));                 // 左下 晶体
            altars.add(new Point("hexagram_nw", hexagram[5][0], hexagram[5][1]));            // 左上 祭坛
            // 核心信标在金库内（唯一 region=core）
            beacons.add(new Beacon("core", 100, 73, "core", 6));
            // 全部放在庄园外围的开放地面上，保证不被建筑围死。
            crystals.add(new Node("c4", 100, 24, 6));
            crystals.add(new Node("c5", 150, 112, 8));
            crystals.add(new Node("c6", 20, 177, 8));
            for (Beacon beacon : beacons) marker(Kind.MARKER_BEACON, "beacon/" + beacon.id(), beacon.x(), beacon.z());
            for (Node node : crystals) marker(Kind.MARKER_CRYSTAL, "crystal/" + node.id(), node.x(), node.z());

            altars.add(new Point("a1", 62, 92));
            altars.add(new Point("a2", 138, 92));
            for (Point altar : altars) marker(Kind.MARKER_ALTAR, "altar/" + altar.id(), altar.x(), altar.z());

            int[][] commonCrates = {{30, 84}, {30, 128}, {186, 84}, {188, 116}, {70, 140}, {130, 140},
                    {60, 60}, {140, 60}, {62, 164}, {138, 164}};
            for (int n = 0; n < commonCrates.length; n++) {
                String id = "s" + (n + 1);
                crates.add(new Crate(id, commonCrates[n][0], commonCrates[n][1], "COMMON", true));
                marker(Kind.MARKER_CRATE, "crate/" + id, commonCrates[n][0], commonCrates[n][1]);
            }
            int[][] blackMarket = {{96, 74}, {104, 74}};
            for (int n = 0; n < blackMarket.length; n++) {
                String id = "b" + (n + 1);
                crates.add(new Crate(id, blackMarket[n][0], blackMarket[n][1], "BLACK_MARKET", false));
                marker(Kind.MARKER_BLACK_CRATE, "crate/" + id, blackMarket[n][0], blackMarket[n][1]);
            }

            blackNodes.add(new Point("black_w", 16, 110));
            blackNodes.add(new Point("black_e", 180, 110));
            for (Point node : blackNodes) marker(Kind.MARKER_BLACK_NODE, "black/" + node.id(), node.x(), node.z());
            blackDropoffs.add(new Point("drop_w", 44, 166));
            blackDropoffs.add(new Point("drop_e", 156, 60));
            for (Point drop : blackDropoffs) marker(Kind.MARKER_BLACK_DROPOFF, "drop/" + drop.id(), drop.x(), drop.z());

            brokerExit = new Point("broker_exit", 100, 180);
            marker(Kind.MARKER_BROKER, "broker/" + brokerExit.id(), brokerExit.x(), brokerExit.z());
            freeControl = new Point("service_exit", 100, 68);
            marker(Kind.MARKER_FREE_EXIT, "free/" + freeControl.id(), freeControl.x(), freeControl.z());

            // 控制台不再嵌墙：根点、完整占地和正面通道都在门外平台。
            // 门体范围必须与 fill(...Kind.GATE) 完全一致：x 固定、y=4..7、z=71..73（半开区间 71..74）。
            // 面板在 z=70 的墙上，不在门体里，否则关门时会被一起写空气而留下缺口。
            doors.add(new Door("west", 90, 70, new Box(92, 4, 71, 93, 7, 74)));
            doors.add(new Door("east", 110, 70, new Box(108, 4, 71, 109, 7, 74)));
            for (Door door : doors) marker(Kind.PANEL, "core/" + door.id(), door.panelX(), door.panelZ());
            marker(Kind.PANEL, "evac/control", 148, 160);
        }

        private Layout finish() {
            return new Layout(canvas.snapshot(), List.copyOf(carriers), List.copyOf(interceptors), List.copyOf(brokers),
                    List.copyOf(beacons), List.copyOf(crystals), List.copyOf(altars), List.copyOf(crates),
                    List.copyOf(blackNodes), List.copyOf(blackDropoffs), brokerExit, freeControl,
                    new SpawnPoint(100, 96, 180.0F), List.copyOf(doors), new Box(93, 4, 67, 108, 10, 78),
                    new Point("evac_control", 148, 160), new Box(150, 4, 163, 151, 7, 166),
                    new Box(152, 4, 156, 178, 8, 176), new SpawnPoint(146, 163, 270.0F),
                    new Point("exit_drop", 146, 170), new SpawnPoint(100, 100, 180.0F),
                    List.copyOf(markers), Map.copyOf(machineCells));
        }
    }
}
