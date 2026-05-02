package io.wifi.events.day_night_fight.entity;

import io.wifi.events.day_night_fight.block.DNFTaskPointBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class DNFTaskPointEntity extends Entity {
    private BlockPos sourcePos = BlockPos.ZERO;

    public DNFTaskPointEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
    }

    public static void spawnForBlock(ServerLevel level, BlockPos pos) {
        boolean exists = !level.getEntitiesOfClass(DNFTaskPointEntity.class, new AABB(pos).inflate(1.5),
                entity -> entity.isForBlock(pos)).isEmpty();
        if (exists) {
            return;
        }
        DNFTaskPointEntity entity = new DNFTaskPointEntity(DNFEntities.TASK_POINT, level);
        entity.setSourcePos(pos);
        entity.setPos(pos.getX() + 0.5, pos.getY() + 1.15, pos.getZ() + 0.5);
        level.addFreshEntity(entity);
    }

    public boolean isForBlock(BlockPos pos) {
        return sourcePos.equals(pos);
    }

    public void setSourcePos(BlockPos sourcePos) {
        this.sourcePos = sourcePos.immutable();
    }

    public BlockPos getSourcePos() {
        return sourcePos;
    }

    @Override
    public void tick() {
        super.tick();
        this.noPhysics = true;
        this.setDeltaMovement(Vec3.ZERO);
        if (!level().isClientSide) {
            if (!(level().getBlockState(sourcePos).getBlock() instanceof DNFTaskPointBlock)) {
                discard();
                return;
            }
            setPos(sourcePos.getX() + 0.5, sourcePos.getY() + 1.15, sourcePos.getZ() + 0.5);
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("SourcePos")) {
            sourcePos = BlockPos.of(tag.getLong("SourcePos"));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong("SourcePos", sourcePos.asLong());
    }
}
