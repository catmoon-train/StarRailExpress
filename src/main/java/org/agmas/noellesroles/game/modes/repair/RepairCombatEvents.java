package org.agmas.noellesroles.game.modes.repair;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import org.agmas.noellesroles.component.ModComponents;

public final class RepairCombatEvents {
    private RepairCombatEvents() {
    }

    public static void register() {
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            if (ModComponents.REPAIR_ROLES.get(serverPlayer).carrying != null) {
                serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.repair.cannot_attack_carrying")
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResult.FAIL;
            }
//            boolean repairActor = RepairModeState.isHunter(serverPlayer) || RepairModeState.isNonHunterRepairPlayer(serverPlayer);
//            if (repairActor) {
//                RepairModeState.broadcastCombatFeedback(serverLevel, RepairCombatFeedbackS2CPacket.ATTACK, serverPlayer,
//                        serverPlayer.getX(), serverPlayer.getY() + 1.0D, serverPlayer.getZ(), 20.0D);
//                if (entity instanceof LivingEntity target) {
//                    RepairModeState.broadcastCombatFeedback(serverLevel, RepairCombatFeedbackS2CPacket.HIT, target,
//                            target.getX(), target.getY() + target.getBbHeight() * 0.55D, target.getZ(), 20.0D);
//                    serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
//                            target.getX(), target.getY() + target.getBbHeight() * 0.55D, target.getZ(), 1,
//                            0.15D, 0.2D, 0.15D, 0.0D);
//                    serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.DAMAGE_INDICATOR,
//                            target.getX(), target.getY() + target.getBbHeight() * 0.65D, target.getZ(), 4,
//                            0.25D, 0.25D, 0.25D, 0.05D);
//                }
//                if (RepairModeState.isHunter(serverPlayer)) {
//                    serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.repair.use_hunter_weapon")
//                            .withStyle(ChatFormatting.YELLOW), true);
//                }
//                return InteractionResult.FAIL;
//            }
            return InteractionResult.PASS;
        });
    }
}
