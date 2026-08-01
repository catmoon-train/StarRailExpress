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

package org.agmas.noellesroles.game.roles.killer.warlock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 「灰髓之境」场景构建器：高空虚空中的一座灰白祭场。
 *
 * 圆形凝灰岩祭场，骨白色纹路自中心放射；外缘一圈灰色玻璃「雾墙」防止坠落，
 * 头顶铁链自黑暗中垂下灵魂灯笼，中央是黑石祭坛。整体只用灰白冷色 —— 与
 * 塔罗会的暖色大厅形成对照。
 */
final class WarlockDomainSceneBuilder {

    /** 祭场半径。 */
    static final int RADIUS = 14;
    /** 雾墙高度。 */
    static final int WALL_HEIGHT = 8;

    private final ServerLevel level;

    WarlockDomainSceneBuilder(ServerLevel level) {
        this.level = level;
    }

    void build(BlockPos center) {
        clear(center);
        buildFloor(center);
        buildMistWall(center);
        buildAltar(center);
        buildHangingChains(center);
    }

    private void clear(BlockPos center) {
        fill(center.offset(-RADIUS - 2, -3, -RADIUS - 2),
                center.offset(RADIUS + 2, WALL_HEIGHT + 6, RADIUS + 2),
                Blocks.AIR.defaultBlockState());
    }

    private void buildFloor(BlockPos center) {
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist > RADIUS + 0.5D)
                    continue;
                BlockPos pos = center.offset(x, -1, z);
                BlockState state;
                if (dist > RADIUS - 1.2D) {
                    state = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
                } else if ((x == 0 || z == 0 || Math.abs(x) == Math.abs(z)) && dist > 2.5D) {
                    // 自中心放射的骨白纹路
                    state = Blocks.BONE_BLOCK.defaultBlockState();
                } else if ((x + z) % 2 == 0) {
                    state = Blocks.TUFF.defaultBlockState();
                } else {
                    state = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
                }
                setBlock(pos, state);
                setBlock(center.offset(x, -2, z), Blocks.DEEPSLATE.defaultBlockState());
            }
        }
    }

    private void buildMistWall(BlockPos center) {
        for (int x = -RADIUS - 1; x <= RADIUS + 1; x++) {
            for (int z = -RADIUS - 1; z <= RADIUS + 1; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist < RADIUS - 0.2D || dist > RADIUS + 1.3D)
                    continue;
                for (int y = 0; y < WALL_HEIGHT; y++) {
                    // 越往上越稀疏，营造雾气升腾感
                    if (y >= 3 && (x * 31 + z * 17 + y * 7) % (y) != 0)
                        continue;
                    setBlock(center.offset(x, y, z),
                            y < 2 ? Blocks.GRAY_STAINED_GLASS.defaultBlockState()
                                    : Blocks.LIGHT_GRAY_STAINED_GLASS.defaultBlockState());
                }
            }
        }
    }

    private void buildAltar(BlockPos center) {
        fill(center.offset(-2, -1, -2), center.offset(2, -1, 2),
                Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        setBlock(center.offset(-2, 0, -2), Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
        setBlock(center.offset(2, 0, -2), Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
        setBlock(center.offset(-2, 0, 2), Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
        setBlock(center.offset(2, 0, 2), Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
        setBlock(center.offset(-2, 1, -2), Blocks.SOUL_LANTERN.defaultBlockState());
        setBlock(center.offset(2, 1, -2), Blocks.SOUL_LANTERN.defaultBlockState());
        setBlock(center.offset(-2, 1, 2), Blocks.SOUL_LANTERN.defaultBlockState());
        setBlock(center.offset(2, 1, 2), Blocks.SOUL_LANTERN.defaultBlockState());
    }

    private void buildHangingChains(BlockPos center) {
        int anchorY = WALL_HEIGHT + 5;
        int[][] offsets = { { 0, 0 }, { -7, -7 }, { 7, -7 }, { -7, 7 }, { 7, 7 }, { 0, -10 }, { 0, 10 },
                { -10, 0 }, { 10, 0 } };
        for (int i = 0; i < offsets.length; i++) {
            int chainLength = 3 + (i * 2) % 4;
            BlockPos top = center.offset(offsets[i][0], anchorY, offsets[i][1]);
            for (int y = 0; y < chainLength; y++) {
                setBlock(top.below(y), Blocks.CHAIN.defaultBlockState());
            }
            setBlock(top.below(chainLength), Blocks.SOUL_LANTERN.defaultBlockState());
        }
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
