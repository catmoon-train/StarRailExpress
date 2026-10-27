package org.agmas.noellesroles.game.modes.echo.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 横板（pallet）：逃脱者可以放下它挡住通道，截流者可以拆掉，30 秒后自动复位。
 *
 * <p>状态机：
 * <pre>
 *   立着（UP，无碰撞，可穿过）
 *     -- 逃脱者空手右键 -->  放下（DOWN，整格碰撞，挡路）
 *     -- 截流者破坏    -->  拆掉（BREAKING，无碰撞，短暂后可再立起）
 *     -- 放下满 30 秒  -->  自动回到立着（UP）
 * </pre>
 *
 * <p>三条关键约束：
 * <ul>
 * <li><b>不阻断路线准入</b>：立着（UP）时无碰撞，因此地图校验与步行图把它当空气；
 * 只有玩家在对局中主动放下才会挡路，不会让地图变成不可通行。</li>
 * <li><b>不写方块实体</b>：计时完全靠 {@code scheduleTick}，模板复制与整块覆盖都能安全搬运。</li>
 * <li><b>可确定性复现</b>：状态只由方块属性决定，重新生成地图一定得到同一初始态（全部立着）。</li>
 * </ul>
 */
public final class EchoPalletBlock extends Block {
    public static final MapCodec<EchoPalletBlock> CODEC = simpleCodec(EchoPalletBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** true = 已放下挡路；false = 立着可穿过。 */
    public static final BooleanProperty DOWN = BooleanProperty.create("down");
    /** true = 刚被截流者拆掉，暂时无法放下。 */
    public static final BooleanProperty BROKEN = BooleanProperty.create("broken");

    /** 放下后自动复位的时间（tick）。 */
    public static final int RESET_TICKS = 30 * 20;
    /** 被拆掉后重新可用的时间（tick）。 */
    public static final int REPAIR_TICKS = 5 * 20;

    /** 立着时的碰撞（无）。 */
    private static final VoxelShape UP_SHAPE = Shapes.empty();
    /** 放下时的碰撞：满格，真正挡人。 */
    private static final VoxelShape DOWN_SHAPE = Shapes.block();

    public EchoPalletBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(DOWN, false)
                .setValue(BROKEN, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, DOWN, BROKEN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** 立着时可穿过；放下时满格碰撞。 */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(DOWN) && !state.getValue(BROKEN) ? DOWN_SHAPE : UP_SHAPE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(DOWN) && !state.getValue(BROKEN) ? DOWN_SHAPE : box(2.0D, 0.0D, 6.0D, 14.0D, 16.0D, 10.0D);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    /**
     * 放下：只有托运者（逃脱者）能放，且必须在立着、未损坏的状态下。
     * 返回是否成功，供服务端决定要不要播声音/提示。
     */
    public static boolean lower(Level level, BlockPos pos, Player player) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof EchoPalletBlock)) return false;
        if (state.getValue(DOWN) || state.getValue(BROKEN)) return false;
        if (!EchoHideRoles.isCarrier(player)) return false;
        BlockState down = state.setValue(DOWN, true).setValue(BROKEN, false);
        // 放下会挡人：先确认目标格没有别的玩家卡在里面，避免把队友夹住。
        if (level instanceof ServerLevel server && !playersInside(server, pos).isEmpty()) return false;
        level.setBlock(pos, down, Block.UPDATE_CLIENTS);
        level.scheduleTick(pos, (Block) state.getBlock(), RESET_TICKS);
        return true;
    }

    /** 截流者拆板：放下的板子被拆回立着，并进入短暂损坏状态。 */
    public static boolean breakPallet(Level level, BlockPos pos, Player player) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof EchoPalletBlock)) return false;
        if (!state.getValue(DOWN)) return false;
        if (!EchoHideRoles.isInterceptor(player)) return false;
        BlockState broken = state.setValue(DOWN, false).setValue(BROKEN, true);
        level.setBlock(pos, broken, Block.UPDATE_CLIENTS);
        level.scheduleTick(pos, (Block) state.getBlock(), REPAIR_TICKS);
        return true;
    }

    private static java.util.List<Player> playersInside(ServerLevel level, BlockPos pos) {
        var box = new net.minecraft.world.phys.AABB(pos).inflate(-0.01D);
        return level.getEntitiesOfClass(Player.class, box, p -> !p.isSpectator());
    }

    /** 30 秒自动复位 / 5 秒修复完成。 */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(DOWN)) {
            level.setBlock(pos, state.setValue(DOWN, false), Block.UPDATE_CLIENTS);
            return;
        }
        if (state.getValue(BROKEN)) {
            level.setBlock(pos, state.setValue(BROKEN, false), Block.UPDATE_CLIENTS);
        }
    }

    /** 放置与状态变化后补一次 tick 调度，保证计时一定在跑。 */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.isClientSide) return;
        if (state.getValue(DOWN)) level.scheduleTick(pos, this, RESET_TICKS);
        else if (state.getValue(BROKEN)) level.scheduleTick(pos, this, REPAIR_TICKS);
    }
}
