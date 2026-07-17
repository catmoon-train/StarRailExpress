package net.exmo.sre.sixtyseconds.island;

import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.ServerTaskInfoClasses;
import net.exmo.sre.sixtyseconds.entity.SixtySecondsMonsterEntity;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsPveSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.Noellesroles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 海岛程序化生成器：值噪声（fBm）岛屿地形 + 按等级色板 + 树木岩石 + 废墟/物资箱/怪物，
 * 全部经 {@link Placer} 记录快照、走 {@code GameUtils.serverTaskQueue} 跨 tick 异步建造
 * （仿 {@code SixtySecondsArena.BuildTask}，防看门狗卡死）。
 * <p>
 * 形状函数 {@link #landValue} 只依赖岛屿 seed 与世界坐标——客户端海图用同一函数低分辨率重采样
 * 即可画出与实际一致的岛屿轮廓（无需同步方块）。
 */
public final class SixtySecondsIslandGenerator {

    /** 环岛水裙边宽度（陆地半径之外再铺这么宽的海面）。 */
    public static final int WATER_SKIRT = 18;
    /** 单元格纵向生成范围：海平面以下挖/铺的深度、以上净空+山体高度。 */
    public static final int DEPTH_BELOW_SEA = 8;
    public static final int HEIGHT_ABOVE_SEA = 72;
    /** 岸线阈值：landValue 大于它即为陆地。 */
    public static final float LAND_THRESHOLD = 0.12F;
    /** 每 tick 处理的工作项数（每项 ≈ 16×16 柱）。 */
    private static final int MAX_ITEMS_PER_TICK = 30;
    /** 列 patch 边长。 */
    private static final int PATCH = 16;

    private SixtySecondsIslandGenerator() {
    }

    // ── 值噪声（确定性、无依赖；客户端海图共用）────────────────────────────

    private static float hash(long seed, int x, int z) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    private static float valueNoise(long seed, double x, double z) {
        int x0 = Mth.floor(x);
        int z0 = Mth.floor(z);
        float fx = (float) (x - x0);
        float fz = (float) (z - z0);
        float sx = fx * fx * (3 - 2 * fx);
        float sz = fz * fz * (3 - 2 * fz);
        float a = hash(seed, x0, z0);
        float b = hash(seed, x0 + 1, z0);
        float c = hash(seed, x0, z0 + 1);
        float d = hash(seed, x0 + 1, z0 + 1);
        return Mth.lerp(sz, Mth.lerp(sx, a, b), Mth.lerp(sx, c, d));
    }

    /** 分形值噪声，返回约 [0,1]。 */
    public static float fbm(long seed, double x, double z, int octaves) {
        float sum = 0;
        float amp = 0.5F;
        float norm = 0;
        double freq = 1;
        for (int i = 0; i < octaves; i++) {
            sum += amp * valueNoise(seed + i * 1013L, x * freq, z * freq);
            norm += amp;
            freq *= 2;
            amp *= 0.5F;
        }
        return sum / norm;
    }

    /**
     * 岛屿形状函数：>{@link #LAND_THRESHOLD} 为陆地，值越大越接近岛心/山顶。
     * 径向衰减 × 噪声扰动，岸线自然破碎。客户端海图用同一函数画轮廓。
     * 珊瑚环礁类型：中心为潟湖（低值），环形礁盘（高值）。
     */
    public static float landValue(SixtySecondsIsland island, double worldX, double worldZ) {
        double dx = worldX - island.centerX;
        double dz = worldZ - island.centerZ;
        double d = Math.sqrt(dx * dx + dz * dz) / island.radius;
        if (d > 1.35) {
            return 0;
        }
        float n = fbm(island.seed, worldX * 0.02, worldZ * 0.02, 4);

        if (island.type == SixtySecondsIsland.Type.CORAL) {
            // 环礁：环形高值，中心低（潟湖），外缘快速衰减
            // d=0 → center of lagoon (low), d≈0.55 → reef ring (peak), d>0.9 → outer slope
            float ring = d < 0.2F ? 0.08F + 0.12F * n
                    : d < 0.7F ? 0.35F + 0.9F * n * (float) Math.sin(d * Math.PI / 0.7F)
                            : (float) ((1.0 - d / 1.35) * (0.3F + 0.7F * n));
            return Math.max(0, ring);
        }
        float radial = (float) (1.0 - Math.pow(d, 1.9));
        return radial * (0.45F + 1.1F * n);
    }

    // ── 群岛规划 ─────────────────────────────────────────────────────────

    /** 等级分布模式（首岛恒为 1 级港湾，其余按此循环；偏重低等级以配合小型/中型岛）。 */
    private static final int[] LEVEL_PATTERN = {1, 2, 1, 2, 3, 1, 2, 3, 1, 2, 4, 1, 3, 5, 1, 2, 3, 4, 2};

    /** 默认岛屿基准半径（{@code plan} 的 baseRadius 传 ≤0 时使用）。 */
    public static final int DEFAULT_BASE_RADIUS = 340;

    /**
     * 规划一批海岛：等级分布、大小分类、名字（前缀不重复）、噪声种子、互不重叠的位置。
     * 首岛恒为 1 级中型港湾（海图默认解锁）。
     * 大小分布 ~45% 小型 / ~35% 中型 / ~20% 大型；
     * 小型岛等级偏低（1-2）、大型岛偏高（2-5）、中型按模式循环。
     * {@code baseRadius} 为可编辑的基准半径（≤0 用默认 {@link #DEFAULT_BASE_RADIUS}）；
     * 实际半径 = base×size.radiusMult + level×size.levelRadiusBonus + 随机(0..variance)。
     */
    public static List<SixtySecondsIsland> plan(RandomSource rng, int count, int centerX, int centerZ, int seaY,
            int baseRadius) {
        int base = baseRadius > 0 ? baseRadius : DEFAULT_BASE_RADIUS;
        List<Integer> prefixes = new ArrayList<>();
        for (int i = 0; i < SixtySecondsIsland.NAME_PREFIX_COUNT; i++) {
            prefixes.add(i);
        }
        Collections.shuffle(prefixes, new java.util.Random(rng.nextLong()));
        List<SixtySecondsIsland> islands = new ArrayList<>();
        // 布局域以大岛基准半径为准
        int effBase = (int) (base * SixtySecondsIsland.Size.LARGE.radiusMult);
        int extent = (int) (2.2 * (effBase + WATER_SKIRT + 8) * Math.sqrt(count)) + 120;
        int patIdx = 0;
        for (int i = 0; i < count; i++) {
            SixtySecondsIsland island = new SixtySecondsIsland();
            island.id = i;
            // ── 大小分类 ──
            if (i == 0) {
                island.size = SixtySecondsIsland.Size.MEDIUM;
                island.level = 1;
            } else {
                float r = rng.nextFloat();
                if (r < 0.45F) {
                    island.size = SixtySecondsIsland.Size.SMALL;
                    island.level = 1 + rng.nextInt(2); // 1~2
                } else if (r < 0.80F) {
                    island.size = SixtySecondsIsland.Size.MEDIUM;
                    island.level = LEVEL_PATTERN[patIdx % LEVEL_PATTERN.length];
                    patIdx++;
                } else {
                    island.size = SixtySecondsIsland.Size.LARGE;
                    island.level = 2 + rng.nextInt(4); // 2~5
                }
            }
            island.namePrefix = prefixes.get(i % prefixes.size());
            island.nameSuffix = rng.nextInt(SixtySecondsIsland.NAME_SUFFIX_COUNT);
            island.seed = rng.nextLong();
            island.seaY = seaY;
            // ── 生态类型：按等级分布，首岛恒为热带（友好新手岛）──
            island.type = assignType(rng, island.level, i == 0);
            // ── 半径：base×size乘数 + level加成 + 随机 ──
            SixtySecondsIsland.Size sz = island.size;
            island.radius = (int) (base * sz.radiusMult)
                    + island.level * sz.levelRadiusBonus
                    + rng.nextInt(sz.radiusVariance + 1);
            // 位置：矩形域内拒绝采样，保证与已放置的岛间距足够（水裙边不相互吞并）
            for (int attempt = 0; attempt < 400; attempt++) {
                int x = i == 0 ? centerX : centerX + rng.nextInt(extent * 2 + 1) - extent;
                int z = i == 0 ? centerZ : centerZ + rng.nextInt(extent * 2 + 1) - extent;
                boolean ok = true;
                for (SixtySecondsIsland other : islands) {
                    double need = island.radius + other.radius + WATER_SKIRT * 2 + 16;
                    if (other.distSqr(x, z) < need * need) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    island.centerX = x;
                    island.centerZ = z;
                    islands.add(island);
                    break;
                }
                if (attempt == 399) {
                    extent += 80; // 放不下就扩域重试本岛
                    i--;
                }
            }
            island.dockX = island.centerX;
            island.dockY = seaY;
            island.dockZ = island.centerZ;
        }
        return islands;
    }

    /**
     * 按等级分配生态类型。首岛强制热带（新手友好）。
     * 等级越高越偏危险/荒凉类型。
     */
    private static SixtySecondsIsland.Type assignType(RandomSource rng, int level, boolean firstIsland) {
        if (firstIsland) return SixtySecondsIsland.Type.TROPICAL;
        float r = rng.nextFloat();
        return switch (level) {
            case 1 -> r < 0.50F ? SixtySecondsIsland.Type.TROPICAL
                    : r < 0.80F ? SixtySecondsIsland.Type.MARSH
                            : SixtySecondsIsland.Type.CORAL;
            case 2 -> r < 0.20F ? SixtySecondsIsland.Type.FROST
                    : r < 0.40F ? SixtySecondsIsland.Type.PLATEAU
                            : r < 0.60F ? SixtySecondsIsland.Type.JUNGLE
                                    : r < 0.75F ? SixtySecondsIsland.Type.TROPICAL
                                            : r < 0.90F ? SixtySecondsIsland.Type.MARSH
                                                    : SixtySecondsIsland.Type.CORAL;
            case 3 -> r < 0.25F ? SixtySecondsIsland.Type.JUNGLE
                    : r < 0.50F ? SixtySecondsIsland.Type.PLATEAU
                            : r < 0.70F ? SixtySecondsIsland.Type.FROST
                                    : r < 0.85F ? SixtySecondsIsland.Type.BARREN
                                            : SixtySecondsIsland.Type.TROPICAL;
            case 4 -> r < 0.40F ? SixtySecondsIsland.Type.VOLCANIC
                    : r < 0.80F ? SixtySecondsIsland.Type.BARREN
                            : SixtySecondsIsland.Type.JUNGLE;
            default -> r < 0.70F ? SixtySecondsIsland.Type.VOLCANIC // level 5
                    : SixtySecondsIsland.Type.BARREN;
        };
    }

    /** 快照（还原用）：仅记录我们覆盖前<b>非空气</b>的方块；还原=单元格清空后回写这些。 */
    record Snapshot(BlockState state, CompoundTag blockEntityTag) {
    }

    /** 记录快照的放置器：所有生成写入都必须经它。 */
    public static final class Placer {
        final ServerLevel level;
        final LinkedHashMap<BlockPos, Snapshot> snapshots;

        Placer(ServerLevel level, LinkedHashMap<BlockPos, Snapshot> snapshots) {
            this.level = level;
            this.snapshots = snapshots;
        }

        public void set(BlockPos pos, BlockState state) {
            BlockState old = level.getBlockState(pos);
            if (old == state) {
                return;
            }
            if (!old.isAir() && !snapshots.containsKey(pos)) {
                BlockEntity be = level.getBlockEntity(pos);
                CompoundTag tag = be == null ? null : be.saveWithFullMetadata(level.registryAccess());
                snapshots.put(pos.immutable(), new Snapshot(old, tag));
                net.minecraft.world.Clearable.tryClear(be);
            }
            level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            level.getLightEngine().checkBlock(pos);
        }

        public void air(BlockPos pos) {
            set(pos, Blocks.AIR.defaultBlockState());
        }

        public ServerLevel level() {
            return level;
        }
    }

    /**
     * 把整个群岛的建造排入 {@code GameUtils.serverTaskQueue}。完成后回调 {@code onComplete}。
     * 工作项顺序：每岛先地形列 patch，再装饰（树/岩）、废墟、物资箱+怪物+登岛点。
     */
    public static void queueBuild(ServerLevel level, List<SixtySecondsIsland> islands,
            LinkedHashMap<BlockPos, Snapshot> snapshots, boolean placeShelterDoors, Runnable onComplete) {
        Placer placer = new Placer(level, snapshots);
        List<Runnable> work = new ArrayList<>();
        for (SixtySecondsIsland island : islands) {
            int r = island.radius + WATER_SKIRT;
            for (int px = island.centerX - r; px <= island.centerX + r; px += PATCH) {
                for (int pz = island.centerZ - r; pz <= island.centerZ + r; pz += PATCH) {
                    int x0 = px;
                    int z0 = pz;
                    work.add(() -> buildPatch(placer, island, x0, z0,
                            Math.min(x0 + PATCH - 1, island.centerX + r),
                            Math.min(z0 + PATCH - 1, island.centerZ + r)));
                }
            }
            work.add(() -> decorate(placer, island));
            work.add(() -> SixtySecondsRuins.placeAll(placer, island));
            work.add(() -> populate(placer, island));
            // 一级岛：地形建好后放一扇避难所门（走 Placer 记快照，还原时随地形自动清除）。
            if (placeShelterDoors && island.level == 1) {
                work.add(() -> placeShelterDoor(placer, island));
            }
        }
        GameUtils.serverTaskQueue.add(new IslandTask(level, work,
                "message.noellesroles.sixty_seconds.island.building", onComplete));
    }

    /**
     * 在一级岛地表合适位置放一扇避难所门，并把门坐标写回 {@code island.shelterDoorX/Y/Z}
     * （供 {@link net.exmo.sre.sixtyseconds.island.SixtySecondsIslands} 在建造完成后登记门绑定/锚点）。
     * 找不到落脚点则不放门（{@code hasShelterDoor()} 保持 false）。
     */
    static void placeShelterDoor(Placer p, SixtySecondsIsland island) {
        RandomSource rng = RandomSource.create(island.seed ^ 0x5D00_0DL);
        BlockPos ground = null;
        for (int attempt = 0; attempt < 24 && ground == null; attempt++) {
            ground = randomGround(p.level, island, rng, 0.0, 0.45); // 靠岛心一带
        }
        if (ground == null) {
            ground = scanGround(p.level, island, island.centerX, island.centerZ);
        }
        if (ground == null) {
            return;
        }
        // 门朝向背向岛心（人从岛心侧看到门正面）；两格实心地面上放单格实心门方块。
        net.minecraft.core.Direction facing = outwardDir(island, ground);
        p.set(ground, org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_SHELTER_DOOR
                .defaultBlockState()
                .setValue(net.exmo.sre.sixtyseconds.content.block.ShelterDoorBlock.FACING, facing));
        island.shelterDoorX = ground.getX();
        island.shelterDoorY = ground.getY();
        island.shelterDoorZ = ground.getZ();
    }

    /** 从岛心指向该点的水平朝向（四向取最接近者）；用于门朝向背向岛心。 */
    private static net.minecraft.core.Direction outwardDir(SixtySecondsIsland island, BlockPos pos) {
        int dx = pos.getX() - island.centerX;
        int dz = pos.getZ() - island.centerZ;
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? net.minecraft.core.Direction.EAST : net.minecraft.core.Direction.WEST;
        }
        return dz >= 0 ? net.minecraft.core.Direction.SOUTH : net.minecraft.core.Direction.NORTH;
    }

    /** 把整个群岛的还原（清空+快照回写）排入任务队列。 */
    public static void queueRestore(ServerLevel level, List<SixtySecondsIsland> islands,
            LinkedHashMap<BlockPos, Snapshot> snapshots, Runnable onComplete) {
        List<Runnable> work = new ArrayList<>();
        for (SixtySecondsIsland island : islands) {
            int r = island.radius + WATER_SKIRT;
            for (int px = island.centerX - r; px <= island.centerX + r; px += PATCH) {
                for (int pz = island.centerZ - r; pz <= island.centerZ + r; pz += PATCH) {
                    int x0 = px;
                    int z0 = pz;
                    work.add(() -> restorePatch(level, island, snapshots, x0, z0,
                            Math.min(x0 + PATCH - 1, island.centerX + r),
                            Math.min(z0 + PATCH - 1, island.centerZ + r)));
                }
            }
        }
        work.add(() -> restoreSnapshots(level, snapshots));
        GameUtils.serverTaskQueue.add(new IslandTask(level, work,
                "message.noellesroles.sixty_seconds.island.restoring", onComplete));
    }

    /** 还原一个列 patch：把生成范围内所有方块清成空气（快照位除外，稍后统一回写）。 */
    private static void restorePatch(ServerLevel level, SixtySecondsIsland island,
            LinkedHashMap<BlockPos, Snapshot> snapshots, int x0, int z0, int x1, int z1) {
        BlockState air = Blocks.AIR.defaultBlockState();
        int yMin = island.seaY - DEPTH_BELOW_SEA;
        int yMax = island.seaY + HEIGHT_ABOVE_SEA;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = yMin; y <= yMax; y++) {
                    pos.set(x, y, z);
                    if (level.getBlockState(pos).isAir() || snapshots.containsKey(pos)) {
                        continue;
                    }
                    net.minecraft.world.Clearable.tryClear(level.getBlockEntity(pos));
                    level.setBlock(pos, air, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    level.getLightEngine().checkBlock(pos);
                }
            }
        }
    }

    /** 快照回写（还原收尾）：把生成前的非空气方块（含 BE NBT，坐标改回自身）原样放回。 */
    private static void restoreSnapshots(ServerLevel level, LinkedHashMap<BlockPos, Snapshot> snapshots) {
        for (var entry : snapshots.entrySet()) {
            BlockPos pos = entry.getKey();
            Snapshot snap = entry.getValue();
            level.setBlock(pos, snap.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            if (snap.blockEntityTag() != null) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be != null) {
                    CompoundTag tag = snap.blockEntityTag().copy();
                    tag.putInt("x", pos.getX());
                    tag.putInt("y", pos.getY());
                    tag.putInt("z", pos.getZ());
                    be.loadWithComponents(tag, level.registryAccess());
                }
            }
            level.getLightEngine().checkBlock(pos);
        }
        snapshots.clear();
    }

    // ── 地形 ────────────────────────────────────────────────────────────

    /** 岛屿等级+类型联合色板。type==null 回退纯 level 色板（旧存档兼容）。 */
    private record Palette(BlockState top, BlockState topAlt, BlockState under, BlockState core,
            BlockState beach, BlockState seabed) {
    }

    private static Palette palette(SixtySecondsIsland island) {
        if (island.type == null) {
            return legacyPalette(island.level);
        }
        return switch (island.type) {
            case TROPICAL -> new Palette(Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.GRASS_BLOCK.defaultBlockState(),
                    Blocks.DIRT.defaultBlockState(), Blocks.STONE.defaultBlockState(),
                    Blocks.SAND.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState());
            case MARSH -> new Palette(Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.PODZOL.defaultBlockState(),
                    Blocks.MUD.defaultBlockState(), Blocks.STONE.defaultBlockState(),
                    Blocks.MUD.defaultBlockState(), Blocks.MUD.defaultBlockState());
            case VOLCANIC -> new Palette(Blocks.BLACKSTONE.defaultBlockState(), Blocks.BASALT.defaultBlockState(),
                    Blocks.BASALT.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState(),
                    Blocks.BLACKSTONE.defaultBlockState(), Blocks.MAGMA_BLOCK.defaultBlockState());
            case CORAL -> new Palette(Blocks.SAND.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(),
                    Blocks.SANDSTONE.defaultBlockState(), Blocks.STONE.defaultBlockState(),
                    Blocks.SAND.defaultBlockState(), Blocks.PRISMARINE.defaultBlockState());
            case FROST -> new Palette(Blocks.SNOW_BLOCK.defaultBlockState(), Blocks.POWDER_SNOW.defaultBlockState(),
                    Blocks.PACKED_ICE.defaultBlockState(), Blocks.STONE.defaultBlockState(),
                    Blocks.SNOW_BLOCK.defaultBlockState(), Blocks.BLUE_ICE.defaultBlockState());
            case PLATEAU -> new Palette(Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.TERRACOTTA.defaultBlockState(),
                    Blocks.STONE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(),
                    Blocks.GRAVEL.defaultBlockState(), Blocks.STONE.defaultBlockState());
            case JUNGLE -> new Palette(Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.PODZOL.defaultBlockState(),
                    Blocks.DIRT.defaultBlockState(), Blocks.MOSSY_COBBLESTONE.defaultBlockState(),
                    Blocks.SAND.defaultBlockState(), Blocks.CLAY.defaultBlockState());
            case BARREN -> new Palette(Blocks.COARSE_DIRT.defaultBlockState(), Blocks.GRAVEL.defaultBlockState(),
                    Blocks.COARSE_DIRT.defaultBlockState(), Blocks.STONE.defaultBlockState(),
                    Blocks.GRAVEL.defaultBlockState(), Blocks.TUFF.defaultBlockState());
        };
    }

    /** 旧版纯等级色板（type==null 时使用，保持旧存档地形不变）。 */
    private static Palette legacyPalette(int level) {
        return switch (level) {
            case 1 -> new Palette(Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.GRASS_BLOCK.defaultBlockState(),
                    Blocks.DIRT.defaultBlockState(), Blocks.STONE.defaultBlockState(),
                    Blocks.SANDSTONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState());
            case 2 -> new Palette(Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.PODZOL.defaultBlockState(),
                    Blocks.DIRT.defaultBlockState(), Blocks.STONE.defaultBlockState(),
                    Blocks.SANDSTONE.defaultBlockState(), Blocks.TUFF.defaultBlockState());
            case 3 -> new Palette(Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.MUD.defaultBlockState(),
                    Blocks.DIRT.defaultBlockState(), Blocks.STONE.defaultBlockState(),
                    Blocks.TUFF.defaultBlockState(), Blocks.TUFF.defaultBlockState());
            case 4 -> new Palette(Blocks.COARSE_DIRT.defaultBlockState(), Blocks.SOUL_SOIL.defaultBlockState(),
                    Blocks.COARSE_DIRT.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(),
                    Blocks.TUFF.defaultBlockState(), Blocks.SOUL_SAND.defaultBlockState());
            default -> new Palette(Blocks.BLACKSTONE.defaultBlockState(), Blocks.BASALT.defaultBlockState(),
                    Blocks.BASALT.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState(),
                    Blocks.BASALT.defaultBlockState(), Blocks.MAGMA_BLOCK.defaultBlockState());
        };
    }

    /** 地形高度（陆地列）：等级越高山越高。类型影响地形风格：高原=平顶，沼泽=低平，火山=锥形。 */
    private static int surfaceY(SixtySecondsIsland island, int x, int z, float landVal) {
        float mountain = fbm(island.seed ^ 0x5DEECE66DL, x * 0.045, z * 0.045, 3);
        double normLand = Math.max(0, landVal - LAND_THRESHOLD);

        SixtySecondsIsland.Type type = island.type;
        if (type == null) {
            float h = (float) Math.pow(normLand, 1.15)
                    * (8 + island.level * 5 + mountain * (12 + island.level * 5));
            return island.seaY + (int) h;
        }

        float h = switch (type) {
            case PLATEAU -> {
                // 高原：平顶+陡崖——中心部分压缩高差，边缘急剧上升
                double centerDist = Math.sqrt(island.distSqr(x + 0.5, z + 0.5)) / island.radius;
                if (centerDist < 0.4) {
                    yield (float) (10 + island.level * 4 + mountain * 4); // 平顶，稳定的高原面
                }
                yield (float) (Math.pow(normLand, 0.7) * (6 + island.level * 3 + mountain * 8)); // 边缘陡升
            }
            case MARSH -> {
                // 沼泽：极低平，几乎不隆起
                yield (float) (normLand * (3 + island.level * 2 + mountain * 4));
            }
            case VOLCANIC -> {
                // 火山：锥形山体，中心急速拔高
                yield (float) (Math.pow(normLand, 2.0) * (10 + island.level * 7 + mountain * 16));
            }
            case CORAL -> {
                // 环礁：极低平（只是一圈沙/珊瑚礁盘露出水面）
                yield (float) (normLand * (2 + island.level + mountain * 3));
            }
            case BARREN -> {
                // 荒芜：低矮丘陵
                yield (float) (Math.pow(normLand, 1.0) * (5 + island.level * 3 + mountain * 8));
            }
            default -> {
                // 热带/冰霜/密林：标准地形
                yield (float) (Math.pow(normLand, 1.15)
                        * (8 + island.level * 5 + mountain * (12 + island.level * 5)));
            }
        };
        return island.seaY + (int) h;
    }

    /** 建一个 16×16 列 patch：净空 → 海床/海水/滩涂/陆地按列成形。 */
    private static void buildPatch(Placer p, SixtySecondsIsland island, int x0, int z0, int x1, int z1) {
        int rOuter = island.radius + WATER_SKIRT;
        Palette pal = palette(island);
        BlockState water = Blocks.WATER.defaultBlockState();
        int yMin = island.seaY - DEPTH_BELOW_SEA;
        int yMax = island.seaY + HEIGHT_ABOVE_SEA;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                double distSqr = island.distSqr(x + 0.5, z + 0.5);
                if (distSqr > (double) rOuter * rOuter) {
                    continue;
                }
                float landVal = landValue(island, x, z);
                boolean land = landVal > LAND_THRESHOLD;
                int surface = land ? surfaceY(island, x, z, landVal) : island.seaY - 2
                        - (int) (2 * fbm(island.seed ^ 77L, x * 0.08, z * 0.08, 2));
                // 净空：地表以上的原有方块全部清掉（含海面以上）
                for (int y = Math.max(surface + 1, yMin); y <= yMax; y++) {
                    pos.set(x, y, z);
                    if (!p.level.getBlockState(pos).isAir()) {
                        p.air(pos);
                    }
                }
                if (land) {
                    boolean beach = surface <= island.seaY;
                    float topNoise = fbm(island.seed ^ 31L, x * 0.11, z * 0.11, 2);
                    for (int y = yMin; y <= surface; y++) {
                        pos.set(x, y, z);
                        BlockState state;
                        if (y == surface) {
                            state = beach ? pal.beach() : (topNoise > 0.62F ? pal.topAlt() : pal.top());
                        } else if (y >= surface - 2) {
                            state = beach ? pal.beach() : pal.under();
                        } else {
                            state = pal.core();
                        }
                        p.set(pos, state);
                    }
                } else {
                    // 海：海床 + 水体到海平面；最外两圈收成浅滩环（拦住水体，防止向单元格外流散）
                    boolean rim = distSqr > (double) (rOuter - 2) * (rOuter - 2);
                    for (int y = yMin; y <= island.seaY; y++) {
                        pos.set(x, y, z);
                        p.set(pos, y <= surface || rim ? pal.seabed() : water);
                    }
                }
            }
        }
    }

    // ── 装饰：树木 / 岩石 / 植被 ───────────────────────────────────────────

    private static void decorate(Placer p, SixtySecondsIsland island) {
        RandomSource rng = RandomSource.create(island.seed ^ 0xDEC0L);
        float dm = island.size.decoMult;
        SixtySecondsIsland.Type type = island.type;

        // ── 树木：数量与品种因类型而异 ──
        int treeMult = treeCountMultiplier(type, island.level);
        int trees = Math.max(type == SixtySecondsIsland.Type.CORAL || type == SixtySecondsIsland.Type.BARREN ? 0 : 1,
                (int) (dm * treeMult));
        for (int i = 0; i < trees + rng.nextInt(Math.max(1, (int) (dm * 4))); i++) {
            BlockPos ground = randomGround(p.level, island, rng, 0.15, 0.85);
            if (ground == null) continue;
            placeTree(p, island, ground, rng);
        }

        // ── 岩石堆 ──
        int rocks = Math.max(1, (int) (dm * (4 + island.level)));
        BlockState rock = rockBlock(type);
        for (int i = 0; i < rocks; i++) {
            BlockPos ground = randomGround(p.level, island, rng, 0.1, 0.9);
            if (ground == null) continue;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (rng.nextFloat() < 0.6F) {
                        p.set(ground.offset(dx, 0, dz), rock);
                        if (rng.nextFloat() < 0.3F) {
                            p.set(ground.offset(dx, 1, dz), rock);
                        }
                    }
                }
            }
        }

        // ── 低植被 ──
        int flora = Math.max(type == SixtySecondsIsland.Type.BARREN ? 5 : 1,
                (int) (dm * floraCount(type, island.level)));
        for (int i = 0; i < flora; i++) {
            BlockPos ground = randomGround(p.level, island, rng, 0.1, 0.95);
            if (ground == null) continue;
            BlockState below = p.level.getBlockState(ground.below());
            BlockState plant = plantBlock(type, island.level, below, rng);
            if (plant != null) p.set(ground, plant);
        }

        // ── 类型特殊装饰 ──
        typeSpecial(p, island, rng);
    }

    /** 树木数量基数（类型×等级）。 */
    private static int treeCountMultiplier(SixtySecondsIsland.Type type, int level) {
        if (type == null) {
            return switch (level) { case 1 -> 6; case 2 -> 10; case 3 -> 9; case 4 -> 5; default -> 3; };
        }
        return switch (type) {
            case TROPICAL -> 8 + level;
            case MARSH -> 5;
            case VOLCANIC -> Math.max(1, 4 - level);
            case CORAL, BARREN -> 0;
            case FROST -> 4 + level;
            case PLATEAU -> 3;
            case JUNGLE -> 12 + level * 2;
        };
    }

    /** 岩石材质。 */
    private static BlockState rockBlock(SixtySecondsIsland.Type type) {
        if (type == null) return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        return switch (type) {
            case VOLCANIC -> Blocks.BLACKSTONE.defaultBlockState();
            case CORAL -> Blocks.PRISMARINE.defaultBlockState();
            case FROST -> Blocks.PACKED_ICE.defaultBlockState();
            case PLATEAU -> Blocks.ANDESITE.defaultBlockState();
            case BARREN -> Blocks.COBBLESTONE.defaultBlockState();
            default -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        };
    }

    /** 植被数量。 */
    private static int floraCount(SixtySecondsIsland.Type type, int level) {
        if (type == null) return level <= 3 ? 40 : 14;
        return switch (type) {
            case TROPICAL -> 50 + level * 5;
            case MARSH -> 30;
            case VOLCANIC -> 8 + level;
            case CORAL -> 0;
            case FROST -> 10;
            case PLATEAU -> 20;
            case JUNGLE -> 55 + level * 8;
            case BARREN -> 5;
        };
    }

    /** 低植被品种。 */
    private static BlockState plantBlock(SixtySecondsIsland.Type type, int level, BlockState below,
            RandomSource rng) {
        if (type == null) {
            // 旧版纯等级逻辑
            if (level <= 3 && (below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.DIRT))) {
                return rng.nextFloat() < 0.7F ? Blocks.SHORT_GRASS.defaultBlockState()
                        : Blocks.FERN.defaultBlockState();
            }
            if (below.is(Blocks.SANDSTONE) || below.is(Blocks.COARSE_DIRT) || below.is(Blocks.TUFF)) {
                return Blocks.DEAD_BUSH.defaultBlockState();
            }
            return null;
        }
        return switch (type) {
            case TROPICAL -> (below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.DIRT))
                    ? (rng.nextFloat() < 0.6F ? Blocks.FERN.defaultBlockState()
                            : Blocks.SHORT_GRASS.defaultBlockState())
                    : below.is(Blocks.SAND) ? Blocks.DEAD_BUSH.defaultBlockState() : null;
            case MARSH -> (below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.MUD) || below.is(Blocks.PODZOL))
                    ? (rng.nextFloat() < 0.5F ? Blocks.BROWN_MUSHROOM.defaultBlockState()
                            : Blocks.RED_MUSHROOM.defaultBlockState())
                    : null;
            case VOLCANIC -> (below.is(Blocks.BLACKSTONE) || below.is(Blocks.BASALT))
                    ? Blocks.DEAD_BUSH.defaultBlockState() : null;
            case FROST -> (below.is(Blocks.SNOW_BLOCK) || below.is(Blocks.PACKED_ICE))
                    ? (rng.nextFloat() < 0.6F ? Blocks.SHORT_GRASS.defaultBlockState() : null) : null;
            case PLATEAU -> (below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.TERRACOTTA))
                    ? (rng.nextFloat() < 0.5F ? Blocks.SHORT_GRASS.defaultBlockState()
                            : Blocks.DEAD_BUSH.defaultBlockState())
                    : null;
            case JUNGLE -> (below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.PODZOL) || below.is(Blocks.DIRT))
                    ? Blocks.FERN.defaultBlockState() : null;
            case BARREN -> below.is(Blocks.COARSE_DIRT) || below.is(Blocks.GRAVEL)
                    ? Blocks.DEAD_BUSH.defaultBlockState() : null;
            default -> null;
        };
    }

    /** 类型专属特殊装饰。 */
    private static void typeSpecial(Placer p, SixtySecondsIsland island, RandomSource rng) {
        SixtySecondsIsland.Type type = island.type;
        if (type == null) {
            // 旧版：level 5 火山岛岩浆块
            if (island.level >= 5) {
                float dm = island.size.decoMult;
                int lava = Math.max(1, (int) (dm * 8));
                for (int i = 0; i < lava; i++) {
                    BlockPos ground = randomGround(p.level, island, rng, 0.0, 0.4);
                    if (ground != null) p.set(ground.below(), Blocks.MAGMA_BLOCK.defaultBlockState());
                }
            }
            return;
        }
        float dm = island.size.decoMult;
        switch (type) {
            case VOLCANIC -> {
                // 山顶岩浆块 + 随机火焰
                int lava = Math.max(1, (int) (dm * 10));
                for (int i = 0; i < lava; i++) {
                    BlockPos ground = randomGround(p.level, island, rng, 0.0, 0.35);
                    if (ground != null) {
                        p.set(ground.below(), Blocks.MAGMA_BLOCK.defaultBlockState());
                        if (rng.nextFloat() < 0.15F) p.set(ground.above(), Blocks.FIRE.defaultBlockState());
                    }
                }
            }
            case MARSH -> {
                // 沼泽水面随机莲叶
                int pads = Math.max(1, (int) (dm * 6));
                for (int i = 0; i < pads; i++) {
                    BlockPos ground = randomGround(p.level, island, rng, 0.3, 0.9);
                    if (ground != null && ground.getY() <= island.seaY) {
                        p.set(ground, Blocks.LILY_PAD.defaultBlockState());
                    }
                }
            }
            case CORAL -> {
                // 环礁潟湖/礁盘上散布珊瑚
                int coral = Math.max(1, (int) (dm * 12));
                for (int i = 0; i < coral; i++) {
                    BlockPos ground = randomGround(p.level, island, rng, 0.1, 1.0);
                    if (ground != null && ground.getY() <= island.seaY + 2) {
                        BlockState c = rng.nextFloat() < 0.5F
                                ? Blocks.BRAIN_CORAL_BLOCK.defaultBlockState()
                                : rng.nextFloat() < 0.5F
                                        ? Blocks.TUBE_CORAL_BLOCK.defaultBlockState()
                                        : Blocks.HORN_CORAL_BLOCK.defaultBlockState();
                        p.set(ground, c);
                    }
                }
                // 海泡菜
                int pickles = Math.max(1, (int) (dm * 8));
                for (int i = 0; i < pickles; i++) {
                    BlockPos ground = randomGround(p.level, island, rng, 0.5, 1.0);
                    if (ground != null && ground.getY() <= island.seaY) {
                        p.set(ground, Blocks.SEA_PICKLE.defaultBlockState());
                    }
                }
            }
            case FROST -> {
                // 冰霜岛：额外积雪层
                int snow = Math.max(1, (int) (dm * 20));
                for (int i = 0; i < snow; i++) {
                    BlockPos ground = randomGround(p.level, island, rng, 0.0, 0.8);
                    if (ground != null && p.level.getBlockState(ground).isAir()
                            && p.level.getBlockState(ground.below()).isSolidRender(p.level, ground.below())) {
                        p.set(ground, Blocks.SNOW.defaultBlockState());
                    }
                }
            }
            case JUNGLE -> {
                // 密林岛：藤蔓挂满树干/悬崖
                int vines = Math.max(1, (int) (dm * 20));
                for (int i = 0; i < vines; i++) {
                    BlockPos ground = randomGround(p.level, island, rng, 0.0, 0.8);
                    if (ground != null) {
                        for (int dy = 1; dy <= 3; dy++) {
                            BlockPos vp = ground.above(dy);
                            if (p.level.getBlockState(vp).isAir() && rng.nextFloat() < 0.4F) {
                                p.set(vp, Blocks.VINE.defaultBlockState());
                            }
                        }
                    }
                }
                // 随机可可豆
                int cocoa = Math.max(1, (int) (dm * 4));
                for (int i = 0; i < cocoa; i++) {
                    BlockPos ground = randomGround(p.level, island, rng, 0.1, 0.7);
                    if (ground != null && p.level.getBlockState(ground).isAir()) {
                        p.set(ground, Blocks.COCOA.defaultBlockState());
                    }
                }
            }
            case PLATEAU -> {
                // 高原岛：山顶/崖边额外岩石堆
                int extraRocks = Math.max(1, (int) (dm * 5));
                for (int i = 0; i < extraRocks; i++) {
                    BlockPos ground = randomGround(p.level, island, rng, 0.0, 0.3);
                    if (ground != null) {
                        p.set(ground.below(), Blocks.COBBLESTONE.defaultBlockState());
                        if (rng.nextBoolean()) p.set(ground, Blocks.COBBLESTONE.defaultBlockState());
                    }
                }
            }
        }
    }

    private static void placeTree(Placer p, SixtySecondsIsland island, BlockPos ground, RandomSource rng) {
        SixtySecondsIsland.Type type = island.type;
        int level = island.level;
        boolean dead = type == SixtySecondsIsland.Type.VOLCANIC || type == SixtySecondsIsland.Type.BARREN
                || (type == null && level >= 4);

        BlockState log;
        BlockState leaves;
        int height = 4 + rng.nextInt(3);

        if (type == null) {
            // 旧版纯等级逻辑
            log = switch (level) {
                case 1 -> Blocks.OAK_LOG.defaultBlockState();
                case 2 -> Blocks.SPRUCE_LOG.defaultBlockState();
                case 3 -> Blocks.DARK_OAK_LOG.defaultBlockState();
                default -> Blocks.STRIPPED_OAK_LOG.defaultBlockState();
            };
            leaves = switch (level) {
                case 1 -> Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
                case 2 -> Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
                default -> Blocks.DARK_OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
            };
        } else {
            switch (type) {
                case TROPICAL -> {
                    log = Blocks.JUNGLE_LOG.defaultBlockState();
                    leaves = Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
                    height = 5 + rng.nextInt(5); // 热带树更高
                }
                case MARSH -> {
                    log = Blocks.OAK_LOG.defaultBlockState();
                    leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
                    height = 3 + rng.nextInt(3);
                }
                case VOLCANIC -> {
                    log = Blocks.BASALT.defaultBlockState();
                    leaves = Blocks.AIR.defaultBlockState();
                    dead = true;
                    height = 2 + rng.nextInt(2);
                }
                case FROST -> {
                    log = Blocks.SPRUCE_LOG.defaultBlockState();
                    leaves = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
                    height = 5 + rng.nextInt(4);
                }
                case PLATEAU -> {
                    log = Blocks.OAK_LOG.defaultBlockState();
                    leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
                    height = 3 + rng.nextInt(2);
                }
                case JUNGLE -> {
                    log = Blocks.JUNGLE_LOG.defaultBlockState();
                    leaves = Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
                    height = 6 + rng.nextInt(6); // 密林树最高
                }
                default -> {
                    log = Blocks.OAK_LOG.defaultBlockState();
                    leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
                }
            }
        }

        for (int y = 0; y < height; y++) {
            p.set(ground.above(y), log);
        }
        if (dead) {
            if (rng.nextBoolean()) {
                p.set(ground.above(height - 1).relative(
                        net.minecraft.core.Direction.Plane.HORIZONTAL.getRandomDirection(rng)), log);
            }
            return;
        }
        BlockPos top = ground.above(height - 1);
        int leafRadius = type == SixtySecondsIsland.Type.JUNGLE ? 3 : 2;
        for (int dx = -leafRadius; dx <= leafRadius; dx++) {
            for (int dz = -leafRadius; dz <= leafRadius; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    int man = Math.abs(dx) + Math.abs(dz) + dy;
                    if (man == 0 || man > 3 || rng.nextFloat() < 0.15F) continue;
                    BlockPos leafPos = top.offset(dx, dy, dz);
                    if (p.level.getBlockState(leafPos).isAir()) {
                        p.set(leafPos, leaves);
                    }
                }
            }
        }
        // 密林岛树顶额外藤蔓
        if (type == SixtySecondsIsland.Type.JUNGLE && rng.nextFloat() < 0.4F) {
            p.set(top.above(2), Blocks.VINE.defaultBlockState());
        }
    }

    // ── 物资箱 / 怪物 / 登岛点 ─────────────────────────────────────────────

    /** 物资箱抽类别池。 */
    private static final String[] BOX_CATEGORIES = {"food", "water", "medicine", "tool", "material", "weapon"};

    private static void populate(Placer p, SixtySecondsIsland island) {
        ServerLevel level = p.level();
        RandomSource rng = RandomSource.create(island.seed ^ 0xB0B0L);
        float sm = island.size.supplyMult;
        // 登岛点：向群岛原点一侧的滩头（找不到就用岛心地表）
        BlockPos dock = findDock(level, island);
        island.dockX = dock.getX();
        island.dockY = dock.getY();
        island.dockZ = dock.getZ();

        // 普通物资箱：数量随等级+大小；1 级小岛约 6~13 个，1 级大岛 13~26 个；3 级起部分上锁
        // 总数在+30%基础上再+20%，即 1.3×1.2=1.56 → 取整用 1.5
        int normal = Math.max(2, (int) (sm * (10 + island.level * 3) * 1.5)
                + rng.nextInt(Math.max(1, (int) (sm * 14 * 1.5))));
        for (int i = 0; i < normal; i++) {
            BlockPos spot = randomGround(level, island, rng, 0.05, 0.9);
            if (spot == null) {
                continue;
            }
            boolean asRandom = rng.nextFloat() < 0.5F;
            boolean locked = !asRandom && island.level >= 3 && rng.nextFloat() < 0.3F;
            placeSupplyBox(p, spot, asRandom
                    ? org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_LOW_TIER_RANDOM_SUPPLY_BOX
                    : (locked
                            ? org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_SUPPLY_BOX_LOCKED
                            : org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_SUPPLY_BOX),
                    BOX_CATEGORIES[rng.nextInt(BOX_CATEGORIES.length)]);
        }
        // 高级物资箱：等级-1 个（按大小缩放，+30%）；4 级起带高级锁（需钳子）；一半刷成随机
        int advanced = Math.max(0, (int) (sm * (island.level - 1) * 1.5));
        for (int i = 0; i < advanced; i++) {
            BlockPos spot = randomGround(level, island, rng, 0.0, 0.6);
            if (spot == null) {
                continue;
            }
            boolean asRandom = rng.nextFloat() < 0.5F;
            boolean advancedLocked = !asRandom && island.level >= 4;
            placeSupplyBox(p, spot, asRandom
                    ? org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_HIGH_TIER_RANDOM_SUPPLY_BOX
                    : (advancedLocked
                            ? org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_SUPPLY_BOX_ADVANCED_LOCKED
                            : org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_SUPPLY_BOX_ADVANCED),
                    BOX_CATEGORIES[rng.nextInt(BOX_CATEGORIES.length)]);
        }
        // 初始驻岛怪：数量/强度随等级+大小（后续增援由 PveSystem 游荡怪按 levelAt 自动缩放）
        int monsters = Math.max(1, (int) (sm * island.level * 2));
        for (int i = 0; i < monsters; i++) {
            BlockPos spot = randomGround(level, island, rng, 0.1, 0.8);
            if (spot == null) {
                continue;
            }
            SixtySecondsPveSystem.createMonster(level, spot, rollVariant(rng, island.level),
                    1.0 + 0.15 * (island.level - 1), 1.0);
        }
    }

    /** 登岛怪/驻岛怪变体（等级越高精英占比越大）。 */
    public static SixtySecondsMonsterEntity.Variant rollVariant(RandomSource rng, int level) {
        float danger = level / 5.0F;
        float r = rng.nextFloat();
        if (r < 0.08F + danger * 0.2F) {
            return SixtySecondsMonsterEntity.Variant.BRUTE;
        }
        if (r < 0.3F + danger * 0.3F) {
            return rng.nextBoolean() ? SixtySecondsMonsterEntity.Variant.RUNNER
                    : SixtySecondsMonsterEntity.Variant.SPITTER;
        }
        return SixtySecondsMonsterEntity.Variant.SHAMBLER;
    }

    /** 放一个物资箱并设类别。 */
    static void placeSupplyBox(Placer p, BlockPos pos, Block block, String category) {
        p.set(pos, block.defaultBlockState());
        if (p.level.getBlockEntity(pos)
                instanceof net.exmo.sre.sixtyseconds.content.block_entity.SupplyBoxBlockEntity box) {
            box.category = category;
            box.setChanged();
        }
    }

    /** 找登岛滩头：从岛心向群岛外围方向走到岸线附近的可站立点。 */
    private static BlockPos findDock(ServerLevel level, SixtySecondsIsland island) {
        RandomSource rng = RandomSource.create(island.seed ^ 0xD0C4L);
        for (int attempt = 0; attempt < 40; attempt++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double dist = island.radius * (0.55 + rng.nextDouble() * 0.35);
            int x = island.centerX + (int) Math.round(Math.cos(angle) * dist);
            int z = island.centerZ + (int) Math.round(Math.sin(angle) * dist);
            BlockPos ground = scanGround(level, island, x, z);
            if (ground != null && ground.getY() <= island.seaY + 4) {
                return ground;
            }
        }
        BlockPos center = scanGround(level, island, island.centerX, island.centerZ);
        return center != null ? center : new BlockPos(island.centerX, island.seaY, island.centerZ);
    }

    /**
     * 岛上随机找一个可放置的地表空气格（其下为实心陆地、非水）；distMin/Max 为相对半径比例。
     * 供装饰/废墟/物资箱/怪物选点共用。
     */
    public static BlockPos randomGround(ServerLevel level, SixtySecondsIsland island, RandomSource rng,
            double distMin, double distMax) {
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double dist = island.radius * (distMin + rng.nextDouble() * (distMax - distMin));
            int x = island.centerX + (int) Math.round(Math.cos(angle) * dist);
            int z = island.centerZ + (int) Math.round(Math.sin(angle) * dist);
            BlockPos ground = scanGround(level, island, x, z);
            if (ground != null) {
                return ground;
            }
        }
        return null;
    }

    /** 自上而下扫该列的地表：返回「地面上的第一格空气」；水面/无地面返回 null。 */
    public static BlockPos scanGround(ServerLevel level, SixtySecondsIsland island, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = island.seaY + HEIGHT_ABOVE_SEA - 2; y > island.seaY - 2; y--) {
            pos.set(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (!state.getFluidState().isEmpty()) {
                return null; // 水面
            }
            return state.isSolidRender(level, pos) && level.getBlockState(pos.above()).isAir()
                    ? pos.above().immutable() : null;
        }
        return null;
    }

    /** 跨 tick 分批执行工作项的任务（仿 {@code SixtySecondsArena.BuildTask}）。 */
    private static final class IslandTask extends ServerTaskInfoClasses.ServerTaskInfo {
        private final ServerLevel level;
        private final List<Runnable> work;
        private final String progressKey;
        private final Runnable onComplete;
        private int index = 0;
        private int tickCounter = 0;

        private IslandTask(ServerLevel level, List<Runnable> work, String progressKey, Runnable onComplete) {
            this.level = level;
            this.work = work;
            this.progressKey = progressKey;
            this.onComplete = onComplete;
        }

        @Override
        public boolean onTick(MinecraftServer server) {
            int done = 0;
            while (index < work.size() && done < MAX_ITEMS_PER_TICK) {
                work.get(index).run();
                index++;
                done++;
            }
            if (index < work.size() && (++tickCounter % 10) == 0) {
                int percent = (int) (100.0 * index / Math.max(1, work.size()));
                Component msg = Component.translatable(progressKey, percent).withStyle(ChatFormatting.YELLOW);
                for (ServerPlayer player : level.players()) {
                    player.displayClientMessage(msg, true);
                }
            }
            return index >= work.size();
        }

        @Override
        public void onFinished() {
            Component done = Component.translatable(progressKey, 100).withStyle(ChatFormatting.YELLOW);
            for (ServerPlayer player : level.players()) {
                player.displayClientMessage(done, true);
            }
            Noellesroles.LOGGER.info("[60s] 海岛任务完成：{} 个工作项。", work.size());
            onComplete.run();
        }
    }
}
