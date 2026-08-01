/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.content.block;

import com.mojang.serialization.MapCodec;

import io.wifi.starrailexpress.content.block.api.TaskInstinctShowableInterface;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.awt.Color;

import org.agmas.noellesroles.content.block_entity.SupplyCrateBlockEntity;
import org.agmas.noellesroles.packet.OpenSupplyCrateScreenS2CPacket;
import org.jetbrains.annotations.Nullable;

public class SupplyCrateBlock extends BaseEntityBlock implements TaskInstinctShowableInterface {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPENED = BooleanProperty.create("opened");
    private static final MapCodec<SupplyCrateBlock> CODEC = simpleCodec(SupplyCrateBlock::new);
    protected static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 14.0, 15.0);

    public SupplyCrateBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(
                this.stateDefinition.any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(OPENED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter world, BlockPos pos) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPENED);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SupplyCrateBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, org.agmas.noellesroles.init.ModBlocks.SUPPLY_CRATE_BLOCK_ENTITY,
                SupplyCrateBlockEntity::tick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (handleInteraction(level, pos, player, state)) {
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (handleInteraction(level, pos, player, state)) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    private boolean handleInteraction(Level level, BlockPos pos, Player player, BlockState state) {
        if (level.isClientSide()) return true;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof SupplyCrateBlockEntity crate)) return false;

        // 创造模式玩家：打开配置 GUI
        if (player.isCreative()) {
            if (player instanceof ServerPlayer serverPlayer) {
                ServerPlayNetworking.send(serverPlayer, new OpenSupplyCrateScreenS2CPacket(pos));
            }
            return true;
        }

        // 冒险/生存模式玩家：领取物资
        if (!crate.hasItems(player)) {
            return true; // 没有可领取的
        }

        java.util.List<ItemStack> items = crate.claimItems(player);
        if (items.isEmpty()) return true;

        // 给予玩家物品
        for (ItemStack item : items) {
            if (!player.addItem(item.copy())) {
                player.drop(item.copy(), false);
            }
        }

        // 播放开箱声音
        level.playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.8F, 1.0F);

        // 更新方块打开状态（仅非共享模式）
        if (!crate.isSharedSupplies() && state.hasProperty(OPENED)) {
            level.setBlockAndUpdate(pos, state.setValue(OPENED, true));
        }

        return true;
    }

    @Override
    public boolean shouldRenderTaskInstinct(Level level, BlockState state, BlockPos pos, Player player) {
        return true;
    }

    @Override
    public Color taskInstinctRenderColor(BlockState state, BlockPos pos, Player player) {
        return new java.awt.Color(186, 85, 211);
    }
}
