package org.agmas.noellesroles.game.modes.repair;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.content.block_entity.RepairStationBlockEntity;
import org.agmas.noellesroles.packet.RepairCombatFeedbackS2CPacket;

public record HunterAttackProfile(
        String id,
        int windupTicks,
        int cooldownTicks,
        int secondHitWindowTicks,
        double reach,
        float damage,
        int slowTicks,
        int slowAmplifier,
        double knockback,
        ParticleOptions hitParticle,
        SoundEvent hitSound) {
    public static HunterAttackProfile of(String roleId, String pluginId) {
        return of(roleId, pluginId, "blade");
    }

    public static HunterAttackProfile of(String roleId, String pluginId, String weaponId) {
        HunterAttackProfile profile = switch (roleId) {
            case "brute" -> new HunterAttackProfile("brute", 10, 55, 20 * 26, 2.9D, 4.0F, 65, 2, 0.9D,
                    ParticleTypes.EXPLOSION, SoundEvents.GENERIC_EXPLODE.value());
            case "tracker" -> new HunterAttackProfile("tracker", 10, 38, 20 * 18, 3.45D, 2.4F, 45, 0, 0.35D,
                    ParticleTypes.SCULK_SOUL, SoundEvents.SCULK_CLICKING);
            case "warden" -> new HunterAttackProfile("warden", 10, 48, 20 * 20, 3.15D, 3.0F, 55, 1, 0.5D,
                    ParticleTypes.ENCHANTED_HIT, SoundEvents.ANVIL_LAND);
            default -> new HunterAttackProfile("basic", 10, 45, 20 * 20, 3.2D, 3.0F, 50, 1, 0.55D,
                    ParticleTypes.SWEEP_ATTACK, SoundEvents.PLAYER_ATTACK_STRONG);
        };
        profile = switch (pluginId) {
            case "laceration" -> new HunterAttackProfile(profile.id + "_laceration", profile.windupTicks,
                    profile.cooldownTicks + 5, profile.secondHitWindowTicks + 20, profile.reach, profile.damage + 1.0F,
                    profile.slowTicks, profile.slowAmplifier, profile.knockback, ParticleTypes.DAMAGE_INDICATOR,
                    SoundEvents.PLAYER_ATTACK_CRIT);
            case "concussion" -> new HunterAttackProfile(profile.id + "_concussion", profile.windupTicks,
                    profile.cooldownTicks + 7, profile.secondHitWindowTicks + 60, Math.max(2.75D, profile.reach - 0.15D),
                    profile.damage, profile.slowTicks + 25, profile.slowAmplifier + 1, profile.knockback + 0.25D,
                    ParticleTypes.CRIT, SoundEvents.ANVIL_LAND);
            case "tracking" -> new HunterAttackProfile(profile.id + "_tracking", profile.windupTicks,
                    Math.max(32, profile.cooldownTicks - 4), profile.secondHitWindowTicks, profile.reach + 0.2D,
                    profile.damage, profile.slowTicks, profile.slowAmplifier, profile.knockback, ParticleTypes.GLOW,
                    SoundEvents.SCULK_CLICKING);
            case "suppression" -> new HunterAttackProfile(profile.id + "_suppression", profile.windupTicks,
                    profile.cooldownTicks, profile.secondHitWindowTicks + 40, profile.reach, profile.damage,
                    profile.slowTicks + 10, profile.slowAmplifier, profile.knockback, ParticleTypes.ENCHANT,
                    SoundEvents.ANVIL_PLACE);
            default -> profile;
        };
        return switch (weaponId) {
            case "hammer" -> new HunterAttackProfile(profile.id + "_hammer", 15,
                    Math.max(profile.cooldownTicks + 14, 64), profile.secondHitWindowTicks + 40,
                    2.65D, profile.damage + 1.2F, profile.slowTicks + 25,
                    Math.max(profile.slowAmplifier + 1, 2), profile.knockback + 0.35D,
                    ParticleTypes.EXPLOSION, SoundEvents.ANVIL_LAND);
            case "hook" -> new HunterAttackProfile(profile.id + "_hook", 15,
                    Math.max(100, profile.cooldownTicks + 4), profile.secondHitWindowTicks,
                    4.35D, Math.max(2.0F, profile.damage - 0.4F), profile.slowTicks,
                    profile.slowAmplifier, Math.max(0.25D, profile.knockback - 0.15D),
                    ParticleTypes.SCULK_SOUL, SoundEvents.CHAIN_PLACE);
            default -> new HunterAttackProfile(profile.id + "_blade", profile.windupTicks,
                    Math.max(28, profile.cooldownTicks - 8), profile.secondHitWindowTicks,
                    profile.reach, profile.damage, profile.slowTicks, profile.slowAmplifier, profile.knockback,
                    ParticleTypes.SWEEP_ATTACK, SoundEvents.PLAYER_ATTACK_STRONG);
        };
    }

    public boolean applyHit(ServerPlayer hunter, ServerPlayer target) {
        ServerLevel level = hunter.serverLevel();
        var hunterComponent = ModComponents.REPAIR_ROLES.get(hunter);
        var targetComponent = ModComponents.REPAIR_ROLES.get(target);
        String plugin = hunterComponent.activeAttackPlugin;
        long now = level.getGameTime();
        boolean secondHit = targetComponent.repairInjuryLevel > 0
                && now - targetComponent.lastHunterHitTick <= secondHitWindowTicks;

        target.hurt(hunter.damageSources().playerAttack(hunter), damage);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slowTicks, slowAmplifier, false, true, true));
        if ("warden".equals(hunterComponent.activeRole) || "suppression".equals(plugin)) {
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 6, 0, false, true, true));
            jamNearbyStations(level, target.blockPosition(), 7, 5, 20 * 7);
        }
        if ("tracker".equals(hunterComponent.activeRole) || "tracking".equals(plugin)) {
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 7, 0, false, true, true));
            RepairModeState.highlightActiveStationsFor(hunter, 20 * 6);
        }
        if ("laceration".equals(plugin)) {
            target.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 4, 0, false, true, true));
        }

        Vec3 away = target.position().subtract(hunter.position()).normalize();
        target.push(away.x * knockback, 0.12D, away.z * knockback);
        target.hurtMarked = true;
        level.sendParticles(hitParticle, target.getX(), target.getY() + target.getBbHeight() * 0.55D,
                target.getZ(), 10, 0.24D, 0.24D, 0.24D, 0.05D);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY() + target.getBbHeight() * 0.65D,
                target.getZ(), 6, 0.24D, 0.24D, 0.24D, 0.05D);
        level.playSound(null, target.blockPosition(), hitSound, SoundSource.PLAYERS, 0.95F, 0.85F);
        RepairModeState.broadcastCombatFeedback(level, RepairCombatFeedbackS2CPacket.HIT, target,
                target.getX(), target.getY() + target.getBbHeight() * 0.55D, target.getZ(), 26.0D);

        targetComponent.repairInjuryLevel = 1;
        targetComponent.lastHunterHitTick = now;
        targetComponent.sync();

        if (secondHit && RepairModeState.downPlayer(target)) {
            RepairModeState.awardCoins(hunter, 35, "repair_coin_source.down");
            hunter.displayClientMessage(Component.translatable("message.noellesroles.repair.hunter_weapon_downed",
                    target.getName()).withStyle(ChatFormatting.RED), true);
        }
        return true;
    }

    private static void jamNearbyStations(ServerLevel level, BlockPos center, int radius, int amount, int ticks) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -2, -radius), center.offset(radius, 2, radius))) {
            if (level.getBlockEntity(pos) instanceof RepairStationBlockEntity station) {
                station.sabotage(amount, ticks);
            }
        }
    }
}
