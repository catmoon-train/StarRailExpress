package pro.fazeclan.river.stupid_express.role.arsonist;

import io.wifi.starrailexpress.cca.StarGameWorldComponent;
import io.wifi.starrailexpress.game.GameFunctions;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import pro.fazeclan.river.stupid_express.constants.SEItems;
import pro.fazeclan.river.stupid_express.constants.SERoles;
import pro.fazeclan.river.stupid_express.role.arsonist.cca.DousedPlayerComponent;

public class OilDousingHandler {

    public static void init() {
        UseEntityCallback.EVENT.register(((player, level, interactionHand, entity, entityHitResult) -> {
            if (!(player instanceof ServerPlayer interacting)) {
                return InteractionResult.PASS;
            }
            if (!interacting.gameMode.isSurvival()) {
                return InteractionResult.PASS;
            }
            StarGameWorldComponent gameWorldComponent = StarGameWorldComponent.KEY.get(player.level());
            if (!gameWorldComponent.isRole(player, SERoles.ARSONIST)) {
                return InteractionResult.PASS;
            }
            if (!(entity instanceof ServerPlayer victim)) {
                return InteractionResult.PASS;
            }

            var item = player.getItemInHand(interactionHand);
            if (!item.is(SEItems.JERRY_CAN)) {
                return InteractionResult.PASS;
            }
            if (interacting.getCooldowns().isOnCooldown(item.getItem())) {
                return InteractionResult.PASS;
            }

            if (!gameWorldComponent.isSkillAvailable) {
                // 技能不可用
                player.displayClientMessage(
                        Component.translatable("message.stupid_express.generic.skill_not_available").withStyle(ChatFormatting.RED), true);
                return InteractionResult.PASS;
            }
            if (interacting.gameMode.isSurvival()) {
                var alivePlayers = ((ServerLevel) level).getPlayers(GameFunctions::isPlayerAliveAndSurvival);
                var playerCount = alivePlayers.size();
                var dousedPlayers = alivePlayers.stream().filter(p -> DousedPlayerComponent.KEY.get(p).getDoused())
                        .toList();
                var cd = 45 - (5 / 3.0) * (double) playerCount;

                if (playerCount > 15) {
                    cd = 20;
                }

                interacting.getCooldowns().addCooldown(item.getItem(), (int) (cd * 20));
                if (dousedPlayers.size() >= (int) (alivePlayers.size() * 0.3)) {
                    interacting.getCooldowns().addCooldown(SEItems.LIGHTER, (int) (cd * 20));
                }
            }
            DousedPlayerComponent doused = DousedPlayerComponent.KEY.get(victim);
            doused.setDoused(true);

            interacting.playNotifySound(SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 1.0f, 1.0f);

            // 增加纵火犯泼油计数
            var dousedCountComponent = DousedPlayerComponent.KEY.get(player);
            dousedCountComponent.dousedCount++;

            return InteractionResult.CONSUME;
        }));
    }

}
