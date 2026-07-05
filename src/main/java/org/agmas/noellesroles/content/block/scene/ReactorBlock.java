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
import org.agmas.noellesroles.content.block_entity.scene.ReactorBlockEntity;
import org.agmas.noellesroles.init.ModSceneBlocks;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.function.Consumer;

/**
 * 反应堆装置：破坏任务激活时进入过载（active），玩家右键打开温度调节小游戏。
 * 完成小游戏关闭反应堆，两个配对反应堆均关闭后结束破坏任务。
 * 中立/杀手可通过任务透视全局看到反应堆（绿色边框）。
 */
public class ReactorBlock extends BaseEntityBlock implements TaskInstinctShowableInterface {

    public static final int TASK_INSTINCT_ID = 16;

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final BooleanProperty CLOSED = BooleanProperty.create("closed");

    /** 客户端回调：打开反应堆小游戏屏幕。由 NoellesrolesClient 在客户端初始化时设置。 */
    public static Consumer<BlockPos> openReactorScreenCallback;

    public ReactorBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(ACTIVE, false)
                .setValue(CLOSED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return null;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, CLOSED);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level.isClientSide) {
            // 客户端：通过回调打开温度调节小游戏（避免服务端加载 Screen 类）
            if (state.getValue(ACTIVE) && !state.getValue(CLOSED) && openReactorScreenCallback != null) {
                openReactorScreenCallback.accept(pos);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ReactorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state,
            BlockEntityType<T> type) {
        if (world.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModSceneBlocks.REACTOR_ENTITY,
                (lvl, pos, s, be) -> ReactorBlockEntity.serverTick(lvl, pos, s, be));
    }

    // ── 任务透视接口 ──

    @Override
    public int taskInstinctId() {
        return TASK_INSTINCT_ID;
    }

    @Override
    public boolean shouldRenderTaskInstinct(Level level, BlockState state, BlockPos pos, Player player) {
        return state.getValue(ACTIVE) && !state.getValue(CLOSED);
    }

    @Override
    public Color taskInstinctRenderColor(BlockState state, BlockPos pos, Player player) {
        return new Color(0x4CAF50); // 绿色，区分于其他任务点
    }
}
