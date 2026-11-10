package pro.fazeclan.river.stupid_express.mixin.client;

import io.wifi.starrailexpress.client.SREClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pro.fazeclan.river.stupid_express.client.ArsonistClient;

/** 纵火附身死者复用难民的统一匿名外观规则，涵盖皮肤、尸体与帽子。 */
@Mixin(value = SREClient.class, remap = false)
public abstract class ArsonistSpectatorSkinMixin {
    @Inject(method = "getLooseEndPenalty", at = @At("HEAD"), cancellable = true, remap = false)
    private static void stupidExpress$hideMomentIdentities(CallbackInfoReturnable<Boolean> cir) {
        if (ArsonistClient.hasMomentDeathPenalty()) cir.setReturnValue(true);
    }
}
