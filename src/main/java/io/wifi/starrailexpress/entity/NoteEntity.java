package io.wifi.starrailexpress.entity;

import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public class NoteEntity extends Entity {
    private static final EntityDataAccessor<Integer> DIRECTION = SynchedEntityData.defineId(NoteEntity.class,
            EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> LINE1 = SynchedEntityData.defineId(NoteEntity.class,
            EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> LINE2 = SynchedEntityData.defineId(NoteEntity.class,
            EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> LINE3 = SynchedEntityData.defineId(NoteEntity.class,
            EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> LINE4 = SynchedEntityData.defineId(NoteEntity.class,
            EntityDataSerializers.STRING);
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

    public String[] getLines() {
        return new String[] {
                this.entityData.get(LINE1),
                this.entityData.get(LINE2),
                this.entityData.get(LINE3),
                this.entityData.get(LINE4)
        };
    }

    public void setLines(String @NotNull [] lines) {
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
        builder.define(LINE1, "");
        builder.define(LINE2, "");
        builder.define(LINE3, "");
        builder.define(LINE4, "");
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag nbt) {
        nbt.putInt("Direction", this.entityData.get(DIRECTION));
        nbt.putString("Line1", this.entityData.get(LINE1));
        nbt.putString("Line2", this.entityData.get(LINE2));
        nbt.putString("Line3", this.entityData.get(LINE3));
        nbt.putString("Line4", this.entityData.get(LINE4));
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag nbt) {
        if (nbt.contains("Direction"))
            this.entityData.set(DIRECTION, nbt.getInt("Direction"));
        if (nbt.contains("Line1"))
            this.entityData.set(LINE1, nbt.getString("Line1"));
        if (nbt.contains("Line2"))
            this.entityData.set(LINE2, nbt.getString("Line2"));
        if (nbt.contains("Line3"))
            this.entityData.set(LINE3, nbt.getString("Line3"));
        if (nbt.contains("Line4"))
            this.entityData.set(LINE4, nbt.getString("Line4"));
    }
}