/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.content.block.entity;

import io.wifi.starrailexpress.index.TMMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HornBlockEntity extends BlockEntity {
    public double prevPull;
    public double pull;
    public int cooldown;

    public HornBlockEntity(BlockPos pos, BlockState state) {
        super(TMMBlockEntities.HORN, pos, state);
    }

    public static void tick(Level world, BlockPos pos, BlockState state, @NotNull HornBlockEntity horn) {
        horn.prevPull = horn.pull;
        if (horn.pull > 0) {
            horn.pull -= .05f;
            if (horn.pull < 0.01f) {
                horn.pull = 0;
                if (world != null) world.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
            }
        }
        horn.cooldown--;
    }

    public void pull(int pull) {
        if (this.cooldown <= 0) this.cooldown = 600; // 30s
        this.pull = pull;
        if (this.level != null)
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        this.setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        nbt.putDouble("pull", this.pull);
        nbt.putInt("cooldown", this.cooldown);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        this.pull = nbt.getDouble("pull");
        this.cooldown = nbt.getInt("cooldown");
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