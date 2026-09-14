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

package io.wifi.starrailexpress.content.block;

import com.mojang.serialization.MapCodec;
import io.wifi.starrailexpress.content.block_entity.DisplayBlockEntityBase;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 展示方块基类（文本展示方块 / 方块展示方块）。
 *
 * <p>方块本体不渲染（{@link RenderShape#INVISIBLE}）且没有碰撞箱，内容全部交给方块实体渲染器
 * 按原版展示实体（{@code Display}）的规则绘制——好处是不占实体、可以按方块坐标对齐。
 *
 * <p>创造模式或 OP 等级 2 的玩家右键可打开编辑界面（服务端判定，客户端只是体验）。
 */
public abstract class DisplayBlockBase extends BaseEntityBlock {

    protected DisplayBlockBase(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return null;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** 保留整格选中框，方便在地图里找到这种看不见的方块。 */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    /** 创造模式或 OP 等级 2 可以编辑内容。 */
    public static boolean canEdit(Player player) {
        if (player.isCreative()) {
            return true;
        }
        return player instanceof ServerPlayer serverPlayer && serverPlayer.hasPermissions(2);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level.isClientSide) {
            // 客户端直接返回 SUCCESS 让手臂有挥动反馈，真正的判定在服务端。
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof DisplayBlockEntityBase display) {
            if (canEdit(player)) {
                display.openEditScreen(serverPlayer);
            } else {
                player.displayClientMessage(Component.translatable("gui.display_block.no_permission"), true);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** 编辑界面标题用的翻译键。 */
    public abstract String editScreenTitleKey();
}
