package org.agmas.noellesroles.content.block_entity.scene;

import java.util.Comparator;
import java.util.UUID;

import org.agmas.noellesroles.content.entity.MummyEntity;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.init.ModSceneBlocks;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class CoffinBlockEntity extends BlockEntity {
    private static final int DWELL_TICKS = 5 * 20;
    private static final int COOLDOWN_TICKS = 30 * 20;
    private static final double PLAYER_RADIUS = 3.0D;
    private static final double SPAWN_RADIUS = 4.0D;

    private UUID mummyUuid;
    private int nearbyTicks;
    private int cooldownTicks;

    public CoffinBlockEntity(BlockPos pos, BlockState state) {
        super(ModSceneBlocks.COFFIN_ENTITY, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CoffinBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel) || !GameUtils.isGameStarted) {
            be.nearbyTicks = 0;
            return;
        }

        Entity mummy = be.mummyUuid == null ? null : serverLevel.getEntity(be.mummyUuid);
        if (!(mummy instanceof MummyEntity) || !mummy.isAlive()) {
            be.mummyUuid = null;
        }
        if (be.cooldownTicks > 0) {
            be.cooldownTicks--;
            return;
        }
        if (be.mummyUuid != null) {
            return;
        }

        Vec3 center = Vec3.atCenterOf(pos);
        boolean playerNearby = serverLevel.getEntitiesOfClass(ServerPlayer.class,
                new AABB(center.x - PLAYER_RADIUS, center.y - PLAYER_RADIUS, center.z - PLAYER_RADIUS,
                        center.x + PLAYER_RADIUS, center.y + PLAYER_RADIUS, center.z + PLAYER_RADIUS),
                p -> p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE && GameUtils.isPlayerAliveAndSurvival(p)
                        && p.distanceToSqr(center) <= PLAYER_RADIUS * PLAYER_RADIUS).size() > 0;
        be.nearbyTicks = playerNearby ? be.nearbyTicks + 1 : 0;
        if (be.nearbyTicks >= DWELL_TICKS) {
            be.spawnMummy(serverLevel);
            be.nearbyTicks = 0;
            be.cooldownTicks = COOLDOWN_TICKS;
            be.setChanged();
        }
    }

    private void spawnMummy(ServerLevel serverLevel) {
        findSpawn(serverLevel).ifPresent(spawn -> {
            MummyEntity mummy = new MummyEntity(ModEntities.MUMMY, serverLevel);
            mummy.setHome(worldPosition);
            mummy.moveTo(spawn.getX() + 0.5D, spawn.getY() + 1.0D, spawn.getZ() + 0.5D, serverLevel.random.nextFloat() * 360.0F, 0.0F);
            serverLevel.addFreshEntity(mummy);
            mummyUuid = mummy.getUUID();
        });
    }

    private java.util.Optional<BlockPos> findSpawn(ServerLevel serverLevel) {
        return BlockPos.betweenClosedStream(worldPosition.offset(-4, -4, -4), worldPosition.offset(4, 4, 4))
                .map(BlockPos::immutable)
                .filter(pos -> pos.distSqr(worldPosition) <= SPAWN_RADIUS * SPAWN_RADIUS)
                .filter(pos -> serverLevel.getBlockState(pos).isFaceSturdy(serverLevel, pos, net.minecraft.core.Direction.UP))
                .filter(pos -> serverLevel.getBlockState(pos.above()).getCollisionShape(serverLevel, pos.above()).isEmpty())
                .filter(pos -> serverLevel.getBlockState(pos.above(2)).getCollisionShape(serverLevel, pos.above(2)).isEmpty())
                .sorted(Comparator.comparingDouble(p -> p.distSqr(worldPosition)))
                .findFirst();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (mummyUuid != null) tag.putUUID("Mummy", mummyUuid);
        tag.putInt("NearbyTicks", nearbyTicks);
        tag.putInt("CooldownTicks", cooldownTicks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mummyUuid = tag.hasUUID("Mummy") ? tag.getUUID("Mummy") : null;
        nearbyTicks = tag.getInt("NearbyTicks");
        cooldownTicks = tag.getInt("CooldownTicks");
    }
}
