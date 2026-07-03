package io.wifi.starrailexpress.content.block;

import com.mojang.serialization.MapCodec;
import io.wifi.starrailexpress.cca.SRERoleWorldComponent;
import io.wifi.starrailexpress.content.block.api.AutoResetBlockInterface;
import io.wifi.starrailexpress.content.block.api.TaskInstinctShowableInterface;
import io.wifi.starrailexpress.content.block_entity.CameraBlockEntity;
import io.wifi.starrailexpress.game.GameUtils.BlockEntityInfo;
import io.wifi.starrailexpress.index.TMMBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class CameraBlock extends BaseEntityBlock implements TaskInstinctShowableInterface, AutoResetBlockInterface {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    protected static final VoxelShape SHAPE = Block.box(5.0D, 0.0D, 5.0D, 11.0D, 10.0D, 11.0D);

    public CameraBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    private static final MapCodec<CameraBlock> CODEC = simpleCodec(CameraBlock::new);

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CameraBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (player.isCreative()) {
            if (!world.isClientSide) {
                sendTip(player, world, pos);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    public static void sendTip(Player player, Level world, BlockPos pos) {
        if (world.getBlockEntity(pos) instanceof CameraBlockEntity cbe) {
            player.displayClientMessage(
                    Component
                            .translatable("block.trainmurdermystery.camera.click_tip", pos.toShortString(),
                                    cbe.getBrokenTime() / 20)
                            .withStyle(ChatFormatting.YELLOW),
                    true);
        }
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return createTickerHelper(type, TMMBlockEntities.CAMERA, CameraBlockEntity::tick);
    }

    @Override
    public BlockState onResetBlockState(ServerLevel level, BlockState state, BlockPos pos) {
        return state;
    }

    @Override
    public BlockEntityInfo onResetBlockEntity(ServerLevel level, BlockState state, BlockEntity blockEntity,
            BlockPos pos) {
        if (blockEntity instanceof CameraBlockEntity cbe) {
            cbe.reset();
        }
        return new BlockEntityInfo(blockEntity.saveCustomOnly(level.registryAccess()), blockEntity.components());
    }

    @Override
    public boolean shouldRenderTaskInstinct(BlockState state, BlockPos pos, Player player) {
        Level level = player.level();
        var roleCCA = SRERoleWorldComponent.KEY.get(level);
        return roleCCA.isRole(player, ModRoles.DELAYER);
    }

    @Override
    public Color taskInstinctRenderColor(BlockState state, BlockPos pos, Player player) {
        return java.awt.Color.MAGENTA;
    }
}