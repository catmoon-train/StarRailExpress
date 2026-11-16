package io.wifi.srepokemoncompat.mixin.mega;

import io.wifi.starrailexpress.compat.cobblemon.CobblemonMechanics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 关闭时不扫描能量点、不增删玩家关键道具；退出战斗清理仍可执行。 */
@Pseudo
@Mixin(targets = "com.github.yajatkaul.mega_showdown.gimmick.GimmickTurnCheck", remap = false)
public abstract class MegaGimmickCheckMixin {
    @Inject(method = "check", at = @At("HEAD"), cancellable = true, require = 1)
    private static void sre$environmentCheck(CallbackInfo ci) {
        if (!CobblemonMechanics.enabled()) ci.cancel();
    }
}
