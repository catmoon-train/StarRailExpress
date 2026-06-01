package org.agmas.noellesroles.game.modes.repair;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.ModComponents;

public final class RepairGameplayEffects {
    private RepairGameplayEffects() {
    }

    public static boolean isHunter(Player player) {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        if (game == null || !game.isRunning()) {
            return false;
        }
        return RepairRoleDefinition.byId(ModComponents.REPAIR_ROLES.get(player).activeRole)
                .map(role -> role.faction == RepairRoleDefinition.Faction.HUNTER)
                .orElseGet(() -> {
                    return game != null && game.getRole(player) != null && game.getRole(player).canUseKiller();
                });
    }

    public static boolean isSurvivor(Player player) {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        if (game == null || !game.isRunning()) {
            return false;
        }
        return RepairRoleDefinition.byId(ModComponents.REPAIR_ROLES.get(player).activeRole)
                .map(role -> role.faction == RepairRoleDefinition.Faction.SURVIVOR)
                .orElse(false);
    }

    public static void burst(ServerLevel level, double x, double y, double z, int colorType) {
        if (colorType == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 32, 0.7D, 0.45D, 0.7D, 0.04D);
            level.sendParticles(ParticleTypes.WAX_ON, x, y + 0.1D, z, 18, 0.5D, 0.35D, 0.5D, 0.02D);
        } else if (colorType == 1) {
            level.sendParticles(ParticleTypes.SMOKE, x, y, z, 50, 1.2D, 0.65D, 1.2D, 0.03D);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 0.3D, z, 8, 0.8D, 0.35D, 0.8D, 0.01D);
        } else {
            level.sendParticles(ParticleTypes.SONIC_BOOM, x, y + 0.4D, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            level.sendParticles(ParticleTypes.END_ROD, x, y + 0.4D, z, 24, 0.7D, 0.5D, 0.7D, 0.03D);
        }
    }

    public static void disorientHunters(ServerLevel level, double x, double y, double z, double radius, int durationTicks) {
        for (ServerPlayer target : level.players()) {
            if (!isHunter(target) || target.distanceToSqr(x, y, z) > radius * radius) {
                continue;
            }
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, durationTicks, 1, false, true, true));
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, Math.min(durationTicks, 80), 0, false, true, true));
        }
        level.playSound(null, x, y, z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.0F, 1.25F);
    }
}
