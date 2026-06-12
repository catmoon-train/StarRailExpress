package io.wifi.starrailexpress.content.block;

import io.wifi.starrailexpress.content.block_entity.UpSmallDoorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Supplier;

public class UpTrainDoorBlock extends UpSmallDoorBlock {
    public UpTrainDoorBlock(Supplier<BlockEntityType<UpSmallDoorBlockEntity>> typeSupplier, Properties settings) {
        super(typeSupplier, settings);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
            BlockHitResult hit) {
        return TrainDoorBlock.useWithoutItemGeneric(
                (s, w, p, pl, h) -> super.useWithoutItem(s, w, p, pl, h), // lambda 调用父类并传递参数
                state, world, pos, player, hit);
    }
}
