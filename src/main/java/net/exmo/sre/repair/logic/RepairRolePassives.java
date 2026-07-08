package net.exmo.sre.repair.logic;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.repair.state.RepairModeState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.agmas.noellesroles.component.ModComponents;

/** 修机模式角色被动效果（疾跑者残影、蛮力者力量、追踪者定期高亮最近目标）。 */
public final class RepairRolePassives {
    private RepairRolePassives() {
    }

    public static void tick(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        for (ServerPlayer player : serverWorld.players()) {
            String active = ModComponents.REPAIR_ROLES.get(player).activeRole;
            if ("runner".equals(active)) {
                if (serverWorld.getGameTime() % 8 == 0) {
                    serverWorld.sendParticles(ParticleTypes.CLOUD,
                            player.getX(), player.getY() + 0.1D, player.getZ(),
                            1, 0.08D, 0.02D, 0.08D, 0.005D);
                }
            } else if ("brute".equals(active)) {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 0, false, false, true));
                if (serverWorld.getGameTime() % 12 == 0) {
                    serverWorld.sendParticles(ParticleTypes.CRIT,
                            player.getX(), player.getY() + 1.0D, player.getZ(),
                            4, 0.25D, 0.35D, 0.25D, 0.02D);
                }
            } else if ("tracker".equals(active) && serverWorld.getGameTime() % (20 * 12) == 0) {
                ServerPlayer target = nearestTrackableTarget(serverWorld, gameWorldComponent, player, 28.0D);
                if (target != null) {
                    target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false, true));
                    serverWorld.sendParticles(ParticleTypes.SCULK_SOUL,
                            target.getX(), target.getY() + 1.0D, target.getZ(),
                            8, 0.35D, 0.45D, 0.35D, 0.02D);
                }
            }
        }
    }

    private static ServerPlayer nearestTrackableTarget(ServerLevel level, SREGameWorldComponent gameWorldComponent,
            ServerPlayer hunter, double radius) {
        double bestDistance = radius * radius;
        ServerPlayer best = null;
        for (ServerPlayer target : level.players()) {
            if (target == hunter || GameUtils.isPlayerEliminated(target)
                    || target.getTags().contains(RepairModeState.ESCAPED_TAG)
                    || RepairModeState.isHunter(target)) {
                continue;
            }
            double distance = target.distanceToSqr(hunter);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = target;
            }
        }
        return best;
    }
}
