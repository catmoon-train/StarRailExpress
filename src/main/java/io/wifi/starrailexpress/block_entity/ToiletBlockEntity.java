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

public class ToiletBlockEntity extends BlockEntity {
    private boolean hasPoison = false;
    private UUID poisoner;
    private UUID playerUUID;
    private long satTime;
    private boolean messageShown = false;

    public boolean hasPoison() {
        return hasPoison;
    }

    public void setHasPoison(boolean hasPoison, @Nullable UUID poisoner) {
        this.hasPoison = hasPoison;
        this.poisoner = poisoner;
        sync();
    }

    public UUID getPoisoner() {
        return poisoner;
    }

    public void playerSat(UUID playerUUID) {
        this.playerUUID = playerUUID;
        this.satTime = System.currentTimeMillis();
        this.messageShown = false;
        sync();
    }

    public boolean shouldShowMessage() {
        if (messageShown) return false;
        if (playerUUID == null) return false;
        // 检查是否已经过了6秒
        long elapsed = System.currentTimeMillis() - satTime;
        return elapsed >= 6000; // 6秒 = 6000毫秒
    }

    public void markMessageShown() {
        this.messageShown = true;
        sync();
    }

    public void reset() {
        this.hasPoison = false;
        this.poisoner = null;
        this.playerUUID = null;
        this.satTime = 0;
        this.messageShown = false;
        sync();
    }

    public ToiletBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static ToiletBlockEntity create(BlockPos pos, BlockState state) {
        return new ToiletBlockEntity(TMMBlockEntities.TOILET, pos, state);
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // 客户端毒药粒子效果（只有杀手能看到）
    public static <T extends BlockEntity> void clientTick(Level world, BlockPos pos, BlockState state, T t) {
        ToiletBlockEntity entity = (ToiletBlockEntity) t;
        if (!SREClient.isKiller()) return;
        if (!entity.hasPoison()) return;
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
        nbt.putBoolean("hasPoison", this.hasPoison);
        if (this.poisoner != null) nbt.putUUID("poisoner", this.poisoner);
        if (this.playerUUID != null) nbt.putUUID("playerUUID", this.playerUUID);
        nbt.putLong("satTime", this.satTime);
        nbt.putBoolean("messageShown", this.messageShown);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        this.hasPoison = nbt.getBoolean("hasPoison");
        this.poisoner = nbt.hasUUID("poisoner") ? nbt.getUUID("poisoner") : null;
        this.playerUUID = nbt.hasUUID("playerUUID") ? nbt.getUUID("playerUUID") : null;
        this.satTime = nbt.getLong("satTime");
        this.messageShown = nbt.getBoolean("messageShown");
    }
}
