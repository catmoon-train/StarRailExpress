package pro.fazeclan.river.stupid_express.mixin.role.necromancer;

import io.wifi.starrailexpress.game.GameFunctions;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.fazeclan.river.stupid_express.role.necromancer.cca.NecromancerComponent;

@Mixin(GameFunctions.class)
public class NecromancerStatsMixin {
    @Inject(
            method = "finalizeGame",
            at = @At("TAIL")
    )
    private static void resetNecroStat(ServerLevel world, CallbackInfo ci) {

        var component = NecromancerComponent.KEY.get(world);
        component.reset();

    }

}
