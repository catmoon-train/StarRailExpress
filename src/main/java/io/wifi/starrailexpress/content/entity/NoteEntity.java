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

import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import io.wifi.starrailexpress.util.SRENBTUtils;

import java.util.function.Supplier;

public class NoteEntity extends Entity {
    private static final EntityDataAccessor<Integer> DIRECTION = SynchedEntityData.defineId(NoteEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<Component> LINE1 = SynchedEntityData.defineId(NoteEntity.class,
            EntityDataSerializers.COMPONENT);
    private static final EntityDataAccessor<Component> LINE2 = SynchedEntityData.defineId(NoteEntity.class,
            EntityDataSerializers.COMPONENT);
    private static final EntityDataAccessor<Component> LINE3 = SynchedEntityData.defineId(NoteEntity.class,
            EntityDataSerializers.COMPONENT);
    private static final EntityDataAccessor<Component> LINE4 = SynchedEntityData.defineId(NoteEntity.class,
            EntityDataSerializers.COMPONENT);
    public final int seed;

    public NoteEntity(EntityType<?> type, Level world) {
        super(type, world);
        this.seed = this.random.nextInt();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();

        Supplier<Float> randomGiver = () -> (random.nextFloat() - .5f) * .2f;
        if (random.nextFloat() < .1f) {
            this.level().addParticle(ParticleTypes.WAX_ON, this.getX() + randomGiver.get(),
                    this.getY() + randomGiver.get() + this.getBbHeight() / 2f, this.getZ() + randomGiver.get(), 0, 0,
                    0);
        }
    }

    public Component[] getLines() {
        return new Component[] {
                this.entityData.get(LINE1),
                this.entityData.get(LINE2),
                this.entityData.get(LINE3),
                this.entityData.get(LINE4)
        };
    }

    public void setLines(String @NotNull [] lines) {
        Component[] arr = new Component[lines.length];
        for (int i = 0; i < lines.length; i++) {
            arr[i] = Component.nullToEmpty(lines[i]);
        }
        setLines(arr);
    }

    public void setLines(Component @NotNull [] lines) {
        if (lines.length > 0)
            this.entityData.set(LINE1, lines[0]);
        if (lines.length > 1)
            this.entityData.set(LINE2, lines[1]);
        if (lines.length > 2)
            this.entityData.set(LINE3, lines[2]);
        if (lines.length > 3)
            this.entityData.set(LINE4, lines[3]);
    }

    public @NotNull Direction getDirection() {
        return Direction.values()[this.entityData.get(DIRECTION)];
    }

    public void setDirection(@NotNull Direction direction) {
        this.entityData.set(DIRECTION, direction.get3DDataValue());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        builder.define(DIRECTION, Direction.NORTH.get3DDataValue());
        builder.define(LINE1, Component.empty());
        builder.define(LINE2, Component.empty());
        builder.define(LINE3, Component.empty());
        builder.define(LINE4, Component.empty());
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag nbt) {
        nbt.putInt("Direction", this.entityData.get(DIRECTION));
        SRENBTUtils.writeComponent(nbt, "Text1", this.entityData.get(LINE1), this.registryAccess());
        SRENBTUtils.writeComponent(nbt, "Text2", this.entityData.get(LINE2), this.registryAccess());
        SRENBTUtils.writeComponent(nbt, "Text3", this.entityData.get(LINE3), this.registryAccess());
        SRENBTUtils.writeComponent(nbt, "Text4", this.entityData.get(LINE4), this.registryAccess());
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag nbt) {
        if (nbt.contains("Direction"))
            this.entityData.set(DIRECTION, nbt.getInt("Direction"));

        // 兼容旧版本
        {
            if (nbt.contains("Line1")) {
                String str = nbt.getString("Line1");
                Component message = Component.nullToEmpty(str);
                if (message != null)
                    this.entityData.set(LINE1, message);
            }

            if (nbt.contains("Line2")) {
                String str = nbt.getString("Line2");
                Component message = Component.nullToEmpty(str);
                if (message != null)
                    this.entityData.set(LINE2, message);
            }

            if (nbt.contains("Line3")) {
                String str = nbt.getString("Line3");
                Component message = Component.nullToEmpty(str);
                if (message != null)
                    this.entityData.set(LINE3, message);
            }

            if (nbt.contains("Line4")) {
                String str = nbt.getString("Line4");
                Component message = Component.nullToEmpty(str);
                if (message != null)
                    this.entityData.set(LINE4, message);
            }
        }
        // 新版本读取
        {
            {
                Component message = SRENBTUtils.readComponent(nbt, "Text1", this.registryAccess());
                if (message != null)
                    this.entityData.set(LINE1, message);
            }
            {
                Component message = SRENBTUtils.readComponent(nbt, "Text2", this.registryAccess());
                if (message != null)
                    this.entityData.set(LINE2, message);
            }
            {
                Component message = SRENBTUtils.readComponent(nbt, "Text3", this.registryAccess());
                if (message != null)
                    this.entityData.set(LINE3, message);
            }
            {
                Component message = SRENBTUtils.readComponent(nbt, "Text4", this.registryAccess());
                if (message != null)
                    this.entityData.set(LINE4, message);
            }
        }
    }
}