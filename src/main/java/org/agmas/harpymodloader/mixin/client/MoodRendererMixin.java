package org.agmas.harpymodloader.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.wifi.starrailexpress.api.GameMode;

import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.client.gui.MoodRenderer;
import org.agmas.harpymodloader.Harpymodloader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MoodRenderer.class)
public class MoodRendererMixin {
    @Redirect(method = "renderHud", at = @At(value = "INVOKE", target = "Lio/wifi/starrailexpress/cca/GameWorldComponent;getGameMode()Lio/wifi/starrailexpress/api/GameMode;"))
    private static GameMode a(GameWorldComponent instance) {
        if (instance.getGameMode().equals(Harpymodloader.MODDED_GAMEMODE)) return SREGameModes.MURDER;
        return instance.getGameMode();
    }
}
