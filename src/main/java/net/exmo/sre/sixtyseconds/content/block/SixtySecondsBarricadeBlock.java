package net.exmo.sre.sixtyseconds.content.block;

import net.exmo.sre.sixtyseconds.logic.SixtySecondsDefenseSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 路障（木路障 / 书柜·沙发等重型路障，用 hp 区分）：堵门/堵路的临时工事，
 * 夜袭怪物会优先冲击附近路障（{@link SixtySecondsDefenseSystem} 掉耐久，0 则被摧毁）。
 * 放在白色混凝土标记上方（{@code SixtySecondsPlaceableBlockItem}），扳手可拆除返还。
 */
public class SixtySecondsBarricadeBlock extends Block {
    private final int hp;

    public SixtySecondsBarricadeBlock(Properties properties, int hp) {
        super(properties);
        this.hp = hp;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel) {
            SixtySecondsDefenseSystem.registerBarricade(serverLevel, pos, hp);
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
