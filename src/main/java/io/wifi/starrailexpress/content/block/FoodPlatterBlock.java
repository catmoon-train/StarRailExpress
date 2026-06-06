package io.wifi.starrailexpress.content.block;

import com.mojang.serialization.MapCodec;

import io.wifi.starrailexpress.content.block_entity.BeveragePlateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class FoodPlatterBlock extends PlatterBlock {
    public static final MapCodec<FoodPlatterBlock> CODEC = createSimpleCodec(FoodPlatterBlock::new);

    public FoodPlatterBlock(Properties settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newPlateBlockEntity(BlockPos pos, BlockState state) {
        BeveragePlateBlockEntity plate = new BeveragePlateBlockEntity(pos, state);
        plate.setDrink(false);
        return plate;
    }
}
