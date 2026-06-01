package org.agmas.noellesroles.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.game.modes.repair.RepairGameplayEffects;

public class HunterSnareBlock extends Block {
    public HunterSnareBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level instanceof ServerLevel serverLevel && entity instanceof Player player && !RepairGameplayEffects.isHunter(player)) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 2, false, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 80, 0, false, true, true));
            RepairGameplayEffects.burst(serverLevel, pos.getX() + 0.5D, pos.getY() + 0.2D, pos.getZ() + 0.5D, 0);
            level.destroyBlock(pos, false);
        }
        super.entityInside(state, level, pos, entity);
    }
}
