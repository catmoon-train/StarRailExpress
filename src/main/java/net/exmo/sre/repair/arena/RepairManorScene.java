package net.exmo.sre.repair.arena;

import io.wifi.starrailexpress.index.TMMBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.agmas.noellesroles.init.ModBlocks;

import java.util.ArrayList;
import java.util.List;

/**
 * 修机模式默认场景：庄园 + 后院 + 墓地（含教堂与地穴）。
 *
 * <p>设计原则：
 * <ul>
 * <li>主通道全部用无门拱廊，幸存者永远不会被锁死在房间里；锁只出现在 3 个捷径上。</li>
 * <li>每个房间至少两个出入口，形成追逐环路；托盘放在拱口做反制点。</li>
 * <li>庄园明亮（理智安全区），墓地昏暗（理智侵蚀区）但战利品更好 —— 风险换收益。</li>
 * <li>出口大门方块放在地板层的净空豁口中，让它的自建拱门结构能正常生成。</li>
 * </ul>
 *
 * <p>结构不变量（改坐标时务必维持）：
 * <ul>
 * <li>y=0 是地表方块层，玩家脚踩 y=1；y=-1/-2 是实心地基，所以任何门口、拱口、
 * 楼梯井下方都不会露出虚空。地穴自带更深的地基。</li>
 * <li>楼梯的 FACING 指向"高的那一侧"：朝 D 方向上行用 FACING=D，朝 D 方向下行用
 * FACING=opposite(D)。地穴向北下行 → FACING=SOUTH。</li>
 * <li>床、家具、朝向类装饰一律显式设置 FACING/PART，不要依赖默认状态。</li>
 * </ul>
 *
 * 平面（相对 base，x 0..64 西→东）：
 * <pre>
 * z112 ─┬────────墓地北门(出口2)────────┬─
 *       │  墓碑阵   [地穴入口]    教堂   │   地穴(锁#3+密道) 教堂(修机台7 + 锁#2)
 * z64  ─┴──墓地南门──────工坊捷径口──────┴─
 * z57  ────────────后院小径───────────────
 * z56  ──庄园北墙(大厅拱门 / 工坊捷径铁门 锁#1)──
 *       │ 医务室 │           │  工坊  │   修机台5(医务) 修机台6(工坊) 笼子(工坊)
 * z38  ─┤  木门  │   大 厅   │  木门  ├─
 *       │  餐厅  │ (笼子/立柱) │ 藏书馆 │   修机台3(餐厅) 修机台4(藏书) 笼子(餐厅) 服务电梯(藏书东墙)
 * z18  ─┤  木门  │───大 拱───│  木门  ├─
 *       │  厨房  │   门厅    │  书房  │   修机台1(厨房) 修机台2(书房)
 * z0   ─┴──────────主门(出口1)──────────┴─
 *      x0      x20         x44        x64
 * </pre>
 */
public final class RepairManorScene {
    @FunctionalInterface
    public interface Placer {
        void place(BlockPos pos, BlockState state);
    }

    // ==================== 尺寸常量 ====================

    /** 场景外接框（含散水与树篱），clearVolume / 地基都以此为界。 */
    private static final int MIN_X = -3;
    private static final int MAX_X = 67;
    private static final int MIN_Z = -3;
    private static final int MAX_Z = 115;
    private static final int SKY_Y = 8;

    /** 庄园：x 0..64，z 0..56。 */
    private static final int MANOR_X1 = 64;
    private static final int MANOR_Z1 = 56;
    /** 西翼 / 东翼隔墙。 */
    private static final int WING_W = 20;
    private static final int WING_E = 44;
    /** 翼内南 / 北横墙。 */
    private static final int CROSS_S = 18;
    private static final int CROSS_N = 38;
    private static final int WALL_TOP = 6;
    private static final int ROOF_Y = 7;

    /** 后院与墓地。 */
    private static final int YARD_Z0 = 57;
    private static final int GRAVE_Z0 = 64;
    private static final int GRAVE_Z1 = 112;

    /** 教堂：x 38..58，z 80..102。 */
    private static final int CHAPEL_X0 = 38;
    private static final int CHAPEL_X1 = 58;
    private static final int CHAPEL_Z0 = 80;
    private static final int CHAPEL_Z1 = 102;

    /** 地穴外壳：x 7..23，z 76..102，天花板 y=-1，地板 y=-5。 */
    private static final int CRYPT_X0 = 7;
    private static final int CRYPT_X1 = 23;
    private static final int CRYPT_Z0 = 76;
    private static final int CRYPT_Z1 = 102;
    private static final int CRYPT_FLOOR_Y = -5;
    private static final int CRYPT_CEIL_Y = -1;
    /** 地穴竖井（在地表陵墓内部），x 13..15。 */
    private static final int SHAFT_X0 = 13;
    private static final int SHAFT_X1 = 15;
    private static final int SHAFT_Z0 = 79;

    // ==================== 与 RepairLockedDoorState 共享的坐标 ====================

    /** 锁 #1：工坊 → 墓地捷径后门（旧钥匙）。 */
    public static final int[] LOCK_WORKSHOP_BACKDOOR = { 55, 1, MANOR_Z1 };
    /** 锁 #2：教堂东侧门（撬锁器）。 */
    public static final int[] LOCK_CHAPEL_SIDE_DOOR = { CHAPEL_X1, 1, 83 };
    /** 锁 #3：地穴内铁门（旧钥匙），门后是密道与高级战利品。 */
    public static final int[] LOCK_CRYPT_GATE = { 14, CRYPT_FLOOR_Y + 1, 92 };
    /** 逃生路线：地穴密道铁栏。 */
    public static final int[] ROUTE_CRYPT_TUNNEL = { 14, CRYPT_FLOOR_Y + 1, 101 };
    /** 逃生路线：藏书馆东墙服务电梯。 */
    public static final int[] ROUTE_SERVICE_LIFT = { MANOR_X1, 2, 28 };

    public static BlockPos at(BlockPos base, int[] offset) {
        return base.offset(offset[0], offset[1], offset[2]);
    }

    // ==================== 方块调色板 ====================

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState WALL = Blocks.DARK_OAK_PLANKS.defaultBlockState();
    private static final BlockState PILLAR = Blocks.SPRUCE_LOG.defaultBlockState();
    private static final BlockState ROOF = Blocks.SPRUCE_SLAB.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState DEEP = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
    private static final BlockState FOUNDATION = Blocks.STONE.defaultBlockState();
    private static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();
    private static final BlockState HANGING_LANTERN = Blocks.LANTERN.defaultBlockState()
            .setValue(LanternBlock.HANGING, true);

    /** 追捕者出生点（墓地中部，远离幸存者）。 */
    private static final int[][] HUNTER_SPAWNS = {
            { 32, 1, 84 }, { 28, 1, 78 }, { 36, 1, 78 }, { 32, 1, 72 }
    };
    /** 幸存者出生点（庄园各房间分散，两两之间隔墙）。 */
    private static final int[][] SURVIVOR_SPAWNS = {
            { 6, 1, 4 }, { 58, 1, 4 }, { 28, 1, 5 }, { 36, 1, 5 },
            { 10, 1, 30 }, { 54, 1, 30 }, { 10, 1, 50 }, { 54, 1, 50 }
    };

    private RepairManorScene() {
    }

    public static int[][] hunterSpawns() {
        return HUNTER_SPAWNS;
    }

    public static int[][] survivorSpawns() {
        return SURVIVOR_SPAWNS;
    }

    /**
     * 战利品柜位置 {x, y, z}。前 9 个位置分布全图，会被 RepairLootSpawner
     * 塞入 9 件必需品（逃生道具等），后面的位置是随机战利品。
     */
    public static List<int[]> lootOffsets() {
        List<int[]> offsets = new ArrayList<>();
        // 必需品位（分散强迫探图；两个在墓地/地穴）
        offsets.add(new int[] { 3, 1, 3 });      // 厨房
        offsets.add(new int[] { 61, 1, 3 });     // 书房
        offsets.add(new int[] { 3, 1, 36 });     // 餐厅
        offsets.add(new int[] { 61, 1, 36 });    // 藏书馆
        offsets.add(new int[] { 3, 1, 53 });     // 医务室
        offsets.add(new int[] { 61, 1, 53 });    // 工坊
        offsets.add(new int[] { 22, 1, 53 });    // 大厅西北角
        offsets.add(new int[] { 41, 1, 82 });    // 教堂门厅
        offsets.add(new int[] { 10, -4, 86 });   // 地穴前厅（昏暗高危）
        // 随机战利品位
        offsets.add(new int[] { 17, 1, 3 });
        offsets.add(new int[] { 47, 1, 3 });
        offsets.add(new int[] { 22, 1, 3 });
        offsets.add(new int[] { 42, 1, 3 });
        offsets.add(new int[] { 3, 1, 15 });
        offsets.add(new int[] { 61, 1, 15 });
        offsets.add(new int[] { 17, 1, 21 });
        offsets.add(new int[] { 47, 1, 21 });
        offsets.add(new int[] { 3, 1, 21 });
        offsets.add(new int[] { 61, 1, 21 });
        offsets.add(new int[] { 22, 1, 21 });
        offsets.add(new int[] { 42, 1, 21 });
        offsets.add(new int[] { 17, 1, 41 });
        offsets.add(new int[] { 47, 1, 41 });
        offsets.add(new int[] { 42, 1, 53 });
        offsets.add(new int[] { 3, 1, 41 });
        offsets.add(new int[] { 61, 1, 41 });
        // 墓地
        offsets.add(new int[] { 6, 1, 70 });
        offsets.add(new int[] { 58, 1, 70 });
        offsets.add(new int[] { 6, 1, 106 });
        offsets.add(new int[] { 58, 1, 106 });
        offsets.add(new int[] { 55, 1, 99 });    // 教堂圣坛旁
        offsets.add(new int[] { 41, 1, 99 });
        // 地穴深处（锁 #3 之后）
        offsets.add(new int[] { 10, -4, 96 });
        offsets.add(new int[] { 20, -4, 96 });
        return List.copyOf(offsets);
    }

    // ==================== 总装 ====================

    public static void build(Placer p, BlockPos base) {
        // 1) 清空地上空间
        fill(p, base, MIN_X, 1, MIN_Z, MAX_X, SKY_Y, MAX_Z, AIR);
        // 2) 两层实心地基 —— 这是"门口/楼梯口踩空掉虚空"的根治手段
        fill(p, base, MIN_X, -2, MIN_Z, MAX_X, -1, MAX_Z, FOUNDATION);
        // 3) 地穴专用深层地基（先填实，再由 buildCrypt 掏空）
        fill(p, base, CRYPT_X0 - 2, CRYPT_FLOOR_Y - 2, CRYPT_Z0 - 2,
                CRYPT_X1 + 2, -3, CRYPT_Z1 + 2, FOUNDATION);
        // 4) 地表兜底
        for (int x = MIN_X; x <= MAX_X; x++) {
            for (int z = MIN_Z; z <= MAX_Z; z++) {
                p.place(base.offset(x, 0, z), Blocks.GRASS_BLOCK.defaultBlockState());
            }
        }
        // 5) 场地四周树篱（防止玩家走出场景边缘）
        BlockState hedge = Blocks.SPRUCE_LEAVES.defaultBlockState()
                .setValue(BlockStateProperties.PERSISTENT, true);
        for (int x = MIN_X; x <= MAX_X; x++) {
            for (int z = MIN_Z; z <= MAX_Z; z++) {
                if (x == MIN_X || x == MAX_X || z == MIN_Z || z == MAX_Z) {
                    for (int y = 1; y <= 3; y++) {
                        p.place(base.offset(x, y, z), hedge);
                    }
                }
            }
        }

        buildManor(p, base);
        buildYard(p, base);
        buildGraveyard(p, base);

        // 储物柜最后放置，避免被房间体块覆盖（地穴内的柜子在 y<0）
        for (int[] loot : lootOffsets()) {
            p.place(base.offset(loot[0], loot[1], loot[2]), ModBlocks.HOTBAR_STORAGE.defaultBlockState());
        }
    }

    // ==================== 庄园 ====================

    private static void buildManor(Placer p, BlockPos base) {
        // 地板 + 外圈石砖散水
        for (int x = -3; x <= MANOR_X1 + 3; x++) {
            for (int z = -3; z <= MANOR_Z1 + 3; z++) {
                boolean inside = x >= 0 && x <= MANOR_X1 && z >= 0 && z <= MANOR_Z1;
                p.place(base.offset(x, 0, z), inside ? floorPattern(x, z) : GRAVEL);
            }
        }

        // 外墙（含窗）与转角立柱
        for (int y = 1; y <= WALL_TOP; y++) {
            for (int x = 0; x <= MANOR_X1; x++) {
                p.place(base.offset(x, y, 0), outerWall(x, y));
                p.place(base.offset(x, y, MANOR_Z1), outerWall(x, y));
            }
            for (int z = 0; z <= MANOR_Z1; z++) {
                p.place(base.offset(0, y, z), outerWall(z, y));
                p.place(base.offset(MANOR_X1, y, z), outerWall(z, y));
            }
        }

        // 内墙：东西翼隔墙（全长）
        fill(p, base, WING_W, 1, 1, WING_W, WALL_TOP, MANOR_Z1 - 1, WALL);
        fill(p, base, WING_E, 1, 1, WING_E, WALL_TOP, MANOR_Z1 - 1, WALL);
        // 翼内横墙（只在两翼）
        fill(p, base, 1, 1, CROSS_S, WING_W - 1, WALL_TOP, CROSS_S, WALL);
        fill(p, base, 1, 1, CROSS_N, WING_W - 1, WALL_TOP, CROSS_N, WALL);
        fill(p, base, WING_E + 1, 1, CROSS_S, MANOR_X1 - 1, WALL_TOP, CROSS_S, WALL);
        fill(p, base, WING_E + 1, 1, CROSS_N, MANOR_X1 - 1, WALL_TOP, CROSS_N, WALL);
        // 门厅与大厅之间的隔墙（中央留 7 宽大拱）
        fill(p, base, WING_W + 1, 1, CROSS_S, WING_E - 1, WALL_TOP, CROSS_S, WALL);

        // 屋顶
        fill(p, base, 0, ROOF_Y, 0, MANOR_X1, ROOF_Y, MANOR_Z1, ROOF);

        // ---- 通道 ----
        // 大拱：门厅 ↔ 大厅（7 宽 4 高）
        archOnEwWall(p, base, 29, 35, CROSS_S, 4);
        // 大厅北门：通后院（5 宽 4 高）
        archOnEwWall(p, base, 30, 34, MANOR_Z1, 4);
        // 翼房拱口（3 宽，主追逐环路，永不上锁）
        archOnNsWall(p, base, WING_W, 8, 10);   // 门厅↔厨房
        archOnNsWall(p, base, WING_E, 8, 10);   // 门厅↔书房
        archOnNsWall(p, base, WING_W, 26, 28);  // 大厅↔餐厅
        archOnNsWall(p, base, WING_E, 26, 28);  // 大厅↔藏书馆
        archOnNsWall(p, base, WING_W, 46, 48);  // 大厅↔医务室
        archOnNsWall(p, base, WING_E, 46, 48);  // 大厅↔工坊
        // 翼内副拱（贴外墙，保证任何翼房都有第二条不经过木门的出路）
        archOnEwWall(p, base, 3, 5, CROSS_S, 3);
        archOnEwWall(p, base, 3, 5, CROSS_N, 3);
        archOnEwWall(p, base, 59, 61, CROSS_S, 3);
        archOnEwWall(p, base, 59, 61, CROSS_N, 3);

        // 翼内木门（可关不可锁，用于博弈）
        woodenDoor(p, base.offset(9, 1, CROSS_S), Direction.SOUTH);   // 厨房↔餐厅
        woodenDoor(p, base.offset(9, 1, CROSS_N), Direction.SOUTH);   // 餐厅↔医务室
        woodenDoor(p, base.offset(55, 1, CROSS_S), Direction.SOUTH);  // 书房↔藏书馆
        woodenDoor(p, base.offset(55, 1, CROSS_N), Direction.SOUTH);  // 藏书馆↔工坊

        // 捷径铁门（锁 #1，见 RepairLockedDoorState）：工坊 → 墓地小路
        ironDoor(p, at(base, LOCK_WORKSHOP_BACKDOOR), Direction.SOUTH);

        // ---- 主出口大门 ----
        // 南墙豁口（6 宽 5 高净空，正好容纳大门方块的自建拱门结构；y=6 的墙与 y=7 的屋顶保留）
        exitGateGap(p, base, 31, 0);
        p.place(base.offset(31, 0, 0), ModBlocks.REPAIR_EXIT_GATE.defaultBlockState());

        // ---- 玩法方块 ----
        repairStation(p, base, 6, 9);    // 修机台1 厨房
        repairStation(p, base, 58, 9);   // 修机台2 书房
        repairStation(p, base, 6, 33);   // 修机台3 餐厅
        repairStation(p, base, 58, 28);  // 修机台4 藏书馆
        repairStation(p, base, 6, 50);   // 修机台5 医务室
        repairStation(p, base, 58, 50);  // 修机台6 工坊

        cagePad(p, base.offset(32, 1, 34));  // 笼子1 大厅中央
        cagePad(p, base.offset(13, 1, 23));  // 笼子2 餐厅
        cagePad(p, base.offset(51, 1, 45));  // 笼子3 工坊

        // 托盘（翻越/砸晕反制点）：放在拱口一侧，留另一侧可通行
        for (int[] pallet : new int[][] {
                { WING_W, 8 }, { WING_E, 8 }, { WING_W, 26 }, { WING_E, 26 },
                { WING_W, 46 }, { WING_E, 46 },
                { 29, CROSS_S }, { 35, CROSS_S }, { 30, MANOR_Z1 }, { 34, MANOR_Z1 } }) {
            p.place(base.offset(pallet[0], 1, pallet[1]), ModBlocks.REPAIR_PALLET.defaultBlockState());
        }

        // 服务电梯（逃生路线，藏书馆东墙内嵌铁板，见 RepairLockedDoorState）
        fill(p, base, MANOR_X1, 1, 27, MANOR_X1, 3, 29, Blocks.IRON_BLOCK.defaultBlockState());
        p.place(base.offset(MANOR_X1 - 1, 1, 26), Blocks.SOUL_LANTERN.defaultBlockState());
        p.place(base.offset(MANOR_X1 - 1, 1, 30), Blocks.SOUL_LANTERN.defaultBlockState());

        roomProps(p, base);
        manorLighting(p, base);
    }

    /** 出口大门豁口：大门结构占 y1..5、z(gz)..(gz+1)，把这块墙彻底掏空。 */
    private static void exitGateGap(Placer p, BlockPos base, int gateX, int gateZ) {
        fill(p, base, gateX - 2, 1, gateZ, gateX + 3, 5, gateZ + 1, AIR);
    }

    private static void roomProps(Placer p, BlockPos base) {
        // ---- 厨房（x1..19, z1..17）：西墙一排灶具 + 中央料理台 ----
        p.place(base.offset(1, 1, 4), horizontal(Blocks.BLAST_FURNACE, Direction.EAST));
        p.place(base.offset(1, 1, 5), horizontal(Blocks.SMOKER, Direction.EAST));
        p.place(base.offset(1, 1, 6), horizontal(Blocks.FURNACE, Direction.EAST));
        p.place(base.offset(1, 1, 8), Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
        p.place(base.offset(1, 1, 12), directional(Blocks.BARREL, Direction.UP));
        p.place(base.offset(1, 1, 13), directional(Blocks.BARREL, Direction.UP));
        counter(p, base, 10, 13, 4, 6);  // 料理台

        // ---- 餐厅（x1..19, z19..37）：长桌 + 两侧座椅 ----
        table(p, base, 5, 15, 28);
        for (int x = 5; x <= 15; x += 2) {
            // 椅背在南 → 人朝北（+Z）面向餐桌
            p.place(base.offset(x, 1, 27), horizontal(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
            // 椅背在北 → 人朝南（-Z）面向餐桌
            p.place(base.offset(x, 1, 29), horizontal(Blocks.SPRUCE_STAIRS, Direction.NORTH));
        }
        // 烛台立在上半砖桌面上（TOP 半砖顶面是实心的，蜡烛不会掉）
        p.place(base.offset(10, 2, 28), Blocks.CANDLE.defaultBlockState()
                .setValue(BlockStateProperties.LIT, true));

        // ---- 医务室（x1..19, z39..55）：病床 + 药柜 ----
        bed(p, base.offset(2, 1, 42), Direction.NORTH, Blocks.WHITE_BED);
        bed(p, base.offset(2, 1, 46), Direction.NORTH, Blocks.WHITE_BED);
        bed(p, base.offset(2, 1, 50), Direction.NORTH, Blocks.RED_BED);
        p.place(base.offset(1, 1, 54), Blocks.BREWING_STAND.defaultBlockState());
        p.place(base.offset(2, 1, 54), directional(Blocks.BARREL, Direction.UP));
        p.place(base.offset(17, 1, 44), Blocks.CAULDRON.defaultBlockState());
        counter(p, base, 15, 18, 52, 53);

        // ---- 门厅（x21..43, z1..17）：地毯 + 盆栽 ----
        for (int x = 30; x <= 34; x++) {
            for (int z = 2; z <= 17; z++) {
                p.place(base.offset(x, 1, z), Blocks.RED_CARPET.defaultBlockState());
            }
        }
        p.place(base.offset(28, 1, 16), Blocks.POTTED_FERN.defaultBlockState());
        p.place(base.offset(36, 1, 16), Blocks.POTTED_FERN.defaultBlockState());
        p.place(base.offset(22, 1, 2), Blocks.POTTED_AZURE_BLUET.defaultBlockState());
        p.place(base.offset(42, 1, 2), Blocks.POTTED_AZURE_BLUET.defaultBlockState());

        // ---- 大厅（x21..43, z19..55）：四根绕柱（追逐环）+ 中央吊灯 ----
        for (int[] col : new int[][] { { 26, 26 }, { 38, 26 }, { 26, 46 }, { 38, 46 } }) {
            fill(p, base, col[0], 1, col[1], col[0], WALL_TOP, col[1], PILLAR);
        }
        chandelier(p, base, 32, 26);
        chandelier(p, base, 32, 50);

        // ---- 书房（x45..63, z1..17）：书架掩体（可绕柱追逐）----
        for (int z = 3; z <= 15; z += 4) {
            fill(p, base, 48, 1, z, 48, 2, z, Blocks.BOOKSHELF.defaultBlockState());
            fill(p, base, 60, 1, z, 60, 2, z, Blocks.BOOKSHELF.defaultBlockState());
        }
        p.place(base.offset(62, 1, 13), horizontal(Blocks.LECTERN, Direction.WEST));
        p.place(base.offset(53, 1, 3), horizontal(Blocks.SPRUCE_STAIRS, Direction.NORTH));

        // ---- 藏书馆（x45..63, z19..37）：书架双排 ----
        for (int z = 21; z <= 35; z += 3) {
            fill(p, base, 48, 1, z, 48, 2, z, Blocks.BOOKSHELF.defaultBlockState());
            fill(p, base, 62, 1, z, 62, 2, z, Blocks.BOOKSHELF.defaultBlockState());
        }
        p.place(base.offset(52, 1, 33), horizontal(Blocks.LECTERN, Direction.SOUTH));

        // ---- 工坊（x45..63, z39..55）：铁砧、锻造台、砂轮 ----
        p.place(base.offset(46, 1, 42), Blocks.SMITHING_TABLE.defaultBlockState());
        p.place(base.offset(47, 1, 42), horizontal(Blocks.ANVIL, Direction.SOUTH));
        p.place(base.offset(48, 1, 42), horizontal(Blocks.STONECUTTER, Direction.SOUTH));
        // 砂轮默认 FACE=WALL（挂墙），落地摆放必须显式改成 FLOOR，否则会悬空
        p.place(base.offset(62, 1, 54), horizontal(Blocks.GRINDSTONE, Direction.WEST)
                .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR));
        p.place(base.offset(62, 1, 41), Blocks.CAULDRON.defaultBlockState());
        counter(p, base, 46, 49, 53, 54);
    }

    private static void manorLighting(Placer p, BlockPos base) {
        // 每个房间挂灯（吊在 y=ROOF_Y 的屋顶板下），保证修机台附近是理智安全区
        for (int[] light : new int[][] {
                { 6, 6 }, { 14, 12 },              // 厨房
                { 6, 24 }, { 14, 33 },             // 餐厅
                { 6, 44 }, { 14, 52 },             // 医务室
                { 58, 6 }, { 50, 12 },             // 书房
                { 50, 24 }, { 58, 33 },            // 藏书馆
                { 50, 44 }, { 58, 52 },            // 工坊
                { 26, 6 }, { 38, 6 }, { 32, 14 },  // 门厅
                { 26, 34 }, { 38, 34 }, { 26, 54 }, { 38, 54 } }) {  // 大厅
            p.place(base.offset(light[0], WALL_TOP, light[1]), HANGING_LANTERN);
        }
    }

    // ==================== 后院（庄园 ↔ 墓地） ====================

    private static void buildYard(Placer p, BlockPos base) {
        // 主路：大厅北拱 → 墓地南门
        fill(p, base, 29, 0, YARD_Z0, 35, 0, GRAVE_Z0, GRAVEL);
        // 捷径小路：工坊后门 → 墓地南门东侧口
        fill(p, base, 54, 0, YARD_Z0, 56, 0, GRAVE_Z0, GRAVEL);
        // 主路托盘反制点
        p.place(base.offset(29, 1, 61), ModBlocks.REPAIR_PALLET.defaultBlockState());
        // 路灯
        lampPost(p, base, 27, 60, false);
        lampPost(p, base, 37, 60, false);
    }

    // ==================== 墓地 ====================

    private static void buildGraveyard(Placer p, BlockPos base) {
        // 地面：草地混合灰化土（昏暗诡异）
        for (int x = 0; x <= MANOR_X1; x++) {
            for (int z = GRAVE_Z0; z <= MAX_Z; z++) {
                p.place(base.offset(x, 0, z), graveyardGround(x, z));
            }
        }
        // 中轴石板路
        fill(p, base, 31, 0, GRAVE_Z0, 33, 0, GRAVE_Z1, GRAVEL);
        // 通往教堂正门的岔路
        fill(p, base, 33, 0, 82, CHAPEL_X0, 0, 84, GRAVEL);
        // 通往陵墓的岔路
        fill(p, base, 16, 0, 72, 31, 0, 74, GRAVEL);
        fill(p, base, 13, 0, 72, 15, 0, 75, GRAVEL);

        // 围栏（圆石墙）+ 南北门口
        BlockState fence = Blocks.COBBLESTONE_WALL.defaultBlockState();
        for (int x = 2; x <= 62; x++) {
            boolean southGap = (x >= 29 && x <= 35) || (x >= 53 && x <= 57);
            if (!southGap) {
                p.place(base.offset(x, 1, GRAVE_Z0), fence);
            }
            if (x < 29 || x > 34) {
                p.place(base.offset(x, 1, GRAVE_Z1), fence);
            }
        }
        for (int z = GRAVE_Z0; z <= GRAVE_Z1; z++) {
            p.place(base.offset(2, 1, z), fence);
            p.place(base.offset(62, 1, z), fence);
        }

        // 墓地北门 = 出口大门 2
        exitGateGap(p, base, 31, GRAVE_Z1);
        p.place(base.offset(31, 0, GRAVE_Z1), ModBlocks.REPAIR_EXIT_GATE.defaultBlockState());

        // 墓碑阵（低掩体：蹲伏可藏身；避开教堂、陵墓、道路与笼位）
        for (int z = 68; z <= 72; z += 4) {
            for (int x = 6; x <= 22; x += 4) {
                if (onMausoleumPath(x, z)) {
                    continue;
                }
                headstone(p, base, x, z);
            }
        }
        for (int z = 88; z <= 104; z += 4) {
            for (int x = 6; x <= 22; x += 4) {
                headstone(p, base, x, z);
            }
        }
        for (int z = 68; z <= 76; z += 4) {
            for (int x = 42; x <= 58; x += 4) {
                headstone(p, base, x, z);
            }
        }
        for (int z = 106; z <= 108; z += 2) {
            for (int x = 40; x <= 56; x += 4) {
                headstone(p, base, x, z);
            }
        }

        deadTree(p, base, 26, 68);
        deadTree(p, base, 38, 106);
        deadTree(p, base, 8, 110);

        buildChapel(p, base);
        buildCrypt(p, base);

        cagePad(p, base.offset(28, 1, 96));  // 笼子4 墓地北部
        cagePad(p, base.offset(36, 1, 70));  // 笼子5 墓地南部

        // 昏暗照明：只有围栏角与路口有零星灵魂灯（大片光照<4 区域侵蚀理智）
        for (int[] light : new int[][] { { 4, 66 }, { 60, 66 }, { 4, 110 }, { 60, 110 },
                { 29, 66 }, { 35, 66 }, { 29, 110 }, { 35, 110 }, { 29, 88 }, { 35, 88 } }) {
            lampPost(p, base, light[0], light[1], true);
        }
    }

    /** 教堂（x38..58, z80..102）：石砖，内含修机台7，东墙锁 #2 捷径侧门。 */
    private static void buildChapel(Placer p, BlockPos base) {
        // 地板 + 实心体
        fill(p, base, CHAPEL_X0, 0, CHAPEL_Z0, CHAPEL_X1, 0, CHAPEL_Z1, STONE);
        for (int y = 1; y <= WALL_TOP; y++) {
            for (int x = CHAPEL_X0; x <= CHAPEL_X1; x++) {
                for (int z = CHAPEL_Z0; z <= CHAPEL_Z1; z++) {
                    boolean wall = x == CHAPEL_X0 || x == CHAPEL_X1 || z == CHAPEL_Z0 || z == CHAPEL_Z1;
                    p.place(base.offset(x, y, z), wall ? chapelWall(x, z, y) : AIR);
                }
            }
        }
        fill(p, base, CHAPEL_X0, ROOF_Y, CHAPEL_Z0, CHAPEL_X1, ROOF_Y, CHAPEL_Z1,
                Blocks.DARK_OAK_SLAB.defaultBlockState());

        // 正门：西墙 3 宽拱（无门），托盘反制点
        archOnNsWall(p, base, CHAPEL_X0, 82, 84);
        p.place(base.offset(CHAPEL_X0, 1, 84), ModBlocks.REPAIR_PALLET.defaultBlockState());
        // 侧门：东墙铁门（锁 #2，撬锁器）
        ironDoor(p, at(base, LOCK_CHAPEL_SIDE_DOOR), Direction.WEST);

        // 修机台7：门厅中轴
        repairStation(p, base, 48, 82);

        // 长椅（楼梯）：椅背朝南 → 信徒面向北侧圣坛。中央 x47..49 留过道
        for (int z = 86; z <= 96; z += 2) {
            for (int x = 42; x <= 46; x++) {
                p.place(base.offset(x, 1, z), horizontal(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
            }
            for (int x = 50; x <= 54; x++) {
                p.place(base.offset(x, 1, z), horizontal(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
            }
        }

        // 圣坛：抬高一级的平台，南面一排上行台阶（朝北上行 → FACING=NORTH）
        fill(p, base, 45, 1, 98, 51, 1, 100, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        for (int x = 45; x <= 51; x++) {
            p.place(base.offset(x, 1, 97), horizontal(Blocks.STONE_BRICK_STAIRS, Direction.NORTH));
        }
        p.place(base.offset(48, 2, 99), Blocks.ENCHANTING_TABLE.defaultBlockState());
        p.place(base.offset(46, 2, 99), Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.LIT, true));
        p.place(base.offset(50, 2, 99), Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.LIT, true));

        // 教堂明亮（墓地中的理智庇护所）
        for (int[] light : new int[][] { { 42, 84 }, { 54, 84 }, { 42, 92 }, { 54, 92 }, { 48, 100 } }) {
            p.place(base.offset(light[0], WALL_TOP, light[1]), HANGING_LANTERN);
        }
    }

    /**
     * 地穴：地表的石砖陵墓（有正经门洞，不是地上的一个洞）→ 三格宽楼梯井向北下行
     * → 地下大厅 → 锁 #3 铁门 → 密道逃生口与高级战利品。
     */
    private static void buildCrypt(Placer p, BlockPos base) {
        // ---- 地下室体：先填实，再掏空 ----
        fill(p, base, CRYPT_X0, CRYPT_FLOOR_Y, CRYPT_Z0, CRYPT_X1, CRYPT_CEIL_Y, CRYPT_Z1, DEEP);
        fill(p, base, CRYPT_X0 + 1, CRYPT_FLOOR_Y + 1, CRYPT_Z0 + 1,
                CRYPT_X1 - 1, CRYPT_CEIL_Y - 1, CRYPT_Z1 - 1, AIR);
        fill(p, base, CRYPT_X0 + 1, CRYPT_FLOOR_Y, CRYPT_Z0 + 1,
                CRYPT_X1 - 1, CRYPT_FLOOR_Y, CRYPT_Z1 - 1, Blocks.DEEPSLATE_TILES.defaultBlockState());

        // ---- 地表陵墓（x10..18, z76..84），把楼梯井包在室内 ----
        for (int y = 1; y <= 4; y++) {
            for (int x = 10; x <= 18; x++) {
                for (int z = 76; z <= 84; z++) {
                    boolean wall = x == 10 || x == 18 || z == 76 || z == 84;
                    p.place(base.offset(x, y, z), wall ? STONE : AIR);
                }
            }
        }
        fill(p, base, 10, 5, 76, 18, 5, 84, Blocks.STONE_BRICK_SLAB.defaultBlockState());
        fill(p, base, 11, 0, 77, 17, 0, 83, STONE);          // 陵墓室内地面
        archOnEwWall(p, base, SHAFT_X0, SHAFT_X1, 76, 3);     // 陵墓门洞（3 宽 3 高）
        p.place(base.offset(11, 1, 78), Blocks.SOUL_LANTERN.defaultBlockState());
        p.place(base.offset(17, 1, 78), Blocks.SOUL_LANTERN.defaultBlockState());
        p.place(base.offset(12, 4, 80), Blocks.CHAIN.defaultBlockState());

        // ---- 楼梯井：从陵墓地面 (y=1 立足) 向北下行 5 级到地穴地面 (y=-4 立足) ----
        // 向北(+Z)下行 ⇒ 高的一侧在南 ⇒ FACING=SOUTH
        fill(p, base, SHAFT_X0, CRYPT_FLOOR_Y + 1, SHAFT_Z0, SHAFT_X1, 4, SHAFT_Z0 + 4, AIR);
        for (int i = 0; i <= 4; i++) {
            int y = -i;
            int z = SHAFT_Z0 + i;
            for (int x = SHAFT_X0; x <= SHAFT_X1; x++) {
                p.place(base.offset(x, y, z), horizontal(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.SOUTH));
                p.place(base.offset(x, y - 1, z), DEEP);   // 阶梯支撑，杜绝踩空
            }
        }

        // ---- 地穴内隔墙 + 锁 #3 铁门（旧钥匙）：门后是密道与高级战利品 ----
        fill(p, base, CRYPT_X0 + 1, CRYPT_FLOOR_Y + 1, 92, CRYPT_X1 - 1, CRYPT_CEIL_Y - 1, 92, DEEP);
        ironDoor(p, at(base, LOCK_CRYPT_GATE), Direction.SOUTH);

        // ---- 棺木与烛台 ----
        for (int z = 86; z <= 90; z += 2) {
            p.place(base.offset(9, CRYPT_FLOOR_Y + 1, z), Blocks.CHISELED_DEEPSLATE.defaultBlockState());
            p.place(base.offset(21, CRYPT_FLOOR_Y + 1, z), Blocks.CHISELED_DEEPSLATE.defaultBlockState());
        }
        p.place(base.offset(9, CRYPT_FLOOR_Y + 1, 95), directional(Blocks.BARREL, Direction.UP));
        p.place(base.offset(21, CRYPT_FLOOR_Y + 1, 95), directional(Blocks.BARREL, Direction.UP));
        p.place(base.offset(11, CRYPT_CEIL_Y - 1, 87), Blocks.SOUL_LANTERN.defaultBlockState()
                .setValue(LanternBlock.HANGING, true));
        p.place(base.offset(19, CRYPT_CEIL_Y - 1, 99), Blocks.SOUL_LANTERN.defaultBlockState()
                .setValue(LanternBlock.HANGING, true));

        // ---- 密道逃生口（RepairLockedDoorState 路线：点击铁栏杆）----
        BlockPos tunnel = at(base, ROUTE_CRYPT_TUNNEL);
        p.place(tunnel, Blocks.IRON_BARS.defaultBlockState());
        p.place(tunnel.above(), Blocks.IRON_BARS.defaultBlockState());
    }

    // ==================== 结构小件 ====================

    /** 通往陵墓的碎石岔路（含墓碑会占用的 z+1 土丘格），墓碑不能压在上面。 */
    private static boolean onMausoleumPath(int x, int z) {
        return x >= 12 && x <= 16 && z >= 70 && z <= 76;
    }

    /** 墓碑：土丘 + 墙柱 + 压顶石板。 */
    private static void headstone(Placer p, BlockPos base, int x, int z) {
        p.place(base.offset(x, 0, z + 1), Blocks.COARSE_DIRT.defaultBlockState());
        p.place(base.offset(x, 1, z), Blocks.COBBLESTONE_WALL.defaultBlockState());
        p.place(base.offset(x, 2, z), Blocks.STONE_BRICK_SLAB.defaultBlockState());
    }

    private static void deadTree(Placer p, BlockPos base, int x, int z) {
        BlockState log = Blocks.DARK_OAK_LOG.defaultBlockState();
        fill(p, base, x, 1, z, x, 5, z, log);
        p.place(base.offset(x + 1, 4, z), log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        p.place(base.offset(x - 1, 5, z), log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        p.place(base.offset(x, 5, z + 1), log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
    }

    /** 路灯：墙柱 + 灵魂灯，灯有实体支撑，不会悬空。 */
    private static void lampPost(Placer p, BlockPos base, int x, int z, boolean soul) {
        p.place(base.offset(x, 1, z), Blocks.COBBLESTONE_WALL.defaultBlockState());
        p.place(base.offset(x, 2, z), Blocks.COBBLESTONE_WALL.defaultBlockState());
        p.place(base.offset(x, 3, z), soul ? Blocks.SOUL_LANTERN.defaultBlockState()
                : Blocks.LANTERN.defaultBlockState());
    }

    /** 吊灯：从屋顶垂下的锁链 + 灯笼。 */
    private static void chandelier(Placer p, BlockPos base, int x, int z) {
        p.place(base.offset(x, WALL_TOP, z), Blocks.CHAIN.defaultBlockState());
        p.place(base.offset(x, WALL_TOP - 1, z), Blocks.CHAIN.defaultBlockState());
        p.place(base.offset(x, WALL_TOP - 2, z), HANGING_LANTERN);
    }

    /** 修机台 + 一条垂到屋顶的供电锁链（顶端有屋顶承接，不悬空）。 */
    private static void repairStation(Placer p, BlockPos base, int x, int z) {
        p.place(base.offset(x, 1, z), ModBlocks.REPAIR_STATION.defaultBlockState());
        for (int y = 2; y <= ROOF_Y - 1; y++) {
            p.place(base.offset(x, y, z), Blocks.CHAIN.defaultBlockState());
        }
    }

    /** 笼位：磨制黑石底座 3x3，笼体由 HunterCageBlockEntity 在囚禁时自建。 */
    private static void cagePad(Placer p, BlockPos pos) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                p.place(pos.offset(dx, -1, dz), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            }
        }
        p.place(pos, ModBlocks.HUNTER_CAGE.defaultBlockState());
    }

    /** 料理台/工作台：底座 + 上盖石板。 */
    private static void counter(Placer p, BlockPos base, int x1, int x2, int z1, int z2) {
        fill(p, base, x1, 1, z1, x2, 1, z2, Blocks.SMOOTH_STONE.defaultBlockState());
        fill(p, base, x1, 2, z1, x2, 2, z2, Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
    }

    /** 长桌：一排上半砖，桌面正好在 y=2.0，与两侧楼梯座椅齐高。 */
    private static void table(Placer p, BlockPos base, int x1, int x2, int z) {
        fill(p, base, x1, 1, z, x2, 1, z,
                Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP));
    }

    /** 双联床：显式放置床尾与床头两半，否则只会生成半张破床。 */
    private static void bed(Placer p, BlockPos foot, Direction facing, Block bedBlock) {
        BlockState oriented = bedBlock.defaultBlockState().setValue(BedBlock.FACING, facing);
        p.place(foot, oriented.setValue(BedBlock.PART, BedPart.FOOT));
        p.place(foot.relative(facing), oriented.setValue(BedBlock.PART, BedPart.HEAD));
    }

    /** 在 x=const 的南北向墙上开拱口（z1..z2，3 格高）。 */
    private static void archOnNsWall(Placer p, BlockPos base, int x, int z1, int z2) {
        fill(p, base, x, 1, z1, x, 3, z2, AIR);
    }

    /** 在 z=const 的东西向墙上开拱口（x1..x2，height 格高）。 */
    private static void archOnEwWall(Placer p, BlockPos base, int x1, int x2, int z, int height) {
        fill(p, base, x1, 1, z, x2, height, z, AIR);
    }

    /** 木门：先在墙上凿出 1x2 门洞再放上下两半，朝向由墙轴决定，不再打侧洞。 */
    private static void woodenDoor(Placer p, BlockPos floor, Direction facing) {
        placeDoor(p, floor, TMMBlocks.SMALL_WOOD_DOOR.defaultBlockState(), facing);
    }

    private static void ironDoor(Placer p, BlockPos floor, Direction facing) {
        placeDoor(p, floor, Blocks.IRON_DOOR.defaultBlockState(), facing);
    }

    private static void placeDoor(Placer p, BlockPos floor, BlockState door, Direction facing) {
        p.place(floor, AIR);
        p.place(floor.above(), AIR);
        BlockState oriented = door.setValue(DoorBlock.FACING, facing);
        p.place(floor, oriented.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        p.place(floor.above(), oriented.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private static BlockState horizontal(Block block, Direction facing) {
        return block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
    }

    private static BlockState directional(Block block, Direction facing) {
        return block.defaultBlockState().setValue(BlockStateProperties.FACING, facing);
    }

    /** 闭区间填充，坐标顺序无所谓。 */
    private static void fill(Placer p, BlockPos base, int x1, int y1, int z1, int x2, int y2, int z2,
            BlockState state) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    p.place(base.offset(x, y, z), state);
                }
            }
        }
    }

    private static BlockState floorPattern(int x, int z) {
        return ((x + z) & 1) == 0 ? Blocks.DARK_OAK_PLANKS.defaultBlockState()
                : Blocks.SPRUCE_PLANKS.defaultBlockState();
    }

    /** 外墙：立柱每 8 格一根，y3/y4 处开玻璃窗带。 */
    private static BlockState outerWall(int along, int y) {
        if (along % 8 == 0) {
            return PILLAR;
        }
        int inBay = along % 8;
        if ((y == 3 || y == 4) && inBay >= 3 && inBay <= 5) {
            return Blocks.GLASS_PANE.defaultBlockState();
        }
        return WALL;
    }

    private static BlockState chapelWall(int x, int z, int y) {
        boolean northSouth = z == CHAPEL_Z0 || z == CHAPEL_Z1;
        boolean eastWest = x == CHAPEL_X0 || x == CHAPEL_X1;
        if ((y == 3 || y == 4) && ((northSouth && x % 4 == 0) || (eastWest && z % 4 == 0))) {
            return Blocks.GLASS_PANE.defaultBlockState();
        }
        return STONE;
    }

    private static BlockState graveyardGround(int x, int z) {
        int hash = (x * 31 + z * 17) & 7;
        if (hash == 0) {
            return Blocks.COARSE_DIRT.defaultBlockState();
        }
        if (hash == 1) {
            return Blocks.PODZOL.defaultBlockState();
        }
        return Blocks.GRASS_BLOCK.defaultBlockState();
    }
}
