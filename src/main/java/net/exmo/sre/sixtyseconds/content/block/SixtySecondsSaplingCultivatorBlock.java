package net.exmo.sre.sixtyseconds.content.block;

import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.content.block_entity.SixtySecondsPlanterBlockEntity;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsCrops;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsCrops.Crop;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 树苗培育箱：5 阶段生长（age 0~4），种橡树苗产橡木 + 树苗 + 概率苹果。
 */
public class SixtySecondsSaplingCultivatorBlock extends SixtySecondsPlanterBlock {

    public SixtySecondsSaplingCultivatorBlock(Properties properties, SixtySecondsCrops.Tier tier) {
        super(properties, tier);
    }

    @Override
    public int maxAge() {
        return 4;
    }

    @Override
    protected void grow(ServerLevel level, BlockPos pos, BlockState state, int newAge,
            SixtySecondsCrops.Crop crop) {
        int target = Math.min(maxAge(), newAge);
        level.setBlock(pos, state.setValue(AGE, target), Block.UPDATE_ALL);
        if (target < maxAge()) {
            float mul = crop != null ? crop.growthTimeMultiplier() : 1.0F;
            level.scheduleTick(pos, this, (int) (SixtySecondsBalance.PLANTER_GROW_STAGE_TICKS * mul));
        }
    }

    @Override
    protected void harvest(BlockState state, ServerLevel level, BlockPos pos, ServerPlayer player,
            SixtySecondsState.TeamData team, Crop crop) {
        // 树苗培育箱专用收获：1 树苗 + 2 橡木，5% 苹果
        Block.popResource(level, pos, new ItemStack(Items.OAK_SAPLING));
        Block.popResource(level, pos, new ItemStack(Items.OAK_LOG, 2));
        if (level.random.nextFloat() < 0.05F) {
            Block.popResource(level, pos, new ItemStack(Items.APPLE));
        }
        if (level.getBlockEntity(pos) instanceof SixtySecondsPlanterBlockEntity be) {
            be.cropId = "";
        }
        level.setBlock(pos, state.setValue(AGE, 0), Block.UPDATE_ALL);
    }
}
