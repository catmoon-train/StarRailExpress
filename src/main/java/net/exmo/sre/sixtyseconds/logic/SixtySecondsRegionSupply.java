package net.exmo.sre.sixtyseconds.logic;

import net.exmo.sre.sixtyseconds.content.block.RandomSupplyBoxBlock;
import net.exmo.sre.sixtyseconds.content.block_entity.RandomSupplyBoxBlockEntity;
import net.exmo.sre.sixtyseconds.content.block_entity.SupplyBoxBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModBlocks;

/**
 * 区域自动撒物资箱：管理员用 {@code /sre:60s_area region add/here} 登记星级区域时，若
 * {@code regionAutoSupplyEnabled} 开着，就在区域盒内地表随机撒三种随机箱——
 * <b>低级随机箱 / 上锁高级箱 / 高级随机箱</b>，数量按区域等级缩放（基准数量可配，见
 * {@link net.exmo.sre.sixtyseconds.config.SixtySecondsConfig#regionSupplyBoxBaseCount}）。
 * 免去手动逐个摆箱。箱子战利品仍由 {@code SupplyBoxBlockEntity.claim} 按 {@code SixtySecondsAreaLevels.levelAt}
 * （即本区域的等级）自动缩放。
 */
public final class SixtySecondsRegionSupply {

    /** 高级/上锁箱的抽类别池（普通/高级箱设 category 用；随机箱自带类别，不用它）。 */
    private static final String[] LOCKED_CATEGORIES = {"food", "water", "medicine", "tool", "material", "weapon"};

    private SixtySecondsRegionSupply() {
    }

    /** 区域应撒的箱子总数：{@code base + (level-1) * max(1, base/2)}。 */
    public static int boxCountFor(int level, int base) {
        return Math.max(0, base + (level - 1) * Math.max(1, base / 2));
    }

    /**
     * 在 [min,max] 盒内地表随机撒箱子。等级越高：高级随机箱占比越大、3 级起出现上锁高级箱。
     * @return 实际放下的箱子数
     */
    public static int spawn(ServerLevel level, BlockPos min, BlockPos max, int areaLevel, int baseCount) {
        int minX = Math.min(min.getX(), max.getX());
        int maxX = Math.max(min.getX(), max.getX());
        int minY = Math.min(min.getY(), max.getY());
        int maxY = Math.max(min.getY(), max.getY());
        int minZ = Math.min(min.getZ(), max.getZ());
        int maxZ = Math.max(min.getZ(), max.getZ());
        RandomSource rng = level.getRandom();
        int want = boxCountFor(areaLevel, baseCount);
        int placed = 0;
        // 尝试次数放宽到目标的 4 倍：地表点可能落在水/无地面处而跳过
        for (int attempt = 0; attempt < want * 4 && placed < want; attempt++) {
            int x = minX + rng.nextInt(maxX - minX + 1);
            int z = minZ + rng.nextInt(maxZ - minZ + 1);
            level.getChunk(x >> 4, z >> 4); // 强载，扫地表需要区块已加载
            // ~50% 的概率优先尝试室内，找不到再回退到室外地表
            Integer y;
            if (rng.nextFloat() < 0.5f) {
                y = scanInterior(level, x, z, maxY, minY);
                if (y == null) {
                    y = scanGround(level, x, z, maxY, minY);
                }
            } else {
                y = scanGround(level, x, z, maxY, minY);
            }
            if (y == null) {
                continue;
            }
            BlockPos pos = new BlockPos(x, y, z);
            if (placeBox(level, pos, areaLevel, rng)) {
                placed++;
            }
        }
        Noellesroles.LOGGER.info("[60s] 区域自动撒箱：等级 {}，目标 {}，实放 {} 个。", areaLevel, want, placed);
        return placed;
    }

    /** 在 pos 放一个箱子：按等级掷种类（低级随机 / 上锁高级 / 高级随机）。 */
    private static boolean placeBox(ServerLevel level, BlockPos pos, int areaLevel, RandomSource rng) {
        float r = rng.nextFloat();
        Block block;
        if (r < 0.15f * areaLevel) {
            block = ModBlocks.SIXTY_SECONDS_HIGH_TIER_RANDOM_SUPPLY_BOX;         // 高级随机箱
        } else if (areaLevel >= 3 && r < 0.15f * areaLevel + 0.15f) {
            block = ModBlocks.SIXTY_SECONDS_SUPPLY_BOX_ADVANCED_LOCKED;          // 上锁高级箱
        } else {
            block = ModBlocks.SIXTY_SECONDS_LOW_TIER_RANDOM_SUPPLY_BOX;          // 低级随机箱
        }
        level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof RandomSupplyBoxBlockEntity rb && block instanceof RandomSupplyBoxBlock rbb) {
            rb.initTierIfNeeded(rbb.tier());
        } else if (be instanceof SupplyBoxBlockEntity sb) {
            sb.category = LOCKED_CATEGORIES[rng.nextInt(LOCKED_CATEGORIES.length)];
            sb.setChanged();
        }
        return true;
    }

    /** 自上而下在 [bottom,top] 找「实心块且其上为空气」的落脚点，返回该空气格 y；找不到返回 null。 */
    private static Integer scanGround(ServerLevel level, int x, int z, int top, int bottom) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = top; y >= bottom; y--) {
            p.set(x, y, z);
            boolean solid = !level.getBlockState(p).isAir()
                    && level.getBlockState(p).getFluidState().isEmpty();
            if (solid && level.getBlockState(p.set(x, y + 1, z)).isAir()) {
                return y + 1;
            }
        }
        return null;
    }

    /**
     * 自下而上在 [bottom,top] 找室内落脚点，要求同时满足三个条件：
     * <ol>
     *   <li>脚下（y-1）是实心块（地板）</li>
     *   <li>当前位置（y）是空气（可放置箱子）</li>
     *   <li>头顶 2~10 格内有实心块（天花板），适配低矮棚屋到高大厅堂等各种建筑</li>
     * </ol>
     * 返回该空气格 y；找不到返回 null。
     */
    private static Integer scanInterior(ServerLevel level, int x, int z, int top, int bottom) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = bottom; y < top; y++) {
            // 地板：脚下必须是实心块
            if (y - 1 < bottom) continue;
            p.set(x, y - 1, z);
            boolean floorBelow = !level.getBlockState(p).isAir()
                    && level.getBlockState(p).getFluidState().isEmpty();
            if (!floorBelow) continue;

            // 站位：当前位置必须是空气（才能放箱子）
            p.set(x, y, z);
            if (!level.getBlockState(p).isAir()) continue;

            // 天花板：头顶 2~10 格内必须有实心块（覆盖低矮棚屋到高大厅堂）
            int ceilingMax = Math.min(y + 10, top);
            for (int cy = y + 2; cy <= ceilingMax; cy++) {
                p.set(x, cy, z);
                if (!level.getBlockState(p).isAir()
                        && level.getBlockState(p).getFluidState().isEmpty()) {
                    return y;
                }
            }
        }
        return null;
    }
}
