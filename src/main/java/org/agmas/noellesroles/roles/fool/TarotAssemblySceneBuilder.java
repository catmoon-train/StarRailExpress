package org.agmas.noellesroles.roles.fool;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class TarotAssemblySceneBuilder {
    private static final int HALL_HALF_WIDTH = 18;
    private static final int HALL_HALF_LENGTH = 28;
    private static final int ROOM_HEIGHT = 11;
    private static final int COLUMN_HEIGHT = 7;

    private final ServerLevel level;

    TarotAssemblySceneBuilder(ServerLevel level) {
        this.level = level;
    }

    void build(BlockPos center) {
        clear(center);
        buildFoundation(center);
        buildFogSea(center);
        buildFloor(center);
        buildColonnade(center);
        buildRoof(center);
        buildThroneDais(center);
        buildBronzeTable(center);
        buildLanterns(center);
    }

    private void clear(BlockPos center) {
        fill(center.offset(-HALL_HALF_WIDTH - 4, -3, -HALL_HALF_LENGTH - 4),
            center.offset(HALL_HALF_WIDTH + 4, ROOM_HEIGHT + 3, HALL_HALF_LENGTH + 4),
                Blocks.AIR.defaultBlockState());
    }

        private void buildFoundation(BlockPos center) {
        fill(center.offset(-HALL_HALF_WIDTH - 3, -2, -HALL_HALF_LENGTH - 3),
            center.offset(HALL_HALF_WIDTH + 3, -2, HALL_HALF_LENGTH + 3),
            Blocks.POLISHED_ANDESITE.defaultBlockState());
        fill(center.offset(-HALL_HALF_WIDTH - 2, -1, -HALL_HALF_LENGTH - 2),
            center.offset(HALL_HALF_WIDTH + 2, -1, HALL_HALF_LENGTH + 2),
            Blocks.SMOOTH_STONE.defaultBlockState());
        fill(center.offset(-HALL_HALF_WIDTH, -1, -HALL_HALF_LENGTH),
            center.offset(HALL_HALF_WIDTH, -1, HALL_HALF_LENGTH),
            Blocks.POLISHED_DIORITE.defaultBlockState());
        }

        private void buildFogSea(BlockPos center) {
        fill(center.offset(-HALL_HALF_WIDTH - 2, 0, -HALL_HALF_LENGTH - 2),
            center.offset(HALL_HALF_WIDTH + 2, 0, HALL_HALF_LENGTH + 2),
            Blocks.WHITE_STAINED_GLASS.defaultBlockState());
        fill(center.offset(-HALL_HALF_WIDTH - 1, 1, -HALL_HALF_LENGTH - 1),
            center.offset(HALL_HALF_WIDTH + 1, 1, HALL_HALF_LENGTH + 1),
            Blocks.WHITE_CARPET.defaultBlockState());
        }

    private void buildFloor(BlockPos center) {
        fill(center.offset(-HALL_HALF_WIDTH, 0, -HALL_HALF_LENGTH),
            center.offset(HALL_HALF_WIDTH, 0, HALL_HALF_LENGTH),
            Blocks.CALCITE.defaultBlockState());

        fill(center.offset(-HALL_HALF_WIDTH, 0, -HALL_HALF_LENGTH),
            center.offset(HALL_HALF_WIDTH, 0, -HALL_HALF_LENGTH),
            Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
        fill(center.offset(-HALL_HALF_WIDTH, 0, HALL_HALF_LENGTH),
            center.offset(HALL_HALF_WIDTH, 0, HALL_HALF_LENGTH),
            Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
        fill(center.offset(-HALL_HALF_WIDTH, 0, -HALL_HALF_LENGTH),
            center.offset(-HALL_HALF_WIDTH, 0, HALL_HALF_LENGTH),
            Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
        fill(center.offset(HALL_HALF_WIDTH, 0, -HALL_HALF_LENGTH),
            center.offset(HALL_HALF_WIDTH, 0, HALL_HALF_LENGTH),
            Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());

        fill(center.offset(-5, 1, -HALL_HALF_LENGTH + 4),
            center.offset(5, 1, HALL_HALF_LENGTH - 4),
            Blocks.LIGHT_GRAY_CARPET.defaultBlockState());
        fill(center.offset(-2, 1, -HALL_HALF_LENGTH + 4),
            center.offset(2, 1, HALL_HALF_LENGTH - 4),
            Blocks.WHITE_CARPET.defaultBlockState());

        fill(center.offset(-14, 1, -12), center.offset(-8, 1, 12), Blocks.GRAY_CARPET.defaultBlockState());
        fill(center.offset(8, 1, -12), center.offset(14, 1, 12), Blocks.GRAY_CARPET.defaultBlockState());
    }

        private void buildColonnade(BlockPos center) {
        int sideX = HALL_HALF_WIDTH - 2;
        int sideZ = HALL_HALF_LENGTH - 2;

        for (int z = -sideZ; z <= sideZ; z += 8) {
            placeColumn(center.offset(-sideX, 0, z));
            placeColumn(center.offset(sideX, 0, z));
        }
        for (int x = -sideX + 6; x <= sideX - 6; x += 8) {
            placeColumn(center.offset(x, 0, -sideZ));
            placeColumn(center.offset(x, 0, sideZ));
        }

        fill(center.offset(-sideX, COLUMN_HEIGHT + 1, -sideZ),
            center.offset(sideX, COLUMN_HEIGHT + 1, -sideZ),
            Blocks.POLISHED_DIORITE.defaultBlockState());
        fill(center.offset(-sideX, COLUMN_HEIGHT + 1, sideZ),
            center.offset(sideX, COLUMN_HEIGHT + 1, sideZ),
            Blocks.POLISHED_DIORITE.defaultBlockState());
        fill(center.offset(-sideX, COLUMN_HEIGHT + 1, -sideZ),
            center.offset(-sideX, COLUMN_HEIGHT + 1, sideZ),
            Blocks.POLISHED_DIORITE.defaultBlockState());
        fill(center.offset(sideX, COLUMN_HEIGHT + 1, -sideZ),
            center.offset(sideX, COLUMN_HEIGHT + 1, sideZ),
            Blocks.POLISHED_DIORITE.defaultBlockState());

        fill(center.offset(-HALL_HALF_WIDTH, 1, -HALL_HALF_LENGTH),
            center.offset(HALL_HALF_WIDTH, 4, -HALL_HALF_LENGTH),
            Blocks.WHITE_STAINED_GLASS.defaultBlockState());
        fill(center.offset(-HALL_HALF_WIDTH, 1, HALL_HALF_LENGTH),
            center.offset(HALL_HALF_WIDTH, 4, HALL_HALF_LENGTH),
            Blocks.WHITE_STAINED_GLASS.defaultBlockState());
        fill(center.offset(-HALL_HALF_WIDTH, 1, -HALL_HALF_LENGTH),
            center.offset(-HALL_HALF_WIDTH, 4, HALL_HALF_LENGTH),
            Blocks.WHITE_STAINED_GLASS.defaultBlockState());
        fill(center.offset(HALL_HALF_WIDTH, 1, -HALL_HALF_LENGTH),
            center.offset(HALL_HALF_WIDTH, 4, HALL_HALF_LENGTH),
            Blocks.WHITE_STAINED_GLASS.defaultBlockState());
    }

    private void buildRoof(BlockPos center) {
        fill(center.offset(-HALL_HALF_WIDTH, ROOM_HEIGHT, -HALL_HALF_LENGTH),
            center.offset(HALL_HALF_WIDTH, ROOM_HEIGHT, HALL_HALF_LENGTH),
                Blocks.TINTED_GLASS.defaultBlockState());
        fill(center.offset(-10, ROOM_HEIGHT, -18), center.offset(10, ROOM_HEIGHT, 18),
                Blocks.WHITE_STAINED_GLASS.defaultBlockState());
        fill(center.offset(-HALL_HALF_WIDTH + 2, ROOM_HEIGHT - 1, -HALL_HALF_LENGTH + 2),
            center.offset(HALL_HALF_WIDTH - 2, ROOM_HEIGHT - 1, HALL_HALF_LENGTH - 2),
            Blocks.POLISHED_DIORITE.defaultBlockState());
        fill(center.offset(-HALL_HALF_WIDTH + 3, ROOM_HEIGHT - 1, -HALL_HALF_LENGTH + 3),
            center.offset(HALL_HALF_WIDTH - 3, ROOM_HEIGHT - 1, HALL_HALF_LENGTH - 3),
            Blocks.AIR.defaultBlockState());
    }

        private void buildThroneDais(BlockPos center) {
        int throneFront = -HALL_HALF_LENGTH + 10;

        fill(center.offset(-7, 0, -HALL_HALF_LENGTH + 4),
            center.offset(7, 0, throneFront + 2),
            Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
        fill(center.offset(-5, 1, -HALL_HALF_LENGTH + 5),
            center.offset(5, 1, throneFront + 1),
            Blocks.POLISHED_DIORITE.defaultBlockState());
        fill(center.offset(-3, 2, -HALL_HALF_LENGTH + 6),
            center.offset(3, 2, throneFront),
            Blocks.CALCITE.defaultBlockState());

        fill(center.offset(-1, 3, -HALL_HALF_LENGTH + 7),
            center.offset(1, 5, -HALL_HALF_LENGTH + 7),
            Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        fill(center.offset(-2, 3, -HALL_HALF_LENGTH + 8),
            center.offset(2, 4, -HALL_HALF_LENGTH + 8),
            Blocks.POLISHED_BLACKSTONE.defaultBlockState());

        setBlock(center.offset(-4, 3, -HALL_HALF_LENGTH + 7), Blocks.SOUL_LANTERN.defaultBlockState());
        setBlock(center.offset(4, 3, -HALL_HALF_LENGTH + 7), Blocks.SOUL_LANTERN.defaultBlockState());
        }

        private void buildBronzeTable(BlockPos center) {
        fill(center.offset(-1, 0, -14), center.offset(1, 0, 14), Blocks.WEATHERED_CUT_COPPER.defaultBlockState());
        fill(center.offset(-2, 0, -16), center.offset(2, 0, -13), Blocks.OXIDIZED_CUT_COPPER.defaultBlockState());
        fill(center.offset(-2, 0, 13), center.offset(2, 0, 16), Blocks.OXIDIZED_CUT_COPPER.defaultBlockState());

        for (int z = -12; z <= 12; z += 4) {
            setBlock(center.offset(-3, 1, z), Blocks.GRAY_CARPET.defaultBlockState());
            setBlock(center.offset(3, 1, z), Blocks.GRAY_CARPET.defaultBlockState());
        }
    }

    private void buildLanterns(BlockPos center) {
        // 中央主吊灯组
        placeHangingLantern(center.offset(0, 0, 0), 4);
        placeHangingLantern(center.offset(0, 0, -12), 3);
        placeHangingLantern(center.offset(0, 0, 12), 3);
        
        // 王座区域吊灯
        placeHangingLantern(center.offset(0, 0, -HALL_HALF_LENGTH + 8), 3);
        placeHangingLantern(center.offset(-6, 0, -HALL_HALF_LENGTH + 6), 2);
        placeHangingLantern(center.offset(6, 0, -HALL_HALF_LENGTH + 6), 2);
        
        // 长桌上方吊灯
        for (int z = -10; z <= 10; z += 5) {
            placeHangingLantern(center.offset(0, 0, z), 2);
        }
        
        // 四角落地灯
        placeFloorLantern(center.offset(-HALL_HALF_WIDTH + 4, 0, -HALL_HALF_LENGTH + 4));
        placeFloorLantern(center.offset(HALL_HALF_WIDTH - 4, 0, -HALL_HALF_LENGTH + 4));
        placeFloorLantern(center.offset(-HALL_HALF_WIDTH + 4, 0, HALL_HALF_LENGTH - 4));
        placeFloorLantern(center.offset(HALL_HALF_WIDTH - 4, 0, HALL_HALF_LENGTH - 4));
        
        // 柱子间壁灯
        for (int z = -HALL_HALF_LENGTH + 6; z <= HALL_HALF_LENGTH - 6; z += 8) {
            placeWallLantern(center.offset(-HALL_HALF_WIDTH + 1, 4, z));
            placeWallLantern(center.offset(HALL_HALF_WIDTH - 1, 4, z));
        }
        
        // 王座后方装饰灯
        setBlock(center.offset(-3, 4, -HALL_HALF_LENGTH + 5), Blocks.SOUL_LANTERN.defaultBlockState());
        setBlock(center.offset(3, 4, -HALL_HALF_LENGTH + 5), Blocks.SOUL_LANTERN.defaultBlockState());
        setBlock(center.offset(0, 5, -HALL_HALF_LENGTH + 5), Blocks.SOUL_LANTERN.defaultBlockState());
    }

        private void placeColumn(BlockPos base) {
        setBlock(base, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        fill(base.above(), base.above(COLUMN_HEIGHT), Blocks.POLISHED_DIORITE.defaultBlockState());
        setBlock(base.above(COLUMN_HEIGHT + 1), Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        }

        private void placeFloorLantern(BlockPos floorPos) {
        setBlock(floorPos, Blocks.POLISHED_ANDESITE.defaultBlockState());
        setBlock(floorPos.above(), Blocks.CHAIN.defaultBlockState());
        setBlock(floorPos.above(2), Blocks.SOUL_LANTERN.defaultBlockState());
    }

        private void placeHangingLantern(BlockPos center, int chainLength) {
        BlockPos anchor = center.above(ROOM_HEIGHT - 1);
        for (int i = 0; i < chainLength; i++) {
            setBlock(anchor.below(i), Blocks.CHAIN.defaultBlockState());
        }
        setBlock(anchor.below(chainLength), Blocks.SOUL_LANTERN.defaultBlockState());
        setBlock(anchor.below(chainLength).east(), Blocks.SOUL_LANTERN.defaultBlockState());
        setBlock(anchor.below(chainLength).west(), Blocks.SOUL_LANTERN.defaultBlockState());
        }

    private void placeWallLantern(BlockPos pos) {
        setBlock(pos, Blocks.SOUL_LANTERN.defaultBlockState());
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