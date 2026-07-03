package org.agmas.noellesroles.content.block.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 地窖：右键打开舱门即可跳到下面，随后自动关闭，并进入冷却，冷却期间无法再次打开。
 * 使用原版云杉木板贴图。
 */
public class CellarBlock extends Block {

    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final BooleanProperty LOCKED = BooleanProperty.create("locked");

    /** 打开后自动关闭的延迟。 */
    public static final int CLOSE_DELAY = 20;
    /** 关闭后无法再次打开的冷却。 */
    public static final int COOLDOWN = 100;

    /** 活板门碰撞箱：厚度 3 像素，大小与原版活板门一致 */
    private static final VoxelShape TRAPDOOR_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 3.0, 16.0);

    public CellarBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(OPEN, false)
                .setValue(LOCKED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN, LOCKED);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : TRAPDOOR_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : TRAPDOOR_SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (state.getValue(OPEN)) {
            return InteractionResult.CONSUME;
        }
        if (state.getValue(LOCKED)) {
            level.playSound(null, pos, SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.4F, 0.6F);
            return InteractionResult.CONSUME;
        }
        // 打开舱门
        level.setBlock(pos, state.setValue(OPEN, true), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.9F, 0.9F);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.3, 0.1, 0.3, 0.05);
            serverLevel.scheduleTick(pos, this, CLOSE_DELAY);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(OPEN)) {
            // 自动关闭 + 进入冷却
            level.setBlock(pos, state.setValue(OPEN, false).setValue(LOCKED, true), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.9F, 1.0F);
            level.scheduleTick(pos, this, COOLDOWN);
        } else if (state.getValue(LOCKED)) {
            // 冷却结束
            level.setBlock(pos, state.setValue(LOCKED, false), Block.UPDATE_ALL);
        }
    }
}
