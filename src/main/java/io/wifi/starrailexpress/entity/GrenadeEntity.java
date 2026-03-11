package io.wifi.starrailexpress.entity;

import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameFunctions;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMParticles;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class GrenadeEntity extends ThrowableItemProjectile {
    private static final float EXPLOSION_RADIUS = 3f;

    public GrenadeEntity(EntityType<?> ignored, Level world) {
        super(TMMEntities.GRENADE, world);
    }

    @Override
    protected Item getDefaultItem() {
        return TMMItems.THROWN_GRENADE;
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (this.level() instanceof ServerLevel world) {
            // Consider sending this in one payload to reduce packets sent - SkyNotTheLimit
            world.playSound(null, this.blockPosition(), TMMSounds.ITEM_GRENADE_EXPLODE, SoundSource.PLAYERS, 5f, 1f + this.getRandom().nextFloat() * .1f - .05f);
            world.sendParticles(TMMParticles.BIG_EXPLOSION, this.getX(), this.getY() + .1f, this.getZ(), 1, 0, 0, 0, 0);
            world.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + .1f, this.getZ(), 100, 0, 0, 0, .2f);
            world.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, this.getDefaultItem().getDefaultInstance()), this.getX(), this.getY() + .1f, this.getZ(), 100, 0, 0, 0, 1f);

            Vec3 explosionPos = this.position().add(0.0D, 0.5D, 0.0D);
            for (ServerPlayer player : world.getPlayers(serverPlayerEntity ->
                    this.getBoundingBox().inflate(EXPLOSION_RADIUS).contains(serverPlayerEntity.position()) &&
                            GameFunctions.isPlayerAliveAndSurvival(serverPlayerEntity))) {
                if (hasExplosionExposure(explosionPos, player)) {
                    GameFunctions.killPlayer(player, true, this.getOwner() instanceof Player playerEntity ? playerEntity : null, GameConstants.DeathReasons.GRENADE);
                }
            }

            this.discard();
        }
    }

    private boolean hasExplosionExposure(Vec3 explosionPos, ServerPlayer target) {
        return Explosion.getSeenPercent(explosionPos, target) > 0.0F;
    }
}
