package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * RPG 火箭投射物（服务端模拟，无实体）：以 {@link SixtySecondsBalance#GUN_RPG_ROCKET_SPEED} 沿视线飞行，
 * 每 tick 推进一段——段内命中方块/玩家/怪物 或 飞满射程 → 在命中点<b>范围爆炸</b>
 * （半径内玩家扣枪伤走倒地路径、<b>含发射者</b>；怪物即死；清晨禁 PvP 时只炸怪）。
 * 飞行尾迹为火焰+烟雾粒子，全员可见。由 {@code END_WORLD_TICK} 全局推进（自注册）。
 */
public final class SixtySecondsRockets {
    /** 出膛保护：前 N tick 不检测实体（避免出膛即炸自己）。 */
    private static final int ARMING_TICKS = 2;

    private static final List<Rocket> ROCKETS = new ArrayList<>();

    private SixtySecondsRockets() {
    }

    private static final class Rocket {
        ResourceKey<Level> dimension;
        UUID shooter;
        Vec3 pos;
        Vec3 velocity;
        double remainingDistance;
        int age;
    }

    /** 模组初始化时注册一次。 */
    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(SixtySecondsRockets::tick);
    }

    /** 发射一枚火箭（从射手眼位沿视线）。 */
    public static void fire(ServerPlayer shooter) {
        Rocket rocket = new Rocket();
        rocket.dimension = shooter.serverLevel().dimension();
        rocket.shooter = shooter.getUUID();
        rocket.pos = shooter.getEyePosition();
        rocket.velocity = shooter.getViewVector(1.0F).normalize()
                .scale(SixtySecondsBalance.GUN_RPG_ROCKET_SPEED);
        rocket.remainingDistance = SixtySecondsBalance.GUN_RPG_RANGE;
        ROCKETS.add(rocket);
        shooter.serverLevel().playSound(null, shooter.getX(), shooter.getEyeY(), shooter.getZ(),
                SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    private static void tick(ServerLevel level) {
        if (ROCKETS.isEmpty()) {
            return;
        }
        for (Iterator<Rocket> it = ROCKETS.iterator(); it.hasNext();) {
            Rocket rocket = it.next();
            if (!rocket.dimension.equals(level.dimension())) {
                continue;
            }
            if (advance(level, rocket)) {
                it.remove();
            }
        }
    }

    /** 推进一段；返回 true=已爆炸/结束。 */
    private static boolean advance(ServerLevel level, Rocket rocket) {
        rocket.age++;
        Vec3 from = rocket.pos;
        Vec3 to = from.add(rocket.velocity);

        // 方块命中
        ServerPlayer shooter = level.getServer().getPlayerList().getPlayer(rocket.shooter);
        HitResult blockHit = level.clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                shooter != null ? shooter : null));
        Vec3 segmentEnd = blockHit.getType() == HitResult.Type.BLOCK ? blockHit.getLocation() : to;

        // 实体命中（出膛保护期后）：段包围盒内的 存活玩家(非射手) / 标记怪物
        if (rocket.age > ARMING_TICKS) {
            AABB sweep = new AABB(from, segmentEnd).inflate(0.5);
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, sweep)) {
                boolean validPlayer = entity instanceof Player p
                        && !p.getUUID().equals(rocket.shooter)
                        && GameUtils.isPlayerAliveAndSurvival(p);
                boolean validMob = entity instanceof LivingEntity && !(entity instanceof Player);
                if (validPlayer || validMob) {
                    explode(level, shooter, entity.position().add(0, entity.getBbHeight() / 2.0, 0));
                    return true;
                }
            }
        }

        // 方块命中 / 射程耗尽 → 爆炸
        rocket.remainingDistance -= rocket.velocity.length();
        if (blockHit.getType() == HitResult.Type.BLOCK || rocket.remainingDistance <= 0) {
            explode(level, shooter, segmentEnd);
            return true;
        }

        // 尾迹粒子 + 前进
        level.sendParticles(ParticleTypes.FLAME, from.x, from.y, from.z, 2, 0.05, 0.05, 0.05, 0.01);
        level.sendParticles(ParticleTypes.SMOKE, from.x, from.y, from.z, 3, 0.08, 0.08, 0.08, 0.01);
        rocket.pos = to;
        return false;
    }

    /** 范围爆炸结算（RPG 命中点）：粒子/音效 + 玩家扣枪伤（含发射者；PvP 受限的目标逐个跳过）+ 怪物即死。 */
    public static void explode(ServerLevel level, ServerPlayer shooter, Vec3 center) {
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        level.playSound(null, center.x, center.y, center.z,
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2F, 0.9F);

        double radius = SixtySecondsBalance.GUN_RPG_BLAST_RADIUS;
        double radiusSqr = radius * radius;

        if (shooter != null) {
            for (ServerPlayer target : level.players()) {
                if (GameUtils.isPlayerAliveAndSurvival(target)
                        && !SixtySecondsHealthSystem.isPvpBlocked(level, shooter, target)
                        && target.position().add(0, target.getBbHeight() / 2.0, 0)
                                .distanceToSqr(center) <= radiusSqr) {
                    SixtySecondsHealthSystem.applyInjury(target, shooter, SixtySecondsBalance.GUN_RPG_DAMAGE);
                }
            }
        }
        for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class,
                AABB.ofSize(center, radius * 2, radius * 2, radius * 2))) {
            if (mob instanceof Player) continue; // 玩家已在上面处理
            if (shooter != null) {
                mob.hurt(shooter.damageSources().playerAttack(shooter), 1000.0F);
            } else {
                mob.hurt(level.damageSources().generic(), 1000.0F);
            }
        }
    }

    public static void reset() {
        ROCKETS.clear();
    }
}
