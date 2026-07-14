package net.exmo.sre.sixtyseconds.content.block;

import net.exmo.sre.sixtyseconds.logic.SixtySecondsDefenseSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 尖刺陷阱：铁压板外观、无碰撞体；夜袭怪物踩入每秒受
 * {@code SixtySecondsBalance.SPIKE_TRAP_DAMAGE} 伤害并减速（{@link SixtySecondsDefenseSystem}）。
 * 放在白色混凝土标记上方，扳手可拆除返还。
 */
public class SixtySecondsSpikeTrapBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 1.5, 15);

    public SixtySecondsSpikeTrapBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return Shapes.empty(); // 可踩入
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel) {
            SixtySecondsDefenseSystem.registerTrap(serverLevel, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            SixtySecondsDefenseSystem.unregister(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
