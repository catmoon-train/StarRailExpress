package net.exmo.sre.sixtyseconds.island;

import net.exmo.sre.sixtyseconds.island.SixtySecondsIslandGenerator.Placer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 海岛废墟建筑：24 个程序化模板（编号 0..23），每岛按等级抽若干个散布在地表，
 * 全部经 {@link Placer} 写入（自动快照，随海岛还原一起回滚）。
 * 每处废墟旁保底一个物资箱（高等级岛概率升级为高级箱）。
 * <ul>
 *   <li>0 坍塌石屋 · 1 瞭望塔 · 2 石环祭坛 · 3 沉船残骸(滩) · 4 废弃码头(滩) · 5 灯塔残基(滩)</li>
 *   <li>6 半埋神殿 · 7 破败仓库 · 8 水井营地 · 9 荒废墓地 · 10 前哨围墙 · 11 教堂残骸</li>
 *   <li>12 渔夫小屋(滩) · 13 烽火台 · 14 矿洞入口 · 15 救生艇(滩) · 16 晾晒架营地 · 17 藤蔓雕像</li>
 *   <li>18 倒塌桥梁(滩) · 19 风暴避难所 · 20 废弃温室 · 21 海盗藏宝 · 22 图腾柱 · 23 篝火圈</li>
 * </ul>
 */
public final class SixtySecondsRuins {

    public static final int TEMPLATE_COUNT = 24;
    /** 滩涂系模板（选点贴近岸线、要求低海拔）。 */
    private static final boolean[] SHORE = new boolean[TEMPLATE_COUNT];

    static {
        SHORE[3] = SHORE[4] = SHORE[5] = true;   // 沉船、废弃码头、灯塔残基
        SHORE[12] = SHORE[15] = SHORE[18] = true; // 渔夫小屋、救生艇、断桥
    }

    private SixtySecondsRuins() {
    }

    /** 每岛放置废墟：数量随等级（2..4 处），模板不重复抽取，滩涂系模板落在岸边。 */
    public static void placeAll(Placer p, SixtySecondsIsland island) {
        RandomSource rng = RandomSource.create(island.seed ^ 0x521A5L);
        int count = Math.min(TEMPLATE_COUNT, 2 + island.level / 2 + rng.nextInt(2));
        List<Integer> deck = new ArrayList<>();
        for (int i = 0; i < TEMPLATE_COUNT; i++) {
            deck.add(i);
        }
        Collections.shuffle(deck, new java.util.Random(rng.nextLong()));
        int placed = 0;
        for (int template : deck) {
            if (placed >= count) {
                break;
            }
            BlockPos origin = findSpot(p.level(), island, rng, SHORE[template]);
            if (origin == null) {
                continue;
            }
            build(p, template, origin, rng, island.level);
            // 保底物资箱：贴废墟放，等级高概率给高级箱
            BlockPos boxSpot = nearbyAir(p.level(), origin, rng);
            if (boxSpot != null) {
                boolean advanced = rng.nextFloat() < 0.12F * island.level;
                SixtySecondsIslandGenerator.placeSupplyBox(p, boxSpot, advanced
                        ? org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_SUPPLY_BOX_ADVANCED
                        : org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_SUPPLY_BOX,
                        rng.nextBoolean() ? "material" : "tool");
            }
            placed++;
        }
    }

    private static BlockPos findSpot(ServerLevel level, SixtySecondsIsland island, RandomSource rng,
            boolean shore) {
        for (int attempt = 0; attempt < 20; attempt++) {
            BlockPos ground = SixtySecondsIslandGenerator.randomGround(level, island, rng,
                    shore ? 0.55 : 0.1, shore ? 0.95 : 0.6);
            if (ground == null) {
                continue;
            }
            if (shore && ground.getY() > island.seaY + 3) {
                continue; // 滩涂系要求低海拔
            }
            return ground;
        }
        return null;
    }

    private static BlockPos nearbyAir(ServerLevel level, BlockPos origin, RandomSource rng) {
        for (int attempt = 0; attempt < 12; attempt++) {
            BlockPos pos = origin.offset(rng.nextInt(9) - 4, 0, rng.nextInt(9) - 4);
            for (int dy = 2; dy >= -2; dy--) {
                BlockPos candidate = pos.above(dy);
                if (level.getBlockState(candidate).isAir()
                        && level.getBlockState(candidate.below()).isSolidRender(level, candidate.below())) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /** 建指定编号的模板（origin = 地表空气格）。 */
    public static void build(Placer p, int template, BlockPos origin, RandomSource rng, int islandLevel) {
        switch (template) {
            case 0 -> cottage(p, origin, rng, islandLevel);
            case 1 -> watchtower(p, origin, rng, islandLevel);
            case 2 -> stoneCircle(p, origin, rng, islandLevel);
            case 3 -> shipwreck(p, origin, rng);
            case 4 -> dock(p, origin, rng);
            case 5 -> lighthouse(p, origin, rng, islandLevel);
            case 6 -> temple(p, origin, rng, islandLevel);
            case 7 -> warehouse(p, origin, rng);
            case 8 -> wellCamp(p, origin, rng);
            case 9 -> graveyard(p, origin, rng);
            case 10 -> outpost(p, origin, rng, islandLevel);
            case 11 -> chapel(p, origin, rng, islandLevel);
            case 12 -> fishermanHut(p, origin, rng, islandLevel);
            case 13 -> signalBeacon(p, origin, rng, islandLevel);
            case 14 -> mineEntrance(p, origin, rng, islandLevel);
            case 15 -> lifeboat(p, origin, rng);
            case 16 -> dryingRackCamp(p, origin, rng);
            case 17 -> overgrownStatue(p, origin, rng, islandLevel);
            case 18 -> collapsedBridge(p, origin, rng, islandLevel);
            case 19 -> stormShelter(p, origin, rng, islandLevel);
            case 20 -> greenhouse(p, origin, rng);
            case 21 -> pirateCache(p, origin, rng);
            case 22 -> totemPole(p, origin, rng, islandLevel);
            default -> campfireCircle(p, origin, rng);
        }
    }

    // ── 材料/几何小工具 ────────────────────────────────────────────────────

    /** 风化石料：苔藓比例随岛等级升高。 */
    private static BlockState stone(RandomSource rng, int islandLevel) {
        if (islandLevel >= 5 && rng.nextFloat() < 0.4F) {
            return Blocks.BLACKSTONE.defaultBlockState();
        }
        return rng.nextFloat() < 0.2F + islandLevel * 0.08F
                ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                : Blocks.COBBLESTONE.defaultBlockState();
    }

    private static BlockState brick(RandomSource rng, int islandLevel) {
        float r = rng.nextFloat();
        if (r < 0.15F + islandLevel * 0.05F) {
            return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        }
        return r < 0.5F ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
                : Blocks.STONE_BRICKS.defaultBlockState();
    }

    /** 地基垫层：footprint 内 y-1 处若非实心则垫上。 */
    private static void pad(Placer p, BlockPos origin, int halfW, int halfD, BlockState block) {
        for (int dx = -halfW; dx <= halfW; dx++) {
            for (int dz = -halfD; dz <= halfD; dz++) {
                BlockPos below = origin.offset(dx, -1, dz);
                if (!p.level().getBlockState(below).isSolidRender(p.level(), below)) {
                    p.set(below, block);
                }
            }
        }
    }

    /** 残墙矩形：周边一圈，高度随机坍塌（0..hMax），留门洞。 */
    private static void ruinedWalls(Placer p, BlockPos origin, int halfW, int halfD, int hMax,
            RandomSource rng, int islandLevel) {
        for (int dx = -halfW; dx <= halfW; dx++) {
            for (int dz = -halfD; dz <= halfD; dz++) {
                boolean edge = Math.abs(dx) == halfW || Math.abs(dz) == halfD;
                if (!edge) {
                    continue;
                }
                if (dz == halfD && Math.abs(dx) <= 1) {
                    continue; // 南侧门洞
                }
                int h = 1 + rng.nextInt(hMax);
                for (int y = 0; y < h; y++) {
                    p.set(origin.offset(dx, y, dz), stone(rng, islandLevel));
                }
            }
        }
    }

    // ── 12 模板 ──────────────────────────────────────────────────────────

    /** 0 坍塌石屋：7×9 残墙 + 半塌屋梁 + 屋内杂物。 */
    private static void cottage(Placer p, BlockPos origin, RandomSource rng, int lvl) {
        pad(p, origin, 3, 4, Blocks.COBBLESTONE.defaultBlockState());
        ruinedWalls(p, origin, 3, 4, 4, rng, lvl);
        for (int dx = -2; dx <= 2; dx++) { // 断梁
            if (rng.nextFloat() < 0.6F) {
                p.set(origin.offset(dx, 3, 0), Blocks.OAK_LOG.defaultBlockState());
            }
        }
        p.set(origin.offset(1, 0, -2), Blocks.BARREL.defaultBlockState());
        if (rng.nextBoolean()) {
            p.set(origin.offset(-2, 0, 1), Blocks.HAY_BLOCK.defaultBlockState());
        }
    }

    /** 1 瞭望塔：3×3 塔身 8~11 高，顶部坍塌开口，内嵌立足层。 */
    private static void watchtower(Placer p, BlockPos origin, RandomSource rng, int lvl) {
        pad(p, origin, 2, 2, Blocks.COBBLESTONE.defaultBlockState());
        int height = 8 + rng.nextInt(4);
        for (int y = 0; y < height; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean edge = Math.abs(dx) == 1 || Math.abs(dz) == 1;
                    if (!edge) {
                        if (y == height - 2) {
                            p.set(origin.offset(dx, y, dz), Blocks.OAK_PLANKS.defaultBlockState()); // 眺望层
                        }
                        continue;
                    }
                    if (y == 0 && dz == 1 && dx == 0) {
                        continue; // 入口
                    }
                    // 顶部随机坍塌
                    if (y >= height - 2 && rng.nextFloat() < 0.45F) {
                        continue;
                    }
                    p.set(origin.offset(dx, y, dz), brick(rng, lvl));
                }
            }
        }
        p.set(origin.offset(0, height - 1, 0), Blocks.LANTERN.defaultBlockState());
    }

    /** 2 石环祭坛：半径 5 的立柱环 + 中心凿制石英祭台。 */
    private static void stoneCircle(Placer p, BlockPos origin, RandomSource rng, int lvl) {
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8;
            BlockPos base = origin.offset((int) Math.round(Math.cos(angle) * 5), 0,
                    (int) Math.round(Math.sin(angle) * 5));
            int h = 1 + rng.nextInt(3);
            for (int y = 0; y < h; y++) {
                p.set(base.above(y), stone(rng, lvl));
            }
        }
        p.set(origin, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        p.set(origin.above(), Blocks.QUARTZ_SLAB.defaultBlockState());
    }

    /** 3 沉船残骸：搁浅的木船壳（龙骨 + 两舷 + 断桅）。 */
    private static void shipwreck(Placer p, BlockPos origin, RandomSource rng) {
        BlockState hull = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState frame = Blocks.DARK_OAK_LOG.defaultBlockState();
        for (int dz = -6; dz <= 6; dz++) {
            int width = Math.abs(dz) >= 5 ? 1 : 2;
            for (int dx = -width; dx <= width; dx++) {
                p.set(origin.offset(dx, 0, dz), hull); // 船底
                if (Math.abs(dx) == width && rng.nextFloat() < 0.75F) {
                    p.set(origin.offset(dx, 1, dz), hull); // 舷侧（缺口=破损）
                    if (Math.abs(dz) <= 3 && rng.nextFloat() < 0.5F) {
                        p.set(origin.offset(dx, 2, dz), hull);
                    }
                }
            }
        }
        for (int y = 1; y <= 3; y++) { // 断桅
            p.set(origin.offset(0, y, -1), frame);
        }
        p.set(origin.offset(1, 3, -1), frame);
        p.set(origin.offset(0, 1, 3), Blocks.BARREL.defaultBlockState());
    }

    /** 4 废弃码头：栈桥伸向海面，木桩 + 缺板。 */
    private static void dock(Placer p, BlockPos origin, RandomSource rng) {
        // 栈桥朝离岛心方向延伸会更自然，这里固定 +X 方向（岛面选点已贴岸）
        for (int dx = 0; dx <= 12; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (rng.nextFloat() < 0.18F) {
                    continue; // 缺板
                }
                p.set(origin.offset(dx, 0, dz), Blocks.OAK_PLANKS.defaultBlockState());
            }
            if (dx % 3 == 0) { // 木桩
                for (int dy = -1; dy >= -4; dy--) {
                    BlockPos pile = origin.offset(dx, dy, -1);
                    if (p.level().getBlockState(pile).isSolidRender(p.level(), pile)) {
                        break;
                    }
                    p.set(pile, Blocks.OAK_FENCE.defaultBlockState());
                }
                p.set(origin.offset(dx, 1, 1), Blocks.OAK_FENCE.defaultBlockState());
            }
        }
        p.set(origin.offset(12, 1, -1), Blocks.LANTERN.defaultBlockState());
    }

    /** 5 灯塔残基：圆形石砖塔基 6 高、断口，顶灯还亮着。 */
    private static void lighthouse(Placer p, BlockPos origin, RandomSource rng, int lvl) {
        pad(p, origin, 3, 3, Blocks.STONE_BRICKS.defaultBlockState());
        int height = 6 + rng.nextInt(3);
        for (int y = 0; y < height; y++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist < 2.2 || dist > 3.2) {
                        continue;
                    }
                    if (y == 0 && dz == 3 && Math.abs(dx) <= 1) {
                        continue; // 门
                    }
                    if (y >= height - 2 && rng.nextFloat() < 0.5F) {
                        continue; // 断口
                    }
                    p.set(origin.offset(dx, y, dz),
                            y % 3 == 2 ? Blocks.WHITE_CONCRETE.defaultBlockState() : brick(rng, lvl));
                }
            }
        }
        p.set(origin.above(height), Blocks.GLOWSTONE.defaultBlockState());
    }

    /** 6 半埋神殿：下沉 2 格的砂石平台 + 四角残柱 + 阶梯。 */
    private static void temple(Placer p, BlockPos origin, RandomSource rng, int lvl) {
        BlockPos base = origin.below(2);
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                p.set(base.offset(dx, 0, dz), Blocks.SANDSTONE.defaultBlockState());
                p.set(base.offset(dx, 1, dz), Blocks.AIR.defaultBlockState());
                p.set(base.offset(dx, 2, dz), Blocks.AIR.defaultBlockState());
            }
        }
        for (int cx = -4; cx <= 4; cx += 8) {
            for (int cz = -4; cz <= 4; cz += 8) {
                int h = 2 + rng.nextInt(3);
                for (int y = 1; y <= h; y++) {
                    p.set(base.offset(cx, y, cz), Blocks.CHISELED_SANDSTONE.defaultBlockState());
                }
            }
        }
        p.set(base.offset(0, 1, 0), Blocks.GOLD_BLOCK.defaultBlockState()); // 祭台残金
        for (int dz = 6; dz <= 7; dz++) { // 没入地面的台阶
            p.set(base.offset(0, dz - 5, dz), Blocks.SANDSTONE.defaultBlockState());
            p.set(base.offset(-1, dz - 5, dz), Blocks.SANDSTONE.defaultBlockState());
            p.set(base.offset(1, dz - 5, dz), Blocks.SANDSTONE.defaultBlockState());
        }
    }

    /** 7 破败仓库：木框架 9×7，屋顶塌了一半，货箱散落。 */
    private static void warehouse(Placer p, BlockPos origin, RandomSource rng) {
        pad(p, origin, 4, 3, Blocks.OAK_PLANKS.defaultBlockState());
        for (int dx = -4; dx <= 4; dx += 8) {
            for (int dz = -3; dz <= 3; dz += 6) {
                for (int y = 0; y < 4; y++) {
                    p.set(origin.offset(dx, y, dz), Blocks.OAK_LOG.defaultBlockState());
                }
            }
        }
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                boolean edge = Math.abs(dx) == 4 || Math.abs(dz) == 3;
                if (edge && rng.nextFloat() < 0.55F) {
                    p.set(origin.offset(dx, 0, dz), Blocks.OAK_PLANKS.defaultBlockState());
                    if (rng.nextFloat() < 0.5F) {
                        p.set(origin.offset(dx, 1, dz), Blocks.OAK_PLANKS.defaultBlockState());
                    }
                }
                if (dx <= 0 && rng.nextFloat() < 0.8F) { // 半边屋顶尚存
                    p.set(origin.offset(dx, 4, dz), Blocks.OAK_SLAB.defaultBlockState());
                }
            }
        }
        for (int i = 0; i < 3; i++) {
            p.set(origin.offset(rng.nextInt(7) - 3, 0, rng.nextInt(5) - 2),
                    Blocks.BARREL.defaultBlockState());
        }
    }

    /** 8 水井营地：石井（含水）+ 熄灭营火 + 坐凳原木。 */
    private static void wellCamp(Placer p, BlockPos origin, RandomSource rng) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean edge = Math.abs(dx) == 1 || Math.abs(dz) == 1;
                if (edge) {
                    p.set(origin.offset(dx, 0, dz), Blocks.COBBLESTONE.defaultBlockState());
                    p.set(origin.offset(dx, -1, dz), Blocks.COBBLESTONE.defaultBlockState());
                } else {
                    p.set(origin.offset(dx, -1, dz), Blocks.WATER.defaultBlockState());
                    p.set(origin.offset(dx, -2, dz), Blocks.COBBLESTONE.defaultBlockState());
                }
            }
        }
        p.set(origin.offset(-1, 1, -1), Blocks.OAK_FENCE.defaultBlockState());
        p.set(origin.offset(1, 1, 1), Blocks.OAK_FENCE.defaultBlockState());
        BlockPos camp = origin.offset(4, 0, 0);
        p.set(camp, Blocks.CAMPFIRE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false));
        p.set(camp.offset(0, 0, 2), Blocks.OAK_LOG.defaultBlockState());
        p.set(camp.offset(0, 0, -2), Blocks.OAK_LOG.defaultBlockState());
    }

    /** 9 荒废墓地：两排土坟 + 残碑 + 枯灌木。 */
    private static void graveyard(Placer p, BlockPos origin, RandomSource rng) {
        for (int row = 0; row < 2; row++) {
            for (int i = 0; i < 4; i++) {
                BlockPos grave = origin.offset(i * 3 - 4, 0, row * 4 - 2);
                p.set(grave, Blocks.COARSE_DIRT.defaultBlockState());
                p.set(grave.offset(0, 0, 1), Blocks.COARSE_DIRT.defaultBlockState());
                if (rng.nextFloat() < 0.8F) {
                    p.set(grave.offset(0, 1, -1), Blocks.COBBLESTONE_WALL.defaultBlockState());
                }
                if (rng.nextFloat() < 0.3F) {
                    p.set(grave.above(), Blocks.DEAD_BUSH.defaultBlockState());
                }
            }
        }
    }

    /** 10 前哨围墙：9×9 半塌石墙环 + 一角塔基 + 铁栅门洞。 */
    private static void outpost(Placer p, BlockPos origin, RandomSource rng, int lvl) {
        ruinedWalls(p, origin, 4, 4, 3, rng, lvl);
        for (int y = 0; y < 5; y++) { // 角塔
            for (int dx = 3; dx <= 4; dx++) {
                for (int dz = 3; dz <= 4; dz++) {
                    if (y >= 3 && rng.nextFloat() < 0.4F) {
                        continue;
                    }
                    p.set(origin.offset(dx, y, dz), brick(rng, lvl));
                }
            }
        }
        p.set(origin.offset(0, 0, 4), Blocks.IRON_BARS.defaultBlockState());
        p.set(origin.offset(0, 1, 4), Blocks.IRON_BARS.defaultBlockState());
        p.set(origin.offset(-2, 0, 0), Blocks.BARREL.defaultBlockState());
    }

    /** 11 教堂残骸：8×13 石砖长厅，山墙 + 断拱 + 祭坛烛台。 */
    private static void chapel(Placer p, BlockPos origin, RandomSource rng, int lvl) {
        pad(p, origin, 3, 6, Blocks.STONE_BRICKS.defaultBlockState());
        for (int dz = -6; dz <= 6; dz++) {
            for (int dx = -3; dx <= 3; dx += 6) {
                int h = dz <= -4 ? 5 : 2 + rng.nextInt(3); // 北端山墙最高，向南塌
                for (int y = 0; y < h; y++) {
                    p.set(origin.offset(dx, y, dz), brick(rng, lvl));
                }
            }
        }
        for (int dx = -3; dx <= 3; dx++) { // 北山墙封口 + 断拱
            for (int y = 0; y < 6 - Math.abs(dx); y++) {
                p.set(origin.offset(dx, y, -6), brick(rng, lvl));
            }
        }
        p.set(origin.offset(0, 0, -5), Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        p.set(origin.offset(0, 1, -5), Blocks.CANDLE.defaultBlockState());
        for (int dz = -3; dz <= 3; dz += 2) { // 断长椅
            if (rng.nextFloat() < 0.7F) {
                p.set(origin.offset(-1, 0, dz), Blocks.OAK_SLAB.defaultBlockState());
                p.set(origin.offset(1, 0, dz), Blocks.OAK_SLAB.defaultBlockState());
            }
        }
    }

    // ── 新增模板 12..23 ────────────────────────────────────────────────────

    /** 12 渔夫小屋：临水小木棚 5×4，半塌屋顶 + 渔具杂物。 */
    private static void fishermanHut(Placer p, BlockPos origin, RandomSource rng, int lvl) {
        pad(p, origin, 2, 2, Blocks.OAK_PLANKS.defaultBlockState());
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                if (!edge) continue;
                if (dz == 2 && dx == 0) continue; // 门
                int h = 1 + rng.nextInt(3);
                for (int y = 0; y < h; y++) {
                    if (y >= 2 && rng.nextFloat() < 0.5F) continue;
                    p.set(origin.offset(dx, y, dz), Blocks.OAK_PLANKS.defaultBlockState());
                }
            }
        }
        // 半边残顶
        for (int dx = -2; dx <= 0; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (rng.nextFloat() < 0.65F)
                    p.set(origin.offset(dx, 2, dz), Blocks.OAK_SLAB.defaultBlockState());
            }
        }
        p.set(origin.offset(1, 0, 1), Blocks.BARREL.defaultBlockState());
        p.set(origin.offset(-1, 0, -1), Blocks.COBWEB.defaultBlockState());
        if (rng.nextBoolean())
            p.set(origin.offset(0, 0, 0), Blocks.CRAFTING_TABLE.defaultBlockState());
    }

    /** 13 信号烽火台：5..8 高石柱，顶置熄灭的营火（可被玩家点燃求援）。 */
    private static void signalBeacon(Placer p, BlockPos origin, RandomSource rng, int lvl) {
        pad(p, origin, 1, 1, Blocks.COBBLESTONE.defaultBlockState());
        int h = 5 + rng.nextInt(4);
        for (int y = 0; y < h; y++) {
            p.set(origin.above(y), brick(rng, lvl));
            // 侧面随机缺块（风化）
            if (y > 0 && y < h - 1 && rng.nextFloat() < 0.12F) {
                Direction dir = randomHorizontal(rng);
                p.set(origin.relative(dir).above(y), Blocks.AIR.defaultBlockState());
            }
        }
        p.set(origin.above(h), Blocks.CAMPFIRE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false));
    }

    /** 14 废弃矿洞入口：山体掏出的 3×3 洞口，木梁支撑，洞内一段隧道。 */
    private static void mineEntrance(Placer p, BlockPos origin, RandomSource rng, int lvl) {
        // 挖出入口隧道（向 -Z 方向）
        for (int dz = 0; dz <= 4; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 2; dy++) {
                    if (dz == 0 && (Math.abs(dx) == 1 && dy <= 1)) continue; // 门框留石
                    BlockPos tp = origin.offset(dx, dy, -dz);
                    p.set(tp, Blocks.AIR.defaultBlockState());
                }
            }
            // 木支撑框每 2 格一道
            if (dz % 2 == 1 || dz == 4) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = 0; dy <= 2; dy++) {
                        boolean frame = Math.abs(dx) == 1 || dy == 2;
                        if (frame)
                            p.set(origin.offset(dx, dy, -dz), Blocks.OAK_LOG.defaultBlockState());
                    }
                }
            }
        }
        // 洞口上方横木
        p.set(origin.offset(0, 2, 0), Blocks.OAK_LOG.defaultBlockState());
        p.set(origin.offset(-1, 2, 0), Blocks.OAK_LOG.defaultBlockState());
        p.set(origin.offset(1, 2, 0), Blocks.OAK_LOG.defaultBlockState());
        // 矿车/铁轨残骸
        p.set(origin.offset(0, 0, -3), Blocks.RAIL.defaultBlockState());
        p.set(origin.offset(0, 0, -4), Blocks.RAIL.defaultBlockState());
        if (rng.nextBoolean())
            p.set(origin.offset(1, 0, -3), Blocks.CHEST.defaultBlockState());
    }

    /** 15 搁浅救生艇：4×2 小木船搁浅在滩头，半截入沙。 */
    private static void lifeboat(Placer p, BlockPos origin, RandomSource rng) {
        BlockState hull = Blocks.SPRUCE_PLANKS.defaultBlockState();
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                boolean rim = Math.abs(dx) == 2;
                if (rim) {
                    p.set(origin.offset(dx, 0, dz), hull);
                    if (Math.abs(dz) <= 1 && rng.nextFloat() < 0.55F)
                        p.set(origin.offset(dx, 1, dz), hull);
                } else {
                    p.set(origin.offset(dx, 0, dz), hull);
                }
            }
        }
        // 船尾板 + 断桨
        p.set(origin.offset(0, 1, -1), Blocks.SPRUCE_SLAB.defaultBlockState());
        p.set(origin.offset(0, 1, 1), Blocks.SPRUCE_SLAB.defaultBlockState());
        if (rng.nextBoolean())
            p.set(origin.offset(2, 1, 0), Blocks.OAK_FENCE.defaultBlockState()); // 残桨
    }

    /** 16 晾晒架营地：两根木柱 + 横梁 + 蛛网/线模拟晾晒物，旁有木桶。 */
    private static void dryingRackCamp(Placer p, BlockPos origin, RandomSource rng) {
        pad(p, origin, 3, 2, Blocks.COARSE_DIRT.defaultBlockState());
        // 两排晾晒架
        for (int rack = 0; rack < 2; rack++) {
            int dz = rack * 3 - 1;
            for (int y = 0; y < 3; y++)
                p.set(origin.offset(-2, y, dz), Blocks.OAK_FENCE.defaultBlockState());
            for (int y = 0; y < 3; y++)
                p.set(origin.offset(2, y, dz), Blocks.OAK_FENCE.defaultBlockState());
            p.set(origin.offset(-2, 2, dz), Blocks.OAK_SLAB.defaultBlockState());
            p.set(origin.offset(2, 2, dz), Blocks.OAK_SLAB.defaultBlockState());
            // 横绳
            p.set(origin.offset(-1, 2, dz), Blocks.TRIPWIRE.defaultBlockState());
            p.set(origin.offset(0, 2, dz), Blocks.TRIPWIRE.defaultBlockState());
            p.set(origin.offset(1, 2, dz), Blocks.TRIPWIRE.defaultBlockState());
            // 晾晒物（随机蛛网）
            if (rng.nextFloat() < 0.5F)
                p.set(origin.offset(0, 1, dz), Blocks.COBWEB.defaultBlockState());
        }
        p.set(origin.offset(0, 0, 2), Blocks.BARREL.defaultBlockState());
    }

    /** 17 藤蔓覆盖的雕像：3 高石像底座，风化残缺，挂满藤蔓。 */
    private static void overgrownStatue(Placer p, BlockPos origin, RandomSource rng, int lvl) {
        pad(p, origin, 1, 1, Blocks.STONE_BRICKS.defaultBlockState());
        // 底座
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                p.set(origin.offset(dx, 0, dz), Blocks.STONE_BRICKS.defaultBlockState());
        // 雕像主体
        p.set(origin.offset(0, 1, 0), Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        p.set(origin.offset(0, 2, 0), Blocks.STONE_BRICK_WALL.defaultBlockState());
        // 头部（残缺）
        if (rng.nextFloat() < 0.65F)
            p.set(origin.offset(0, 3, 0), Blocks.PLAYER_HEAD.defaultBlockState());
        // 藤蔓覆盖
        for (int y = 0; y <= 3; y++) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                if (rng.nextFloat() < 0.3F + lvl * 0.08F) {
                    BlockPos vine = origin.relative(dir).above(y);
                    if (p.level().getBlockState(vine).isAir())
                        p.set(vine, Blocks.VINE.defaultBlockState());
                }
            }
        }
    }

    /** 18 倒塌桥梁：断桥向海面伸出 8 格后断裂，木桩 + 缺板 + 水中残柱。 */
    private static void collapsedBridge(Placer p, BlockPos origin, RandomSource rng, int lvl) {
        // 桥面向 +X 延伸
        for (int dx = 0; dx <= 8; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx >= 5 && rng.nextFloat() < 0.3F) continue; // 后半更破
                if (rng.nextFloat() < 0.12F) continue; // 随机缺板
                p.set(origin.offset(dx, 0, dz), Blocks.OAK_PLANKS.defaultBlockState());
            }
            // 护栏残桩
            p.set(origin.offset(dx, 1, -2), Blocks.OAK_FENCE.defaultBlockState());
            p.set(origin.offset(dx, 1, 2), Blocks.OAK_FENCE.defaultBlockState());
            // 桥墩（入水）
            if (dx % 2 == 0) {
                for (int dy = -1; dy >= -4; dy--)
                    p.set(origin.offset(dx, dy, -1), Blocks.OAK_LOG.defaultBlockState());
                for (int dy = -1; dy >= -4; dy--)
                    p.set(origin.offset(dx, dy, 1), Blocks.OAK_LOG.defaultBlockState());
            }
        }
        // 断裂处残骸散落水中
        p.set(origin.offset(8, -1, 0), Blocks.OAK_SLAB.defaultBlockState());
        p.set(origin.offset(9, -1, -1), Blocks.OAK_PLANKS.defaultBlockState());
    }

    /** 19 风暴避难所：半地下入口 3×3 铁门 + 向下阶梯 + 铁块墙壁。 */
    private static void stormShelter(Placer p, BlockPos origin, RandomSource rng, int lvl) {
        // 入口框体
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                boolean frame = Math.abs(dx) == 1 || dy == 2;
                if (frame)
                    p.set(origin.offset(dx, dy, 0), Blocks.IRON_BLOCK.defaultBlockState());
            }
        }
        // 向下阶梯（origin 处原为空气，向下挖）
        for (int dy = 0; dy >= -5; dy--) {
            p.set(origin.offset(0, dy, 1), Blocks.AIR.defaultBlockState());
            p.set(origin.offset(1, dy, 1), Blocks.AIR.defaultBlockState());
            p.set(origin.offset(-1, dy, 1), Blocks.AIR.defaultBlockState());
            // 阶梯
            p.set(origin.offset(0, dy, 2), Blocks.STONE_BRICKS.defaultBlockState());
            p.set(origin.offset(0, dy, 0), Blocks.STONE_BRICKS.defaultBlockState());
            p.set(origin.offset(-1, dy, 2), Blocks.STONE_BRICKS.defaultBlockState());
            p.set(origin.offset(1, dy, 2), Blocks.STONE_BRICKS.defaultBlockState());
        }
        // 底部小空间
        for (int dx = -2; dx <= 2; dx++) {
            p.set(origin.offset(dx, -5, 2), Blocks.STONE_BRICKS.defaultBlockState());
            if (Math.abs(dx) <= 1) p.set(origin.offset(dx, -5, 1), Blocks.AIR.defaultBlockState());
        }
        if (rng.nextBoolean())
            p.set(origin.offset(0, -4, 0), Blocks.CHEST.defaultBlockState());
    }

    /** 20 废弃温室：玻璃+铁框 6×4 大棚，屋顶塌了大半，内部残存花盆。 */
    private static void greenhouse(Placer p, BlockPos origin, RandomSource rng) {
        pad(p, origin, 3, 2, Blocks.IRON_BLOCK.defaultBlockState());
        // 铁框架
        for (int dx = -3; dx <= 3; dx += 6) {
            for (int dz = -2; dz <= 2; dz += 4) {
                for (int y = 0; y < 3; y++)
                    p.set(origin.offset(dx, y, dz), Blocks.IRON_BLOCK.defaultBlockState());
            }
        }
        // 玻璃墙（残破）
        for (int dz = -2; dz <= 2; dz++) {
            for (int y = 0; y <= 2; y++) {
                if (rng.nextFloat() < 0.35F) continue;
                p.set(origin.offset(-3, y, dz), Blocks.GLASS_PANE.defaultBlockState());
                p.set(origin.offset(3, y, dz), Blocks.GLASS_PANE.defaultBlockState());
            }
        }
        // 半边玻璃顶
        for (int dx = -2; dx <= 0; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (rng.nextFloat() < 0.4F) continue;
                p.set(origin.offset(dx, 3, dz), Blocks.GLASS.defaultBlockState());
            }
        }
        // 花盆
        p.set(origin.offset(-1, 0, 0), Blocks.FLOWER_POT.defaultBlockState());
        if (rng.nextBoolean())
            p.set(origin.offset(1, 0, 0), Blocks.DEAD_BUSH.defaultBlockState());
    }

    /** 21 海盗藏宝处：岩石裂缝中埋藏的宝箱 + 散落金块/骨块。 */
    private static void pirateCache(Placer p, BlockPos origin, RandomSource rng) {
        // 岩石围成掩护
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean edge = Math.abs(dx) >= 2 || Math.abs(dz) >= 2;
                if (!edge) continue;
                if (dz == -2 && dx == 0) continue; // 裂缝入口
                for (int y = 0; y <= 1; y++) {
                    if (rng.nextFloat() < 0.25F) continue;
                    p.set(origin.offset(dx, y, dz), Blocks.MOSSY_COBBLESTONE.defaultBlockState());
                }
            }
        }
        // 宝箱埋在半腰
        p.set(origin.offset(0, -1, 0), Blocks.SAND.defaultBlockState());
        p.set(origin, Blocks.CHEST.defaultBlockState());
        // 散落财物
        p.set(origin.offset(1, 0, 1), Blocks.GOLD_ORE.defaultBlockState());
        if (rng.nextFloat() < 0.4F)
            p.set(origin.offset(-1, 0, 1), Blocks.BONE_BLOCK.defaultBlockState());
        if (rng.nextFloat() < 0.25F)
            p.set(origin.offset(0, 1, 0), Blocks.SKELETON_SKULL.defaultBlockState());
    }

    /** 22 图腾柱：5..7 高木柱，不同高度段交替用不同原木 + 雕刻南瓜顶。 */
    private static void totemPole(Placer p, BlockPos origin, RandomSource rng, int lvl) {
        pad(p, origin, 1, 1, Blocks.COBBLESTONE.defaultBlockState());
        int h = 5 + rng.nextInt(3);
        for (int y = 0; y < h; y++) {
            BlockState log = switch (y % 3) {
                case 0 -> Blocks.OAK_LOG.defaultBlockState();
                case 1 -> Blocks.SPRUCE_LOG.defaultBlockState();
                default -> Blocks.CHERRY_LOG.defaultBlockState();
            };
            p.set(origin.above(y), log);
            // 侧面雕刻（不同方向小突起）
            if (y % 2 == 1) {
                Direction dir = randomHorizontal(rng);
                BlockPos side = origin.relative(dir).above(y);
                if (p.level().getBlockState(side).isAir())
                    p.set(side, Blocks.OAK_FENCE.defaultBlockState());
            }
        }
        // 顶饰
        if (rng.nextFloat() < 0.5F)
            p.set(origin.above(h), Blocks.CARVED_PUMPKIN.defaultBlockState());
        else
            p.set(origin.above(h), Blocks.LANTERN.defaultBlockState());
        // 低等级岛图腾柱底部有祭品
        if (lvl <= 2 && rng.nextBoolean())
            p.set(origin.offset(1, 0, 0), Blocks.POPPY.defaultBlockState());
    }

    /** 23 篝火集结点：圆形原木座围绕熄灭营火，地面有踩踏痕迹。 */
    private static void campfireCircle(Placer p, BlockPos origin, RandomSource rng) {
        // 踩踏地面
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist <= 3.5 && p.level().getBlockState(origin.offset(dx, -1, dz)).is(Blocks.GRASS_BLOCK))
                    p.set(origin.offset(dx, -1, dz), Blocks.COARSE_DIRT.defaultBlockState());
            }
        }
        // 中心营火（已熄）
        p.set(origin, Blocks.CAMPFIRE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false));
        // 环绕原木坐凳
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8;
            int dx = (int) Math.round(Math.cos(angle) * 2.5);
            int dz = (int) Math.round(Math.sin(angle) * 2.5);
            if (rng.nextFloat() < 0.8F)
                p.set(origin.offset(dx, 0, dz), Blocks.OAK_LOG.defaultBlockState());
        }
        // 柴火堆
        p.set(origin.offset(2, 0, 0), Blocks.OAK_LOG.defaultBlockState());
        p.set(origin.offset(-2, 0, 1), Blocks.OAK_LOG.defaultBlockState());
    }

    /** 供枯树断枝用的水平随机方向（避免引 Direction 泛滥）。 */
    static Direction randomHorizontal(RandomSource rng) {
        return Direction.Plane.HORIZONTAL.getRandomDirection(rng);
    }
}
