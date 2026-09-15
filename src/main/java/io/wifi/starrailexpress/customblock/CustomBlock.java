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

package io.wifi.starrailexpress.customblock;

import com.mojang.serialization.MapCodec;
import io.wifi.starrailexpress.customblock.CustomBlockData.BlockEvent;
import io.wifi.starrailexpress.customblock.CustomBlockData.BlockEventType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import io.wifi.starrailexpress.index.TMMProperties;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 自定义方块（全模组唯一的那个注册方块）。
 *
 * <p>
 * 具体外观与行为由方块实体里记录的 id 决定：所有自定义方块共用这一个方块 + 一个
 * {@link CustomBlockEntity}，渲染完全交给 {@code client.render.block.CustomBlockRenderer}
 * （{@link RenderShape#ENTITYBLOCK_ANIMATED}），所以外观能随配置热改。
 *
 * <p>
 * 只在方块状态里放三样「原版必须靠状态才知道」的东西（其它属性都能从方块实体读，就不放进状态）：
 * <ul>
 * <li>{@link #WATERLOGGED} —— 含水（所有自定义方块都支持，靠 {@link SimpleWaterloggedBlock}）；</li>
 * <li>{@link #FACING} —— 4 向水平朝向（放置时按玩家视线，可在配置里关掉）；</li>
 * <li>{@link #LIGHT} / {@link #PATTERN} —— 发光等级与音效桶。这两个是原版接口的硬限制：
 * {@code Properties.lightLevel} 与 {@code getSoundType} 都只看方块状态、拿不到坐标，
 * 所以只能像原版光源方块那样把值写进状态。它们在放置时写入，方块实体加载时按配置自校正。</li>
 * </ul>
 *
 * <p>
 * 这样状态总数是 4 × 2 × 16 × {@link SoundPattern#VALUES_COUNT} 的量级（约 1024），
 * 与原版红石线（1296）同级；同时因为模型完全由方块实体渲染，blockstate 里只要一条
 * 通配变体（见 {@code blockstates/custom_block.json}），不会随状态数膨胀。
 */
public class CustomBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {

    public static final MapCodec<CustomBlock> CODEC = simpleCodec(CustomBlock::new);

    /** 含水状态（所有自定义方块都支持）。 */
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    /** 4 向水平朝向。 */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** 发光等级 0~15。 */
    public static final IntegerProperty LIGHT = IntegerProperty.create("light", 0, 15);
    /** 是否点亮（关灯事件会临时熄灭；参考 {@code TrainLightBlock}）。 */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** 是否有电（关灯事件会临时断电）。 */
    public static final BooleanProperty ACTIVE = TMMProperties.ACTIVE;
    /** 音效桶（跟随继承方块自动推导，见 {@link SoundPattern}）。 */
    public static final EnumProperty<SoundPattern> PATTERN = EnumProperty.create("pattern", SoundPattern.class);

    public CustomBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(WATERLOGGED, false)
                .setValue(FACING, Direction.NORTH)
                .setValue(LIGHT, 0)
                .setValue(LIT, true)
                .setValue(ACTIVE, true)
                .setValue(PATTERN, SoundPattern.STONE));
    }

    /** 方块基础属性（硬度 / 抗爆 / 透光等固定值，可配置的属性都走方块实体）。 */
    public static BlockBehaviour.Properties defaultProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(1.5F, 6.0F)
                .sound(SoundType.STONE)
                .noOcclusion()
                .pushReaction(PushReaction.NORMAL)
                // 形状 / 碰撞是按「该坐标上记录的配置」动态算的（见 getShape / getCollisionShape），
                // 必须声明 dynamicShape：否则原版会在注册时按「没有世界与坐标」调用一次并把结果
                // 缓存成完整方块，继承楼梯这类非完整形状的方块就会变成整块碰撞——走上去被挤出来。
                .dynamicShape()
                .lightLevel(state -> lightEmission(state));
    }

    /**
     * 发光等级：与列车灯（{@code TrainLightBlock}）一致，熄灭或停电时归零。
     *
     * <p>
     * {@code LIT} / {@code ACTIVE} 是给「关灯」事件用的（{@code SREWorldBlackoutComponent}
     * 只处理同时带这两个属性的方块）。配置里没勾「受关灯影响」的方块不会被登记进关灯点位，
     * 这两个属性会一直是 true，亮度也就等于配置的 {@link #LIGHT}。
     */
    public static int lightEmission(BlockState state) {
        return state.getValue(LIT) && state.getValue(ACTIVE) ? state.getValue(LIGHT) : 0;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CustomBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED, FACING, LIGHT, LIT, ACTIVE, PATTERN);
    }

    /** 外观完全由方块实体渲染。 */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    // ==================== 放置 / 朝向 ====================

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        FluidState fluid = level.getFluidState(pos);
        CustomBlockData data = CustomBlockLoader.getData(context.getItemInHand());
        boolean waterlogged = fluid.getType() == Fluids.WATER;
        Direction facing = data != null && !data.rotate
                ? Direction.NORTH
                : context.getHorizontalDirection().getOpposite();
        return placedState(data, facing, waterlogged);
    }

    /**
     * 构造放置时的方块状态（放置与 {@code /sre:setblock} 指令共用）。
     *
     * <p>
     * 把「原版只能从状态读到」的三样东西写进去：含水、朝向、亮度 + 音效桶。
     */
    public BlockState placedState(CustomBlockData data, Direction facing, boolean waterlogged) {
        Direction dir = facing == null ? Direction.NORTH : facing;
        if (data != null && !data.rotate) {
            dir = Direction.NORTH;
        }
        return defaultBlockState()
                .setValue(WATERLOGGED, waterlogged)
                .setValue(FACING, dir)
                .setValue(LIGHT, data == null ? 0 : data.lightLevel)
                .setValue(PATTERN, SoundPattern.of(CustomBlockLoader.soundType(data)));
    }

    /** 按配置自校正状态里的亮度与音效桶（配置改动后由方块实体加载时触发）。 */
    public BlockState tunedState(BlockState state, CustomBlockData data) {
        int light = data == null ? 0 : data.lightLevel;
        SoundPattern pattern = SoundPattern.of(CustomBlockLoader.soundType(data));
        BlockState tuned = state;
        if (tuned.getValue(LIGHT) != light) {
            tuned = tuned.setValue(LIGHT, light);
        }
        if (tuned.getValue(PATTERN) != pattern) {
            tuned = tuned.setValue(PATTERN, pattern);
        }
        return tuned;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    // ==================== 含水 ====================

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    // ==================== 形状 / 碰撞（委托继承方块）====================

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        BlockState inherited = inheritedShape(state, level, pos);
        return inherited == null ? Shapes.block() : inherited.getShape(level, pos, context);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        CustomBlockData data = CustomBlockLoader.getDataAt(level, pos);
        if (data != null && data.noCollision) {
            return Shapes.empty();
        }
        BlockState inherited = inheritedShape(state, level, pos);
        return inherited == null ? Shapes.block() : inherited.getCollisionShape(level, pos, context);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        CustomBlockData data = CustomBlockLoader.getDataAt(level, pos);
        if (data != null && data.blocksSkylight) {
            return false;
        }
        return super.propagatesSkylightDown(state, level, pos);
    }

    /** 继承方块在「本方块当前朝向 / 含水」下的状态（用于委托形状与碰撞）。 */
    @Nullable
    private BlockState inheritedShape(BlockState state, BlockGetter level, BlockPos pos) {
        CustomBlockData data = CustomBlockLoader.getDataAt(level, pos);
        if (data == null) {
            return null;
        }
        return CustomBlockLoader.inheritedState(data, state.getValue(FACING), state.getValue(WATERLOGGED));
    }

    // ==================== 音效 ====================

    @Override
    protected SoundType getSoundType(BlockState state) {
        return state.getValue(PATTERN).soundType();
    }

    // ==================== 掉落 / 拾取 ====================

    @Override
    public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state) {
        CustomBlockData data = CustomBlockLoader.getDataAt(level, pos);
        if (data != null) {
            return CustomBlockLoader.buildStack(data, 1);
        }
        return super.getCloneItemStack(level, pos, state);
    }

    /** 掉落自身：让自定义方块可以被回收（NBT 里的 id 跟着物品走）。 */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
            @Nullable BlockEntity blockEntity, ItemStack tool) {
        if (!level.isClientSide && blockEntity instanceof CustomBlockEntity entity) {
            CustomBlockData data = CustomBlockLoader.get(entity.getCustomBlockId());
            if (data != null) {
                Block.popResource(level, pos, CustomBlockLoader.buildStack(data, 1));
            }
        }
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
    }

    // ==================== 交互事件 ====================

    /** 右键：触发该方块所有 RIGHT_CLICK 事件。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        CustomBlockData data = CustomBlockLoader.getDataAt(level, pos);
        List<BlockEvent> events = data == null ? List.of() : data.eventsOf(BlockEventType.RIGHT_CLICK);
        if (level.isClientSide) {
            // 客户端只负责挥手反馈：没有右键事件就不摆臂（原版仍然会把这次点击发给服务端，
            // 见 MultiPlayerGameMode#useItemOn —— 返回值只影响本地预测动画）。
            return events.isEmpty() ? InteractionResult.PASS : InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || events.isEmpty()) {
            return InteractionResult.PASS;
        }
        boolean fired = CustomBlockRuntime.fireRightClick(serverPlayer, level, pos, data, events);
        return fired ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    /** 踩踏：触发该方块所有 STEP 事件。 */
    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        super.stepOn(level, pos, state, entity);
        if (level.isClientSide || !(entity instanceof ServerPlayer serverPlayer)) {
            return;
        }
        CustomBlockData data = CustomBlockLoader.getDataAt(level, pos);
        if (data == null) {
            return;
        }
        List<BlockEvent> events = data.eventsOf(BlockEventType.STEP);
        if (!events.isEmpty()) {
            CustomBlockRuntime.fireStep(serverPlayer, level, pos, data, events);
        }
    }

    // ==================== 音效桶 ====================

    /**
     * 音效桶：把「继承方块」的 {@link SoundType} 归并到少数几类，写进方块状态。
     *
     * <p>
     * 原版 {@code getSoundType(BlockState)} 拿不到坐标，所以只能把结果放进状态里；
     * 归并成 {@link #VALUES_COUNT} 类是为了控制状态总数（每类都会让方块状态翻一倍）。
     */
    public enum SoundPattern implements net.minecraft.util.StringRepresentable {
        STONE, WOOD, SOFT, GLASS, WOOL, METAL, PLANT, ORE;

        /** 桶数量（状态空间倍率）。 */
        public static final int VALUES_COUNT = 8;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public SoundType soundType() {
            return switch (this) {
                case STONE -> SoundType.STONE;
                case WOOD -> SoundType.WOOD;
                case SOFT -> SoundType.GRAVEL;
                case GLASS -> SoundType.GLASS;
                case WOOL -> SoundType.WOOL;
                case METAL -> SoundType.METAL;
                case PLANT -> SoundType.GRASS;
                case ORE -> SoundType.DEEPSLATE;
            };
        }

        /** 按继承方块的音效归类（未配置继承方块时是石头）。 */
        public static SoundPattern of(@Nullable SoundType type) {
            if (type == null || type == SoundType.STONE) {
                return STONE;
            }
            if (type == SoundType.WOOD || type == SoundType.CHERRY_WOOD || type == SoundType.BAMBOO_WOOD
                    || type == SoundType.NETHER_WOOD || type == SoundType.LADDER
                    || type == SoundType.SCAFFOLDING || type == SoundType.HANGING_SIGN) {
                return WOOD;
            }
            if (type == SoundType.GRAVEL || type == SoundType.SAND || type == SoundType.SOUL_SAND
                    || type == SoundType.SOUL_SOIL || type == SoundType.MUD || type == SoundType.MUD_BRICKS
                    || type == SoundType.PACKED_MUD || type == SoundType.SUSPICIOUS_GRAVEL
                    || type == SoundType.SUSPICIOUS_SAND || type == SoundType.ROOTED_DIRT) {
                return SOFT;
            }
            if (type == SoundType.GLASS || type == SoundType.AMETHYST || type == SoundType.AMETHYST_CLUSTER
                    || type == SoundType.CANDLE || type == SoundType.DECORATED_POT
                    || type == SoundType.DECORATED_POT_CRACKED) {
                return GLASS;
            }
            if (type == SoundType.WOOL || type == SoundType.SNOW || type == SoundType.POWDER_SNOW
                    || type == SoundType.MOSS_CARPET || type == SoundType.COBWEB
                    || type == SoundType.SLIME_BLOCK || type == SoundType.HONEY_BLOCK) {
                return WOOL;
            }
            if (type == SoundType.METAL || type == SoundType.COPPER || type == SoundType.COPPER_BULB
                    || type == SoundType.COPPER_GRATE || type == SoundType.ANVIL
                    || type == SoundType.LODESTONE || type == SoundType.NETHERITE_BLOCK
                    || type == SoundType.ANCIENT_DEBRIS || type == SoundType.CHAIN || type == SoundType.LANTERN
                    || type == SoundType.VAULT || type == SoundType.HEAVY_CORE || type == SoundType.TRIAL_SPAWNER) {
                return METAL;
            }
            if (type == SoundType.GRASS || type == SoundType.WET_GRASS || type == SoundType.VINE
                    || type == SoundType.ROOTS || type == SoundType.MOSS || type == SoundType.AZALEA
                    || type == SoundType.FLOWERING_AZALEA || type == SoundType.BIG_DRIPLEAF
                    || type == SoundType.SMALL_DRIPLEAF || type == SoundType.LILY_PAD
                    || type == SoundType.CAVE_VINES || type == SoundType.WEEPING_VINES
                    || type == SoundType.TWISTING_VINES || type == SoundType.CROP
                    || type == SoundType.HARD_CROP || type == SoundType.NETHER_WART
                    || type == SoundType.SWEET_BERRY_BUSH || type == SoundType.BAMBOO
                    || type == SoundType.BAMBOO_SAPLING || type == SoundType.STEM
                    || type == SoundType.SPORE_BLOSSOM || type == SoundType.GLOW_LICHEN) {
                return PLANT;
            }
            if (type == SoundType.DEEPSLATE || type == SoundType.DEEPSLATE_BRICKS
                    || type == SoundType.DEEPSLATE_TILES || type == SoundType.POLISHED_DEEPSLATE
                    || type == SoundType.TUFF || type == SoundType.TUFF_BRICKS
                    || type == SoundType.POLISHED_TUFF || type == SoundType.CALCITE
                    || type == SoundType.BASALT || type == SoundType.NETHERRACK
                    || type == SoundType.NETHER_BRICKS || type == SoundType.NETHER_ORE
                    || type == SoundType.DRIPSTONE_BLOCK
                    || type == SoundType.POINTED_DRIPSTONE || type == SoundType.SCULK
                    || type == SoundType.SCULK_CATALYST || type == SoundType.SCULK_SENSOR
                    || type == SoundType.SCULK_SHRIEKER || type == SoundType.SCULK_VEIN
                    || type == SoundType.GILDED_BLACKSTONE || type == SoundType.NETHER_GOLD_ORE
                    || type == SoundType.WART_BLOCK || type == SoundType.NYLIUM
                    || type == SoundType.SHROOMLIGHT || type == SoundType.FROGLIGHT
                    || type == SoundType.FUNGUS || type == SoundType.CORAL_BLOCK
                    || type == SoundType.WET_SPONGE || type == SoundType.SPONGE) {
                return ORE;
            }
            return STONE;
        }
    }
}
