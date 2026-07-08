package net.exmo.sre.repair.logic;

import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.network.TriggerScreenEdgeEffectPayload;
import net.exmo.sre.repair.state.RepairModeState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.component.ModComponents;

/**
 * 理智值系统（恐鬼症融合）：
 * 黑暗、猎人逼近、倒地/被背/受审持续侵蚀理智；靠近队友、待在亮处缓慢恢复。
 * 低理智分级触发幻觉：心跳 → 鬼影与耳语 → 黑暗脉冲与紫屏 → 精神崩溃（对猎人发光暴露）。
 */
public final class RepairSanitySystem {
    public static final int MAX_SANITY = 100;
    public static final int WHISPER_THRESHOLD = 60;
    public static final int APPARITION_THRESHOLD = 40;
    public static final int PANIC_THRESHOLD = 25;
    public static final int BREAKDOWN_THRESHOLD = 10;

    private static final int UPDATE_INTERVAL = 40; // 2s

    private RepairSanitySystem() {
    }

    public static void tick(ServerLevel level) {
        long now = level.getGameTime();
        for (ServerPlayer player : level.players()) {
            if (!RepairModeState.isNonHunterRepairPlayer(player) || GameUtils.isPlayerEliminated(player)
                    || player.getTags().contains(RepairModeState.ESCAPED_TAG)
                    || ModComponents.REPAIR_ROLES.get(player).activeRole.isEmpty()) {
                continue;
            }
            // 按玩家错开更新相位，避免全员同帧掉理智
            long phase = (player.getUUID().getLeastSignificantBits() & 0x1F);
            if ((now + phase) % UPDATE_INTERVAL == 0) {
                updateSanity(level, player);
            }
            applyHallucinations(level, player, now, phase);
        }
    }

    private static void updateSanity(ServerLevel level, ServerPlayer player) {
        var component = ModComponents.REPAIR_ROLES.get(player);
        int delta = 0;
        BlockPos pos = player.blockPosition();
        int light = level.getMaxLocalRawBrightness(pos);
        double hunterDistanceSqr = nearestHunterDistanceSqr(level, player);

        if (light < 4) {
            delta -= 2; // 黑暗是恐惧之源
        }
        if (hunterDistanceSqr <= 12 * 12) {
            delta -= 2;
        } else if (hunterDistanceSqr <= 24 * 24) {
            delta -= 1;
        }
        if (component.downed || component.carriedBy != null || component.trialStand.present()) {
            delta -= 3;
        }
        if (hasCalmCompany(level, player)) {
            delta += 2; // 结伴同行可以壮胆
        }
        if (light >= 10 && hunterDistanceSqr > 24 * 24 && !component.downed) {
            delta += 1;
        }
        if (delta != 0) {
            setSanity(player, component.sanity + delta);
        }
    }

    private static void applyHallucinations(ServerLevel level, ServerPlayer player, long now, long phase) {
        int sanity = ModComponents.REPAIR_ROLES.get(player).sanity;
        if (sanity >= WHISPER_THRESHOLD) {
            return;
        }
        // 心跳（<60）
        if ((now + phase) % 280 == 0) {
            player.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 0.8F, 0.9F);
        }
        // 鬼影与耳语（<40）
        if (sanity < APPARITION_THRESHOLD && (now + phase * 3) % 400 == 0) {
            spawnApparition(level, player);
        }
        // 黑暗脉冲与紫屏（<25）
        if (sanity < PANIC_THRESHOLD && (now + phase * 5) % 320 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 4, 0, false, false, true));
            ServerPlayNetworking.send(player, new TriggerScreenEdgeEffectPayload(0x6A0DAD, 900, 0.7F));
            player.playNotifySound(SoundEvents.SOUL_ESCAPE.value(), SoundSource.AMBIENT, 1.0F, 0.55F);
            player.displayClientMessage(Component.translatable("message.noellesroles.repair.sanity_panic")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
        }
        // 精神崩溃（<=10）：对猎人短暂发光暴露
        if (sanity <= BREAKDOWN_THRESHOLD && (now + phase * 7) % 240 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 3, 0, false, false, true));
            player.playNotifySound(SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.AMBIENT, 0.7F, 0.5F);
            player.displayClientMessage(Component.translatable("message.noellesroles.repair.sanity_breakdown")
                    .withStyle(ChatFormatting.DARK_RED), false);
        }
    }

    /** 仅对该玩家可见的鬼影：视线前方浮现魂魄粒子并伴随耳语。 */
    private static void spawnApparition(ServerLevel level, ServerPlayer player) {
        Vec3 look = player.getViewVector(1.0F);
        Vec3 spot = player.getEyePosition().add(look.scale(5.0D));
        level.sendParticles(player, ParticleTypes.SCULK_SOUL, true,
                spot.x, spot.y, spot.z, 14, 0.25D, 0.6D, 0.25D, 0.015D);
        level.sendParticles(player, ParticleTypes.SOUL, true,
                spot.x, spot.y - 0.4D, spot.z, 8, 0.2D, 0.5D, 0.2D, 0.01D);
        player.playNotifySound(SoundEvents.WARDEN_LISTENING, SoundSource.AMBIENT, 0.9F, 0.65F);
        player.displayClientMessage(Component.translatable("message.noellesroles.repair.sanity_whisper")
                .withStyle(ChatFormatting.DARK_GRAY), true);
    }

    private static boolean hasCalmCompany(ServerLevel level, ServerPlayer player) {
        for (ServerPlayer other : level.players()) {
            if (other == player || GameUtils.isPlayerEliminated(other)
                    || other.getTags().contains(RepairModeState.ESCAPED_TAG)
                    || RepairModeState.isHunter(other)
                    || ModComponents.REPAIR_ROLES.get(other).downed) {
                continue;
            }
            if (other.distanceToSqr(player) <= 8 * 8) {
                return true;
            }
        }
        return false;
    }

    private static double nearestHunterDistanceSqr(ServerLevel level, ServerPlayer player) {
        double best = Double.MAX_VALUE;
        for (ServerPlayer other : level.players()) {
            if (other == player || GameUtils.isPlayerEliminated(other) || !RepairModeState.isHunter(other)) {
                continue;
            }
            best = Math.min(best, other.distanceToSqr(player));
        }
        return best;
    }

    public static void drain(ServerPlayer player, int amount) {
        setSanity(player, ModComponents.REPAIR_ROLES.get(player).sanity - amount);
    }

    public static void restore(ServerPlayer player, int amount) {
        setSanity(player, ModComponents.REPAIR_ROLES.get(player).sanity + amount);
    }

    private static void setSanity(ServerPlayer player, int value) {
        var component = ModComponents.REPAIR_ROLES.get(player);
        int clamped = Mth.clamp(value, 0, MAX_SANITY);
        if (clamped != component.sanity) {
            component.sanity = clamped;
            component.sync();
        }
    }
}
