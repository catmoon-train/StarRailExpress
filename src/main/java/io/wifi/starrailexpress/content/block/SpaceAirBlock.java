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
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Locale;

/**
 * 太空空气：像空气一样的“区域效果方块”，本身不可见、无碰撞、可随意穿过，也不会参与放置 /
 * 右键使用物品 / 破坏这些交互，唯一的作用是让<b>任意部位处于方块内</b>的生物重力发生变化。
 *
 * <p>
 * 重力用方块状态 {@link #GRAVITY}（0..30，每档 {@link #PERCENT_PER_LEVEL}）控制，实际施加的是
 * {@code ADD_MULTIPLIED_TOTAL} 的百分比加成：0 → -200%（重力为负，生物会持续上浮）、
 * {@link #NEUTRAL_LEVEL} → +0%（无效果，也是放置时的默认档位）、30 → +100%。
 * 数值换算见 {@link #modifierFor(int)}，要改范围/步长只动这两个常量即可。
 *
 * <p>
 * 判定走原版 {@code Entity#checkInsideBlocks} → {@code entityInside}，它是按实体碰撞箱覆盖的体素
 * 判定的，与方块自身有没有碰撞箱无关（下界传送门、气泡柱等无碰撞方块也依赖这条路径），
 * 所以“玩家任意部位在方块里”天然成立。真正的重写/回收在 {@link SpaceAirGravityRuntime}。
 *
 * <p>
 * 默认不渲染（{@link RenderShape#INVISIBLE}）：创造模式手持本方块物品时，由
 * {@code ClientWorldMixin} 把物品加进 {@code ClientLevel.MARKER_PARTICLE_ITEMS}，
 * 原版会自动画出屏障粒子；手持本物品或调试棒时才会给出选中形状，方便选中、破坏与用调试棒调档位。
 *
 * <p>
 * 物品侧：中键拾取会把当前档位写进物品堆（见 {@link #getCloneItemStack}），放下时档位不丢；
 * 提示里同时摊开方块状态值与该档位对应的 modifier 数值（见 {@link #appendHoverText}）。
 */
public class SpaceAirBlock extends Block {
    /** 最大档位：0..{@link #MAX_LEVEL} 共 31 档，0 对应 -200%。 */
    public static final int MAX_LEVEL = 30;

    /** 重力档位属性：0..{@link #MAX_LEVEL}。 */
    public static final IntegerProperty GRAVITY = IntegerProperty.create("gravity", 0, MAX_LEVEL);

    /** 无效果档位：+0%，也是放置时的默认档位。 */
    public static final int NEUTRAL_LEVEL = 20;

    /** 每档的百分比步长。配合 0..30 的档位范围即 -200% ~ +100%。 */
    public static final float PERCENT_PER_LEVEL = 0.1F;

    /** 物品提示的配色，与项目内其它方块提示保持一致。 */
    private static final int TOOLTIP_COLOR = 0x7b9aba;

    public static final MapCodec<SpaceAirBlock> CODEC = simpleCodec(SpaceAirBlock::new);

    public SpaceAirBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(GRAVITY, NEUTRAL_LEVEL));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    /**
     * 把方块状态的档位换算成 AttributeModifier 的数值（{@code ADD_MULTIPLIED_TOTAL} 语义，1.0 即 +100%）。
     *
     * @param level {@link #GRAVITY} 档位，0..30
     * @return 落在 -2.0 ~ +1.0 之间的百分比加成
     */
    public static float modifierFor(int level) {
        return (level - NEUTRAL_LEVEL) * PERCENT_PER_LEVEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GRAVITY);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /**
     * 只有手持本物品或调试棒时才给出形状，其余情况形状为空：射线检测会直接穿过方块，
     * 于是放置方块、右键使用物品、破坏都不会命中它；而手持物品/调试棒时又能正常选中与调档位。
     */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return context.isHoldingItem(this.asItem()) || context.isHoldingItem(Items.DEBUG_STICK)
                ? Shapes.block()
                : Shapes.empty();
    }

    // ======================= 拾取、物品显示 =======================

    /**
     * 中键拾取时把档位写进物品堆：原版 {@code BlockItem} 放置时会读取 {@code BLOCK_STATE} 组件并套到
     * 方块状态上，所以「拾取 → 放置」不会丢档位。写法对齐原版灯方块的 {@code LightBlock#getCloneItemStack}。
     */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return setGravityOnStack(super.getCloneItemStack(level, pos, state), state.getValue(GRAVITY));
    }

    /** 把档位写进物品堆；与原版灯方块一致，等于默认档位时不写组件（放下时自然落到默认状态）。 */
    public static ItemStack setGravityOnStack(ItemStack stack, int gravity) {
        if (gravity != NEUTRAL_LEVEL) {
            stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(GRAVITY, gravity));
        }
        return stack;
    }

    /** 读物品堆里记录的档位；没有组件（比如创造物品栏里那一份）时就是默认档位。 */
    public static int gravityOfStack(ItemStack stack) {
        BlockItemStateProperties properties = stack.get(DataComponents.BLOCK_STATE);
        if (properties == null) {
            return NEUTRAL_LEVEL;
        }
        Integer gravity = properties.get(GRAVITY);
        return gravity != null ? gravity : NEUTRAL_LEVEL;
    }

    /** 档位对应的百分比（整数，固定是 10 的倍数）。 */
    public static int percentFor(int level) {
        return Math.round(modifierFor(level) * 100.0F);
    }

    /** 物品提示：把方块状态值与 modifier 数值都摊开，方便管理员照着调档。 */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int gravity = gravityOfStack(stack);
        tooltip.add(Component.translatable("tooltip.starrailexpress.space_air.state", Integer.toString(gravity))
                .withColor(TOOLTIP_COLOR));
        tooltip.add(Component
                .translatable("tooltip.starrailexpress.space_air.modifier",
                        String.format(Locale.ROOT, "%+.1f", modifierFor(gravity)),
                        String.format(Locale.ROOT, "%+d%%", percentFor(gravity)))
                .withColor(TOOLTIP_COLOR));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    // ======================= 空气化：不挡光、可透光 =======================

    /** 与空气一致：不吸收光照，天光可直通下方。 */
    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }

    /**
     * 必须显式覆写：默认实现会读 {@link #getShape}，而手持物品时这里的形状是完整方块，
     * 会让手持瞬间的透光率变化、光照跟着跳一下。
     */
    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    // ======================= 重力效果 =======================

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        // 只在服务端记录，客户端没有权威的属性数据；生物之外（掉落物、箭等）没有重力属性，不必处理
        if (!level.isClientSide && entity instanceof LivingEntity living) {
            SpaceAirGravityRuntime.markInside(living, modifierFor(state.getValue(GRAVITY)));
        }
        super.entityInside(state, level, pos, entity);
    }
}
