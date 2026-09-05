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

package org.agmas.noellesroles.content.block_entity.scene;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.init.ModSceneBlocks;
import org.agmas.noellesroles.scene.LoopingMirrorLoop;
import org.agmas.noellesroles.scene.LoopingMirrorManager;
import org.jetbrains.annotations.Nullable;

/**
 * 保存绑定在这块循环镜子上的两个平面，并同步给客户端生成后方场景。
 */
public class LoopingMirrorBlockEntity extends BlockEntity {
    private @Nullable LoopingMirrorLoop loop;

    public LoopingMirrorBlockEntity(BlockPos pos, BlockState state) {
        super(ModSceneBlocks.LOOPING_MIRROR_ENTITY, pos, state);
    }

    public @Nullable LoopingMirrorLoop getLoop() {
        return loop;
    }

    public boolean isConfigured() {
        return loop != null;
    }

    public void setLoop(LoopingMirrorLoop loop) {
        this.loop = loop;
        setChanged();
        sync();
    }

    public void clearLoop() {
        this.loop = null;
        setChanged();
        sync();
    }

    public Component describe() {
        if (loop == null) {
            return Component.translatable("message.noellesroles.looping_mirror.not_configured")
                    .withStyle(ChatFormatting.GRAY);
        }
        return Component.translatable("message.noellesroles.looping_mirror.info",
                        loop.planeA().outward().getSerializedName(),
                        loop.planeA().uSize(), loop.planeA().vSize(),
                        loop.planeB().outward().getSerializedName(),
                        loop.planeB().uSize(), loop.planeB().vSize())
                .withStyle(ChatFormatting.AQUA);
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void setLevel(net.minecraft.world.level.Level level) {
        super.setLevel(level);
        if (level instanceof ServerLevel serverLevel && loop != null) {
            LoopingMirrorManager.add(serverLevel, loop);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (loop != null) {
            tag.put("Loop", loop.save());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Loop")) {
            loop = LoopingMirrorLoop.load(tag.getCompound("Loop"));
            if (loop != null) {
                loop = loop.withController(worldPosition);
            }
        } else {
            loop = null;
        }
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }
}
