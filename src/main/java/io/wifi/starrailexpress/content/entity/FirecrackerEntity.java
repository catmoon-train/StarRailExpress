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

package io.wifi.starrailexpress.content.entity;

import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.index.TMMParticles;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;

public class FirecrackerEntity extends Entity {
    /** 皮肤名：鞭炮实体跟随物品皮肤渲染。 */
    private static final EntityDataAccessor<String> SKIN = SynchedEntityData.defineId(FirecrackerEntity.class,
            EntityDataSerializers.STRING);

    public FirecrackerEntity(EntityType<?> type, Level world) {
        super(type, world);
    }

    public String getSkin() {
        return this.entityData.get(SKIN);
    }

    public void setSkin(String skinName) {
        this.entityData.set(SKIN, skinName == null || skinName.isBlank() ? "default" : skinName);
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
                serverWorld.playSound(null, this.blockPosition(), TMMSounds.ITEM_REVOLVER_SHOOT, SoundSource.PLAYERS, 5f, 0.7f + this.getRandom().nextFloat() * .1f - .05f);
                serverWorld.sendParticles(TMMParticles.EXPLOSION, this.getX(), this.getY() + .1f, this.getZ(), 1, 0, 0, 0, 0);
                serverWorld.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + .1f, this.getZ(), 25, 0, 0, 0, .05f);
                this.discard();
            }
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SKIN, "default");
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        if (nbt.contains("Skin")) {
            this.setSkin(nbt.getString("Skin"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        nbt.putString("Skin", this.getSkin());
    }
}
