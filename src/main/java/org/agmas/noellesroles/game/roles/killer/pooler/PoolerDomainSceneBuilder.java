/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.game.roles.killer.pooler;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 祈月者两个领域的场景构建器。
 * <p>
 * 两个领域都建在高空虚空里（坐标见 {@link PoolerDomainManager}），互不重叠，
 * 也不与愚者塔罗会、咒术师灰髓之境、冒险家游记三处场景重叠：
 * <ul>
 * <li>{@link #buildMoon(BlockPos)} —— 苍白的月球表面：白色/浅灰色岩层、陨石坑、环形边墙。</li>
 * <li>{@link #buildAquarium(BlockPos)} —— 半透明的大水缸：玻璃外壳 + 内外两层水、
 * 水面以上一圈空气，缸底沙石与海草。</li>
 * </ul>
 * 与咒术师 {@code WarlockDomainSceneBuilder} 一致：全部用 {@code level.setBlock} 现搭，
 * 不使用结构文件，领域结束后不需要还原（地点本身就在虚空）。
 */
final class PoolerDomainSceneBuilder {

    /** 月球领域半径。 */
    static final int MOON_RADIUS = 18;
    /** 月球领域边墙高度。 */
    static final int MOON_WALL_HEIGHT = 7;

    /** 水缸领域半径（内部可活动范围）。 */
    static final int TANK_RADIUS = 14;
    /** 水缸外壳高度。 */
    static final int TANK_HEIGHT = 14;
    /** 水面所在的相对高度：此高度以上是空气，以下是水。 */
    static final int TANK_WATER_LEVEL = 10;

    private final ServerLevel level;

    PoolerDomainSceneBuilder(ServerLevel level) {
        this.level = level;
    }

    // ── 月球领域 ────────────────────────────────────────────────

    void buildMoon(BlockPos center) {
        clear(center, MOON_RADIUS + 2, MOON_WALL_HEIGHT + 6);
        for (int x = -MOON_RADIUS; x <= MOON_RADIUS; x++) {
            for (int z = -MOON_RADIUS; z <= MOON_RADIUS; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > MOON_RADIUS + 0.5D)
                    continue;
                setBlock(center.offset(x, -1, z), moonSurface(x, z, dist));
                // 两层地基，防止从边缘往下看到虚空
                setBlock(center.offset(x, -2, z), Blocks.CALCITE.defaultBlockState());
                setBlock(center.offset(x, -3, z), Blocks.SMOOTH_BASALT.defaultBlockState());
            }
        }
        buildMoonCraters(center);
        buildMoonRim(center);
        buildMoonPillars(center);
    }

    /** 月球表面：以白色系为主，陨石坑用深色填充。 */
    private BlockState moonSurface(int x, int z, double dist) {
        if (dist > MOON_RADIUS - 1.2D) {
            return Blocks.POLISHED_DIORITE.defaultBlockState();
        }
        if (isCrater(x, z)) {
            return Blocks.SMOOTH_BASALT.defaultBlockState();
        }
        int noise = Math.abs(x * 7 + z * 13) % 5;
        return switch (noise) {
            case 0 -> Blocks.WHITE_CONCRETE.defaultBlockState();
            case 1 -> Blocks.CALCITE.defaultBlockState();
            case 2 -> Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
            case 3 -> Blocks.DIORITE.defaultBlockState();
            default -> Blocks.WHITE_CONCRETE_POWDER.defaultBlockState();
        };
    }

    /** 若干圆形陨石坑（只用纯数学判定，保证每次生成的形状一致）。 */
    private static boolean isCrater(int x, int z) {
        int[][] craters = { { 0, 0, 4 }, { -9, -6, 3 }, { 8, -8, 4 }, { -6, 9, 3 }, { 10, 7, 3 } };
        for (int[] crater : craters) {
            int dx = x - crater[0];
            int dz = z - crater[1];
            int r = crater[2];
            int rr = dx * dx + dz * dz;
            if (rr <= r * r && rr > (r - 1) * (r - 1)) {
                return true;
            }
        }
        return false;
    }

    private void buildMoonCraters(BlockPos center) {
        int[][] craters = { { 0, 0, 4 }, { -9, -6, 3 }, { 8, -8, 4 }, { -6, 9, 3 }, { 10, 7, 3 } };
        for (int[] crater : craters) {
            setBlock(center.offset(crater[0], -1, crater[1]), Blocks.BASALT.defaultBlockState());
            setBlock(center.offset(crater[0], 0, crater[1]), Blocks.END_STONE.defaultBlockState());
        }
    }

    /** 环形边墙：透明玻璃 + 顶上一圈海晶灯（月光观感），防止掉进虚空。 */
    private void buildMoonRim(BlockPos center) {
        for (int x = -MOON_RADIUS - 1; x <= MOON_RADIUS + 1; x++) {
            for (int z = -MOON_RADIUS - 1; z <= MOON_RADIUS + 1; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist < MOON_RADIUS - 0.2D || dist > MOON_RADIUS + 1.3D)
                    continue;
                for (int y = 0; y < MOON_WALL_HEIGHT; y++) {
                    setBlock(center.offset(x, y, z), Blocks.WHITE_STAINED_GLASS.defaultBlockState());
                }
                setBlock(center.offset(x, MOON_WALL_HEIGHT, z), Blocks.SEA_LANTERN.defaultBlockState());
            }
        }
    }

    /** 四根月白石柱 + 顶灯的「月台」标记。 */
    private void buildMoonPillars(BlockPos center) {
        int[][] offsets = { { -11, -11 }, { 11, -11 }, { -11, 11 }, { 11, 11 } };
        for (int[] offset : offsets) {
            for (int y = 0; y < 5; y++) {
                setBlock(center.offset(offset[0], y, offset[1]), Blocks.QUARTZ_PILLAR.defaultBlockState());
            }
            setBlock(center.offset(offset[0], 5, offset[1]), Blocks.SEA_LANTERN.defaultBlockState());
        }
        // 中心：一轮「月」的符号（白色地毯十字）
        setBlock(center.offset(0, 0, 0), Blocks.LIGHT_BLUE_CARPET.defaultBlockState());
        setBlock(center.offset(1, 0, 0), Blocks.WHITE_CARPET.defaultBlockState());
        setBlock(center.offset(-1, 0, 0), Blocks.WHITE_CARPET.defaultBlockState());
        setBlock(center.offset(0, 0, 1), Blocks.WHITE_CARPET.defaultBlockState());
        setBlock(center.offset(0, 0, -1), Blocks.WHITE_CARPET.defaultBlockState());
    }

    // ── 水中镜领域（大水缸）────────────────────────────────────

    void buildAquarium(BlockPos center) {
        clear(center, TANK_RADIUS + 2, TANK_HEIGHT + 6);
        int outer = TANK_RADIUS + 1;
        for (int x = -outer; x <= outer; x++) {
            for (int z = -outer; z <= outer; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > outer + 0.5D)
                    continue;

                // 缸底
                BlockState floor;
                if (dist > TANK_RADIUS - 1.0D) {
                    floor = Blocks.PRISMARINE_BRICKS.defaultBlockState();
                } else if ((x + z) % 3 == 0) {
                    floor = Blocks.SAND.defaultBlockState();
                } else if ((x * 5 + z * 3) % 7 == 0) {
                    floor = Blocks.GRAVEL.defaultBlockState();
                } else {
                    floor = Blocks.TUBE_CORAL_BLOCK.defaultBlockState();
                }
                setBlock(center.offset(x, -1, z), floor);
                setBlock(center.offset(x, -2, z), Blocks.PRISMARINE.defaultBlockState());

                boolean shell = dist > TANK_RADIUS - 0.2D;
                for (int y = 0; y < TANK_HEIGHT; y++) {
                    if (shell) {
                        // 外壳：玻璃；最上一圈用海晶灯做「缸口」
                        setBlock(center.offset(x, y, z),
                                y == TANK_HEIGHT - 1 ? Blocks.SEA_LANTERN.defaultBlockState()
                                        : Blocks.GLASS.defaultBlockState());
                    } else if (y < TANK_WATER_LEVEL) {
                        setBlock(center.offset(x, y, z), Blocks.WATER.defaultBlockState());
                    }
                }
                // 缸顶封口，避免玩家跳出去
                setBlock(center.offset(x, TANK_HEIGHT, z), Blocks.GLASS.defaultBlockState());
            }
        }
        buildAquariumPlants(center);
    }

    /** 珊瑚、海草与气泡柱装饰。 */
    private void buildAquariumPlants(BlockPos center) {
        int[][] spots = { { -7, -3 }, { 6, 4 }, { -4, 7 }, { 8, -6 }, { 0, 9 }, { -9, 2 } };
        for (int[] spot : spots) {
            int height = 2 + Math.abs(spot[0] + spot[1]) % 3;
            for (int y = 0; y < height; y++) {
                setBlock(center.offset(spot[0], y, spot[1]), Blocks.KELP_PLANT.defaultBlockState());
            }
            setBlock(center.offset(spot[0], 0, spot[1] + 1),
                    (Math.abs(spot[0]) % 2 == 0 ? Blocks.TUBE_CORAL : Blocks.BRAIN_CORAL)
                            .defaultBlockState());
        }
        // 中央气泡柱（视觉焦点，也是玩家传送后的落点）
        for (int y = 0; y < TANK_WATER_LEVEL; y++) {
            setBlock(center.offset(0, y, 0), Blocks.BUBBLE_COLUMN.defaultBlockState());
        }
        setBlock(center.offset(0, -1, 0), Blocks.SOUL_SAND.defaultBlockState());
    }

    // ── 工具方法 ────────────────────────────────────────────────

    private void clear(BlockPos center, int radius, int height) {
        fill(center.offset(-radius, -3, -radius),
                center.offset(radius, height, radius),
                Blocks.AIR.defaultBlockState());
    }

    private void fill(BlockPos from, BlockPos to, BlockState state) {
        int minX = Math.min(from.getX(), to.getX());
        int maxX = Math.max(from.getX(), to.getX());
        int minY = Math.min(from.getY(), to.getY());
        int maxY = Math.max(from.getY(), to.getY());
        int minZ = Math.min(from.getZ(), to.getZ());
        int maxZ = Math.max(from.getZ(), to.getZ());
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_ALL);
                }
            }
        }
    }

    private void setBlock(BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }
}
