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

import com.mojang.math.Transformation;
import com.mojang.serialization.MapCodec;
import io.wifi.starrailexpress.content.block_entity.DisplayBlockEntityBase;
import io.wifi.starrailexpress.util.EditorGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import org.joml.Quaternionf;

/**
 * 展示方块基类（文本展示方块 / 方块展示方块）。
 *
 * <p>方块本体不渲染（{@link RenderShape#INVISIBLE}）且没有碰撞箱，内容全部交给方块实体渲染器
 * 按原版展示实体（{@code Display}）的规则绘制——好处是不占实体、可以按方块坐标对齐。
 *
 * <p>创造模式或 OP 等级 2 的玩家右键可打开编辑界面（服务端判定，客户端只是体验）。
 */
public abstract class DisplayBlockBase extends BaseEntityBlock {

    /** 放置定向的对齐档位（22.5° = 16 个方向）。 */
    public static final float YAW_STEP_DEGREES = 22.5F;

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

    /**
     * 放置时按放置者朝向定向：**内容正面朝放置者**，和原版展示实体「放下去正对着你」的手感一致。
     *
     * <p>
     * 朝向对齐到 {@link #YAW_STEP_DEGREES} 一档（22.5°，共 16 个方向）：站位差一点点也不会放歪，
     * 一排摆出来是整齐的。
     *
     * <p>
     * 只在「还没有人为转过角度」的方块上生效（left_rotation 仍是单位四元数）：用选取方块复制来的、
     * 或在编辑器里调过旋转的展示方块保持原有朝向，不会被重新摆正。
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide() || !(placer instanceof Player player)
                || !(level.getBlockEntity(pos) instanceof DisplayBlockEntityBase display)) {
            return;
        }
        CompoundTag data = display.getDisplayData();
        Transformation current = DisplayBlockEntityBase.readTransformation(data);
        if (!current.getLeftRotation().equals(new Quaternionf())) {
            return;
        }
        // 换算与渲染器里的 CENTER billboard 一致：rotationY = 180 - 相机看向方块的水平偏航
        double dx = pos.getX() + 0.5D - player.getX();
        double dz = pos.getZ() + 0.5D - player.getZ();
        float cameraYaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        float facingYaw = Math.round((180.0F - cameraYaw) / YAW_STEP_DEGREES) * YAW_STEP_DEGREES;
        Quaternionf rotation = new Quaternionf()
                .rotationYXZ((float) Math.toRadians(facingYaw), 0.0F, 0.0F);
        Transformation oriented = new Transformation(current.getTranslation(), rotation, current.getScale(),
                current.getRightRotation());
        DisplayBlockEntityBase.writeTransformation(data, oriented);
        if (display.setDisplayData(data)) {
            display.syncToClients();
        }
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

    /**
     * 仅「创造模式 + 拥有权限（OP 等级 2）」的玩家可以编辑内容。
     *
     * <p>
     * 判定统一走 {@link EditorGuard}，与保存数据的服务端校验同源（见 {@code DisplayBlockServerNetwork}）。
     */
    public static boolean canEdit(Player player) {
        return EditorGuard.canEdit(player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level.isClientSide) {
            // 客户端拿不到权限信息，只按「创造模式」预测：非创造不给任何反馈（不摆臂）、
            // 也不吞掉这次点击。返回值只影响本地预测动画，包照发，真正的判定在服务端。
            return player.isCreative() ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof DisplayBlockEntityBase display)) {
            return InteractionResult.PASS;
        }
        if (!canEdit(player)) {
            // 创造但没权限：给一句提示；非创造则静默（当作没点到）
            if (player.isCreative()) {
                player.displayClientMessage(Component.translatable("gui.display_block.no_permission"), true);
            }
            return InteractionResult.PASS;
        }
        display.openEditScreen(serverPlayer);
        return InteractionResult.SUCCESS;
    }

    /** 编辑界面标题用的翻译键。 */
    public abstract String editScreenTitleKey();
}
