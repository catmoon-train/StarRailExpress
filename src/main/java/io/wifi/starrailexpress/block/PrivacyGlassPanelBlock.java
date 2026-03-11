package io.wifi.starrailexpress.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

public class PrivacyGlassPanelBlock extends GlassPanelBlock implements PrivacyBlock {

    public PrivacyGlassPanelBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(super.defaultBlockState()
                .setValue(OPAQUE, false)
                .setValue(INTERACTION_COOLDOWN, false));
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isSecondaryUseActive() && !player.getMainHandItem().is(this.asItem()) && this.canInteract(state, pos, world, player, InteractionHand.MAIN_HAND)) {
            this.toggle(state, world, pos);

            return InteractionResult.sidedSuccess(world.isClientSide);
        }

        return super.useWithoutItem(state, world, pos, player, hit);
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        this.toggle(state, world, pos);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPAQUE, INTERACTION_COOLDOWN);
        super.createBlockStateDefinition(builder);
    }
}
