package io.wifi.starrailexpress.content.entity;

import io.wifi.starrailexpress.content.entity.no_water_influenced.NoHeavyWaterInfluencedThrowableItemProjectile;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMParticles;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;

import java.util.HashSet;
import java.util.UUID;

/**
 * 粘性雷 — 投掷后粘在墙面或玩家身上，短延时后爆炸。
 * 复用 GrenadeEntity 的空间视线模拟爆炸判定。
 */
public class StickyGrenadeEntity extends NoHeavyWaterInfluencedThrowableItemProjectile {
    private static final float EXPLOSION_RADIUS = 4f;
    private static final int MAX_KILL_PLAYER_COUNT = 6;
    private static final int FUSE_TICKS = 60; // 粘附后 3 秒爆炸
    private static final double SURFACE_OFFSET = 0.05D;

    private boolean stuck = false;
    private UUID stuckToEntity = null;
    /** 粘附时相对于目标实体的局部偏移（接触点 - 目标位置） */
    private Vec3 stuckOffset = Vec3.ZERO;
    private int fuseTimer = -1; // -1 = 未粘附/未激活

    public StickyGrenadeEntity(EntityType<?> type, Level world) {
        super(TMMEntities.STICKY_GRENADE, world);
    }

    @Override
    protected Item getDefaultItem() {
        return TMMItems.STICKY_GRENADE;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;

        if (!stuck) {
            tryStick();
            return;
        }

        // 粘附状态：跟随目标实体，保持在触碰到的那一点
        if (stuckToEntity != null) {
            Entity target = ((ServerLevel) this.level()).getEntity(stuckToEntity);
            if (target != null && target.isAlive()) {
                this.setPos(target.position().add(stuckOffset));
            }
        }

        // 倒计时
        fuseTimer--;
        if (fuseTimer <= 0) {
            explode();
        }
    }

    private void tryStick() {
        Vec3 current = this.position();
        Vec3 previous = new Vec3(this.xo, this.yo, this.zo);
        Vec3 delta = current.subtract(previous);
        if (delta.lengthSqr() < 1.0E-7D) return;

        // 先尝试粘人
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                this, previous, current,
                this.getBoundingBox().expandTowards(delta).inflate(0.5D),
                target -> target instanceof ServerPlayer sp
                        && sp.isAlive()
                        && !sp.isSpectator()
                        && !sp.getUUID().equals(getOwnerUUID())
                        && sp.canBeHitByProjectile(),
                delta.lengthSqr() + 0.5D);
        if (entityHit != null && entityHit.getEntity() instanceof ServerPlayer target) {
            stickToEntity(target, entityHit.getLocation());
            return;
        }

        // 再尝试粘墙
        if (this.level() instanceof ServerLevel serverLevel) {
            BlockHitResult blockHit = serverLevel.clip(new ClipContext(previous, current,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (blockHit.getType() == HitResult.Type.BLOCK) {
                stickToSurface(blockHit.getLocation(), blockHit.getDirection());
                return;
            }
        }

        // 回退：用碰撞状态检测
        if (this.onGround() || this.horizontalCollision) {
            if (this.horizontalCollision) {
                Direction side = Math.abs(delta.x) > Math.abs(delta.z)
                        ? (delta.x > 0 ? Direction.WEST : Direction.EAST)
                        : (delta.z > 0 ? Direction.NORTH : Direction.SOUTH);
                stickToSurface(current, side);
            } else {
                stickToSurface(current, Direction.UP);
            }
        }
    }

    private void stickToEntity(ServerPlayer target, Vec3 contactPoint) {
        this.stuck = true;
        this.stuckToEntity = target.getUUID();
        // 计算接触点相对于目标位置的局部偏移，后续每 tick 跟随
        this.stuckOffset = contactPoint.subtract(target.position());
        this.fuseTimer = FUSE_TICKS;
        this.setDeltaMovement(Vec3.ZERO);
        this.setNoGravity(true);
        this.hasImpulse = true;
        this.level().playSound(null, this.blockPosition(),
                SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.PLAYERS, 0.7F, 1.3F);
        target.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("message.sre.sticky_grenade.stuck")
                        .withStyle(net.minecraft.ChatFormatting.RED),
                true);
    }

    private void stickToSurface(Vec3 surfacePos, Direction side) {
        this.stuck = true;
        this.fuseTimer = FUSE_TICKS;
        Vec3 normal = Vec3.atLowerCornerOf(side.getNormal());
        this.setPos(surfacePos.add(normal.scale(SURFACE_OFFSET)));
        this.setDeltaMovement(Vec3.ZERO);
        this.setNoGravity(true);
        this.hasImpulse = true;
        this.level().playSound(null, this.blockPosition(),
                SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.PLAYERS, 0.7F, 1.1F);
    }

    private UUID getOwnerUUID() {
        return this.getOwner() != null ? this.getOwner().getUUID() : null;
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

        // 空间多层采样 + 视线模拟（复用 GrenadeEntity 的判定逻辑）
        var hitted = new HashSet<Entity>();
        double[] yOffsets = {0.0, 0.5, -0.5, 1.0, -1.0};
        for (double yOff : yOffsets) {
            hitted.addAll(GrenadeEntity.getPlayersAffectedByExplosion(world,
                    explosionPos.x, explosionPos.y + yOff, explosionPos.z, EXPLOSION_RADIUS));
        }

        Player attacker = this.getOwner() instanceof Player pe ? pe : null;
        // 粘性雷收益结算：使用 GrenadeEntity 相同的配置项
        io.wifi.starrailexpress.cca.SREPlayerShopComponent killerShop = attacker != null
                ? io.wifi.starrailexpress.cca.SREPlayerShopComponent.KEY.get(attacker) : null;
        int balanceBefore = killerShop != null ? killerShop.balance : 0;

        // 肉汁保护
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

        // 收益上限
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
