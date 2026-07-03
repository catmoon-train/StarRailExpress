package io.wifi.starrailexpress.content.block;

import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import io.wifi.starrailexpress.content.block.api.TaskInstinctShowableInterface;
import io.wifi.starrailexpress.content.block_entity.MinigameQuestBlockEntity;
import io.wifi.starrailexpress.index.TMMBlockEntities;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Map;

/**
 * 小游戏任务点镶板
 * 透明、可含水、无碰撞体积，类似实体交互镶板
 * 只有一面，可贴在其他方块上
 */
public class MinigameQuestPanelBlock extends BaseEntityBlock
        implements TaskInstinctShowableInterface, SimpleWaterloggedBlock {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final int TASK_INSTINCT_ID = 15;

    private static final VoxelShape UP_SHAPE = box(0.0, 15.9, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape DOWN_SHAPE = box(0.0, 0.0, 0.0, 16.0, 0.1, 16.0);
    private static final VoxelShape EAST_SHAPE = box(0.0, 0.0, 0.0, 0.1, 16.0, 16.0);
    private static final VoxelShape WEST_SHAPE = box(15.9, 0.0, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape SOUTH_SHAPE = box(0.0, 0.0, 0.0, 16.0, 16.0, 0.1);
    private static final VoxelShape NORTH_SHAPE = box(0.0, 0.0, 15.9, 16.0, 16.0, 16.0);
    private static final Map<Direction, VoxelShape> SHAPES = Util.make(Maps.newEnumMap(Direction.class), shapes -> {
        shapes.put(Direction.NORTH, NORTH_SHAPE);
        shapes.put(Direction.EAST, EAST_SHAPE);
        shapes.put(Direction.SOUTH, SOUTH_SHAPE);
        shapes.put(Direction.WEST, WEST_SHAPE);
        shapes.put(Direction.UP, DOWN_SHAPE);
        shapes.put(Direction.DOWN, UP_SHAPE);
    });

    public MinigameQuestPanelBlock(Properties settings) {
        super(settings.noOcclusion().noCollission());
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return null; }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPES.getOrDefault(state.getValue(FACING), Shapes.block());
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public float getShadeBrightness(BlockState state, BlockGetter world, BlockPos pos) { return 1.0F; }
    @Override public boolean propagatesSkylightDown(BlockState state, BlockGetter world, BlockPos pos) { return true; }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (world.isClientSide) return InteractionResult.SUCCESS;

        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof MinigameQuestBlockEntity questBe) {
            if (player instanceof ServerPlayer sp && sp.isCreative()) {
                questBe.openConfigUI(sp);
            } else if (player instanceof ServerPlayer sp) {
                // 破坏任务触发点：杀手 + canUseSabotage 角色可右键
                if (questBe.isSabotageTrigger()) {
                    var role = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(sp.level())
                            .getRole(sp);
                    if (role == null || (!role.isKiller() && !role.canUseSabotage())) {
                        return InteractionResult.SUCCESS;
                    }
                }
                String minigameId = questBe.getMinigameId();
                if (minigameId != null && !minigameId.isEmpty()) {
                    // 游戏运行中：校验任务和冷却
                    if (io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(sp.level()).isRunning()) {
                        var mgComp = io.wifi.starrailexpress.cca.SREPlayerMinigameTaskComponent.KEY.get(sp);
                        if (!mgComp.hasPendingTask()) {
                            sp.displayClientMessage(
                                    net.minecraft.network.chat.Component.translatable("message.sre.minigame_no_task"),
                                    true);
                            return InteractionResult.SUCCESS;
                        }
                        if (mgComp.isBlockUsed(pos)) {
                            sp.displayClientMessage(
                                    net.minecraft.network.chat.Component.translatable("message.sre.minigame_cooldown"),
                                    true);
                            return InteractionResult.SUCCESS;
                        }
                        // 校验小游戏类型匹配
                        if (mgComp.targetMinigameId != null && !mgComp.targetMinigameId.isEmpty()
                                && !mgComp.targetMinigameId.equals(minigameId)) {
                            sp.displayClientMessage(
                                    net.minecraft.network.chat.Component.translatable("message.sre.minigame_wrong_type",
                                            net.minecraft.network.chat.Component.translatable(
                                                    "minigame.starrailexpress." + mgComp.targetMinigameId)),
                                    true);
                            return InteractionResult.SUCCESS;
                        }
                        mgComp.startBlockCooldown(pos);
                    }
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(sp,
                            new io.wifi.starrailexpress.network.MinigameQuestPayload.OpenGame(pos, minigameId));
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MinigameQuestBlockEntity(pos, state);
    }

    @Nullable @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state,
            BlockEntityType<T> type) {
        return createTickerHelper(type, TMMBlockEntities.MINIGAME_QUEST,
                (lvl, pos, s, be) -> {});
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
    }

    @Override public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Nullable @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = ctx.getClickedFace();
        FluidState fluidState = ctx.getLevel().getFluidState(ctx.getClickedPos());
        return this.defaultBlockState().setValue(FACING, facing)
                .setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
    }

    @Override public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        return Block.canSupportCenter(world, pos.relative(facing.getOpposite()), facing);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world,
            BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) world.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world));
        Direction facing = state.getValue(FACING);
        if (direction == facing.getOpposite() && !state.canSurvive(world, pos)) return Blocks.AIR.defaultBlockState();
        return state;
    }

    // ══════════════════════════════════════════
    // 任务路标接口
    // ══════════════════════════════════════════

    @Override public int taskInstinctId() { return TASK_INSTINCT_ID; }

    @Override
    public boolean shouldRenderTaskInstinct(BlockState state, BlockPos pos, Player player) {
        Level level = player.level();
        if (level != null) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MinigameQuestBlockEntity questBe) {
                // 破坏任务触发点：杀手 + canUseSabotage 角色可见
                if (questBe.isSabotageTrigger()) {
                    var role = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(level)
                            .getRole(player);
                    return role != null && (role.isKiller() || role.canUseSabotage());
                }
                return questBe.isTaskMarker();
            }
        }
        return false;
    }

    @Override
    public Color taskInstinctRenderColor(BlockState state, BlockPos pos, Player player) {
        Level level = player.level();
        if (level != null) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MinigameQuestBlockEntity questBe) {
                int c = questBe.getMarkerColor();
                if (questBe.isSabotageTrigger() && c == 0x00FF00) return Color.RED;
                return new Color(c);
            }
        }
        return Color.GREEN;
    }
}
