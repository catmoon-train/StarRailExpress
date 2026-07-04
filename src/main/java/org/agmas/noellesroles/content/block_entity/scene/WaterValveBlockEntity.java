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
import org.agmas.noellesroles.content.block.scene.WaterValveBlock;
import org.agmas.noellesroles.init.ModSceneBlocks;
import org.agmas.noellesroles.scene.SceneEventManager;
import org.agmas.noellesroles.scene.WaterValveRegistry;
import org.jetbrains.annotations.Nullable;

/**
 * 水阀方块实体：破坏任务激活时漏水，玩家右键打开关闭水阀小游戏，完成后关闭。
 * 两个水阀通过绑定工具配对，全部关闭后结束破坏任务。
 */
public class WaterValveBlockEntity extends BlockEntity {

    @Nullable
    private BlockPos partnerPos;

    public WaterValveBlockEntity(BlockPos pos, BlockState state) {
        super(ModSceneBlocks.WATER_VALVE_ENTITY, pos, state);
    }

    @Nullable
    public BlockPos getPartnerPos() { return partnerPos; }
    public void setPartnerPos(@Nullable BlockPos pos) { this.partnerPos = pos; setChanged(); }
    public boolean hasPartner() { return partnerPos != null; }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (partnerPos != null) {
            tag.putInt("PartnerX", partnerPos.getX());
            tag.putInt("PartnerY", partnerPos.getY());
            tag.putInt("PartnerZ", partnerPos.getZ());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("PartnerX")) {
            partnerPos = new BlockPos(tag.getInt("PartnerX"), tag.getInt("PartnerY"), tag.getInt("PartnerZ"));
        }
    }

    @Override
    public void setRemoved() {
        if (this.level instanceof ServerLevel serverLevel) {
            WaterValveRegistry.remove(serverLevel, this.worldPosition);
        }
        super.setRemoved();
    }

    public boolean isClosed() {
        return getBlockState().getValue(WaterValveBlock.CLOSED);
    }

    public boolean isActive() {
        return getBlockState().getValue(WaterValveBlock.ACTIVE);
    }

    /** 小游戏完成后关闭水阀。 */
    public void close() {
        if (this.level instanceof ServerLevel serverLevel) {
            serverLevel.setBlock(this.worldPosition,
                    getBlockState().setValue(WaterValveBlock.ACTIVE, false).setValue(WaterValveBlock.CLOSED, true),
                    Block.UPDATE_ALL);
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 1.0, this.worldPosition.getZ() + 0.5,
                    25, 0.4, 0.4, 0.4, 0.05);
            serverLevel.playSound(null, this.worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS,
                    1.0F, 1.4F);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WaterValveBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        WaterValveRegistry.add(serverLevel, pos);
        boolean sabotage = SceneEventManager.isSabotageActive(serverLevel);

        if (sabotage) {
            if (!state.getValue(WaterValveBlock.CLOSED) && !state.getValue(WaterValveBlock.ACTIVE)) {
                serverLevel.setBlock(pos, state.setValue(WaterValveBlock.ACTIVE, true), Block.UPDATE_ALL);
            }
            if (state.getValue(WaterValveBlock.ACTIVE)) {
                // 水阀漏水粒子特效
                if (serverLevel.getGameTime() % 4 == 0) {
                    serverLevel.sendParticles(ParticleTypes.FALLING_WATER,
                            pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 3, 0.25, 0.1, 0.25, 0.02);
                    serverLevel.sendParticles(ParticleTypes.SPLASH,
                            pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, 1, 0.35, 0.05, 0.35, 0.01);
                }
                if (serverLevel.getGameTime() % 30 == 0) {
                    serverLevel.playSound(null, pos, SoundEvents.WATER_AMBIENT, SoundSource.BLOCKS, 0.4F, 0.7F);
                }
            }
        } else {
            if (state.getValue(WaterValveBlock.ACTIVE) || state.getValue(WaterValveBlock.CLOSED)) {
                serverLevel.setBlock(pos,
                        state.setValue(WaterValveBlock.ACTIVE, false).setValue(WaterValveBlock.CLOSED, false),
                        Block.UPDATE_ALL);
            }
        }
    }

    /** 本水阀关闭后，检查配对的两个水阀是否都已关闭。 */
    public void onSelfClosed() {
        if (!(this.level instanceof ServerLevel serverLevel)) return;
        if (!isClosed()) return;
        if (!isPartnerClosed(serverLevel)) return;
        if (!WaterValveRegistry.allClosed(serverLevel)) return;
        SceneEventManager.stopSabotage(serverLevel);
        for (var player : serverLevel.players()) {
            player.displayClientMessage(Component.translatable("message.noellesroles.water_valve.all_closed"), false);
        }
    }

    private boolean isPartnerClosed(ServerLevel level) {
        if (partnerPos == null) return true;
        if (!level.isLoaded(partnerPos)) return false;
        BlockState state = level.getBlockState(partnerPos);
        return state.getBlock() instanceof WaterValveBlock
                && state.hasProperty(WaterValveBlock.CLOSED)
                && state.getValue(WaterValveBlock.CLOSED);
    }
}
