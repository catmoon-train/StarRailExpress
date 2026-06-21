package org.agmas.noellesroles.content.block_entity.scene;

import org.agmas.noellesroles.content.block.scene.ReactorBlock;
import org.agmas.noellesroles.init.ModSceneBlocks;
import org.agmas.noellesroles.scene.ReactorRegistry;
import org.agmas.noellesroles.scene.SceneEventManager;

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
import org.jetbrains.annotations.Nullable;

/**
 * 反应堆方块实体：破坏任务激活时过载，玩家右键打开温度调节小游戏，完成后关闭。
 * 两个反应堆通过绑定工具配对，全部关闭后结束破坏任务。
 */
public class ReactorBlockEntity extends BlockEntity {

    @Nullable
    private BlockPos partnerPos;

    public ReactorBlockEntity(BlockPos pos, BlockState state) {
        super(ModSceneBlocks.REACTOR_ENTITY, pos, state);
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
            ReactorRegistry.remove(serverLevel, this.worldPosition);
        }
        super.setRemoved();
    }

    public boolean isClosed() {
        return getBlockState().getValue(ReactorBlock.CLOSED);
    }

    public boolean isActive() {
        return getBlockState().getValue(ReactorBlock.ACTIVE);
    }

    /** 小游戏完成后关闭反应堆。 */
    public void close() {
        if (this.level instanceof ServerLevel serverLevel) {
            serverLevel.setBlock(this.worldPosition,
                    getBlockState().setValue(ReactorBlock.ACTIVE, false).setValue(ReactorBlock.CLOSED, true),
                    Block.UPDATE_ALL);
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 1.0, this.worldPosition.getZ() + 0.5,
                    25, 0.4, 0.4, 0.4, 0.05);
            serverLevel.playSound(null, this.worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS,
                    1.0F, 1.4F);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ReactorBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ReactorRegistry.add(serverLevel, pos);
        boolean sabotage = SceneEventManager.isSabotageActive(serverLevel);

        // 检查破坏任务是否已自然超时（提前结束不触发）
        SceneEventManager.checkAndHandleSabotageTimeout(serverLevel);

        // 破坏任务激活时循环播放警报音效
        if (sabotage) {
            SceneEventManager.tickSabotageAlarm(serverLevel);
        }

        if (sabotage) {
            if (!state.getValue(ReactorBlock.CLOSED) && !state.getValue(ReactorBlock.ACTIVE)) {
                serverLevel.setBlock(pos, state.setValue(ReactorBlock.ACTIVE, true), Block.UPDATE_ALL);
            }
            if (state.getValue(ReactorBlock.ACTIVE)) {
                if (serverLevel.getGameTime() % 6 == 0) {
                    serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                            pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.05);
                }
                if (serverLevel.getGameTime() % 40 == 0) {
                    serverLevel.playSound(null, pos, SoundEvents.CONDUIT_AMBIENT, SoundSource.BLOCKS, 0.6F, 0.8F);
                }
            }
        } else {
            if (state.getValue(ReactorBlock.ACTIVE) || state.getValue(ReactorBlock.CLOSED)) {
                serverLevel.setBlock(pos,
                        state.setValue(ReactorBlock.ACTIVE, false).setValue(ReactorBlock.CLOSED, false),
                        Block.UPDATE_ALL);
            }
        }
    }

    /** 在一个反应堆被小游戏关闭后调用：检查其配对的反应堆是否也已关闭。 */
    public static void onReactorClosed(ServerLevel level) {
        if (ReactorRegistry.allClosed(level)) {
            SceneEventManager.stopSabotage(level);
            for (var player : level.players()) {
                player.displayClientMessage(Component.translatable("message.noellesroles.reactor.all_closed"), false);
            }
        }
    }

    /** 本反应堆关闭后，检查配对的两个反应堆是否都已关闭（直接从 Chunk 读块状态，不依赖全局注册表）。 */
    public void onSelfClosed() {
        if (!(this.level instanceof ServerLevel serverLevel)) return;
        // 从 Chunk 直接读取块状态，确保不受 BE 缓存影响
        boolean selfClosed = serverLevel.getBlockState(this.worldPosition).getValue(ReactorBlock.CLOSED);
        if (!selfClosed) return;

        // 如果有配对的反应堆，检查它是否也关闭了
        if (partnerPos != null) {
            if (serverLevel.isLoaded(partnerPos)) {
                BlockEntity be = serverLevel.getBlockEntity(partnerPos);
                if (be instanceof ReactorBlockEntity) {
                    boolean partnerClosed = serverLevel.getBlockState(partnerPos).getValue(ReactorBlock.CLOSED);
                    if (!partnerClosed) return;
                } else {
                    // 配对方块不是反应堆，无法判定
                    return;
                }
            } else {
                // 配对方块所在区块未加载
                return;
            }
        }
        // 两个都关闭了（或没有配对，单反应堆场景），结束破坏任务
        SceneEventManager.stopSabotage(serverLevel);
        for (var player : serverLevel.players()) {
            player.displayClientMessage(Component.translatable("message.noellesroles.reactor.all_closed"), false);
        }
    }
}
