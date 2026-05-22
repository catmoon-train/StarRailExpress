package org.agmas.noellesroles.packet;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.content.block_entity.RepairStationBlockEntity;
import org.agmas.noellesroles.game.modes.repair.RepairModeState;
import org.agmas.noellesroles.init.ModItems;

public record RepairPrimarySkillC2SPacket() implements CustomPacketPayload {
    public static final Type<RepairPrimarySkillC2SPacket> ID = new Type<>(Noellesroles.id("repair_primary_skill"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RepairPrimarySkillC2SPacket> CODEC = StreamCodec
            .ofMember(RepairPrimarySkillC2SPacket::encode, RepairPrimarySkillC2SPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
    }

    public static RepairPrimarySkillC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new RepairPrimarySkillC2SPacket();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(RepairPrimarySkillC2SPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!RepairModeState.isRepairGameRunning(level)) {
            return;
        }
        var component = ModComponents.REPAIR_ROLES.get(player);
        if (component.carrying != null && RepairModeState.isHunter(player)) {
            RepairModeState.dropCarried(player, 20 * 2);
            player.displayClientMessage(Component.translatable("message.noellesroles.repair.carried_dropped")
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (component.activeSkillCooldownEndTick > level.getGameTime()) {
            player.displayClientMessage(Component.translatable("message.noellesroles.repair.skill_cooldown",
                    RepairModeState.skillCooldownSeconds(player)).withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (component.downed || component.carriedBy != null || component.trialStand.present()
                || player.getTags().contains(RepairModeState.ESCAPED_TAG)) {
            return;
        }
        switch (component.activeRole) {
            case "warden" -> wardenSkill(level, player);
            case "brute" -> bruteSkill(level, player);
            case "tracker" -> trackerSkill(level, player);
            case "mechanic" -> mechanicSkill(level, player);
            case "medic" -> medicSkill(level, player);
            case "runner" -> runnerSkill(level, player);
            case "archivist" -> archivistSkill(player);
            case "saboteur" -> saboteurSkill(level, player);
            case "collector" -> collectorSkill(player);
            default -> {
            }
        }
    }

    private static void wardenSkill(ServerLevel level, ServerPlayer player) {
        ModComponents.REPAIR_ROLES.get(player).activeAttackPlugin = "suppression";
        jamNearbyStations(level, player.blockPosition(), 8.0D, 6, 20 * 10);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 8, 0, false, true, true));
        start(level, player, 20 * 36, "warden_judgment", SoundEvents.ANVIL_PLACE, ParticleTypes.ENCHANT);
    }

    private static void bruteSkill(ServerLevel level, ServerPlayer player) {
        ModComponents.REPAIR_ROLES.get(player).activeAttackPlugin = "concussion";
        Vec3 look = player.getLookAngle().normalize();
        player.push(look.x * 1.45D, 0.12D, look.z * 1.45D);
        player.hurtMarked = true;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 5, 1, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 6, 0, false, true, true));
        start(level, player, 20 * 28, "brute_charge", SoundEvents.RAVAGER_ROAR, ParticleTypes.CRIT);
    }

    private static void trackerSkill(ServerLevel level, ServerPlayer player) {
        ServerPlayer best = null;
        double bestDistance = 36.0D * 36.0D;
        for (ServerPlayer target : level.players()) {
            if (target == player || RepairModeState.isHunter(target) || target.getTags().contains(RepairModeState.ESCAPED_TAG)) {
                continue;
            }
            double distance = target.distanceToSqr(player);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = target;
            }
        }
        if (best != null) {
            best.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 6, 0, false, true, true));
        }
        ModComponents.REPAIR_ROLES.get(player).activeAttackPlugin = "tracking";
        start(level, player, 20 * 30, "tracker_listen", SoundEvents.SCULK_CLICKING, ParticleTypes.SCULK_SOUL);
    }

    private static void mechanicSkill(ServerLevel level, ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 20 * 12, 1, false, true, true));
        player.addItem(new ItemStack(ModItems.SPARE_PARTS, 2));
        start(level, player, 20 * 38, "mechanic_overclock", SoundEvents.IRON_GOLEM_REPAIR, ParticleTypes.ELECTRIC_SPARK);
    }

    private static void medicSkill(ServerLevel level, ServerPlayer player) {
        ServerPlayer target = nearestDowned(level, player, 4.0D);
        if (target != null) {
            RepairModeState.revivePlayer(player, target);
        } else {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 6, 0, false, true, true));
        }
        start(level, player, 20 * 46, "medic_injection", SoundEvents.BREWING_STAND_BREW, ParticleTypes.HEART);
    }

    private static void runnerSkill(ServerLevel level, ServerPlayer player) {
        Vec3 look = player.getLookAngle().normalize();
        Vec3 current = player.getDeltaMovement();
        player.setDeltaMovement(look.x * 2.15D, Math.max(current.y, 0.22D), look.z * 2.15D);
        player.hurtMarked = true;
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 2, 0, false, true, true));
        start(level, player, 20 * 26, "runner_vault", SoundEvents.TRIDENT_RIPTIDE_1.value(), ParticleTypes.CLOUD);
    }

    private static void archivistSkill(ServerPlayer player) {
        RepairModeState.addNeutralTaskProgress(player, "archivist", 1, RepairModeState.ARCHIVIST_TASK_NEEDED);
        RepairModeState.startSkillCooldown(player, 20 * 34, "archivist_notes");
    }

    private static void saboteurSkill(ServerLevel level, ServerPlayer player) {
        RepairModeState.addNeutralTaskProgress(player, "saboteur", 2, RepairModeState.SABOTEUR_TASK_NEEDED);
        level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.PLAYERS, 1.3F, 0.6F);
        level.sendParticles(ParticleTypes.NOTE, player.getX(), player.getY() + 1.0D, player.getZ(), 12, 1.3D, 0.5D, 1.3D, 0.02D);
        RepairModeState.startSkillCooldown(player, 20 * 32, "saboteur_signal");
    }

    private static void collectorSkill(ServerPlayer player) {
        player.addItem(new ItemStack(player.getRandom().nextBoolean() ? ModItems.SMOKE_PELLET : ModItems.SPARE_PARTS, 1));
        RepairModeState.addNeutralTaskProgress(player, "collector", 1, RepairModeState.COLLECTOR_TASK_NEEDED);
        RepairModeState.startSkillCooldown(player, 20 * 36, "collector_supply");
    }

    private static ServerPlayer nearestDowned(ServerLevel level, ServerPlayer player, double radius) {
        ServerPlayer best = null;
        double bestDistance = radius * radius;
        for (ServerPlayer target : level.players()) {
            if (target == player || RepairModeState.isHunter(target)) {
                continue;
            }
            var comp = ModComponents.REPAIR_ROLES.get(target);
            if (!comp.downed || comp.trialStand.present()) {
                continue;
            }
            double distance = target.distanceToSqr(player);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = target;
            }
        }
        return best;
    }

    private static void jamNearbyStations(ServerLevel level, BlockPos center, double radius, int amount, int ticks) {
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset((int) -radius, -2, (int) -radius),
                center.offset((int) radius, 2, (int) radius))) {
            if (pos.distSqr(center) <= radius * radius
                    && level.getBlockEntity(pos) instanceof RepairStationBlockEntity station) {
                station.sabotage(amount, ticks);
            }
        }
    }

    private static void start(ServerLevel level, ServerPlayer player, int cooldown, String state,
            net.minecraft.sounds.SoundEvent sound, net.minecraft.core.particles.ParticleOptions particle) {
        RepairModeState.startSkillCooldown(player, cooldown, state);
        ModComponents.REPAIR_ROLES.get(player).sync();
        level.playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, 0.9F, 1.0F);
        level.sendParticles(particle, player.getX(), player.getY() + 0.9D, player.getZ(), 18, 0.55D, 0.45D, 0.55D, 0.04D);
    }
}
