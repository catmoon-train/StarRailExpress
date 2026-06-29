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
import org.agmas.noellesroles.init.ModItems;

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
            // 暗影轨迹：稀疏的灵魂火与黑烟，低调而非张扬——谋杀应当悄无声息
            if (traveled % 1.0 < step) {
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
                level.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 1, 0.02, 0.02, 0.02, 0.0);
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
                        // 命中：低调的灵魂火与黑烟，而非张扬的岩浆迸溅
                        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.getX(), p.getY() + 1.0, p.getZ(),
                                6, 0.2, 0.4, 0.2, 0.01);
                        level.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 1.0, p.getZ(),
                                4, 0.2, 0.4, 0.2, 0.0);
                        comp.onFireArrowHit(sp, target);
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

        // 用低沉的灵魂气音取代张扬的烈焰咆哮，并压低音量——不再向全场宣告杀手位置
        level.playSound(null, sp.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.5f, 0.7f);
        level.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 8, 0.2, 0.2, 0.2, 0.02);
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

        explode(level, detonation, radius, sp, comp);
    }

    private static void explode(ServerLevel level, Vec3 center, double radius, ServerPlayer caster,
                                WizardPlayerComponent comp) {
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
        if (killed > 0) {
            // var comp = WizardPlayerComponent.KEY.get(caster);
            comp.onKillWhileShielded();
            caster.getCooldowns().addCooldown(ModItems.WIZARD_STAFF,
                    io.wifi.starrailexpress.game.GameConstants.ITEM_COOLDOWNS.get(
                            io.wifi.starrailexpress.index.TMMItems.KNIFE));
        }
    }
}
