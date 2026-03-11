package io.wifi.starrailexpress.mixin.client.restrictions;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DebugScreenOverlay.class)
public class DebugHudMixin {
    @ModifyReturnValue(method = "showDebugScreen", at = @At("RETURN"))
    public boolean shouldShowDebugHud(boolean original) {
        if (SREClient.isInLobby) {
            return original;
        }
        return SREClient.isPlayerCreative() && original;
    }
}
