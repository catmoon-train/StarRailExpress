package net.exmo.sre.sixtyseconds.content.block;

import com.mojang.serialization.MapCodec;
import net.exmo.sre.sixtyseconds.content.block_entity.RandomSupplyBoxBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 随机物资箱方块：完全克隆 {@link SupplyBoxBlock} 的交互（生存搜刮领取、创造右键编辑 loot 表、
 * 潜行+右键切类别），只把方块实体换成 {@link RandomSupplyBoxBlockEntity}——每次刷新随机取一个 loot 类别。
 */
public class RandomSupplyBoxBlock extends SupplyBoxBlock {
    private static final MapCodec<RandomSupplyBoxBlock> CODEC = simpleCodec(RandomSupplyBoxBlock::new);

    public RandomSupplyBoxBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RandomSupplyBoxBlockEntity(pos, state);
    }
}
