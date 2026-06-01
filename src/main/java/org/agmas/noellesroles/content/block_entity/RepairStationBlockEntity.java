package org.agmas.noellesroles.content.block_entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.game.modes.repair.RepairModeState;
import org.agmas.noellesroles.init.ModBlocks;
import org.jetbrains.annotations.Nullable;

public class RepairStationBlockEntity extends BlockEntity {
    private int progress;
    private int animationTicks;
    private int jamTicks;
    private int repairBlockedTicks;
    private int blackSmokeTicks;

    public RepairStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.REPAIR_STATION_BLOCK_ENTITY, pos, state);
    }

    public int getProgress() {
        return progress;
    }

    public int getProgressPercent() {
        if (RepairModeState.REPAIR_STATION_MAX_PROGRESS <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(100, (int) Math.round(progress * 100.0 / RepairModeState.REPAIR_STATION_MAX_PROGRESS)));
    }

    public int getAnimationTicks() {
        return animationTicks;
    }

    public int getJamTicks() {
        return jamTicks;
    }

    public int getRepairBlockedTicks() {
        return repairBlockedTicks;
    }

    public boolean isRepairBlocked() {
        return repairBlockedTicks > 0;
    }

    public boolean isJammed() {
        return jamTicks > 0;
    }

    public boolean isCompleted() {
        return progress >= RepairModeState.REPAIR_STATION_MAX_PROGRESS;
    }

    public boolean addProgress(int amount) {
        if (isCompleted() || isRepairBlocked()) {
            return false;
        }
        if (isJammed()) {
            amount = Math.max(1, amount / 2);
        }
        progress = Math.min(RepairModeState.REPAIR_STATION_MAX_PROGRESS, progress + amount);
        animationTicks = 12;
        setChangedAndSync();
        if (level instanceof ServerLevel serverLevel && isCompleted()) {
            RepairModeState.stationCompleted(serverLevel, worldPosition);
        }
        return true;
    }

    public void sabotage(int amount, int durationTicks) {
        if (isCompleted()) {
            return;
        }
        progress = Math.max(0, progress - amount);
        jamTicks = Math.max(jamTicks, durationTicks);
        animationTicks = 20;
        setChangedAndSync();
    }

    public void triggerAccidentFailure(ServerPlayer cause) {
        if (!(level instanceof ServerLevel serverLevel) || isCompleted()) {
            return;
        }
        repairBlockedTicks = Math.max(repairBlockedTicks, 20 * 5);
        blackSmokeTicks = Math.max(blackSmokeTicks, 20 * 5);
        animationTicks = 20;
        serverLevel.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 0.55F);
        for (ServerPlayer player : serverLevel.players()) {
            if (player.distanceToSqr(worldPosition.getCenter()) <= 8.0D * 8.0D) {
                player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 8, 0, false, true, true));
            }
        }
        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                worldPosition.getX() + 0.5D, worldPosition.getY() + 0.9D, worldPosition.getZ() + 0.5D,
                45, 0.45D, 0.5D, 0.45D, 0.04D);
        setChangedAndSync();
    }

    public static void tick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
            RepairStationBlockEntity entity) {
        boolean changed = false;
        if (entity.animationTicks > 0) {
            entity.animationTicks--;
            changed = true;
        }
        if (entity.jamTicks > 0) {
            entity.jamTicks--;
            changed = true;
        }
        if (entity.repairBlockedTicks > 0) {
            entity.repairBlockedTicks--;
            changed = true;
        }
        if (entity.blackSmokeTicks > 0) {
            entity.blackSmokeTicks--;
            changed = true;
            if (level instanceof ServerLevel serverLevel && entity.blackSmokeTicks % 5 == 0) {
                serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                        pos.getX() + 0.5D, pos.getY() + 0.9D, pos.getZ() + 0.5D,
                        5, 0.28D, 0.28D, 0.28D, 0.02D);
                serverLevel.sendParticles(ParticleTypes.SMOKE,
                        pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                        8, 0.35D, 0.35D, 0.35D, 0.03D);
            }
        }
        if (changed) {
            entity.setChangedAndSync();
        }
    }

    public void setChangedAndSync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putInt("Progress", progress);
        tag.putInt("AnimationTicks", animationTicks);
        tag.putInt("JamTicks", jamTicks);
        tag.putInt("RepairBlockedTicks", repairBlockedTicks);
        tag.putInt("BlackSmokeTicks", blackSmokeTicks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        progress = tag.getInt("Progress");
        animationTicks = tag.getInt("AnimationTicks");
        jamTicks = tag.getInt("JamTicks");
        repairBlockedTicks = tag.getInt("RepairBlockedTicks");
        blackSmokeTicks = tag.getInt("BlackSmokeTicks");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        return saveWithoutMetadata(provider);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
