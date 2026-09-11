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

package net.exmo.sre.planecrash;

import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 飞机坠落事件实体：沿固定航线飞向地图中心，抵达后播放坠毁并消失。
 */
public class CrashPlaneEntity extends Entity {
    public static final int DEFAULT_FLIGHT_TICKS = 150;
    private static final double CRASH_DISTANCE_SQR = 36.0D;

    private double targetX;
    private double targetY;
    private double targetZ;
    private float tiltYaw;
    private double speed = 1.05D;
    private int maxAge = DEFAULT_FLIGHT_TICKS + 40;

    public CrashPlaneEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.noCulling = true;
    }

    public void setupFlight(Vec3 spawn, Vec3 target, float tiltYaw, double speed) {
        this.setPos(spawn);
        this.targetX = target.x;
        this.targetY = target.y;
        this.targetZ = target.z;
        this.tiltYaw = tiltYaw;
        this.speed = Math.max(0.35D, speed);
        aimAtTarget();
    }

    public float tiltYaw() {
        return tiltYaw;
    }

    public Vec3 targetPos() {
        return new Vec3(targetX, targetY, targetZ);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            tickClient();
            return;
        }
        tickServer();
    }

    private void tickServer() {
        if (tickCount > maxAge) {
            crash((ServerLevel) level());
            return;
        }
        Vec3 toTarget = targetPos().subtract(position());
        if (toTarget.lengthSqr() <= CRASH_DISTANCE_SQR) {
            crash((ServerLevel) level());
            return;
        }
        Vec3 step = toTarget.normalize().scale(speed);
        setDeltaMovement(step);
        setPos(getX() + step.x, getY() + step.y, getZ() + step.z);
        aimAtTarget();
    }

    private void tickClient() {
        Vec3 back = getViewVector(1.0F).scale(-1.4D);
        for (int i = 0; i < 3; i++) {
            double ox = (random.nextDouble() - 0.5D) * 1.6D;
            double oy = (random.nextDouble() - 0.5D) * 0.7D;
            double oz = (random.nextDouble() - 0.5D) * 1.6D;
            level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    getX() + back.x + ox, getY() + oy, getZ() + back.z + oz,
                    0.0D, 0.02D, 0.0D);
        }
        if (tickCount % 8 == 0) {
            level().playLocalSound(getX(), getY(), getZ(), TMMSounds.EVENT_PLANE_CRASH_ENGINE,
                    SoundSource.WEATHER, 3.2F, 0.72F + random.nextFloat() * 0.08F, false);
        }
    }

    private void aimAtTarget() {
        Vec3 delta = targetPos().subtract(position());
        double horiz = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        setYRot((float) (Mth.atan2(delta.z, delta.x) * (180.0D / Math.PI)) - 90.0F);
        setXRot((float) (-(Mth.atan2(delta.y, horiz) * (180.0D / Math.PI))));
        yRotO = getYRot();
        xRotO = getXRot();
    }

    private void crash(ServerLevel level) {
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY(), getZ(), 2, 1.5D, 0.8D, 1.5D, 0.0D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY(), getZ(), 40, 2.5D, 1.2D, 2.5D, 0.08D);
        level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, getX(), getY(), getZ(), 24, 1.8D, 1.0D, 1.8D, 0.02D);
        level.playSound(null, getX(), getY(), getZ(), TMMSounds.EVENT_PLANE_CRASH_CRASH,
                SoundSource.WEATHER, 6.0F, 0.75F);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.WEATHER, 4.0F, 0.55F);
        PlaneCrashManager.onPlaneCrashed(level, tiltYaw);
        discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 512.0D * 512.0D;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putDouble("TargetX", targetX);
        tag.putDouble("TargetY", targetY);
        tag.putDouble("TargetZ", targetZ);
        tag.putFloat("TiltYaw", tiltYaw);
        tag.putDouble("Speed", speed);
        tag.putInt("MaxAge", maxAge);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        targetX = tag.getDouble("TargetX");
        targetY = tag.getDouble("TargetY");
        targetZ = tag.getDouble("TargetZ");
        tiltYaw = tag.getFloat("TiltYaw");
        speed = tag.contains("Speed") ? tag.getDouble("Speed") : 1.05D;
        maxAge = tag.contains("MaxAge") ? tag.getInt("MaxAge") : DEFAULT_FLIGHT_TICKS + 40;
    }
}
