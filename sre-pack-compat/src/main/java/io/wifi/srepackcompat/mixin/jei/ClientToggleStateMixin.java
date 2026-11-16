package io.wifi.srepackcompat.mixin.jei;

import io.wifi.srepackcompat.jei.JeiLobbyGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 仅覆盖当前可用性；返回大厅后仍沿用玩家保存的叠加层和书签偏好。 */
@Pseudo
@Mixin(targets = "mezz.jei.common.config.ClientToggleState", remap = false)
public class ClientToggleStateMixin {
    @Inject(method = {"isOverlayEnabled", "isBookmarkOverlayEnabled", "isBookmarkEnabled", "isCheatItemsEnabled", "isEditModeEnabled"},
            at = @At("HEAD"), cancellable = true)
    private void sre$lobbyOnly(CallbackInfoReturnable<Boolean> cir) {
        if (!JeiLobbyGate.enabled()) cir.setReturnValue(false);
    }
}
