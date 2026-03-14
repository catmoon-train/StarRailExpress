package org.agmas.noellesroles.mixin.accessor;

import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SREPlayerPoisonComponent.class)
public interface PlayerPoisonComponentAccessor {
    @Accessor("poisonTicks")
    int getPoisonTicks();
}