package org.agmas.noellesroles.mixin.client.time_stop;

import io.wifi.starrailexpress.cca.StarGameTimeComponent;
import net.minecraft.client.Minecraft;
import org.agmas.noellesroles.effects.TimeStopEffect;
import org.agmas.noellesroles.init.ModEffects;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.CommonTickingComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StarGameTimeComponent.class)
public abstract class GameTimeFreezeMixin implements AutoSyncedComponent, CommonTickingComponent {
    @Inject(method = "getTime", at = @At("HEAD"), cancellable = true)
    public void getTime(CallbackInfoReturnable<Integer> cir) {
        if (Minecraft.getInstance().player.hasEffect(ModEffects.TIME_STOP)){
            cir.setReturnValue(TimeStopEffect.freezeStatedTime);
            cir.cancel();
        }
    }
}
