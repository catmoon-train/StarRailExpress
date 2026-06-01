package io.wifi.starrailexpress.mixin.server;

import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.cca.DNFInventoryBackupComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public class PlayerDiscard {
    @Inject(method = "remove", at = @At("HEAD"))
    public void remove(ServerPlayer serverPlayer, CallbackInfo ci) {
        final var gameWorldComponent = SREGameWorldComponent.KEY.get(serverPlayer.level());
        if (gameWorldComponent != null && gameWorldComponent.isRunning()) {


                DNFInventoryBackupComponent backupComponent = DNFInventoryBackupComponent.KEY.get(serverPlayer);
                backupComponent.saveInventory();

            
            // 其他模式下，如果玩家存活则强制击杀
            if (
                     GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
                GameUtils.forceKillPlayer(serverPlayer, true, null, GameConstants.DeathReasons.DISCONNECT);
            }
        }
    }
}
