package org.agmas.noellesroles.content.block_entity;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.modes.repair.RepairModeState;
import org.agmas.noellesroles.init.ModBlocks;
import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class HunterCageBlockEntity extends BlockEntity {
    public static final int MAX_PRISONERS = 2;
    private final List<TrialEntry> prisoners = new ArrayList<>();
    private int rescueProgress;

    public HunterCageBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.HUNTER_CAGE_BLOCK_ENTITY, pos, state);
    }

    public Optional<UUID> getPrisoner() {
        return prisoners.isEmpty() ? Optional.empty() : Optional.of(prisoners.get(0).prisoner);
    }

    public void setPrisoner(UUID prisoner) {
        addPrisoner(prisoner, null);
    }

    public Optional<UUID> getCaptor() {
        return prisoners.isEmpty() || prisoners.get(0).captor == null
                ? Optional.empty()
                : Optional.of(prisoners.get(0).captor);
    }

    public void setCaptor(UUID captor) {
        if (!prisoners.isEmpty()) {
            prisoners.get(0).captor = captor;
            setChangedAndSync();
        }
    }

    public List<TrialEntry> getTrialEntries() {
        return List.copyOf(prisoners);
    }

    public int getProgress(UUID prisoner) {
        return prisoners.stream()
                .filter(entry -> entry.prisoner.equals(prisoner))
                .findFirst()
                .map(entry -> entry.progress)
                .orElse(0);
    }

    public int getRescueProgress() {
        return rescueProgress;
    }

    public boolean addPrisoner(UUID prisoner, UUID captor) {
        if (prisoners.size() >= MAX_PRISONERS
                || prisoners.stream().anyMatch(entry -> entry.prisoner.equals(prisoner))) {
            return false;
        }
        prisoners.add(new TrialEntry(prisoner, captor, 0));
        rescueProgress = 0;
        setChangedAndSync();
        return true;
    }

    public boolean addRescueProgress(int amount) {
        if (prisoners.isEmpty()) {
            return false;
        }
        rescueProgress = Math.min(100, rescueProgress + amount);
        setChangedAndSync();
        if (rescueProgress >= 100) {
            releasePrisoner();
            return true;
        }
        return false;
    }

    public void releasePrisoner() {
        if (level instanceof ServerLevel serverLevel && !prisoners.isEmpty()) {
            TrialEntry entry = prisoners.remove(0);
            if (serverLevel.getPlayerByUUID(entry.prisoner) instanceof ServerPlayer target) {
                RepairModeState.clearRestraints(target);
                target.teleportTo(worldPosition.getX() + 0.5D, worldPosition.getY(), worldPosition.getZ() + 0.5D);
                target.setHealth(Math.min(target.getMaxHealth(), RepairModeState.REVIVE_HEALTH));
            }
        }
        rescueProgress = 0;
        setChangedAndSync();
    }

    public static void tick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
            HunterCageBlockEntity entity) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        boolean changed = entity.prisoners.removeIf(entry ->
                !(serverLevel.getPlayerByUUID(entry.prisoner) instanceof ServerPlayer target)
                        || target.isSpectator()
                        || GameUtils.isPlayerEliminated(target)
                        || !ModComponents.REPAIR_ROLES.get(target).trialStand.present()
                        || !ModComponents.REPAIR_ROLES.get(target).trialStand.toBlockPos().equals(pos));

        for (TrialEntry entry : List.copyOf(entity.prisoners)) {
            if (!(serverLevel.getPlayerByUUID(entry.prisoner) instanceof ServerPlayer target)) {
                continue;
            }
            entry.progress++;
            target.teleportTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
            target.setHealth(Math.max(1.0F, target.getHealth()));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 10, false, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 2, false, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false, true));
            target.addEffect(new MobEffectInstance(ModEffects.NO_COLLIDE, 40, 0, false, false, true));

            if (entry.progress >= RepairModeState.TRIAL_EXECUTION_TICKS) {
                var component = ModComponents.REPAIR_ROLES.get(target);
                component.downed = false;
                component.carriedBy = null;
                component.trialStand = org.agmas.noellesroles.component.RepairRolePlayerComponent.BlockPosTag.NONE;
                component.sync();
                target.removeEffect(ModEffects.NO_COLLIDE);
                GameUtils.forceKillPlayer(target, true,
                        entry.captor != null ? serverLevel.getPlayerByUUID(entry.captor) : null,
                        ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "repair_trial_execution"));
                entity.prisoners.remove(entry);
                changed = true;
            }
        }

        if (changed || level.getGameTime() % 10 == 0) {
            entity.setChangedAndSync();
        }
    }

    private void setChangedAndSync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        ListTag list = new ListTag();
        for (TrialEntry entry : prisoners) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID("Prisoner", entry.prisoner);
            if (entry.captor != null) {
                entryTag.putUUID("Captor", entry.captor);
            }
            entryTag.putInt("Progress", entry.progress);
            list.add(entryTag);
        }
        tag.put("Prisoners", list);
        tag.putInt("RescueProgress", rescueProgress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        prisoners.clear();
        if (tag.contains("Prisoners", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Prisoners", Tag.TAG_COMPOUND);
            for (Tag raw : list) {
                CompoundTag entry = (CompoundTag) raw;
                if (entry.hasUUID("Prisoner")) {
                    prisoners.add(new TrialEntry(entry.getUUID("Prisoner"),
                            entry.hasUUID("Captor") ? entry.getUUID("Captor") : null,
                            entry.getInt("Progress")));
                }
            }
        } else if (tag.hasUUID("Prisoner")) {
            prisoners.add(new TrialEntry(tag.getUUID("Prisoner"),
                    tag.hasUUID("Captor") ? tag.getUUID("Captor") : null, 0));
        }
        rescueProgress = tag.getInt("RescueProgress");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        return saveWithoutMetadata(provider);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public static class TrialEntry {
        public final UUID prisoner;
        public UUID captor;
        public int progress;

        private TrialEntry(UUID prisoner, UUID captor, int progress) {
            this.prisoner = prisoner;
            this.captor = captor;
            this.progress = progress;
        }
    }
}
