package org.agmas.noellesroles.content.entity;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModItems;

public class ThrowingKnifeEntity extends AbstractArrow {

    private ItemStack it = null;

    public ThrowingKnifeEntity(EntityType<? extends AbstractArrow> entityType, Level level) {
        super(entityType, level);
        it = ModItems.THROWING_KNIFE.getDefaultInstance();
        this.setNoGravity(true);
    }

    public ThrowingKnifeEntity(EntityType<? extends AbstractArrow> entityType, LivingEntity livingEntity, Level level,
            ItemStack itemStack) {
        super(entityType, livingEntity, level, itemStack, null);
        it = itemStack.copy();
        this.setNoGravity(true);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && Math.random() < 0.2) {
            level().addParticle(ParticleTypes.CRIT, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
        }
        if (this.tickCount > 20 * 8) {
            this.remove(RemovalReason.DISCARDED);

        }
    }

    @Override
    protected void onHitEntity(EntityHitResult entityHitResult) {
        super.onHitEntity(entityHitResult);
        if (getOwner() == null)
            return;
        if (entityHitResult.getEntity() instanceof ServerPlayer serverPlayer) {
            if (!serverPlayer.getUUID().equals(getOwner().getUUID())) {
                Vec3 location = entityHitResult.getLocation();
                ServerLevel serverLevel = serverPlayer.serverLevel();
                serverLevel.sendParticles(ParticleTypes.CRIT, location.x, location.y + 1.25f, location.z, 10, 0.3, 0.3,
                        0.3, 0.15);
                serverLevel.players().forEach(player -> {
                    serverLevel.playSound(player, location.x, location.y, location.z, SoundEvents.CHAIN_HIT,
                            SoundSource.PLAYERS, 1.0f, 1.0f);
                });
                ResourceLocation deathReason = Noellesroles.id("throwing_knife_hit");
                if (it != null && !it.isEmpty()) {
                    deathReason = BuiltInRegistries.ITEM.getKey(it.getItem());
                    if (deathReason == null) {
                        deathReason = Noellesroles.id("throwing_knife_hit");
                        ;
                    }
                }
                GameUtils.killPlayer(serverPlayer, true, (ServerPlayer) getOwner(),
                        deathReason);
                this.remove(RemovalReason.KILLED);
            }
        }
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return ModItems.THROWING_KNIFE.getDefaultInstance();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compoundTag) {
        super.addAdditionalSaveData(compoundTag);
    }
    // @Override
    // protected Item getDefaultItem() {
    // return ModItems.THROWING_KNIFE;
    // }
}
