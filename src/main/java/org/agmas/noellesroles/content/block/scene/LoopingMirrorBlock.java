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

package org.agmas.noellesroles.content.block.scene;

import com.mojang.serialization.MapCodec;
import io.wifi.starrailexpress.index.DevItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.agmas.noellesroles.content.block_entity.scene.LoopingMirrorBlockEntity;
import org.agmas.noellesroles.scene.LoopingMirrorManager;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 循环镜子：无碰撞的隐形宿主方块。用工具框选两个平面绑定到这一块上后，
 * 穿过任一平面都会循环到另一平面；客户端在平面后方生成对应场景。
 */
public class LoopingMirrorBlock extends Block implements EntityBlock {
    public static final MapCodec<LoopingMirrorBlock> CODEC = simpleCodec(LoopingMirrorBlock::new);

    public LoopingMirrorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacentState, Direction direction) {
        return true;
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext entityContext
                && entityContext.getEntity() instanceof Player player
                && player.isCreative()) {
            return Shapes.block();
        }
        if (context.isHoldingItem(asItem())
                || context.isHoldingItem(DevItems.LOOPING_MIRROR_TOOL)
                || context.isHoldingItem(Items.DEBUG_STICK)) {
            return Shapes.block();
        }
        return Shapes.empty();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (player.getMainHandItem().is(DevItems.LOOPING_MIRROR_TOOL)) {
            return InteractionResult.PASS;
        }
        if (player.isCreative() && !level.isClientSide
                && level.getBlockEntity(pos) instanceof LoopingMirrorBlockEntity be) {
            player.displayClientMessage(be.describe(), true);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            LoopingMirrorManager.removeContaining(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
            TooltipFlag options) {
        tooltip.add(Component.translatable("block.noellesroles.looping_mirror.tooltip.1"));
        tooltip.add(Component.translatable("block.noellesroles.looping_mirror.tooltip.2"));
        super.appendHoverText(stack, context, tooltip, options);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LoopingMirrorBlockEntity(pos, state);
    }
}
