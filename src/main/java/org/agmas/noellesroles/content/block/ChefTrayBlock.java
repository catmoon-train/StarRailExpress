package org.agmas.noellesroles.content.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.agmas.noellesroles.content.block_entity.ChefPlateBlockEntity;

/**
 * 厨师的「客户端」食物盘 / 饮料盘。
 *
 * <p>这类方块<strong>只存在于客户端</strong>：服务端不生成方块实体、也不保存方块状态，
 * 盘子里装了什么由 {@link org.agmas.noellesroles.game.roles.neutral.chef.ChefTrayManager} 用
 * 「坐标 → 内容物」的方式在服务端单独记账，再通过 S2C 包让所有客户端在自己的世界里
 * {@code level.setBlock} 画出这个盘子（做法与建筑师的客户端墙一致）。
 *
 * <p>因此本方块在服务端被右键时什么也不会发生——真正的交互（放入 / 取出）由客户端
 * mixin 拦截右键、改发 C2S 包交给服务端处理。
 *
 * <p>形状是一个薄盘子（高 2 像素），无碰撞：玩家可以直接走过去。
 */
public abstract class ChefTrayBlock extends Block implements EntityBlock {

    /**
     * 盘子的选取盒：底部 1~15，高度 2 像素。
     *
     * <p>
     * 盘子刻意<b>不带任何 blockstate 属性</b>：盘内物品由
     * {@code ChefPlateRenderer} 通过方块实体渲染，不需要「空 / 满」变体。
     */
    protected static final VoxelShape SHAPE = box(1.0D, 0.0D, 1.0D, 15.0D, 2.0D, 15.0D);

    protected ChefTrayBlock(Properties properties) {
        super(properties);
    }

    /**
     * 必须声明为携带方块实体的方块（{@link EntityBlock}）：
     * {@code Level.setBlockEntity} 开头会检查 {@code getBlockState(pos).hasBlockEntity()}，
     * 不满足就<b>静默忽略</b> —— 之前盘内物品一直渲染不出来，就是因为这里没实现 EntityBlock，
     * 客户端挂上去的 {@link ChefPlateBlockEntity} 根本没进渲染管线。
     */
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChefPlateBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return SHAPE;
    }

    /** 这个盘子是饮料盘（true）还是食物盘（false）。 */
    public abstract boolean isDrinkTray();

    @Override
    public String toString() {
        return "ChefTrayBlock[" + (isDrinkTray() ? "drink" : "food") + "]";
    }

    // ==================== 交互：服务端一律不处理 ====================
    // 服务端世界里根本没有这个方块，正常情况下连 use 都不会被调用；
    // 这里显式放行，避免任何"服务端存在同名方块"的边界情况下误吞交互。

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        // 放行：真正的放入/取出由客户端 mixin 拦截右键后改发 C2S 包处理
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** 食物盘。 */
    public static class Food extends ChefTrayBlock {
        public static final MapCodec<Food> CODEC = simpleCodec(Food::new);

        public Food(Properties properties) {
            super(properties);
        }

        @Override
        protected MapCodec<? extends Block> codec() {
            return CODEC;
        }

        @Override
        public boolean isDrinkTray() {
            return false;
        }
    }

    /** 饮料盘。 */
    public static class Drink extends ChefTrayBlock {
        public static final MapCodec<Drink> CODEC = simpleCodec(Drink::new);

        public Drink(Properties properties) {
            super(properties);
        }

        @Override
        protected MapCodec<? extends Block> codec() {
            return CODEC;
        }

        @Override
        public boolean isDrinkTray() {
            return true;
        }
    }
}
