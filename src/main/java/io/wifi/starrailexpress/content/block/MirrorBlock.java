package io.wifi.starrailexpress.content.block;

import com.mojang.serialization.MapCodec;
import io.wifi.starrailexpress.content.block_entity.MirrorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jetbrains.annotations.Nullable;

/**
 * 镜子方块。
 *
 * <p>它本身不渲染任何几何体（{@link RenderShape#INVISIBLE}），只作为一个"孔洞"存在：
 * 客户端的 {@link io.wifi.starrailexpress.client.mirror.MirrorReflectionManager}
 * 会把镜面前方的房间镜像复制到镜面后方的空腔里，玩家透过这个孔洞看到的就是反射像。
 * 因为镜像内容是普通世界几何，它走的是同一个渲染 pass，所以 Iris / Sodium 无需任何适配。
 *
 * <p>建图约束：镜子背后必须留出一段空腔（深度见 {@link MirrorBlockEntity#getDepth()}）。
 * 空腔内原有的方块会被客户端覆盖，并在镜子失活时还原。
 *
 * <p>{@link #FACING} 指向反射面朝向的房间一侧。
 */
public class MirrorBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<MirrorBlock> CODEC = simpleCodec(MirrorBlock::new);

    public MirrorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        // 反射面朝向放置者。
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MirrorBlockEntity(pos, state);
    }
}
