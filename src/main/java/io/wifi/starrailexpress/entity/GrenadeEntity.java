package io.wifi.starrailexpress.entity;

import java.util.ArrayList;
import java.util.HashSet;
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
import org.agmas.noellesroles.entity.PuppeteerBodyEntity;

public class GrenadeEntity extends ThrowableItemProjectile {
    private static final float EXPLOSION_RADIUS = 4f;
    private static final int MAX_KILL_PLAYER_COUNT = 8;

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

            Vec3 explosionPos = this.position();
            var hitted_players = new HashSet<>();
            hitted_players.addAll(getPlayersAffectedByExplosion(world, explosionPos.x, explosionPos.y, explosionPos.z,
                    EXPLOSION_RADIUS));
            hitted_players
                    .addAll(getPlayersAffectedByExplosion(world, explosionPos.x, explosionPos.y + 0.5, explosionPos.z,
                            EXPLOSION_RADIUS));
            hitted_players
                    .addAll(getPlayersAffectedByExplosion(world, explosionPos.x, explosionPos.y - 0.5, explosionPos.z,
                            EXPLOSION_RADIUS));
            int count = 0;
            for (var entity : hitted_players) {
                if (entity instanceof Player player) {
                    GameUtils.killPlayer(player, true,
                            this.getOwner() instanceof Player playerEntity ? playerEntity : null,
                            GameConstants.DeathReasons.GRENADE);
                }
                if (entity instanceof PuppeteerBodyEntity puppeteerBodyEntity) {
                    puppeteerBodyEntity.playerHurt(this.getOwner() instanceof Player playerEntity ? playerEntity : null,
                            GameConstants.DeathReasons.GRENADE);
                }
                count++;
                if (count >= MAX_KILL_PLAYER_COUNT)
                    break;
            }
            this.discard();
        }
    }

    public static ArrayList<Entity> getPlayersAffectedByExplosion(Level level, double x, double y, double z,
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
        ArrayList<Entity> affected = new ArrayList<>();

        for (Entity entity : candidates) {
            if ((entity instanceof Player player)) {
                if (!GameUtils.isPlayerAliveAndSurvival(player))
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
            if (entity instanceof PuppeteerBodyEntity puppeteerBodyEntity) {
                var owner = puppeteerBodyEntity.getOwner();
                if (owner instanceof Player player) {
                    if (!GameUtils.isPlayerAliveAndSurvival(player))
                        continue;
                    double distance = Math.sqrt(puppeteerBodyEntity.distanceToSqr(center));
                    double v = distance / diameter;
                    if (v > 1.0)
                        continue;
                    double seenPercent = Explosion.getSeenPercent(center, puppeteerBodyEntity);
                    if (seenPercent == 0.0)
                        continue;
                    affected.add(puppeteerBodyEntity);
                }
            }
        }
        affected.sort((a, b) -> {
            double da = a.distanceToSqr(x, y, z);
            double db = b.distanceToSqr(x, y, z);
            if (da < db)
                return -1;
            if (da == db)
                return 0;
            return 1;
        });
        return affected;
    }
}
