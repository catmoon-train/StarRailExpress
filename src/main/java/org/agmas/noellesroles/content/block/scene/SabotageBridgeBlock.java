package org.agmas.noellesroles.content.block.scene;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.agmas.noellesroles.content.block_entity.scene.SabotageBridgeBlockEntity;
import org.agmas.noellesroles.init.ModSceneBlocks;
import org.jetbrains.annotations.Nullable;

public class SabotageBridgeBlock extends BaseEntityBlock {
    public static final BooleanProperty BROKEN = BooleanProperty.create("broken");
    private static final VoxelShape SLAB_SHAPE = Block.box(0, 0, 0, 16, 8, 16);

    public SabotageBridgeBlock(Properties settings) {
        super(settings);
        registerDefaultState(stateDefinition.any().setValue(BROKEN, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return null; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BROKEN);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(BROKEN) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return state.getValue(BROKEN) ? Shapes.empty() : SLAB_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return state.getValue(BROKEN) ? Shapes.empty() : SLAB_SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SabotageBridgeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state,
            BlockEntityType<T> type) {
        if (world.isClientSide) return null;
        return createTickerHelper(type, ModSceneBlocks.SABOTAGE_BRIDGE_ENTITY,
                (lvl, pos, s, be) -> SabotageBridgeBlockEntity.serverTick(lvl, pos, s));
    }
}
