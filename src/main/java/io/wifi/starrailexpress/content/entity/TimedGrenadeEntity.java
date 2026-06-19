package io.wifi.starrailexpress.content.entity;

import io.wifi.starrailexpress.content.entity.no_water_influenced.NoHeavyWaterInfluencedThrowableItemProjectile;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMParticles;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;

import java.util.HashSet;

/**
 * 滞时雷 — 经典 FPS 延时雷，抛出后在地面反弹，倒计时到零后爆炸。
 * 爆炸判定复用 GrenadeEntity 的空间视线模拟逻辑。
 */
public class TimedGrenadeEntity extends NoHeavyWaterInfluencedThrowableItemProjectile {
    private static final float EXPLOSION_RADIUS = 4f;
    private static final int MAX_KILL_PLAYER_COUNT = 8;

    /** 实体剩余的引爆 tick 数 */
    private int detonateInTicks;

    public TimedGrenadeEntity(EntityType<?> type, Level world) {
        super(TMMEntities.TIMED_GRENADE, world);
        this.detonateInTicks = 80; // 默认 4 秒
    }

    @Override
    protected Item getDefaultItem() {
        return TMMItems.TIMED_GRENADE;
    }

    /** 设置剩余引爆 tick 数（由 Item 投掷时传入） */
    public void setFuseTicks(int ticks) {
        this.detonateInTicks = Math.max(1, ticks);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;

        detonateInTicks--;

        if (detonateInTicks <= 0) {
            explode();
        }
    }

    /**
     * 拦截方块碰撞，用反射速度替代停止，实现物理反弹。
     * 不调用 super.onHitBlock() 以避免父类停止弹体。
     */
    @Override
    protected void onHitBlock(BlockHitResult hitResult) {
        Vec3 normal = Vec3.atLowerCornerOf(hitResult.getDirection().getNormal());
        Vec3 vel = this.getDeltaMovement();
        double dot = vel.dot(normal);

        // 只有撞向方块时才反弹（不是从方块内部弹出）
        if (dot < 0) {
            Vec3 reflected = vel.subtract(normal.scale(2.0 * dot));
            double bounceFactor = 0.6; // 反弹系数
            this.setDeltaMovement(reflected.scale(bounceFactor));
            this.hasImpulse = true;
        } else {
            this.setDeltaMovement(vel.scale(0.2));
        }

        // 反弹音效
        this.level().playSound(null, this.blockPosition(),
                SoundEvents.STONE_HIT, SoundSource.BLOCKS, 0.4F, 1.2F);
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        // 滞时雷不直接碰撞实体，只在爆炸时判定
        return false;
    }

    private void explode() {
        if (!(this.level() instanceof ServerLevel world)) return;
        Vec3 explosionPos = this.position();

        world.playSound(null, this.blockPosition(), TMMSounds.ITEM_GRENADE_EXPLODE,
                SoundSource.PLAYERS, 5f, 1f + this.getRandom().nextFloat() * .1f - .05f);
        world.sendParticles(TMMParticles.BIG_EXPLOSION, explosionPos.x, explosionPos.y + .1f, explosionPos.z,
                1, 0, 0, 0, 0);
        world.sendParticles(ParticleTypes.SMOKE, explosionPos.x, explosionPos.y + .1f, explosionPos.z,
                100, 0, 0, 0, .2f);
        world.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, this.getDefaultItem().getDefaultInstance()),
                explosionPos.x, explosionPos.y + .1f, explosionPos.z, 100, 0, 0, 0, 1f);

        var hitted = new HashSet<Entity>();
        double[] yOffsets = {0.0, 0.5, -0.5, 1.0, -1.0};
        for (double yOff : yOffsets) {
            hitted.addAll(GrenadeEntity.getPlayersAffectedByExplosion(world,
                    explosionPos.x, explosionPos.y + yOff, explosionPos.z, EXPLOSION_RADIUS));
        }

        Player attacker = this.getOwner() instanceof Player pe ? pe : null;
        io.wifi.starrailexpress.cca.SREPlayerShopComponent killerShop = attacker != null
                ? io.wifi.starrailexpress.cca.SREPlayerShopComponent.KEY.get(attacker) : null;
        int balanceBefore = killerShop != null ? killerShop.balance : 0;

        boolean meatballInRange = false;
        boolean hasInnocentInRange = false;
        for (var entity : hitted) {
            if (entity instanceof Player player) {
                var gw = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(player.level());
                if (gw.isRole(player, org.agmas.noellesroles.role.ModRoles.MEATBALL)) {
                    meatballInRange = true;
                } else if (gw.isInnocent(player)) {
                    hasInnocentInRange = true;
                }
            }
        }

        int count = 0;
        for (var entity : hitted) {
            if (entity instanceof Player player) {
                if (meatballInRange && hasInnocentInRange) {
                    var gw = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(player.level());
                    if (gw.isRole(player, org.agmas.noellesroles.role.ModRoles.MEATBALL)) {
                        if (player instanceof ServerPlayer sp) {
                            sp.displayClientMessage(
                                    net.minecraft.network.chat.Component
                                            .translatable("message.noellesroles.meatball.protected")
                                            .withStyle(net.minecraft.ChatFormatting.GREEN),
                                    true);
                        }
                        continue;
                    }
                }
                GameUtils.killPlayer(player, true, attacker, GameConstants.DeathReasons.GRENADE);
            }
            if (entity instanceof PuppeteerBodyEntity pbe) {
                pbe.playerHurt(attacker, GameConstants.DeathReasons.GRENADE);
            }
            count++;
            if (count >= MAX_KILL_PLAYER_COUNT) break;
        }

        if (killerShop != null) {
            int moneyEarned = killerShop.balance - balanceBefore;
            int grenadePerKill = io.wifi.starrailexpress.SREConfig.instance().grenadeMoneyPerKill;
            int maxReward = io.wifi.starrailexpress.SREConfig.instance().grenadeMaxMoneyReward;
            int targetReward = count * grenadePerKill;
            if (maxReward > 0 && targetReward > maxReward) targetReward = maxReward;
            int adjustment = targetReward - moneyEarned;
            if (adjustment != 0) killerShop.addToBalance(adjustment);
        }

        this.discard();
    }
}
