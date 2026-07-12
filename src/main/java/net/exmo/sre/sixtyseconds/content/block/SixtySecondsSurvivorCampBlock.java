package net.exmo.sre.sixtyseconds.content.block;

import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsWinConditions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 幸存者营地方块：玩家<b>走上去或右键</b>即触发「抵达幸存者阵营」通关（{@link SixtySecondsWinConditions#reachSurvivorCamp}）。
 * 地图作者把它放在需要长途跋涉才能抵达的地方（如搜索区尽头/特殊区域）。
 */
public class SixtySecondsSurvivorCampBlock extends Block {
    public SixtySecondsSurvivorCampBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (level instanceof ServerLevel && entity instanceof ServerPlayer player && SixtySecondsMod.isActive(level)) {
            SixtySecondsWinConditions.reachSurvivorCamp(player);
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (level instanceof ServerLevel && player instanceof ServerPlayer serverPlayer
                && SixtySecondsMod.isActive(level)) {
            SixtySecondsWinConditions.reachSurvivorCamp(serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }
}
