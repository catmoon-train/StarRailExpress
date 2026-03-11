package pro.fazeclan.river.stupid_express.mixin.modifier.loose_end;

import io.wifi.starrailexpress.game.GameFunctions;
import io.wifi.starrailexpress.network.RemoveStatusBarPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameFunctions.class)
public class LooseEndBarClearMixin {
    @Inject(method = "stopGame", at = @At("HEAD"))
    private static void stopGame(ServerLevel world, CallbackInfo ci){
        world.players().forEach(serverPlayer -> {
            RemoveStatusBarPayload payload = new RemoveStatusBarPayload("loose_end");
            ServerPlayNetworking.send(serverPlayer, payload);
        });
    }
}
