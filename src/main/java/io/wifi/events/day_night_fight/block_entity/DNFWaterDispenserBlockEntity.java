package io.wifi.events.day_night_fight.block_entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class DNFWaterDispenserBlockEntity extends BlockEntity {
    private String poisoner;

    public DNFWaterDispenserBlockEntity(BlockPos pos, BlockState state) {
        super(DNFBlockEntities.WATER_DISPENSER, pos, state);
    }

    public String getPoisoner() {
        return poisoner;
    }

    public boolean isPoisoned() {
        return poisoner != null;
    }

    public void setPoisoner(@Nullable String poisoner) {
        this.poisoner = poisoner;
        sync();
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (poisoner != null) {
            tag.putString("Poisoner", poisoner);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        poisoner = tag.contains("Poisoner") ? tag.getString("Poisoner") : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
