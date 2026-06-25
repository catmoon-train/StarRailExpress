package org.agmas.noellesroles.content.block_entity.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.content.block.scene.SabotageBridgeBlock;
import org.agmas.noellesroles.init.ModSceneBlocks;
import org.agmas.noellesroles.scene.SceneEventManager;

public class SabotageBridgeBlockEntity extends BlockEntity {
    public SabotageBridgeBlockEntity(BlockPos pos, BlockState state) {
        super(ModSceneBlocks.SABOTAGE_BRIDGE_ENTITY, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel serverLevel) || level.getGameTime() % 10 != 0) return;
        boolean broken = SceneEventManager.isSabotageActive(serverLevel);
        if (broken != state.getValue(SabotageBridgeBlock.BROKEN)) {
            serverLevel.setBlock(pos, state.setValue(SabotageBridgeBlock.BROKEN, broken), Block.UPDATE_ALL);
            serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5,
                    pos.getY() + 0.5, pos.getZ() + 0.5, 16, 0.4, 0.25, 0.4, 0.06);
            serverLevel.playSound(null, pos, broken ? SoundEvents.WOOD_BREAK : SoundEvents.WOOD_PLACE,
                    SoundSource.BLOCKS, 0.8F, broken ? 0.8F : 1.1F);
        }
    }
}
