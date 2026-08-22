/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package pro.fazeclan.river.stupid_express.role.arsonist;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
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
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
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
                var alivePlayers = ((ServerLevel) level).getPlayers(GameUtils::isPlayerAliveAndSurvival);
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

            // 回放记录：为玩家浇上汽油
            SRE.REPLAY_MANAGER.recordCustomEvent(
                Component.translatable("replay.event.arsonist.douse_gasoline",
                    GameReplayUtils.getReplayPlayerDisplayText(interacting, true),
                    GameReplayUtils.getReplayPlayerDisplayText(victim, true)));

            interacting.playNotifySound(SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 1.0f, 1.0f);

            // 增加纵火犯泼油计数
            var dousedCountComponent = DousedPlayerComponent.KEY.get(player);
            dousedCountComponent.dousedCount++;
            dousedCountComponent.sync();

            return InteractionResult.CONSUME;
        }));
    }

}
