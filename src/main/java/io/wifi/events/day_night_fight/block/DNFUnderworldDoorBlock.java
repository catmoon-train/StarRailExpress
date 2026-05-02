package io.wifi.events.day_night_fight.block;

import io.wifi.events.day_night_fight.cca.DNFUnderworldComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class DNFUnderworldDoorBlock extends Block {
    public DNFUnderworldDoorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 3; i++) {
            world.addParticle(ParticleTypes.PORTAL,
                    pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.5,
                    pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.8,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.5,
                    (random.nextDouble() - 0.5) * 0.08,
                    (random.nextDouble() - 0.5) * 0.08,
                    (random.nextDouble() - 0.5) * 0.08);
        }
    }

    @Override
    public void entityInside(BlockState state, Level world, BlockPos pos, Entity entity) {
        if (world.isClientSide || !(entity instanceof ServerPlayer player)) {
            return;
        }
        DNFUnderworldComponent component = DNFUnderworldComponent.KEY.get(player);
        if (component.isWaitingRoom()) {
            component.enterTrueUnderworld(player);
        }
    }
}
