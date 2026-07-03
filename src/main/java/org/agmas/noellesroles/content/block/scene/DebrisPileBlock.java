package org.agmas.noellesroles.content.block.scene;

import com.mojang.serialization.MapCodec;
import io.wifi.starrailexpress.content.block.api.TaskInstinctShowableInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.phys.BlockHitResult;
import org.agmas.noellesroles.content.block_entity.scene.DebrisPileBlockEntity;
import org.agmas.noellesroles.init.ModSceneBlocks;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.function.Consumer;

public class DebrisPileBlock extends BaseEntityBlock implements TaskInstinctShowableInterface {
    public static final int TASK_INSTINCT_ID = 18;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final BooleanProperty CLOSED = BooleanProperty.create("closed");

    /** 客户端回调：打开碎屑堆灭火小游戏屏幕。由 NoellesrolesClient 在客户端初始化时设置。 */
    public static Consumer<BlockPos> openDebrisPileScreenCallback;

    public DebrisPileBlock(Properties settings) {
        super(settings);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false).setValue(CLOSED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return null; }

    @Override
    public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, CLOSED);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level.isClientSide) {
            if (state.getValue(ACTIVE) && !state.getValue(CLOSED) && openDebrisPileScreenCallback != null) {
                openDebrisPileScreenCallback.accept(pos);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DebrisPileBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state,
            BlockEntityType<T> type) {
        if (world.isClientSide) return null;
        return createTickerHelper(type, ModSceneBlocks.DEBRIS_PILE_ENTITY,
                (lvl, pos, s, be) -> DebrisPileBlockEntity.serverTick(lvl, pos, s, be));
    }

    @Override
    public int taskInstinctId() { return TASK_INSTINCT_ID; }

    @Override
    public boolean shouldRenderTaskInstinct(BlockState state, BlockPos pos, Player player) { return true; }

    @Override
    public Color taskInstinctRenderColor(BlockState state, BlockPos pos, Player player) {
        return new Color(0x7CFC00);
    }
}
