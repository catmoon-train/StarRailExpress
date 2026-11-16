package io.wifi.srepokemoncompat.mixin.mega.client;

import io.wifi.srepackcompat.cobblemon.CobblemonPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 只禁止 Mega 的独立 HUD 回调，不取消 Minecraft Gui.render，也不屏蔽战斗同步。 */
@Pseudo
@Mixin(targets = "com.github.yajatkaul.mega_showdown.client.battle.hud.BattleHUD", remap = false)
public abstract class MegaBattleHudMixin {
    @Inject(method = {"hudCallback", "drawStatChanges"}, at = @At("HEAD"), cancellable = true, require = 2)
    private static void sre$hideMegaHud(CallbackInfo ci) {
        if (!CobblemonPolicy.enabled()) ci.cancel();
    }
}
