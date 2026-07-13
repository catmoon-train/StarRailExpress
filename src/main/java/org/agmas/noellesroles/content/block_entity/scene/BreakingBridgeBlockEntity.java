package org.agmas.noellesroles.content.block_entity.scene;

import org.agmas.noellesroles.init.ModSceneBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class BreakingBridgeBlockEntity extends BlockEntity {
    public int breakingTime = 10;
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
        tag.putInt("breaking_time", breakingTime);
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
            displayState = NbtUtils.readBlockState(this.level.holderLookup(Registries.BLOCK), tag);
        }
        if (tag.contains("breaking_time")) {
            this.breakingTime = tag.getInt("breaking_time");
        }
    }

    public void setDisplayState(BlockState diState) {
        this.displayState = diState;
        var pos = this.getBlockPos();
        this.level.setBlockAndUpdate(pos, this.getBlockState());
    }
}
