package org.agmas.noellesroles.mixin.accessor;

import io.wifi.starrailexpress.cca.PlayerPoisonComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerPoisonComponent.class)
public interface PlayerPoisonComponentAccessor {
    @Accessor("poisonTicks")
    int getPoisonTicks();
}