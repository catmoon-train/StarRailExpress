package io.wifi.starrailexpress.block_entity;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.index.TMMBlockEntities;
import io.wifi.starrailexpress.index.TMMParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class TrimmedBedBlockEntity extends BlockEntity {
    private boolean hasScorpion = false;
    private UUID poisoner;

    public boolean hasScorpion() {
        return hasScorpion;
    }

    public void setHasScorpion(boolean hasScorpion, @Nullable UUID poisoner) {
        this.hasScorpion = hasScorpion;
        this.poisoner = poisoner;
        sync();
    }

    public UUID getPoisoner() {
        return poisoner;
    }

    public TrimmedBedBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static TrimmedBedBlockEntity create(BlockPos pos, BlockState state) {
        return new TrimmedBedBlockEntity(TMMBlockEntities.TRIMMED_BED, pos, state);
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public static <T extends BlockEntity> void clientTick(Level world, BlockPos pos, BlockState state, T t) {
        TrimmedBedBlockEntity entity = (TrimmedBedBlockEntity) t;
        if (!SREClient.isKiller()) return;
        if (!entity.hasScorpion()) return;
        if (world.getRandom().nextIntBetweenInclusive(0, 20) < 17) return;

        world.addParticle(
                TMMParticles.POISON,
                pos.getX() + 0.5f,
                pos.getY() + 0.5f,
                pos.getZ() + 0.5f,
                0f, 0.05f, 0f
        );
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registryLookup) {
        return saveWithoutMetadata(registryLookup);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        nbt.putBoolean("hasScorpion", this.hasScorpion);
        if (this.poisoner != null) nbt.putUUID("poisoner", this.poisoner);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        this.hasScorpion = nbt.getBoolean("hasScorpion");
        this.poisoner = nbt.hasUUID("poisoner") ? nbt.getUUID("poisoner") : null;
    }
}
