package io.wifi.starrailexpress.mixin.server;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.SRE;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public class PlayerDiscord {
    @Inject( method = "remove", at = @At("HEAD"))
     public void remove(ServerPlayer serverPlayer, CallbackInfo ci) {
        final var gameWorldComponent = SREGameWorldComponent.KEY.get(serverPlayer.level());
        if (gameWorldComponent != null && gameWorldComponent.isRunning() && GameUtils.isPlayerAliveAndSurvival( serverPlayer)) {

            if (System.currentTimeMillis() - GameUtils.startTime > 45000) {
                GameUtils.killPlayer(serverPlayer, true, null, SRE.id("disconnected"));
            }
        }
    }
}
