package io.wifi.starrailexpress.entity;

import java.util.ArrayList;
import java.util.List;

import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMParticles;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class GrenadeEntity extends ThrowableItemProjectile {
    private static final float EXPLOSION_RADIUS = 4f;

    public GrenadeEntity(EntityType<?> ignored, Level world) {
        super(TMMEntities.GRENADE, world);
    }

    @Override
    protected Item getDefaultItem() {
        return TMMItems.GRENADE;
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (this.level() instanceof ServerLevel world) {
            // Consider sending this in one payload to reduce packets sent - SkyNotTheLimit
            world.playSound(null, this.blockPosition(), TMMSounds.ITEM_GRENADE_EXPLODE, SoundSource.PLAYERS, 5f,
                    1f + this.getRandom().nextFloat() * .1f - .05f);
            world.sendParticles(TMMParticles.BIG_EXPLOSION, this.getX(), this.getY() + .1f, this.getZ(), 1, 0, 0, 0, 0);
            world.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + .1f, this.getZ(), 100, 0, 0, 0, .2f);
            world.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, this.getDefaultItem().getDefaultInstance()),
                    this.getX(), this.getY() + .1f, this.getZ(), 100, 0, 0, 0, 1f);

            Vec3 explosionPos = this.position().add(0.0D, 0.5D, 0.0D);
            var hitted_players = getPlayersAffectedByExplosion(world, explosionPos.x, explosionPos.y, explosionPos.z,
                    EXPLOSION_RADIUS);
            for (Player player : hitted_players) {
                GameUtils.killPlayer(player, true,
                        this.getOwner() instanceof Player playerEntity ? playerEntity : null,
                        GameConstants.DeathReasons.GRENADE);
            }
            this.discard();
        }
    }

    public static ArrayList<Player> getPlayersAffectedByExplosion(Level level, double x, double y, double z,
            float radius) {
        float diameter = radius;
        int minX = Mth.floor(x - diameter - 1.0F);
        int maxX = Mth.floor(x + diameter + 1.0F);
        int minY = Mth.floor(y - diameter - 1.0F);
        int maxY = Mth.floor(y + diameter + 1.0F);
        int minZ = Mth.floor(z - diameter - 1.0F);
        int maxZ = Mth.floor(z + diameter + 1.0F);

        List<Entity> candidates = level.getEntities(
                null,
                new AABB(minX, minY, minZ, maxX, maxY, maxZ));

        Vec3 center = new Vec3(x, y, z);
        ArrayList<Player> affected = new ArrayList<>();

        for (Entity entity : candidates) {
            if (!(entity instanceof Player player))
                continue;
            if (player.isSpectator())
                continue;
            if (player.isCreative())
                continue;
            // 与爆炸中心的距离比值，> 1.0 则超出范围
            double distance = Math.sqrt(entity.distanceToSqr(center));
            double v = distance / diameter;
            if (v > 1.0)
                continue;

            // 检测视线遮挡（与原版 getSeenPercent 一致）
            double seenPercent = Explosion.getSeenPercent(center, entity);
            if (seenPercent == 0.0)
                continue;

            affected.add(player);
        }

        return affected;
    }
}
