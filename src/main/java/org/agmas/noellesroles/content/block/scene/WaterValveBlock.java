package org.agmas.noellesroles.content.block.scene;

import com.mojang.serialization.MapCodec;
import io.wifi.starrailexpress.content.block.PanelBlock;
import io.wifi.starrailexpress.content.block.api.TaskInstinctShowableInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.agmas.noellesroles.content.block_entity.scene.WaterValveBlockEntity;
import org.agmas.noellesroles.init.ModSceneBlocks;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.function.Consumer;

/**
 * 水阀装置（镶板方块）：破坏任务激活时漏水，玩家右键打开关闭水阀小游戏。
 * 继承 PanelBlock → 具备镶板的方向放置和薄型碰撞箱。
 */
public class WaterValveBlock extends PanelBlock implements EntityBlock, TaskInstinctShowableInterface {

    public static final int TASK_INSTINCT_ID = 17;

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final BooleanProperty CLOSED = BooleanProperty.create("closed");

    /** 客户端回调：打开水阀小游戏屏幕。由 NoellesrolesClient 在客户端初始化时设置。 */
    public static Consumer<BlockPos> openWaterValveScreenCallback;

    public WaterValveBlock(Properties settings) {
        super(settings);
        BlockState base = this.defaultBlockState();
        this.registerDefaultState(base.setValue(ACTIVE, false).setValue(CLOSED, false));
    }

    @Override
    protected MapCodec<? extends PanelBlock> codec() {
        return null;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVE, CLOSED);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level.isClientSide) {
            if (state.getValue(ACTIVE) && !state.getValue(CLOSED) && openWaterValveScreenCallback != null) {
                openWaterValveScreenCallback.accept(pos);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WaterValveBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state,
            BlockEntityType<T> type) {
        if (world.isClientSide)
            return null;
        if (type != ModSceneBlocks.WATER_VALVE_ENTITY)
            return null;
        return (lvl, pos, s, be) -> {
            if (be instanceof WaterValveBlockEntity wv) {
                WaterValveBlockEntity.serverTick(lvl, pos, s, wv);
            }
        };
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
        return new Color(0x0D47A1);
    }
}
