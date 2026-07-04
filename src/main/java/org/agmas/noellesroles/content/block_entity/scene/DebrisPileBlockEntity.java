package org.agmas.noellesroles.content.block_entity.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.content.block.scene.DebrisPileBlock;
import org.agmas.noellesroles.init.ModSceneBlocks;
import org.agmas.noellesroles.scene.DebrisPileRegistry;
import org.agmas.noellesroles.scene.SceneEventManager;

import java.util.HashSet;
import java.util.Set;

public class DebrisPileBlockEntity extends BlockEntity {
    private final Set<BlockPos> linked = new HashSet<>();

    public DebrisPileBlockEntity(BlockPos pos, BlockState state) {
        super(ModSceneBlocks.DEBRIS_PILE_ENTITY, pos, state);
    }

    public Set<BlockPos> linked() { return linked; }

    public void addLinked(BlockPos pos) {
        if (!pos.equals(worldPosition)) {
            linked.add(pos.immutable());
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        var list = new net.minecraft.nbt.ListTag();
        for (BlockPos pos : linked) {
            CompoundTag p = new CompoundTag();
            p.putInt("X", pos.getX());
            p.putInt("Y", pos.getY());
            p.putInt("Z", pos.getZ());
            list.add(p);
        }
        tag.put("Linked", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        linked.clear();
        if (tag.contains("Linked", net.minecraft.nbt.Tag.TAG_LIST)) {
            var list = tag.getList("Linked", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag p = list.getCompound(i);
                linked.add(new BlockPos(p.getInt("X"), p.getInt("Y"), p.getInt("Z")));
            }
        }
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel) DebrisPileRegistry.remove(serverLevel, worldPosition);
        super.setRemoved();
    }

    public void extinguish() {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.setBlock(worldPosition,
                    getBlockState().setValue(DebrisPileBlock.ACTIVE, false).setValue(DebrisPileBlock.CLOSED, true),
                    Block.UPDATE_ALL);
            serverLevel.sendParticles(ParticleTypes.CLOUD, worldPosition.getX() + 0.5, worldPosition.getY() + 0.8,
                    worldPosition.getZ() + 0.5, 28, 0.4, 0.3, 0.4, 0.03);
            serverLevel.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DebrisPileBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        DebrisPileRegistry.add(serverLevel, pos);
        boolean sabotage = SceneEventManager.isSabotageActive(serverLevel);
        SceneEventManager.checkAndHandleSabotageTimeout(serverLevel);
        if (sabotage) {
            SceneEventManager.tickSabotageAlarm(serverLevel);
            if (!state.getValue(DebrisPileBlock.CLOSED) && !state.getValue(DebrisPileBlock.ACTIVE)) {
                serverLevel.setBlock(pos, state.setValue(DebrisPileBlock.ACTIVE, true), Block.UPDATE_ALL);
            }
            if (serverLevel.getGameTime() % 5 == 0 && state.getValue(DebrisPileBlock.ACTIVE)) {
                serverLevel.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5,
                        4, 0.35, 0.25, 0.35, 0.02);
            }
        } else if (state.getValue(DebrisPileBlock.ACTIVE) || state.getValue(DebrisPileBlock.CLOSED)) {
            serverLevel.setBlock(pos, state.setValue(DebrisPileBlock.ACTIVE, false).setValue(DebrisPileBlock.CLOSED, false),
                    Block.UPDATE_ALL);
        }
    }

    public void onSelfExtinguished() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (!DebrisPileRegistry.allExtinguished(serverLevel)) return;
        SceneEventManager.stopSabotage(serverLevel);
        for (var player : serverLevel.players()) {
            player.displayClientMessage(Component.translatable("message.noellesroles.debris_pile.all_extinguished"), false);
        }
    }
}
