package io.wifi.starrailexpress.content.block.entity;

import io.wifi.starrailexpress.index.TMMBlockEntities;
import io.wifi.starrailexpress.util.SRENBTUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class RemoteRedstoneBlockEntity extends BlockEntity {
    @Nullable
    private BlockPos pos = null;

    public void setTargetBlockPos(BlockPos target) {
        pos = target;
        this.setChanged();
    }

    public BlockPos getTargetBlockPos() {
        return pos;
    }

    public RemoteRedstoneBlockEntity(BlockPos pos, BlockState state) {
        super(TMMBlockEntities.REMOTE_REDSTONE, pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag compoundTag, HolderLookup.Provider provider) {
        super.saveAdditional(compoundTag, provider);
        compoundTag.put("target", SRENBTUtils.blockPosToTag(pos));
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);
        if (nbt.contains("target")) {
            pos = SRENBTUtils.tagToBlockPos(nbt.getCompound("target"));
        } else {
            pos = null;
        }
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
}
