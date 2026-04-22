package org.agmas.noellesroles.content.block;

import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.block.api.AutoResetBlockInterface;
import io.wifi.starrailexpress.content.block.entity.SeatEntity;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.modes.funny.SREDevilRouletteGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.agmas.noellesroles.content.block_entity.DevilRouletteTableEntity;
import org.agmas.noellesroles.mini_gme.DevilRouletteGame;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

public class DevilRouletteTable extends Block implements EntityBlock, AutoResetBlockInterface {
    public enum TablePart implements StringRepresentable {
        NORTH_WEST(-1, -1),
        NORTH(0, -1),
        NORTH_EAST(1, -1),
        WEST(-1, 0),
        CENTER(0, 0),
        EAST(1, 0),
        SOUTH_WEST(-1, 1),
        SOUTH(0, 1),
        SOUTH_EAST(1, 1);

        private final int xOffset;
        private final int zOffset;

        TablePart(int xOffset, int zOffset) {
            this.xOffset = xOffset;
            this.zOffset = zOffset;
        }

        @Override
        public @NotNull String getSerializedName() {
            return name().toLowerCase();
        }
    }
    public DevilRouletteTable() {
        super(BlockBehaviour.Properties.of()
                .strength(2.0F)
                .noOcclusion());
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH).setValue(PART, TablePart.CENTER));
    }
    /** 定义方块状态属性 */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }
    /** 检查方块放置条件 */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos corePos = context.getClickedPos();
        Direction facing = context.getHorizontalDirection().getOpposite();
        for (TablePart part : TablePart.values()) {
            BlockPos targetPos = corePos.offset(part.xOffset, 0, part.zOffset);
            boolean canReplace = context.getLevel().getBlockState(targetPos).canBeReplaced(context)
                    && context.getLevel().getWorldBorder().isWithinBounds(targetPos);
            if (!canReplace) {
                return null;
            }
        }
        return defaultBlockState().setValue(FACING, facing).setValue(PART, TablePart.CENTER);
    }
    public static void placeStructure(LevelAccessor level, BlockPos corePos, Direction facing) {
        BlockState centerState = level.getBlockState(corePos);
        if (!(centerState.getBlock() instanceof DevilRouletteTable block)) {
            return;
        }
        for (TablePart part : TablePart.values()) {
            // 放置同一结构其他方块时，设置其PART属性
            level.setBlock(corePos.offset(part.xOffset, 0, part.zOffset),
                    block.defaultBlockState().setValue(FACING, facing).setValue(PART, part),
                    Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE);
        }
    }
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @org.jetbrains.annotations.Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            // 放置完成后，在服务端生成结构
            placeStructure(level, pos, state.getValue(FACING));
        }
    }


    /** 移除结构 */
    private static void removeStructure(Level level, BlockPos corePos) {
        for (TablePart part : TablePart.values()) {
            level.setBlock(corePos.offset(part.xOffset, 0, part.zOffset), Blocks.AIR.defaultBlockState(),
                    // 忽略掉落物 ： 防止掉落多个物品
                    Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
        }
    }
    @Override
    public @NotNull BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            // 玩家破坏方块时，在服务端移除结构
            removeStructure(level, getCore(state, pos));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
    /** 获取结构核心位置 */
    public static BlockPos getCore(BlockState state, BlockPos pos) {
        if (!(state.getBlock() instanceof DevilRouletteTable)) {
            return pos;
        }
        TablePart part = state.getValue(PART);
        return pos.offset(-part.xOffset, 0, -part.zOffset);
    }
    @Override
    protected @NotNull VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return SHAPE;
    }

    /**
     * 检测结构是否完整:
     *  - 当中心附近有缺损时，替换为空气
     */
    @Override
    protected @NotNull BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                              LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        BlockPos corePos = getCore(state, pos);
        for (TablePart part : TablePart.values()) {
            if (!level.getBlockState(corePos.offset(part.xOffset, 0, part.zOffset)).is(this)) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }
    /** 仅中心位置可创建方块实体 */
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(PART) != TablePart.CENTER) {
            return null;
        }
        return new DevilRouletteTableEntity(pos, state);
    }
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // 客户端调用 方块实体 tick ： 刷新动画
//        if (level.isClientSide) {
//            return (lvl, pos, st, be) -> {
//                if (be instanceof DevilRouletteTableEntity table) {
//                    table.clientTick();
//                }
//            };
//        }
        if (state.getValue(PART) == TablePart.CENTER && !level.isClientSide) {
            return (lvl, pos, st, be) -> {
                if (be instanceof DevilRouletteTableEntity table) {
                    table.serverTick (lvl, pos, st);
                }
            };
        }
        return null;
    }

    // 交互逻辑

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /**
     * 点击方块时
     * <p>
     *     如果游戏未开始：
     *     - 检查状态：仅坐在正确位置的椅子上才可请求
     *          如果点击位置是中心，则检查座位上的玩家是否正确，如果正确检查游戏开始条件
     *          如果是其他位置或游戏开始条件不满足，向方块实体请求占位：结果成功/失败
     * </p>
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        BlockPos corePos = getCore(state, pos);
        BlockEntity be = level.getBlockEntity(corePos);
        if (be instanceof DevilRouletteTableEntity table) {
            // 只有玩家坐在座位上才能继续操作
            if (player.getVehicle() instanceof SeatEntity seatEntity) {
                var seatPos = seatEntity.getSeatPos();
                if (seatPos != null && table.isSeatAvailable(seatPos))
                {
                    boolean isFront = table.isFrontSeat(seatPos);
                    // 游戏未开始时，如果游戏模式是轮盘赌模式，则不允许自动开启
                    if (!table.isGameActive() && table.getGameMode() != DevilRouletteGame.GameMode.Roulette) {
                        // 满足开始条件，且操作玩家位置正确：开始游戏
                        if (corePos.equals(hit.getBlockPos()) && table.checkCanStartGame() &&
                                table.checkPlayerInRightSeat(player, isFront)) {
                            table.startGame();
                        }
                        // 进行占位操作，再次点击取消
                        else {
                            if (isFront){
                                if(!table.removePlayerIfSame(player, true) && table.addPlayer(player, true)) {
                                    return ItemInteractionResult.CONSUME;
                                }
                            }
                            else {
                                if(!table.removePlayerIfSame(player, false) && table.addPlayer(player, false)) {
                                    return ItemInteractionResult.CONSUME;
                                }
                            }
                        }
                    }
                    // 游戏已开始则由方块实体判断具体玩家操作
                    else if (table.checkPlayerInRightSeat(player, isFront) && table.canPlayerOperate(player)) {
                        return table.useItemOn(stack, state, level, pos, player, hand, hit);
                    }
                }
            }
        }
        return ItemInteractionResult.SUCCESS;
    }

    // 游戏模式内重置逻辑
    @Override
    public BlockState onResetBlockState(ServerLevel level, BlockState state, BlockPos pos) {
        if (state.getValue(PART) == TablePart.CENTER) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DevilRouletteTableEntity table) {
                table.reset();
                SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(level);
                GameMode mode = gameComponent.getGameMode();
                if (mode instanceof SREDevilRouletteGameMode devilRouletteGameMode) {
                    table.setGameMode(DevilRouletteGame.GameMode.Roulette);
                    // 将地图中的 轮盘赌桌 实体加入到游戏模式类中
                    devilRouletteGameMode.addRouletteTableEntity(table);
                }
            }
        }
        return state;
    }

    @Override
    public GameUtils.BlockEntityInfo onResetBlockEntity(ServerLevel level, BlockState state, BlockEntity blockEntity, BlockPos pos) {
        return null;
    }


    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<TablePart> PART =
            EnumProperty.create("part", TablePart.class);
    public static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 2, 16);
}
