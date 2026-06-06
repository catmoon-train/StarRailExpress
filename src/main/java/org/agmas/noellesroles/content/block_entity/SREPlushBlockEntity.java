package org.agmas.noellesroles.content.block_entity;

import org.agmas.noellesroles.init.ModBlocks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SREPlushBlockEntity extends BlockEntity {
    public double squash;

    public SREPlushBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.PLUSH_BLOCK_ENTITY, pos, state);
    }

    public static void tick(Level world, BlockPos pos, BlockState state, @NotNull SREPlushBlockEntity spark) {
        if (spark.squash > (double) 0.0F) {
            spark.squash /= (double) 3.0F;
            if (spark.squash < (double) 0.01F) {
                spark.squash = (double) 0.0F;
                if (world != null) {
                    world.sendBlockUpdated(pos, state, state, 2);
                }
            }
        }
    }

    public void squish(int squash) {
        this.squash += (double) squash;
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
        }

        this.setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        nbt.putDouble("squash", this.squash);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        this.squash = nbt.getDouble("squash");
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }
}
