package org.agmas.noellesroles.content.block.scene;

import java.util.Optional;

import org.agmas.noellesroles.content.block_entity.scene.BreakingBridgeBlockEntity;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 断桥方块：玩家踩过后短暂延迟即断裂（失去碰撞、不可见），一段时间后自动恢复。
 * 使用原版木板贴图。
 */
public class BreakingBridgeBlock extends SlabBlock implements EntityBlock {

    public static final BooleanProperty BROKEN = BooleanProperty.create("broken");
    public static final MapCodec<BreakingBridgeBlock> CODEC = simpleCodec(BreakingBridgeBlock::new);

    public MapCodec<? extends BreakingBridgeBlock> codec() {
        return CODEC;
    }

    /** 踩上到断裂的延迟。 */
    public static final int BREAK_DELAY = 10;
    /** 断裂到恢复的延迟。 */
    public static final int RECOVER_DELAY = 80;

    public BreakingBridgeBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(this.defaultBlockState().setValue(BROKEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BROKEN);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return state.getValue(BROKEN) ? Shapes.empty() : super.getShape(state, world, pos, context);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return state.getValue(BROKEN) ? Shapes.empty() : super.getCollisionShape(state, world, pos, context);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!level.isClientSide && !state.getValue(BROKEN) && entity instanceof Player
                && level instanceof ServerLevel serverLevel
                && !serverLevel.getBlockTicks().hasScheduledTick(pos, this)) {
            // 即将断裂提示
            serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 6, 0.3, 0.05, 0.3, 0.0);
            serverLevel.playSound(null, pos, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.8F, 0.7F);
            serverLevel.scheduleTick(pos, this, BREAK_DELAY);
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(BROKEN)) {
            // 断裂
            level.setBlock(pos, state.setValue(BROKEN, true), Block.UPDATE_ALL);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 25, 0.4, 0.4, 0.4, 0.1);
            level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.0F, 0.9F);
            level.scheduleTick(pos, this, RECOVER_DELAY);
        } else {
            // 恢复
            level.setBlock(pos, state.setValue(BROKEN, false), Block.UPDATE_ALL);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.defaultBlockState()),
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.0);
            level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.7F, 1.1F);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BreakingBridgeBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack itemStack, BlockState blockState, Level level, BlockPos blockPos,
            Player player, InteractionHand interactionHand, BlockHitResult blockHitResult) {
        if (!player.isCreative())
            return super.useItemOn(itemStack, blockState, level, blockPos, player, interactionHand, blockHitResult);
        if (level.isClientSide)
            return ItemInteractionResult.CONSUME;
        if (!itemStack.isEmpty()) {
            var diState = getBlockStateFromItem(itemStack, blockState.getOptionalValue(TYPE));
            if (diState == null) {
                return ItemInteractionResult.FAIL;
            }
            var entity = level.getBlockEntity(blockPos);
            if (entity instanceof BreakingBridgeBlockEntity bbbe) {
                bbbe.setDisplayState(diState);
            }
        }
        return ItemInteractionResult.CONSUME;
    }

    public static BlockState getBlockStateFromItem(ItemStack stack, Optional<SlabType> slabType) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return null;
        }

        // 获取默认状态
        BlockState state = blockItem.getBlock().defaultBlockState();

        // 尝试读取 BlockStateTag
        BlockItemStateProperties tag = stack.get(DataComponents.BLOCK_STATE);
        if (tag != null) {
            for (var entry : tag.properties().entrySet()) {
                // 检查该属性是否存在于当前 BlockState 中
                String key = entry.getKey();
                Property<?> property = state.getBlock().getStateDefinition().getProperty(key);
                if (property != null) {
                    // 获取值的字符串表示
                    String value = entry.getValue();
                    // 尝试解析并设置值
                    state = setPropertyValue(state, property, value);
                }
            }
        } else {
            var slabTypeValue = slabType.orElse(null);
            if (state.hasProperty(TYPE)) {
                state = state.setValue(TYPE, slabTypeValue);
            }
        }

        return state;
    }

    // 辅助方法：将字符串值安全地应用到属性上
    private static <T extends Comparable<T>> BlockState setPropertyValue(BlockState state, Property<T> property,
            String value) {
        return property.getValue(value)
                .map(val -> state.setValue(property, val))
                .orElse(state); // 解析失败则保持不变
    }
}
