package org.agmas.noellesroles.content.entity;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.no_water_influenced.NoHeavyWaterInfluencedThrowableItemProjectile;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.roles.killer.spellbreaker.SpellbreakerPlayerComponent;
import org.agmas.noellesroles.init.ModItems;

public class SilenceTotemEntity extends NoHeavyWaterInfluencedThrowableItemProjectile {
    private static final int MAX_LIFETIME = 30 * 20;
    private static final double RADIUS = 8.0D;
    private static final int EFFECT_REFRESH_DURATION = 3 * 20;

    private int lifetime = 0;
    private boolean landed = false;

    public SilenceTotemEntity(EntityType<? extends NoHeavyWaterInfluencedThrowableItemProjectile> entityType, Level level) {
        super(entityType, level);
        this.setInvisible(true);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.SILENCE_TOTEM;
    }

    @Override
    protected void onHit(HitResult hitResult) {
        if (!landed) {
            landed = true;
            setDeltaMovement(Vec3.ZERO);
            setNoGravity(true);
            setInvisible(true);
            if (level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, blockPosition(), SoundEvents.SCULK_CLICKING,
                        SoundSource.PLAYERS, 0.8F, 0.65F);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        setInvisible(true);

        if (!level().isClientSide) {
            if (landed) {
                setDeltaMovement(Vec3.ZERO);
            }

            lifetime++;
            if (lifetime >= MAX_LIFETIME) {
                discard();
                return;
            }

            if (landed && lifetime % 10 == 0 && level() instanceof ServerLevel serverLevel) {
                applyAura(serverLevel);
            }
        }
    }

    private void applyAura(ServerLevel serverLevel) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverLevel);
        AABB area = getBoundingBox().inflate(RADIUS);
        for (ServerPlayer target : serverLevel.getEntitiesOfClass(ServerPlayer.class, area,
                GameUtils::isPlayerAliveAndSurvival)) {
            if (target.distanceToSqr(this) > RADIUS * RADIUS
                    || !SpellbreakerPlayerComponent.isNonKiller(target, gameWorld)) {
                continue;
            }

            target.serverLevel().sendParticles(ParticleTypes.SMOKE, target.getX(), target.getY()+1, target.getZ(),5,0.2,0.2,0.2,0.35);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    EFFECT_REFRESH_DURATION, 1, false, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,
                    EFFECT_REFRESH_DURATION, 0, false, false, true));
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        clearOwnerTotem();
        super.remove(reason);
    }

    private void clearOwnerTotem() {
        Entity owner = getOwner();
        if (owner instanceof ServerPlayer player) {
            SpellbreakerPlayerComponent.KEY.get(player).clearActiveTotem(getUUID());
        }
    }
}
