package net.exmo.sre.repair.arena;

import io.wifi.starrailexpress.index.TMMBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.agmas.noellesroles.init.ModBlocks;

import java.util.List;

/**
 * 修机模式默认场景：庄园 + 墓地。
 * 设计原则：
 * <ul>
 * <li>主通道全部用无门拱廊，幸存者永远不会被锁死在房间里；锁只出现在 3 个捷径上。</li>
 * <li>每个房间至少两个出入口，形成追逐环路；托盘放在拱口做反制点。</li>
 * <li>庄园明亮（理智安全区），墓地昏暗（理智侵蚀区）但战利品更好 —— 风险换收益。</li>
 * <li>出口大门方块放在地板层的净空豁口中，让它的自建拱门结构能正常生成。</li>
 * </ul>
 *
 * 平面（相对 base，x 0..44 西→东，z 0..40 庄园，z 44..76 墓地）：
 * <pre>
 * z76 ─┬───墓地北门(出口2)───┬─
 *      │ 地穴(下沉)   教堂    │   墓地：墓碑阵 + 教堂(修机台5) + 地穴(密道逃生)
 * z44 ─┴────墓地南门────────┴─
 * z40 ──庄园北墙(工坊捷径铁门)──
 *      │ 医务室 │ 大  厅 │ 工坊 │   修机台3(医务) 修机台4(工坊) 笼子(大厅)
 * z26 ─┤  木门  │(无隔断)│ 木门 ├─
 *      │ 餐厅   │        │ 藏书 │   笼子(餐厅) 服务电梯(藏书东墙)
 * z14 ─┤  木门  │──拱──│ 木门 ├─
 *      │ 厨房   │ 门厅   │ 书房 │   修机台1(厨房) 修机台2(书房)
 * z0  ─┴──────主门(出口1)──────┴─
 * </pre>
 */
public final class RepairManorScene {
    @FunctionalInterface
    public interface Placer {
        void place(BlockPos pos, BlockState state);
    }

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState WALL = Blocks.DARK_OAK_PLANKS.defaultBlockState();
    private static final BlockState PILLAR = Blocks.SPRUCE_LOG.defaultBlockState();
    private static final BlockState ROOF = Blocks.SPRUCE_SLAB.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE_BRICKS.defaultBlockState();

    /** 追捕者出生点（墓地中部，远离幸存者）。 */
    private static final int[][] HUNTER_SPAWNS = {
            { 22, 1, 60 }, { 18, 1, 56 }, { 26, 1, 56 }, { 22, 1, 52 }
    };
    /** 幸存者出生点（庄园南侧分散）。 */
    private static final int[][] SURVIVOR_SPAWNS = {
            { 6, 1, 4 }, { 38, 1, 4 }, { 17, 1, 4 }, { 27, 1, 4 },
            { 6, 1, 20 }, { 38, 1, 20 }, { 6, 1, 36 }, { 38, 1, 36 }
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
        return List.of(
                // 必需品位（分散强迫探图；两个在墓地）
                new int[] { 4, 1, 4 },      // 厨房
                new int[] { 40, 1, 4 },     // 书房
                new int[] { 4, 1, 36 },     // 医务室
                new int[] { 40, 1, 36 },    // 工坊
                new int[] { 4, 1, 20 },     // 餐厅
                new int[] { 40, 1, 20 },    // 藏书馆
                new int[] { 17, 1, 36 },    // 大厅西北角
                new int[] { 33, 1, 60 },    // 教堂
                new int[] { 8, -2, 66 },    // 地穴（昏暗高危）
                // 随机战利品位
                new int[] { 10, 1, 4 }, new int[] { 34, 1, 4 }, new int[] { 22, 1, 4 },
                new int[] { 10, 1, 36 }, new int[] { 34, 1, 36 },
                new int[] { 17, 1, 16 }, new int[] { 27, 1, 16 }, new int[] { 27, 1, 36 },
                new int[] { 4, 1, 12 }, new int[] { 40, 1, 12 },
                new int[] { 4, 1, 28 }, new int[] { 40, 1, 28 },
                new int[] { 37, 1, 60 }, new int[] { 10, -2, 64 },
                new int[] { 4, 1, 50 }, new int[] { 38, 1, 48 });
    }

    public static void build(Placer p, BlockPos base) {
        clearVolume(p, base, -6, 1, -6, 50, 12, 82);
        clearVolume(p, base, 4, -4, 56, 14, 0, 70); // 地穴下沉空间
        // 兜底地面 + 场地四周树篱（防止玩家走出场景边缘）
        BlockState hedge = Blocks.SPRUCE_LEAVES.defaultBlockState()
                .setValue(BlockStateProperties.PERSISTENT, true);
        for (int x = -6; x <= 50; x++) {
            for (int z = -6; z <= 82; z++) {
                p.place(base.offset(x, 0, z), Blocks.GRASS_BLOCK.defaultBlockState());
                if (x == -6 || x == 50 || z == -6 || z == 82) {
                    p.place(base.offset(x, 1, z), hedge);
                    p.place(base.offset(x, 2, z), hedge);
                }
            }
        }
        buildManor(p, base);
        buildGraveyard(p, base);
        // 储物柜最后放置，避免被房间体块覆盖（地穴内的柜子在 y<0）
        for (int[] loot : lootOffsets()) {
            p.place(base.offset(loot[0], loot[1], loot[2]), ModBlocks.HOTBAR_STORAGE.defaultBlockState());
        }
    }

    // ==================== 庄园 ====================

    private static void buildManor(Placer p, BlockPos base) {
        // 地板 + 外圈石砖散水
        for (int x = -2; x <= 46; x++) {
            for (int z = -3; z <= 42; z++) {
                boolean inside = x >= 0 && x <= 44 && z >= 0 && z <= 40;
                p.place(base.offset(x, 0, z), inside ? floorPattern(x, z) : Blocks.GRAVEL.defaultBlockState());
            }
        }

        // 外墙（含窗）与转角立柱
        for (int x = 0; x <= 44; x++) {
            for (int y = 1; y <= 5; y++) {
                p.place(base.offset(x, y, 0), outerWall(x, y, 44));
                p.place(base.offset(x, y, 40), outerWall(x, y, 44));
            }
        }
        for (int z = 0; z <= 40; z++) {
            for (int y = 1; y <= 5; y++) {
                p.place(base.offset(0, y, z), outerWall(z, y, 40));
                p.place(base.offset(44, y, z), outerWall(z, y, 40));
            }
        }

        // 内墙：东西翼隔墙 x=14 / x=30（全长），翼内隔墙 z=14 / z=26（只在两翼）
        for (int z = 1; z <= 39; z++) {
            for (int y = 1; y <= 5; y++) {
                p.place(base.offset(14, y, z), WALL);
                p.place(base.offset(30, y, z), WALL);
            }
        }
        for (int x = 1; x <= 13; x++) {
            for (int y = 1; y <= 5; y++) {
                p.place(base.offset(x, y, 14), WALL);
                p.place(base.offset(x, y, 26), WALL);
            }
        }
        for (int x = 31; x <= 43; x++) {
            for (int y = 1; y <= 5; y++) {
                p.place(base.offset(x, y, 14), WALL);
                p.place(base.offset(x, y, 26), WALL);
            }
        }
        // 门厅与大厅之间的隔墙（中央留 5 宽大拱）
        for (int x = 15; x <= 29; x++) {
            for (int y = 1; y <= 5; y++) {
                p.place(base.offset(x, y, 14), WALL);
            }
        }

        // 屋顶
        for (int x = 0; x <= 44; x++) {
            for (int z = 0; z <= 40; z++) {
                p.place(base.offset(x, 6, z), ROOF);
            }
        }

        // ---- 通道 ----
        // 大拱：门厅 ↔ 大厅（5 宽）
        arch(p, base, 20, 24, 14);
        // 翼房拱口（2 宽，主追逐环路，永不上锁）
        archOnZWall(p, base, 14, 6);   // 门厅↔厨房
        archOnZWall(p, base, 30, 6);   // 门厅↔书房
        archOnZWall(p, base, 14, 19);  // 大厅↔餐厅
        archOnZWall(p, base, 30, 19);  // 大厅↔藏书馆
        archOnZWall(p, base, 14, 32);  // 大厅↔医务室
        archOnZWall(p, base, 30, 32);  // 大厅↔工坊
        // 大厅北门：通墓地（3 宽拱）
        arch(p, base, 21, 23, 40);

        // 翼内木门（可关不可锁，用于博弈）
        woodenDoor(p, base.offset(7, 1, 14), Direction.SOUTH);   // 厨房↔餐厅
        woodenDoor(p, base.offset(7, 1, 26), Direction.SOUTH);   // 餐厅↔医务室
        woodenDoor(p, base.offset(37, 1, 14), Direction.SOUTH);  // 书房↔藏书馆
        woodenDoor(p, base.offset(37, 1, 26), Direction.SOUTH);  // 藏书馆↔工坊

        // 捷径铁门（锁 #1，见 RepairLockedDoorState）：工坊 → 墓地小路
        ironDoor(p, base.offset(37, 1, 40), Direction.SOUTH);

        // ---- 主出口大门 ----
        // 南墙豁口（6 宽净空，让大门方块自建拱门结构）
        for (int x = 20; x <= 25; x++) {
            for (int y = 1; y <= 6; y++) {
                p.place(base.offset(x, y, 0), AIR);
                p.place(base.offset(x, y, 1), AIR);
            }
        }
        p.place(base.offset(22, 0, 0), ModBlocks.REPAIR_EXIT_GATE.defaultBlockState());

        // ---- 玩法方块 ----
        repairStation(p, base.offset(6, 1, 7));    // 修机台1 厨房
        repairStation(p, base.offset(38, 1, 7));   // 修机台2 书房
        repairStation(p, base.offset(6, 1, 33));   // 修机台3 医务室
        repairStation(p, base.offset(38, 1, 33));  // 修机台4 工坊

        cagePad(p, base.offset(22, 1, 22));        // 笼子1 大厅中央
        cagePad(p, base.offset(7, 1, 20));         // 笼子2 餐厅

        // 托盘（翻越/砸晕反制点）：放在拱口一半，留另一半可通行
        for (int[] pallet : new int[][] { { 14, 7 }, { 30, 7 }, { 14, 20 }, { 30, 20 },
                { 14, 33 }, { 30, 33 }, { 22, 14 }, { 22, 43 } }) {
            p.place(base.offset(pallet[0], 1, pallet[1]), ModBlocks.REPAIR_PALLET.defaultBlockState());
        }

        // 服务电梯（逃生路线，藏书馆东墙内嵌铁板，见 RepairLockedDoorState）
        for (int z = 19; z <= 21; z++) {
            for (int y = 1; y <= 3; y++) {
                p.place(base.offset(44, y, z), Blocks.IRON_BLOCK.defaultBlockState());
            }
        }
        p.place(base.offset(43, 3, 20), Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));

        roomProps(p, base);
        manorLighting(p, base);
    }

    private static void roomProps(Placer p, BlockPos base) {
        // 厨房：炉灶与料理台
        p.place(base.offset(2, 1, 2), Blocks.BLAST_FURNACE.defaultBlockState());
        p.place(base.offset(3, 1, 2), Blocks.SMOKER.defaultBlockState());
        p.place(base.offset(2, 1, 10), Blocks.BARREL.defaultBlockState());
        p.place(base.offset(9, 1, 2), Blocks.WATER_CAULDRON.defaultBlockState());
        // 书房：书架掩体（可绕柱追逐）
        for (int z = 3; z <= 11; z += 4) {
            p.place(base.offset(34, 1, z), Blocks.BOOKSHELF.defaultBlockState());
            p.place(base.offset(34, 2, z), Blocks.BOOKSHELF.defaultBlockState());
        }
        p.place(base.offset(41, 1, 10), Blocks.LECTERN.defaultBlockState());
        // 餐厅：长桌（避开笼位 z20）
        for (int x = 4; x <= 10; x++) {
            p.place(base.offset(x, 1, 17), Blocks.DARK_OAK_SLAB.defaultBlockState());
        }
        // 藏书馆：书架双排
        for (int z = 16; z <= 24; z += 4) {
            p.place(base.offset(33, 1, z), Blocks.BOOKSHELF.defaultBlockState());
            p.place(base.offset(33, 2, z), Blocks.BOOKSHELF.defaultBlockState());
            p.place(base.offset(41, 1, z), Blocks.BOOKSHELF.defaultBlockState());
            p.place(base.offset(41, 2, z), Blocks.BOOKSHELF.defaultBlockState());
        }
        // 医务室：病床与药柜
        p.place(base.offset(3, 1, 29), Blocks.WHITE_BED.defaultBlockState());
        p.place(base.offset(3, 1, 32), Blocks.RED_BED.defaultBlockState());
        p.place(base.offset(2, 1, 38), Blocks.BREWING_STAND.defaultBlockState());
        // 工坊：铁砧与锻造台
        p.place(base.offset(33, 1, 29), Blocks.SMITHING_TABLE.defaultBlockState());
        p.place(base.offset(34, 1, 29), Blocks.ANVIL.defaultBlockState());
        p.place(base.offset(41, 1, 38), Blocks.GRINDSTONE.defaultBlockState());
        // 大厅：四根绕柱（追逐环）+ 中央吊灯座
        for (int[] col : new int[][] { { 18, 18 }, { 26, 18 }, { 18, 34 }, { 26, 34 } }) {
            for (int y = 1; y <= 5; y++) {
                p.place(base.offset(col[0], y, col[1]), PILLAR);
            }
        }
        // 门厅：地毯与花盆
        for (int x = 20; x <= 24; x++) {
            for (int z = 3; z <= 11; z++) {
                p.place(base.offset(x, 0, z), Blocks.RED_WOOL.defaultBlockState());
            }
        }
        p.place(base.offset(17, 1, 12), Blocks.FLOWER_POT.defaultBlockState());
        p.place(base.offset(27, 1, 12), Blocks.FLOWER_POT.defaultBlockState());
    }

    private static void manorLighting(Placer p, BlockPos base) {
        BlockState hanging = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        // 每个房间中心 + 大厅四角吊灯，保证修机台附近是理智安全区
        for (int[] light : new int[][] { { 7, 7 }, { 37, 7 }, { 7, 20 }, { 37, 20 }, { 7, 33 }, { 37, 33 },
                { 22, 7 }, { 18, 22 }, { 26, 22 }, { 18, 30 }, { 26, 30 }, { 22, 38 } }) {
            p.place(base.offset(light[0], 5, light[1]), hanging);
        }
        // 大厅中央吊灯（锁链 + 灯笼）
        p.place(base.offset(22, 5, 26), Blocks.CHAIN.defaultBlockState());
        p.place(base.offset(22, 4, 26), hanging);
    }

    // ==================== 墓地 ====================

    private static void buildGraveyard(Placer p, BlockPos base) {
        // 地面：草地混合灰化土（昏暗诡异）
        for (int x = 0; x <= 44; x++) {
            for (int z = 41; z <= 78; z++) {
                p.place(base.offset(x, 0, z), graveyardGround(x, z));
            }
        }
        // 连接小路
        for (int x = 20; x <= 24; x++) {
            for (int z = 41; z <= 44; z++) {
                p.place(base.offset(x, 0, z), Blocks.GRAVEL.defaultBlockState());
            }
        }

        // 围栏（圆石墙）+ 南北门口
        BlockState fence = Blocks.COBBLESTONE_WALL.defaultBlockState();
        for (int x = 2; x <= 42; x++) {
            // 南栏留两个口：中路大门口 (20..25) 和工坊捷径口 (36..38)
            boolean southGap = (x >= 20 && x <= 25) || (x >= 36 && x <= 38);
            if (!southGap) {
                p.place(base.offset(x, 1, 44), fence);
            }
            if (x < 20 || x > 25) {
                p.place(base.offset(x, 1, 76), fence);
            }
        }
        for (int z = 44; z <= 76; z++) {
            p.place(base.offset(2, 1, z), fence);
            p.place(base.offset(42, 1, z), fence);
        }

        // 墓地北门 = 出口大门 2（豁口 + 大门方块）
        for (int x = 20; x <= 25; x++) {
            for (int y = 1; y <= 6; y++) {
                p.place(base.offset(x, y, 76), AIR);
                p.place(base.offset(x, y, 77), AIR);
            }
        }
        p.place(base.offset(22, 0, 76), ModBlocks.REPAIR_EXIT_GATE.defaultBlockState());

        // 墓碑阵（低掩体：蹲伏可藏身，与怨灵猎杀的躲避规则联动；避开地穴顶盖）
        for (int z = 48; z <= 53; z += 5) {
            for (int x = 6; x <= 18; x += 4) {
                headstone(p, base.offset(x, 0, z));
            }
        }
        for (int z = 64; z <= 72; z += 4) {
            for (int x = 16; x <= 24; x += 4) {
                headstone(p, base.offset(x, 0, z));
            }
        }

        // 枯树
        deadTree(p, base.offset(5, 1, 73));
        deadTree(p, base.offset(30, 1, 47));

        buildChapel(p, base);
        buildCrypt(p, base);

        cagePad(p, base.offset(20, 1, 52)); // 笼子3 墓地中部

        // 昏暗照明：只有围栏角与小路口有零星灵魂灯（大片光照<4 区域侵蚀理智）
        BlockState soul = Blocks.SOUL_LANTERN.defaultBlockState();
        for (int[] light : new int[][] { { 2, 44 }, { 42, 44 }, { 2, 76 }, { 42, 76 }, { 22, 46 } }) {
            p.place(base.offset(light[0], 2, light[1]), soul);
        }
    }

    /** 教堂（x27..39, z56..70）：石砖，内含修机台5，东墙锁 #2 捷径侧门。 */
    private static void buildChapel(Placer p, BlockPos base) {
        for (int x = 27; x <= 39; x++) {
            for (int z = 56; z <= 70; z++) {
                p.place(base.offset(x, 0, z), STONE);
                for (int y = 1; y <= 5; y++) {
                    boolean wall = x == 27 || x == 39 || z == 56 || z == 70;
                    p.place(base.offset(x, y, z), wall ? chapelWall(x, z, y) : AIR);
                }
                p.place(base.offset(x, 6, z), Blocks.DARK_OAK_SLAB.defaultBlockState());
            }
        }
        // 正门：西墙 2 宽拱（无门），托盘反制点
        for (int y = 1; y <= 2; y++) {
            p.place(base.offset(27, y, 62), AIR);
            p.place(base.offset(27, y, 63), AIR);
        }
        p.place(base.offset(27, 1, 63), ModBlocks.REPAIR_PALLET.defaultBlockState());
        // 侧门：东墙铁门（锁 #2，撬锁器）
        ironDoor(p, base.offset(39, 1, 63), Direction.WEST);

        // 长椅（楼梯）与圣坛
        for (int z = 59; z <= 67; z += 2) {
            p.place(base.offset(30, 1, z),
                    Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
            p.place(base.offset(35, 1, z),
                    Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
        }
        p.place(base.offset(37, 1, 69), Blocks.ENCHANTING_TABLE.defaultBlockState());
        repairStation(p, base.offset(33, 1, 66)); // 修机台5

        // 教堂明亮（墓地中的理智庇护所）
        BlockState hanging = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        p.place(base.offset(33, 5, 60), hanging);
        p.place(base.offset(33, 5, 66), hanging);
    }

    /** 地穴（x5..13, z58..68，下沉 3 格）：锁 #3 铁门 + 密道逃生 + 高级战利品。 */
    private static void buildCrypt(Placer p, BlockPos base) {
        // 下沉室体：深板岩砖墙，内部净空
        for (int x = 5; x <= 13; x++) {
            for (int z = 58; z <= 68; z++) {
                p.place(base.offset(x, -3, z), Blocks.DEEPSLATE_TILES.defaultBlockState());
                for (int y = -2; y <= 0; y++) {
                    boolean wall = x == 5 || x == 13 || z == 58 || z == 68;
                    p.place(base.offset(x, y, z),
                            wall ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : AIR);
                }
                // 顶盖（地面层），入口处留洞
                p.place(base.offset(x, 1, z), Blocks.DEEPSLATE_BRICK_SLAB.defaultBlockState());
            }
        }
        // 入口阶梯：地表 (9,1,57) 向北下行三级到室内地面 y-2
        for (int i = 0; i <= 2; i++) {
            BlockPos step = base.offset(9, -i, 58 + i);
            p.place(step.above(), AIR);
            p.place(step.above(2), AIR);
            p.place(step.above(3), AIR);
            p.place(step, Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
            p.place(step.below(), Blocks.DEEPSLATE_BRICKS.defaultBlockState()); // 阶梯支撑
        }

        // 地穴内隔墙 + 锁 #3 铁门（旧钥匙）：门后是密道与高级战利品
        for (int x = 6; x <= 12; x++) {
            for (int y = -2; y <= 0; y++) {
                p.place(base.offset(x, y, 62), Blocks.DEEPSLATE_BRICKS.defaultBlockState());
            }
        }
        ironDoor(p, base.offset(9, -2, 62), Direction.SOUTH);

        // 棺木与烛台
        p.place(base.offset(7, -2, 64), Blocks.CHISELED_DEEPSLATE.defaultBlockState());
        p.place(base.offset(11, -2, 64), Blocks.CHISELED_DEEPSLATE.defaultBlockState());
        p.place(base.offset(7, -2, 66), Blocks.BARREL.defaultBlockState());
        // 密道逃生口（RepairLockedDoorState 路线：点击铁栏杆）
        p.place(base.offset(9, -2, 67), Blocks.IRON_BARS.defaultBlockState());
        p.place(base.offset(9, -1, 67), Blocks.IRON_BARS.defaultBlockState());
        p.place(base.offset(9, -1, 66), Blocks.SOUL_LANTERN.defaultBlockState()
                .setValue(LanternBlock.HANGING, true));
    }

    // ==================== 结构小件 ====================

    private static void headstone(Placer p, BlockPos ground) {
        p.place(ground.offset(0, 0, 1), Blocks.COARSE_DIRT.defaultBlockState());
        p.place(ground.above(), Blocks.COBBLESTONE_WALL.defaultBlockState());
        p.place(ground.above(2), Blocks.STONE_SLAB.defaultBlockState());
    }

    private static void deadTree(Placer p, BlockPos ground) {
        BlockState log = Blocks.DARK_OAK_LOG.defaultBlockState();
        for (int y = 0; y <= 3; y++) {
            p.place(ground.above(y), log);
        }
        p.place(ground.offset(1, 3, 0), log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        p.place(ground.offset(-1, 4, 0), log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
    }

    private static void repairStation(Placer p, BlockPos pos) {
        p.place(pos, ModBlocks.REPAIR_STATION.defaultBlockState());
        p.place(pos.above(), Blocks.CHAIN.defaultBlockState());
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

    /** 在 x=const 的南北向墙上开 2 宽拱口（z, z+1）。 */
    private static void archOnZWall(Placer p, BlockPos base, int x, int z) {
        for (int dz = 0; dz <= 1; dz++) {
            for (int y = 1; y <= 3; y++) {
                p.place(base.offset(x, y, z + dz), AIR);
            }
        }
    }

    /** 在 z=const 的东西向墙上开拱口（x1..x2）。 */
    private static void arch(Placer p, BlockPos base, int x1, int x2, int z) {
        for (int x = x1; x <= x2; x++) {
            for (int y = 1; y <= 3; y++) {
                p.place(base.offset(x, y, z), AIR);
            }
        }
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

    private static void clearVolume(Placer p, BlockPos base, int x1, int y1, int z1, int x2, int y2, int z2) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                for (int y = y1; y <= y2; y++) {
                    p.place(base.offset(x, y, z), AIR);
                }
            }
        }
    }

    private static BlockState floorPattern(int x, int z) {
        return ((x + z) & 1) == 0 ? Blocks.DARK_OAK_PLANKS.defaultBlockState()
                : Blocks.SPRUCE_PLANKS.defaultBlockState();
    }

    /** 外墙：立柱每 8 格一根，y3 处开玻璃窗带。 */
    private static BlockState outerWall(int along, int y, int max) {
        if (along % 8 == 0 || along == max) {
            return PILLAR;
        }
        if (y == 3 && along % 4 == 2) {
            return Blocks.GLASS_PANE.defaultBlockState();
        }
        return WALL;
    }

    private static BlockState chapelWall(int x, int z, int y) {
        if (y == 3 && ((x % 3 == 0 && (z == 56 || z == 70)) || (z % 3 == 0 && (x == 27 || x == 39)))) {
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
