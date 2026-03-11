package io.wifi.starrailexpress.entity;

import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.index.TMMParticles;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;

public class FirecrackerEntity extends Entity {
    public FirecrackerEntity(EntityType<?> type, Level world) {
        super(type, world);
    }

    @Override
    public void tick() {
        super.tick();
        double angle = Math.toRadians(this.getYRot() + 110);
        Vector3d particlePos = new Vector3d(Math.cos(angle), .1f, Math.sin(angle)).mul(0.3f);
        if (!(this.level() instanceof ServerLevel serverWorld)) {
            if (this.tickCount % 5 == 0)
                this.level().addParticle(ParticleTypes.SMOKE, this.getX() + particlePos.x(), this.getY() + particlePos.y(), this.getZ() + particlePos.z(), 0, 0, 0);
        } else {
            if (this.tickCount >= GameConstants.getFirecrackerTimer()) {
                serverWorld.playSound(null, this.blockPosition(), TMMSounds.ITEM_REVOLVER_SHOOT, SoundSource.PLAYERS, 5f, 1f + this.getRandom().nextFloat() * .1f - .05f);
                serverWorld.sendParticles(TMMParticles.EXPLOSION, this.getX(), this.getY() + .1f, this.getZ(), 1, 0, 0, 0, 0);
                serverWorld.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + .1f, this.getZ(), 25, 0, 0, 0, .05f);
                this.discard();
            }
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {

    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {

    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {

    }
}
