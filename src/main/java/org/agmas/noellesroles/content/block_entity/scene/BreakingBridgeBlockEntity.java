package org.agmas.noellesroles.content.block_entity.scene;

import org.agmas.noellesroles.content.block.scene.BreakingBridgeBlock;
import org.agmas.noellesroles.init.ModSceneBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class BreakingBridgeBlockEntity extends BlockEntity {
    public int breakingStage = 0;
    public int nowTime = -1;
    public int breakingTime = 10;
    public int restoringTime = 80;
    public BlockState displayState = null;

    public BreakingBridgeBlockEntity(BlockPos pos, BlockState state) {
        super(ModSceneBlocks.BREAKING_BRIDGE_ENTITY, pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        if (displayState != null) {
            tag.put("BlockState", NbtUtils.writeBlockState(displayState));
        }
        tag.putInt("now_time", nowTime);
        tag.putInt("breaking_stage", breakingStage);
        tag.putInt("breaking_time", breakingTime);
        tag.putInt("restoring_time", restoringTime);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registryLookup) {
        return this.saveWithoutMetadata(registryLookup);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains("BlockState")) {
            displayState = NbtUtils.readBlockState(provider.lookup(Registries.BLOCK).orElseThrow(),
                    tag.getCompound("BlockState"));
        }
        if (tag.contains("breaking_stage")) {
            this.breakingStage = tag.getInt("breaking_stage");
        }

        if (tag.contains("breaking_time")) {
            this.breakingTime = tag.getInt("breaking_time");
        }
        if (tag.contains("restoring_time")) {
            this.restoringTime = tag.getInt("restoring_time");
        }
        if (tag.contains("now_time")) {
            this.nowTime = tag.getInt("now_time");
        }
    }

    public void setDisplayState(BlockState diState) {
        this.displayState = diState;
        sync();
    }

    public void sync() {
        this.setChanged();
        this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(),
                Block.UPDATE_CLIENTS);
    }

    public void addBreakingStage() {
        this.breakingStage++;
        if (breakingStage > 10)
            breakingStage = 0;
        sync();
    }

    public static void tick(Level world, BlockPos pos, BlockState state, BreakingBridgeBlockEntity d) {
        if (d != null)
            d.tick(world, pos, state);
    }

    public void tick(Level world, BlockPos pos, BlockState state) {
        if (this.nowTime >= 0 && this.nowTime < this.breakingTime) {
            this.nowTime++;
            if (this.nowTime >= this.breakingTime) {
                this.nowTime = -1;
                this.broken(world, pos, state);
            }
        }

    }

    public void startBreaking(ServerLevel level, BlockState state, BlockPos pos) {
        this.nowTime = 0;
        if (this.breakingTime <= 0)
            this.breakingTime = 1;
        if (this.restoringTime <= 0)
            this.restoringTime = 1;
        SoundEvent sound = SoundEvents.WOOD_HIT;
        float pitch = 0.7f;
        float volume = 0.8f;
        if (displayState != null) {
            sound = displayState.getSoundType().getHitSound();
            pitch = displayState.getSoundType().getPitch();
            volume = displayState.getSoundType().getVolume();
        }
        level.playSound(null, pos, sound, SoundSource.BLOCKS,
                volume, pitch);
        notifyPlayers(level, pos, state, true, false);
        sync();
    }

    private void broken(Level world, BlockPos pos, BlockState state) {
        if (world.isClientSide)
            return;
        if (!(world instanceof ServerLevel serverWorld))
            return;
        this.nowTime = -1;
        notifyPlayers(serverWorld, pos, state, true, true);
        level.setBlock(pos, state.setValue(BreakingBridgeBlock.BROKEN, true), Block.UPDATE_ALL);
        if (state.getBlock() instanceof BreakingBridgeBlock bbb) {
            bbb.startRecovery(state, serverWorld, pos, restoringTime);
        }
        sync();
    }

    public void notifyPlayers(ServerLevel serverWorld, BlockPos pos, BlockState state, boolean isBroken,
            boolean hasSound) {
        SoundEvent sound = isBroken ? SoundEvents.WOOD_BREAK : SoundEvents.WOOD_PLACE;

        float pitch = 0.7f;
        float volume = 0.8f;
        if (displayState != null) {
            sound = isBroken ? displayState.getSoundType().getBreakSound()
                    : displayState.getSoundType().getPlaceSound();
            pitch = displayState.getSoundType().getPitch();
            volume = displayState.getSoundType().getVolume();
            serverWorld.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                    displayState),
                    pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, isBroken ? 10 : 6, 0.3, 0.05, 0.3,
                    0.0);
        } else {
            serverWorld.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                    state),
                    pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 6, 0.3, 0.05, 0.3,
                    0.0);
        }
        if (hasSound) {
            serverWorld.playSound(null, pos, sound, SoundSource.BLOCKS,
                    volume, pitch);
        }
    }

    public void recoveried(ServerLevel level, BlockState state, BlockPos pos, RandomSource random) {
        notifyPlayers(level, pos, state, false, true);
    }

}
