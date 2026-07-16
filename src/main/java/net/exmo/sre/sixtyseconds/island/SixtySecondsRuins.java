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
 * 海岛废墟建筑：12 个程序化模板（编号 0..11），每岛按等级抽若干个散布在地表，
 * 全部经 {@link Placer} 写入（自动快照，随海岛还原一起回滚）。
 * 每处废墟旁保底一个物资箱（高等级岛概率升级为高级箱）。
 * <ul>
 *   <li>0 坍塌石屋 · 1 瞭望塔 · 2 石环祭坛 · 3 沉船残骸(滩) · 4 废弃码头(滩) · 5 灯塔残基(滩)</li>
 *   <li>6 半埋神殿 · 7 破败仓库 · 8 水井营地 · 9 荒废墓地 · 10 前哨围墙 · 11 教堂残骸</li>
 * </ul>
 */
public final class SixtySecondsRuins {

    public static final int TEMPLATE_COUNT = 12;
    /** 滩涂系模板（选点贴近岸线、要求低海拔）。 */
    private static final boolean[] SHORE = new boolean[TEMPLATE_COUNT];

    static {
        SHORE[3] = SHORE[4] = SHORE[5] = true;
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
            default -> chapel(p, origin, rng, islandLevel);
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

    /** 供枯树断枝用的水平随机方向（避免引 Direction 泛滥）。 */
    static Direction randomHorizontal(RandomSource rng) {
        return Direction.Plane.HORIZONTAL.getRandomDirection(rng);
    }
}
