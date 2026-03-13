package org.agmas.harpymodloader.mixin;


import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.cca.StarGameWorldComponent;
import io.wifi.starrailexpress.game.GameFunctions;
import net.minecraft.server.level.ServerLevel;
import org.agmas.harpymodloader.Harpymodloader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameFunctions.class)
public class GameFunctionsMixin {
    @Inject(method = "initializeGame", at = @At("HEAD"))
    private static void a(ServerLevel serverWorld, CallbackInfo ci) {
        StarGameWorldComponent gameComponent = (StarGameWorldComponent)StarGameWorldComponent.KEY.get(serverWorld);
        if (gameComponent.getGameMode().equals(SREGameModes.MURDER) && !Harpymodloader.wantsToStartVannila) {
            gameComponent.setGameMode(Harpymodloader.MODDED_GAMEMODE);
        }
        Harpymodloader.wantsToStartVannila = false;
    }
}
