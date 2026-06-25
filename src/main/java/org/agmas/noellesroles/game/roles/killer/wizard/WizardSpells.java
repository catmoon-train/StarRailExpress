package org.agmas.noellesroles.game.roles.killer.wizard;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.config.NoellesRolesConfig;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 巫师法杖的两种施法：魔法火焰箭（最多贯穿数名玩家、命中即死）与 Explosion! 九环火球术（穿墙 AoE）。
 * 全部为服务端逻辑，配合粒子与音效呈现“华丽”的释放表现。
 */
public final class WizardSpells {

    private WizardSpells() {
    }

    private static NoellesRolesConfig config() {
        return NoellesRolesConfig.HANDLER.instance();
    }

    /**
     * 魔法火焰箭：从视线方向射出一道火焰，遇墙停止，最多贯穿
     * {@link NoellesRolesConfig#wizardFireArrowMaxPierce} 名玩家；命中即直接造成死亡。
     */
    public static void castFireArrow(WizardPlayerComponent comp, ServerPlayer sp) {
        if (!(sp.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 start = sp.getEyePosition();
        Vec3 dir = sp.getViewVector(1.0f).normalize();
        double range = config().wizardFireArrowRange;
        int maxPierce = config().wizardFireArrowMaxPierce;
        double hitRadius = 1.0;
        double step = 0.25;

        Set<UUID> hit = new HashSet<>();
        Vec3 pos = start;
        for (double traveled = 0; traveled <= range; traveled += step) {
            pos = start.add(dir.scale(traveled));
            // 火焰轨迹粒子
            if (traveled % 0.5 < step) {
                level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 2, 0.05, 0.05, 0.05, 0.0);
                level.sendParticles(ParticleTypes.SMALL_FLAME, pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
            // 命中玩家（贯穿，命中即死）
            for (Player p : level.players()) {
                if (p == sp || hit.contains(p.getUUID()) || !GameUtils.isPlayerAliveAndSurvival(p)) {
                    continue;
                }
                if (p.getBoundingBox().inflate(hitRadius - 0.5).contains(pos)
                        || p.position().add(0, p.getBbHeight() / 2, 0).distanceToSqr(pos) <= hitRadius * hitRadius) {
                    if (p instanceof ServerPlayer target) {
                        hit.add(p.getUUID());
                        level.sendParticles(ParticleTypes.LAVA, p.getX(), p.getY() + 1.0, p.getZ(),
                                12, 0.3, 0.5, 0.3, 0.05);
                        GameUtils.killPlayer(target, true, sp, Noellesroles.id("wizard_fire_arrow"));
                    }
                }
            }
            // 已达到最大贯穿人数则停止
            if (hit.size() >= maxPierce) {
                break;
            }
            // 遇到实心方块则停止
            BlockPos bp = BlockPos.containing(pos);
            BlockState state = level.getBlockState(bp);
            if (!state.getCollisionShape(level, bp).isEmpty()) {
                break;
            }
        }

        level.playSound(null, sp.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.4f, 1.1f);
        level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 30, 0.3, 0.3, 0.3, 0.05);
    }

    /**
     * 九环火球术（Explosion!）：沿视线方向飞出（无视墙体），到达首个玩家或最大射程处引爆；
     * 引爆点半径内的玩家（无视墙体阻挡）全部被秒杀。引爆呈“九环”火焰粒子。
     */
    public static void castNineRingFireball(WizardPlayerComponent comp, ServerPlayer sp) {
        if (!(sp.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 start = sp.getEyePosition();
        Vec3 dir = sp.getViewVector(1.0f).normalize();
        double range = config().wizardFireballRange;
        double radius = config().wizardFireballRadius;
        double step = 0.5;

        // 飞行（穿墙）：每步留下火焰，遇到玩家附近即引爆
        Vec3 detonation = start.add(dir.scale(range));
        for (double traveled = 0; traveled <= range; traveled += step) {
            Vec3 pos = start.add(dir.scale(traveled));
            level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 3, 0.1, 0.1, 0.1, 0.0);
            boolean found = false;
            for (Player p : level.players()) {
                if (p == sp || !GameUtils.isPlayerAliveAndSurvival(p)) {
                    continue;
                }
                if (p.position().add(0, p.getBbHeight() / 2, 0).distanceToSqr(pos) <= 1.5 * 1.5) {
                    detonation = pos;
                    found = true;
                    break;
                }
            }
            if (found) {
                break;
            }
        }

        explode(level, detonation, radius, sp);
    }

    private static void explode(ServerLevel level, Vec3 center, double radius, ServerPlayer caster) {
        // “九环”火焰环 + 大爆炸粒子
        for (int ring = 1; ring <= 9; ring++) {
            double r = radius * ring / 9.0;
            int points = 8 + ring * 2;
            for (int i = 0; i < points; i++) {
                double angle = (Math.PI * 2 * i) / points;
                double x = center.x + Math.cos(angle) * r;
                double z = center.z + Math.sin(angle) * r;
                level.sendParticles(ParticleTypes.FLAME, x, center.y, z, 1, 0.0, 0.05, 0.0, 0.01);
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        level.playSound(null, BlockPos.containing(center), SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, 4.0f, 0.9f);

        // 无视墙体的 AoE 击杀
        double r2 = radius * radius;
        int killed = 0;
        for (Player p : level.players()) {
            if (p == caster || !GameUtils.isPlayerAliveAndSurvival(p)) {
                continue;
            }
            if (p.position().add(0, p.getBbHeight() / 2, 0).distanceToSqr(center) <= r2) {
                GameUtils.killPlayer(p, true, caster, Noellesroles.id("wizard_fireball"));
                killed++;
                if (killed >= config().wizardFireballMaxKills) {
                    break;
                }
            }
        }
    }
}
