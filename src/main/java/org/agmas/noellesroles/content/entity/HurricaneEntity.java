package org.agmas.noellesroles.content.entity;

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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HurricaneEntity extends Entity {
    private static final int DEFAULT_MAX_AGE = 20 * 20;
    private static final double BASE_RADIUS = 1.2D;
    private static final double DEFAULT_HEIGHT = 8.0D;
    private static final DustParticleOptions PARTICLE =
            new DustParticleOptions(new Vector3f(0.72F, 0.86F, 0.95F), 1.35F);

    private final Map<UUID, Integer> caughtPlayers = new HashMap<>();
    private int age;
    private int maxAge = DEFAULT_MAX_AGE;
    private double height = DEFAULT_HEIGHT;
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

    public void setHeight(double height) {
        this.height = Math.clamp(height, 2.0D, 325.0D);
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
        double topSpread = height * 0.28D + 0.5D;
        double catchRadius = Math.max(3.2D, topSpread + 2.0D);
        AABB box = new AABB(center.x - catchRadius, center.y - 0.5D, center.z - catchRadius,
                center.x + catchRadius, center.y + height + 1.0D, center.z + catchRadius);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box, GameUtils::isPlayerAliveAndSurvival)) {
            double dx = player.getX() - getX();
            double dz = player.getZ() - getZ();
            double horiz = Math.sqrt(dx * dx + dz * dz);
            if (horiz < 0.01D) {
                dx = 0.01D;
                dz = 0.0D;
                horiz = 0.01D;
            }
            double y = Math.max(0.0D, player.getY() - getY());
            double visualRadius = 0.5D + y * 0.28D;
            if (horiz > visualRadius + 2.0D) {
                continue;
            }

            int caughtTicks = caughtPlayers.getOrDefault(player.getUUID(), 0) + 1;
            caughtPlayers.put(player.getUUID(), caughtTicks);

            // 径向单位向量（指向外）
            double rx = dx / horiz;
            double rz = dz / horiz;
            // 切向单位向量（顺时针环绕）
            double tx = -rz;
            double tz = rx;

            // 轨道半径（飓风边缘）
            double orbitRadius = Math.max(1.2D, visualRadius * 0.9D);

            // 径向力：拉到轨道半径
            double radialForce = (horiz - orbitRadius) * 0.4D;
            // 切向力：推动环绕
            double orbitSpeed = 1.8D + (1.0D - Mth.clamp(horiz / Math.max(orbitRadius, 1.0D), 0.0D, 1.0D)) * 2.0D;
            double tangentialForce = orbitRadius * orbitSpeed * 0.15D;

            double heightRatio = Mth.clamp(y / height, 0.0D, 1.0D);
            double upwardBase = 0.24D - heightRatio * 0.14D;
            double upwardOscillation = Math.sin(caughtTicks * 0.2D) * 0.05D;
            double upward = upwardBase + upwardOscillation;

            // 合速度 = 径向 + 切向 + 上升
            double vx = -rx * radialForce + tx * tangentialForce;
            double vz = -rz * radialForce + tz * tangentialForce;

            int maxCaughtTicks = (int) Math.clamp(height * 10, 80, 400);
            if (player.getY() >= getY() + height - 0.35D || caughtTicks >= maxCaughtTicks) {
                player.setDeltaMovement(rx * 1.5D, 0.7D, rz * 1.5D);
                caughtPlayers.remove(player.getUUID());
            } else {
                player.setDeltaMovement(vx, upward, vz);
                player.fallDistance = 0.0F;
            }
            player.hurtMarked = true;
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
        caughtPlayers.keySet().removeIf(uuid -> level.getPlayerByUUID(uuid) == null);
    }

    private void spawnParticles(ServerLevel level) {
        int particleCount = (int) Math.clamp(height * 15, 120, 800);
        for (int i = 0; i < particleCount; i++) {
            double h = level.random.nextDouble() * height;
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
        tag.putDouble("Height", height);
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
        height = tag.contains("Height") ? tag.getDouble("Height") : DEFAULT_HEIGHT;
        homeX = tag.getDouble("HomeX");
        homeY = tag.getDouble("HomeY");
        homeZ = tag.getDouble("HomeZ");
        roamRadius = tag.getInt("RoamRadius");
        targetX = tag.getDouble("TargetX");
        targetZ = tag.getDouble("TargetZ");
    }

}
