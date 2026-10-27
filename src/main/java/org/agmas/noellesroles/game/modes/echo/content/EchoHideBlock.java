package org.agmas.noellesroles.game.modes.echo.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 回声托运站草丛：可穿过的视觉遮挡块，玩家站进去会触发草丛隐身。
 *
 * <p>方块本身**没有碰撞**，玩家能直接走进去；隐身的判定完全由
 * {@link EchoHideService} 在客户端按观察者计算，方块本身不需要方块实体、不需要 tick。
 * 因此方块本身不需要方块实体、不需要 tick、不写任何玩家状态。
 *
 * <p>之所以做成方块而不是装饰模型，是因为路线准入、模板复制、地图校验都只认方块状态：
 * 草丛是整格无碰撞方块，{@code stand()} 会把它当空气，不会破坏步行图。
 */
public final class EchoHideBlock extends Block {
    public static final MapCodec<EchoHideBlock> CODEC = simpleCodec(EchoHideBlock::new);
    /** 朝向只影响模型朝向，不参与任何玩法判定。 */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public EchoHideBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends Block> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** 无碰撞：玩家可以自由走进草丛，路线准入也把它当空气。 */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /** 不遮挡光照与视线的完整形状由模型提供；这里给一个接近满格的选择框。 */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return box(1.0D, 0.0D, 1.0D, 15.0D, 14.0D, 15.0D);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState neighbor, Direction side) {
        return false;
    }

    /** 玩家破坏草丛时立即刷新一次隐身状态，避免离开后还保持隐藏。 */

    /** 创造模式物品栏用：草丛只能放在实心地面上。 */
    public static boolean canSurviveOn(Level level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                || level.getBlockState(below).is(Blocks.GRASS_BLOCK);
    }
}
