package org.agmas.noellesroles.mixin.accessor;

import io.wifi.starrailexpress.cca.StarPlayerPoisonComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StarPlayerPoisonComponent.class)
public interface PlayerPoisonComponentAccessor {
    @Accessor("poisonTicks")
    int getPoisonTicks();
}