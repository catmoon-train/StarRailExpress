package org.agmas.noellesroles.content.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class HurricaneEntity extends Entity {
    private static final int DEFAULT_MAX_AGE = 20 * 20;
    private static final double BASE_RADIUS = 1.2D;
    private static final double HEIGHT = 8.0D;
    private static final DustParticleOptions PARTICLE =
            new DustParticleOptions(new Vector3f(0.72F, 0.86F, 0.95F), 1.35F);

    private final Map<UUID, Integer> caughtPlayers = new HashMap<>();
    private int age;
    private int maxAge = DEFAULT_MAX_AGE;
    private double homeX;
    private double homeY;
    private double homeZ;
    private int roamRadius;
    private double targetX;
    private double targetZ;

    public HurricaneEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public void setMaxAgeSeconds(int seconds) {
        this.maxAge = Math.max(20, seconds * 20);
    }

    public void setupRoaming(BlockPos home, int radius) {
        this.homeX = home.getX() + 0.5D;
        this.homeY = home.getY() + 1.0D;
        this.homeZ = home.getZ() + 0.5D;
        this.roamRadius = Math.max(1, radius);
        pickRoamTarget();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        age++;
        if (age > maxAge) {
            discard();
            return;
        }
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        tickRoaming();
        spawnParticles(serverLevel);
        pullPlayers(serverLevel);
    }

    private void tickRoaming() {
        if (roamRadius <= 0) {
            return;
        }
        if (age % 80 == 1 || distanceToSqr(targetX, getY(), targetZ) < 0.5D) {
            pickRoamTarget();
        }
        Vec3 toTarget = new Vec3(targetX - getX(), 0.0D, targetZ - getZ());
        if (toTarget.lengthSqr() > 0.001D) {
            Vec3 step = toTarget.normalize().scale(0.045D);
            setPos(getX() + step.x, homeY, getZ() + step.z);
        }
    }

    private void pickRoamTarget() {
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double dist = Math.sqrt(random.nextDouble()) * roamRadius;
        this.targetX = homeX + Math.cos(angle) * dist;
        this.targetZ = homeZ + Math.sin(angle) * dist;
    }

    private void pullPlayers(ServerLevel level) {
        Vec3 center = position();
        AABB box = new AABB(center.x - 3.2D, center.y - 0.5D, center.z - 3.2D,
                center.x + 3.2D, center.y + HEIGHT + 1.0D, center.z + 3.2D);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box, GameUtils::isPlayerAliveAndSurvival)) {
            Vec3 relative = player.position().subtract(center);
            double horizontal = Math.sqrt(relative.x * relative.x + relative.z * relative.z);
            double y = Math.max(0.0D, player.getY() - getY());
            double allowedRadius = BASE_RADIUS + y * 0.28D;
            if (horizontal > allowedRadius + 0.8D) {
                continue;
            }

            int caughtTicks = caughtPlayers.getOrDefault(player.getUUID(), 0) + 1;
            caughtPlayers.put(player.getUUID(), caughtTicks);
            double angle = Math.atan2(relative.z, relative.x) + 0.55D;
            double targetRadius = Mth.clamp(horizontal, 0.65D, Math.max(0.85D, allowedRadius));
            double targetX = getX() + Math.cos(angle) * targetRadius;
            double targetZ = getZ() + Math.sin(angle) * targetRadius;
            double upward = caughtTicks < 55 ? 0.24D : 0.12D;
            Vec3 motion = new Vec3((targetX - player.getX()) * 0.28D, upward, (targetZ - player.getZ()) * 0.28D);

            if (player.getY() >= getY() + HEIGHT - 0.35D || caughtTicks >= 80) {
                Vec3 throwDir = new Vec3(Math.cos(angle), 0.35D, Math.sin(angle)).normalize().scale(1.25D);
                player.setDeltaMovement(throwDir.x, 0.7D, throwDir.z);
                caughtPlayers.remove(player.getUUID());
            } else {
                player.setDeltaMovement(motion);
                player.fallDistance = 0.0F;
            }
            player.hurtMarked = true;
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
        caughtPlayers.keySet().removeIf(uuid -> level.getPlayerByUUID(uuid) == null);
    }

    private void spawnParticles(ServerLevel level) {
        for (int i = 0; i < 120; i++) {
            double h = level.random.nextDouble() * HEIGHT;
            double radius = 0.25D + h * 0.28D;
            double angle = age * 0.34D + h * 1.4D + i * 0.42D;
            double x = getX() + Math.cos(angle) * radius + level.random.nextGaussian() * 0.05D;
            double z = getZ() + Math.sin(angle) * radius + level.random.nextGaussian() * 0.05D;
            double y = getY() + h;
            level.sendParticles(PARTICLE, x, y, z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", age);
        tag.putInt("MaxAge", maxAge);
        tag.putDouble("HomeX", homeX);
        tag.putDouble("HomeY", homeY);
        tag.putDouble("HomeZ", homeZ);
        tag.putInt("RoamRadius", roamRadius);
        tag.putDouble("TargetX", targetX);
        tag.putDouble("TargetZ", targetZ);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        age = tag.getInt("Age");
        maxAge = tag.contains("MaxAge") ? tag.getInt("MaxAge") : DEFAULT_MAX_AGE;
        homeX = tag.getDouble("HomeX");
        homeY = tag.getDouble("HomeY");
        homeZ = tag.getDouble("HomeZ");
        roamRadius = tag.getInt("RoamRadius");
        targetX = tag.getDouble("TargetX");
        targetZ = tag.getDouble("TargetZ");
    }

}
