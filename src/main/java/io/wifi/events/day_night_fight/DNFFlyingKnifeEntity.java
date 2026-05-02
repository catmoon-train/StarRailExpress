package io.wifi.events.day_night_fight;

import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.agmas.noellesroles.content.entity.ThrowingKnifeEntity;

public class DNFFlyingKnifeEntity extends ThrowingKnifeEntity {
    private Vec3 lastCheckedPosition = null;

    public DNFFlyingKnifeEntity(EntityType<? extends AbstractArrow> entityType, Level level) {
        super(entityType, level);
        setNoPhysics(true);
    }

    public DNFFlyingKnifeEntity(EntityType<? extends AbstractArrow> entityType, LivingEntity owner, Level level,
            ItemStack itemStack) {
        super(entityType, owner, level, itemStack);
        setNoPhysics(true);
    }

    @Override
    protected boolean tryPickup(Player player) {
        return false;
    }

    @Override
    public void tick() {
        Vec3 before = lastCheckedPosition == null ? position() : lastCheckedPosition;
        super.tick();
        Vec3 after = position();
        lastCheckedPosition = after;

        if (level().isClientSide) {
            return;
        }
        if (isBlockedBySolid(before, after)) {
            remove(RemovalReason.DISCARDED);
            return;
        }
        hitPlayerOnPath(before, after);
    }

    private boolean isBlockedBySolid(Vec3 start, Vec3 end) {
        double distance = start.distanceTo(end);
        int steps = Math.max(1, (int) (distance / 0.15));
        for (int i = 1; i <= steps; i++) {
            Vec3 sample = start.lerp(end, i / (double) steps);
            BlockPos pos = BlockPos.containing(sample);
            BlockState state = level().getBlockState(pos);
            if (state.isAir() || state.is(Blocks.IRON_BARS)) {
                continue;
            }
            VoxelShape shape = state.getCollisionShape(level(), pos);
            if (!shape.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onHitEntity(EntityHitResult entityHitResult) {
        return;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    private void hitPlayerOnPath(Vec3 start, Vec3 end) {
        if (!(getOwner() instanceof ServerPlayer owner) || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        for (ServerPlayer target : serverLevel.players()) {
            if (target == owner || !GameUtils.isPlayerAliveAndSurvival(target)) {
                continue;
            }
            if (target.getBoundingBox().inflate(0.5).clip(start, end).isPresent()) {
                Vec3 location = target.getEyePosition();
                serverLevel.sendParticles(ParticleTypes.CRIT, location.x, location.y, location.z, 14, 0.25, 0.25,
                        0.25, 0.2);
                serverLevel.playSound(null, location.x, location.y, location.z, TMMSounds.ITEM_KNIFE_STAB,
                        SoundSource.PLAYERS, 1.0f, 1.0f);
                serverLevel.playSound(null, location.x, location.y, location.z, SoundEvents.CHAIN_HIT,
                        SoundSource.PLAYERS, 0.8f, 1.35f);
                GameUtils.killPlayer(target, true, owner, DNFRoles.FLYING_KNIFE_DEATH);
                remove(RemovalReason.KILLED);
                return;
            }
        }
    }
}
