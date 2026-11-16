package io.wifi.srepokemoncompat.mixin.journeymap;

import io.wifi.srepackcompat.cobblemon.CobblemonPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 小地图所有渲染层共用入口；不修改玩家显示偏好。界面打开由共享 ScreensMixin 拦截。 */
@Pseudo
@Mixin(targets = "journeymap.client.ui.UIManager", remap = false)
public class MapUiMixin {
    @Inject(method = {"drawMiniMap", "drawWaypointDecorations"}, at = @At("HEAD"), cancellable = true, require = 2)
    private void sre$requireServerPermission(CallbackInfo ci) {
        if (!CobblemonPolicy.enabled()) ci.cancel();
    }
}
