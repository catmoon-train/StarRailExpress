package io.wifi.starrailexpress.content.block;

import com.mojang.serialization.MapCodec;

import io.wifi.starrailexpress.content.block_entity.BeveragePlateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class DrinkTrayBlock extends FoodPlatterBlock {
    public static final MapCodec<DrinkTrayBlock> CODEC = simpleCodec(DrinkTrayBlock::new);

    public DrinkTrayBlock(Properties settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newPlateBlockEntity(BlockPos pos, BlockState state) {
        BeveragePlateBlockEntity plate = new BeveragePlateBlockEntity(pos, state);
        plate.setDrink(true);
        return plate;
    }
}