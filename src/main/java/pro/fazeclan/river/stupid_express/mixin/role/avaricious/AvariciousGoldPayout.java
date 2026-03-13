package pro.fazeclan.river.stupid_express.mixin.role.avaricious;

import io.wifi.starrailexpress.cca.StarGameWorldComponent;
import io.wifi.starrailexpress.cca.StarPlayerShopComponent;
import io.wifi.starrailexpress.game.GameFunctions;
import io.wifi.starrailexpress.game.StarRailMurderGameMode;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.fazeclan.river.stupid_express.constants.SERoles;
import pro.fazeclan.river.stupid_express.role.avaricious.AvariciousGoldHandler;

@Mixin(StarRailMurderGameMode.class)
public class AvariciousGoldPayout {

    @Inject(method = "tickServerGameLoop", at = @At("TAIL"))
    private void payout(
            ServerLevel serverWorld, StarGameWorldComponent gameWorldComponent, CallbackInfo ci) {
        
        long time = serverWorld.getGameTime();

        if (AvariciousGoldHandler.gameStartTime == -1) {
            AvariciousGoldHandler.gameStartTime = time;
            return;
        }

        long elapsed = time - AvariciousGoldHandler.gameStartTime;
        long timeinterval = elapsed % AvariciousGoldHandler.TIMER_TICKS;

        if (elapsed % AvariciousGoldHandler.TIMER_TICKS != 0) {
            if (elapsed % 20 == 0) {
                for (ServerPlayer player : serverWorld.players()) {
                    if (!gameWorldComponent.isRole(player, SERoles.AVARICIOUS))
                        continue;
                    player.sendSystemMessage(
                            Component.translatable(
                                    "hud.stupid_express.avaricious.payout_timer",
                                    ((AvariciousGoldHandler.TIMER_TICKS - timeinterval) / 20))
                                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                            true);
                }
            }
            return;
        }

        for (ServerPlayer player : serverWorld.players()) {
            if (!gameWorldComponent.isRole(player, SERoles.AVARICIOUS))
                continue;

            int nearbyPlayers = 0;
            for (ServerPlayer other : serverWorld.players()) {
                if (GameFunctions.isPlayerEliminated(other))
                    continue;
                if (other == player)
                    continue;
                if (other.distanceTo(player) <= AvariciousGoldHandler.MAX_DISTANCE)
                    nearbyPlayers++;
            }

            if (nearbyPlayers > 0) {
                int totalPlayers = serverWorld.players().size();
                // 计算平均距离
                double totalDistance = 0.0;
                for (ServerPlayer other : serverWorld.players()) {
                    if (GameFunctions.isPlayerEliminated(other))
                        continue;
                    if (other == player)
                        continue;
                    if (other.distanceTo(player) <= AvariciousGoldHandler.MAX_DISTANCE) {
                        totalDistance += other.distanceTo(player);
                    }
                }
                double avgDistance = totalDistance / nearbyPlayers;

                int payoutPerPlayer = AvariciousGoldHandler.calculatePayout(totalPlayers, nearbyPlayers, avgDistance);
                int totalPayout = nearbyPlayers * payoutPerPlayer;
                // 确保不超过150金币上限
                totalPayout = Math.min(totalPayout, 150);
                StarPlayerShopComponent.KEY.get(player).addToBalance(totalPayout);
                player.playNotifySound(TMMSounds.UI_SHOP_BUY, SoundSource.PLAYERS, 10.0f, 0.5f);
            }
        }
    }

}
