package org.agmas.noellesroles.content.block_entity.scene;

import java.util.UUID;

import org.agmas.noellesroles.content.entity.HurricaneEntity;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.init.ModSceneBlocks;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class HurricaneDeviceBlockEntity extends BlockEntity {
    private UUID hurricaneUuid;
    private int radius = 6;
    private boolean persistent = true;
    private int spawnIntervalSeconds = 20;
    private int durationSeconds = 12;
    private long nextSpawnTick;

    public HurricaneDeviceBlockEntity(BlockPos pos, BlockState state) {
        super(ModSceneBlocks.HURRICANE_DEVICE_ENTITY, pos, state);
    }

    public int getRadius() {
        return Mth.clamp(radius, 1, 64);
    }

    public boolean isPersistent() {
        return persistent;
    }

    public int getSpawnIntervalSeconds() {
        return Mth.clamp(spawnIntervalSeconds, 1, 3600);
    }

    public int getDurationSeconds() {
        return Mth.clamp(durationSeconds, 1, 3600);
    }

    public void setConfig(int radius, boolean persistent, int spawnIntervalSeconds, int durationSeconds) {
        this.radius = Mth.clamp(radius, 1, 64);
        this.persistent = persistent;
        this.spawnIntervalSeconds = Mth.clamp(spawnIntervalSeconds, 1, 3600);
        this.durationSeconds = Mth.clamp(durationSeconds, 1, 3600);
        this.nextSpawnTick = 0;
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HurricaneDeviceBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel) || !GameUtils.isGameStarted) {
            return;
        }

        Entity current = be.hurricaneUuid == null ? null : serverLevel.getEntity(be.hurricaneUuid);
        boolean alive = current instanceof HurricaneEntity && current.isAlive();
        long now = serverLevel.getGameTime();

        if (be.persistent) {
            if (!alive) {
                be.spawnHurricane(serverLevel, 24 * 60 * 60);
            }
            return;
        }

        if (alive) {
            return;
        }
        if (be.nextSpawnTick <= 0) {
            be.nextSpawnTick = now + be.getSpawnIntervalSeconds() * 20L;
        }
        if (now >= be.nextSpawnTick) {
            be.spawnHurricane(serverLevel, be.getDurationSeconds());
            be.nextSpawnTick = now + be.getSpawnIntervalSeconds() * 20L + be.getDurationSeconds() * 20L;
        }
    }

    private void spawnHurricane(ServerLevel serverLevel, int durationSeconds) {
        BlockPos spawn = randomSpawnPos(serverLevel);
        HurricaneEntity hurricane = new HurricaneEntity(ModEntities.HURRICANE, serverLevel);
        hurricane.setPos(spawn.getX() + 0.5D, spawn.getY() + 0.05D, spawn.getZ() + 0.5D);
        hurricane.setMaxAgeSeconds(durationSeconds);
        hurricane.setupRoaming(worldPosition, getRadius());
        serverLevel.addFreshEntity(hurricane);
        hurricaneUuid = hurricane.getUUID();
        setChanged();
    }

    private BlockPos randomSpawnPos(ServerLevel serverLevel) {
        int r = getRadius();
        double angle = serverLevel.random.nextDouble() * Math.PI * 2.0D;
        double dist = Math.sqrt(serverLevel.random.nextDouble()) * r;
        int x = worldPosition.getX() + Mth.floor(Math.cos(angle) * dist);
        int z = worldPosition.getZ() + Mth.floor(Math.sin(angle) * dist);
        return new BlockPos(x, worldPosition.getY() + 1, z);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (hurricaneUuid != null) tag.putUUID("Hurricane", hurricaneUuid);
        tag.putInt("Radius", radius);
        tag.putBoolean("Persistent", persistent);
        tag.putInt("SpawnIntervalSeconds", spawnIntervalSeconds);
        tag.putInt("DurationSeconds", durationSeconds);
        tag.putLong("NextSpawnTick", nextSpawnTick);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        hurricaneUuid = tag.hasUUID("Hurricane") ? tag.getUUID("Hurricane") : null;
        if (tag.contains("Radius")) radius = tag.getInt("Radius");
        if (tag.contains("Persistent")) persistent = tag.getBoolean("Persistent");
        if (tag.contains("SpawnIntervalSeconds")) spawnIntervalSeconds = tag.getInt("SpawnIntervalSeconds");
        if (tag.contains("DurationSeconds")) durationSeconds = tag.getInt("DurationSeconds");
        if (tag.contains("NextSpawnTick")) nextSpawnTick = tag.getLong("NextSpawnTick");
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

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel && hurricaneUuid != null) {
            Entity entity = serverLevel.getEntity(hurricaneUuid);
            if (entity != null) entity.discard();
        }
        super.setRemoved();
    }
}
